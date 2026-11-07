package com.arcticlauncher.polarium.legacy;

/**
 * What Polarium keeps per chunk section about the geometry it last uploaded, filled on the render thread when
 * a layer's display list or vertex buffer is made (so it always matches what is in it). Implemented on the
 * game's section class by a mixin.
 */
public interface SectionData {
	/** Vertices in the layer's display list or vertex buffer as last uploaded, by layer ordinal. */
	int[] polarium$vertices();

	/** Whether the layer was uploaded split by face direction (see {@link FaceBuckets}), by layer ordinal. */
	boolean[] polarium$split();

	/** Vertices per face bucket, {@link FaceBuckets#BUCKETS} per split layer. */
	int[] polarium$bucketVertices();

	/** Lowest and highest plane of each axis bucket, 12 per split layer, in the coordinates the section is compiled in. */
	float[] polarium$bucketPlanes();

	/** First of the section's face bucket display lists (BUCKETS per split layer), or 0 before they are made. */
	int polarium$bucketLists();

	void polarium$setBucketLists(int first);
}
