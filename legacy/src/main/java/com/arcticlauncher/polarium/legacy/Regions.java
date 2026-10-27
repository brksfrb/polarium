package com.arcticlauncher.polarium.legacy;

import com.mojang.blaze3d.platform.GLX;

/**
 * Chunk sections are compiled relative to the 128 by 128 block region (full height) they sit in instead of
 * their own corner, so all the sections of a region can be drawn under one translate. Only the display
 * list renderer (the default; vertex buffers off) uses it.
 */
public final class Regions {
	/** Region size in blocks (a power of two, a multiple of 16). */
	public static final int SIZE = 128;

	private Regions() {
	}

	public static boolean active() {
		return Flags.REGIONS && !GLX.supportsVbo();
	}

	/** The west/north corner of the region that holds this block coordinate. */
	public static int origin(int blockCoordinate) {
		return blockCoordinate & -SIZE;
	}
}
