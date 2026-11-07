package com.arcticlauncher.polarium.legacy;

import org.lwjgl.opengl.GL11;

/**
 * Pipelined submission on software OpenGL (Mesa's llvmpipe and softpipe). A software driver only starts
 * rasterizing once the commands are flushed, so by default the game's thread (which does the vertex work,
 * clipping and binning) and the rasterizer threads take turns: the frame costs both added up. A glFlush
 * every so many vertices hands what is queued to the rasterizer threads while the game keeps going.
 * glFlush never changes what is drawn.
 *
 * <p>The budget is counted in vertices, not in calls: one region call can hold a hundred thousand
 * vertices, one section call a few hundred. Hardware drivers queue work on their own, so there it stays off.
 */
public final class Pipeline {
	/** Vertices between two flushes (about a dozen to twenty terrain sections). */
	public static final int BUDGET = Math.max(1000, Integer.getInteger("polarium.pipeline.vertices", 20000));
	/** Below this many queued vertices a pass boundary does not flush (a flush has a fixed cost). */
	private static final int MIN_AT_BOUNDARY = BUDGET / 4;

	/** 0: not decided yet (needs the GL context), 1: on, -1: off. */
	private static int state;
	private static int pending;
	private static int calls;

	private Pipeline() {
	}

	/** True on a software renderer with the switch on. Call on the render thread. */
	public static boolean active() {
		if (state == 0) {
			decide();
		}
		return state > 0;
	}

	private static void decide() {
		if (!Flags.PIPELINE) {
			state = -1;
			return;
		}
		String renderer;
		try {
			renderer = GL11.glGetString(GL11.GL_RENDERER);
		} catch (Throwable t) {
			renderer = null;
		}
		boolean software = isSoftware(renderer);
		state = software ? 1 : -1;
		System.out.println("[Polarium] pipelined submission " + (software ? "on, a flush every " + BUDGET + " vertices" : "off (not a software renderer)")
				+ ": " + renderer);
	}

	static boolean isSoftware(String renderer) {
		if (renderer == null) {
			return false;
		}
		String r = renderer.toLowerCase(java.util.Locale.ROOT);
		return r.contains("llvmpipe") || r.contains("softpipe");
	}

	/** True when this many more vertices reach the budget: the caller submits what it holds, then calls {@link #flush()}. */
	public static boolean due(int vertices) {
		return pending + vertices >= BUDGET;
	}

	/** Counts vertices just submitted; flushes when the budget is reached. */
	public static void submitted(int vertices) {
		pending += vertices;
		if (pending >= BUDGET) {
			flush();
		}
	}

	/** One draw call whose size is unknown (the game's own section path): a flush every sixteen. */
	public static void submittedCall() {
		if (++calls >= 16) {
			flush();
		}
	}

	/** End of a pass: hand what is queued to the rasterizer while the game prepares the next one. */
	public static void boundary() {
		if (pending >= MIN_AT_BOUNDARY || calls > 0) {
			flush();
		}
	}

	/** After a pass with a lot of pixels and few vertices (sky, clouds, entities): always worth handing over. */
	public static void passEnd() {
		if (active()) {
			flush();
		}
	}

	public static void flush() {
		GL11.glFlush();
		pending = 0;
		calls = 0;
	}
}
