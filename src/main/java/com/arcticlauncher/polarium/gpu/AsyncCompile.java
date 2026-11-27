//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One pipeline's compile, never waited on: joining a compile that stalled in the driver
 * froze the game for good (crash 596, an Intel iGPU on 26.3). While it compiles the
 * game's own pipeline draws instead; once it compiled it's {@link #ready}; if it can't
 * compile, the game's pipeline draws from then on.
 *
 * <p>{@link #advance} is for the render thread only (it starts and finishes compiles);
 * {@link #ready} is safe on any thread.
 */
final class AsyncCompile<C> {
	enum State {
		/** Not started (or freed by a resource reload, to compile again). */
		NEW,
		/** Started: the game's pipeline draws meanwhile. */
		COMPILING,
		/** Compiled: draw with it. */
		READY,
		/** Won't compile: the game's pipeline draws, for good. */
		FAILED,
	}

	interface Compiler<C> {
		/**
		 * Starts compiling, without waiting for it (render thread). The future's value finishes
		 * the compile on the render thread and returns the result, or null if it didn't compile.
		 */
		CompletableFuture<? extends Supplier<? extends C>> start();

		/** Whether a compiled result was freed since (a resource reload clears the game's pipeline cache). */
		boolean isClosed(C compiled);
	}

	private final Compiler<C> compiler;
	/** Told once, when it fails (null: it compiled to nothing, no exception). */
	private final Consumer<Throwable> onFailure;
	private volatile State state = State.NEW;
	/** Written before {@link #state} turns READY. */
	private volatile C compiled;
	/** The compile under way (render thread only). */
	private CompletableFuture<? extends Supplier<? extends C>> pending;

	AsyncCompile(Compiler<C> compiler, Consumer<Throwable> onFailure) {
		this.compiler = compiler;
		this.onFailure = onFailure;
	}

	State state() {
		return state;
	}

	/** The compiled result, or null while it compiles or if it never will (any thread, never waits). */
	C ready() {
		return state == State.READY ? compiled : null;
	}

	/** Starts the compile, or takes it once done (render thread, never waits); then {@link #ready}. */
	C advance() {
		switch (state) {
			case NEW -> start();
			case COMPILING -> finishIfDone();
			case READY -> {
				C done = compiled;
				if (done != null && compiler.isClosed(done)) {
					state = State.NEW;
					compiled = null;
					start();
				}
			}
			case FAILED -> {
			}
		}
		return ready();
	}

	private void start() {
		try {
			pending = java.util.Objects.requireNonNull(compiler.start(), "no compile started");
		} catch (RuntimeException | LinkageError e) {
			fail(e);
			return;
		}
		state = State.COMPILING;
		// One that finished at once (a backend that compiles right away) is ready this frame.
		finishIfDone();
	}

	private void finishIfDone() {
		CompletableFuture<? extends Supplier<? extends C>> future = pending;
		if (future == null || !future.isDone()) {
			return;
		}
		pending = null;
		Supplier<? extends C> finish;
		try {
			finish = future.getNow(null);
		} catch (CompletionException | CancellationException e) {
			fail(e.getCause() != null ? e.getCause() : e);
			return;
		}
		C result;
		try {
			result = finish != null ? finish.get() : null;
		} catch (RuntimeException | LinkageError e) {
			fail(e);
			return;
		}
		if (result == null) {
			fail(null);
			return;
		}
		compiled = result;
		state = State.READY;
	}

	private void fail(Throwable cause) {
		state = State.FAILED;
		onFailure.accept(cause);
	}
}
//#endif
