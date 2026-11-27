package com.arcticlauncher.polarium.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** The twin compile's states: pending draws the game's way, compiled draws the twin, failed stays the game's way. */
class AsyncCompileTest {
	/** A compiled pipeline stand-in. */
	static final class Compiled {
		boolean closed;
	}

	/** Hands out futures the test completes; counts starts. */
	static final class FakeCompiler implements AsyncCompile.Compiler<Compiled> {
		final List<CompletableFuture<Supplier<Compiled>>> started = new ArrayList<>();
		RuntimeException throwOnStart;

		@Override
		public CompletableFuture<Supplier<Compiled>> start() {
			if (throwOnStart != null) {
				throw throwOnStart;
			}
			CompletableFuture<Supplier<Compiled>> future = new CompletableFuture<>();
			started.add(future);
			return future;
		}

		@Override
		public boolean isClosed(Compiled compiled) {
			return compiled.closed;
		}

		CompletableFuture<Supplier<Compiled>> last() {
			return started.get(started.size() - 1);
		}
	}

	private final FakeCompiler compiler = new FakeCompiler();
	private final List<Throwable> failures = new ArrayList<>();
	private int failureCalls;
	private final AsyncCompile<Compiled> compile = new AsyncCompile<>(compiler, e -> {
		failureCalls++;
		failures.add(e);
	});

	@Test
	void newIsNotReadyAndOffThreadReadsDontStart() {
		assertEquals(AsyncCompile.State.NEW, compile.state());
		assertNull(compile.ready());
		assertEquals(0, compiler.started.size());
	}

	@Test
	void pendingUsesTheGamesPipelineWithoutWaiting() {
		assertNull(compile.advance());
		assertEquals(AsyncCompile.State.COMPILING, compile.state());
		// A compile that never finishes (crash 596): every frame just checks, never waits.
		for (int frame = 0; frame < 1000; frame++) {
			assertNull(compile.advance());
		}
		assertEquals(1, compiler.started.size());
		assertEquals(AsyncCompile.State.COMPILING, compile.state());
		assertEquals(0, failureCalls);
	}

	@Test
	void successUsesTheTwin() {
		compile.advance();
		Compiled done = new Compiled();
		compiler.last().complete(() -> done);
		assertNull(compile.ready(), "not taken until the render thread finishes it");
		assertSame(done, compile.advance());
		assertEquals(AsyncCompile.State.READY, compile.state());
		assertSame(done, compile.ready());
		assertSame(done, compile.advance());
		assertEquals(1, compiler.started.size());
	}

	@Test
	void alreadyFinishedIsReadyAtOnce() {
		Compiled done = new Compiled();
		AsyncCompile<Compiled> now = new AsyncCompile<>(new AsyncCompile.Compiler<>() {
			@Override
			public CompletableFuture<Supplier<Compiled>> start() {
				return CompletableFuture.completedFuture(() -> done);
			}

			@Override
			public boolean isClosed(Compiled compiled) {
				return false;
			}
		}, e -> failureCalls++);
		assertSame(done, now.advance());
		assertEquals(0, failureCalls);
	}

	@Test
	void exceptionalFutureFailsForGoodAndLogsOnce() {
		compile.advance();
		IllegalStateException boom = new IllegalStateException("shader stalled");
		compiler.last().completeExceptionally(boom);
		assertNull(compile.advance());
		assertEquals(AsyncCompile.State.FAILED, compile.state());
		for (int frame = 0; frame < 10; frame++) {
			assertNull(compile.advance());
			assertNull(compile.ready());
		}
		assertEquals(1, compiler.started.size(), "never compiled again");
		assertEquals(1, failureCalls);
		assertSame(boom, failures.get(0));
	}

	@Test
	void compiledToNothingFailsForGood() {
		compile.advance();
		compiler.last().complete(() -> null);
		assertNull(compile.advance());
		assertEquals(AsyncCompile.State.FAILED, compile.state());
		assertEquals(1, failureCalls);
		assertNull(failures.get(0));
	}

	@Test
	void finishThrowingFailsForGood() {
		compile.advance();
		RuntimeException boom = new RuntimeException("link failed");
		compiler.last().complete(() -> {
			throw boom;
		});
		assertNull(compile.advance());
		assertEquals(AsyncCompile.State.FAILED, compile.state());
		assertSame(boom, failures.get(0));
		assertNull(compile.advance());
		assertEquals(1, failureCalls);
	}

	@Test
	void startThrowingFailsForGood() {
		compiler.throwOnStart = new IllegalStateException("no pipeline cache yet");
		assertNull(compile.advance());
		assertEquals(AsyncCompile.State.FAILED, compile.state());
		compiler.throwOnStart = null;
		assertNull(compile.advance());
		assertEquals(0, compiler.started.size());
		assertEquals(1, failureCalls);
	}

	@Test
	void cancelledFailsForGood() {
		compile.advance();
		compiler.last().cancel(false);
		assertNull(compile.advance());
		assertEquals(AsyncCompile.State.FAILED, compile.state());
		assertEquals(1, failureCalls);
	}

	@Test
	void freedByAReloadCompilesAgainWithoutWaiting() {
		compile.advance();
		Compiled first = new Compiled();
		compiler.last().complete(() -> first);
		assertSame(first, compile.advance());
		first.closed = true;
		assertNull(compile.advance(), "the game's pipeline again while it recompiles");
		assertEquals(AsyncCompile.State.COMPILING, compile.state());
		assertEquals(2, compiler.started.size());
		Compiled second = new Compiled();
		compiler.last().complete(() -> second);
		assertSame(second, compile.advance());
		assertEquals(0, failureCalls);
	}
}
