package com.arcticlauncher.polarium.legacy;

/** A vertex buffer builder that remembers whether its layer was split by face direction (see {@link FaceBuckets}). */
public interface SplitBuffer {
	/** True after {@link FaceBuckets#split}, until the builder starts again. */
	boolean polarium$isSplit();

	void polarium$setSplit(boolean split);

	int[] polarium$bucketVertices();

	float[] polarium$bucketPlanes();
}
