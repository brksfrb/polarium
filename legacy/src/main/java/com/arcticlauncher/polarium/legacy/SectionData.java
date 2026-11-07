package com.arcticlauncher.polarium.legacy;

/**
 * What Polarium keeps per chunk section about the geometry it last uploaded, filled on the render thread when
 * a layer's display list or vertex buffer is made (so it always matches what is in it). Implemented on the
 * game's section class by a mixin.
 */
public interface SectionData {
	/** Vertices in the layer's display list or vertex buffer as last uploaded, by layer ordinal. */
	int[] polarium$vertices();
}
