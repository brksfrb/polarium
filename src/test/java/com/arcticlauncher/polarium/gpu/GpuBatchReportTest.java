package com.arcticlauncher.polarium.gpu;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Executes the compiled report method without creating GPU resources or starting Minecraft. */
public class GpuBatchReportTest {
	private Object batches;
	private Method report;
	private final CountingList<CountingList<FakeBatch>> groups = new CountingList<>();
	private final List<Object[]> logged = new ArrayList<>();

	@BeforeEach void loadReport() throws Exception {
		String name = "com/arcticlauncher/polarium/gpu/GpuBatches";
		String fakeBatch = FakeBatch.class.getName().replace('.', '/');
		String flags = Flags.class.getName().replace('.', '/');
		ClassWriter writer = new ClassWriter(0);
		try (InputStream in = getClass().getResourceAsStream("/" + name + ".class")) {
			assumeTrue(in != null, "GPU batches are compiled only for 26.2+");
			new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9, writer) {
				@Override public void visit(int version, int access, String n, String sig, String parent, String[] interfaces) {
					super.visit(version, access, n, null, "java/lang/Object", null);
				}
				@Override public FieldVisitor visitField(int access, String n, String desc, String sig, Object value) {
					return Arrays.asList("groups", "label", "lastReport", "LOG").contains(n)
							? super.visitField(access & ~Opcodes.ACC_FINAL, n, desc, sig, value) : null;
				}
				@Override public MethodVisitor visitMethod(int access, String n, String desc, String sig, String[] exceptions) {
					if (!n.equals("report")) return null;
					return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, n, desc, sig, exceptions)) {
						@Override public void visitFieldInsn(int op, String owner, String field, String descriptor) {
							if (owner.equals("com/arcticlauncher/polarium/Timeline")) owner = flags;
							if (owner.equals(name + "$Batch")) owner = fakeBatch;
							super.visitFieldInsn(op, owner, field, descriptor);
						}
						@Override public void visitTypeInsn(int op, String type) {
							super.visitTypeInsn(op, type.equals(name + "$Batch") ? fakeBatch : type);
						}
						@Override public void visitMethodInsn(int op, String owner, String method, String descriptor, boolean itf) {
							if (owner.equals("java/lang/System") && method.equals("nanoTime")) owner = flags;
							super.visitMethodInsn(op, owner, method, descriptor, itf);
						}
					};
				}
			}, 0);
		}
		// Only construction of GPU resources is replaced; the complete report bytecode above is unchanged.
		MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		constructor.visitCode();
		constructor.visitVarInsn(Opcodes.ALOAD, 0);
		constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		constructor.visitInsn(Opcodes.RETURN);
		constructor.visitMaxs(1, 1);
		constructor.visitEnd();
		Class<?> type = new ClassLoader(getClass().getClassLoader()) {
			Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
		}.define(writer.toByteArray());
		batches = type.getConstructor().newInstance();
		report = type.getDeclaredMethod("report");
		report.setAccessible(true);
		set("groups", groups);
		set("label", "entities");
		set("LOG", Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Logger.class}, (proxy, method, args) -> {
			if (method.getName().equals("info")) logged.add((Object[]) args[1]);
			return null;
		}));
		CountingList<FakeBatch> group = new CountingList<>();
		group.add(new FakeBatch(2));
		group.add(new FakeBatch(5));
		groups.add(group);
		Flags.REPORTS = false;
		Flags.clocks = 0;
		Flags.now = 20_000_000_000L;
	}

	@Test void disabledReportDoesNotReadClockOrVisitBatches() throws Exception {
		for (int frame = 0; frame < 10000; frame++) report.invoke(batches);
		assertEquals(0, Flags.clocks);
		assertEquals(0, groups.iterations);
		assertTrue(logged.isEmpty());
	}

	@Test void enabledReportCountsOnlyWhenDueAndKeepsItsMessage() throws Exception {
		Flags.REPORTS = true;
		report.invoke(batches);
		assertArrayEquals(new Object[] {7, "entities", 2}, logged.get(0));
		assertEquals(1, groups.iterations);
		for (int frame = 0; frame < 100; frame++) report.invoke(batches);
		assertEquals(1, groups.iterations);
		assertEquals(1, logged.size());
		Flags.now += 10_000_000_001L;
		report.invoke(batches);
		assertEquals(2, logged.size());
	}

	@Test void emptyFrameDoesNotDelayTheNextNonemptyReport() throws Exception {
		Flags.REPORTS = true;
		CountingList<FakeBatch> group = groups.remove(0);
		report.invoke(batches);
		assertTrue(logged.isEmpty());
		groups.add(group);
		report.invoke(batches);
		assertArrayEquals(new Object[] {7, "entities", 2}, logged.get(0));
	}

	private void set(String name, Object value) throws Exception {
		Field field = batches.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(batches, value);
	}

	public static final class Flags {
		public static boolean REPORTS;
		static int clocks;
		static long now;
		public static long nanoTime() { clocks++; return now; }
	}
	public static final class FakeBatch {
		public int instances;
		FakeBatch(int n) { instances = n; }
	}
	private static final class CountingList<T> extends ArrayList<T> {
		int iterations;
		@Override public Iterator<T> iterator() { iterations++; return super.iterator(); }
	}
}
