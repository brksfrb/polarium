package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.FaceBuckets;
import com.arcticlauncher.polarium.legacy.SectionData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.world.BuiltChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The per-section data of {@link SectionData}. */
@Mixin(BuiltChunk.class)
abstract class BuiltChunkDataMixin implements SectionData {
	@Unique
	private final int[] polarium$vertices = new int[RenderLayer.values().length];
	@Unique
	private final boolean[] polarium$split = new boolean[RenderLayer.values().length];
	@Unique
	private final int[] polarium$bucketVertices = new int[FaceBuckets.LAYERS * FaceBuckets.BUCKETS];
	@Unique
	private final float[] polarium$bucketPlanes = new float[FaceBuckets.LAYERS * FaceBuckets.AXIS_BUCKETS * 2];
	@Unique
	private int polarium$bucketLists;

	@Override
	public int[] polarium$vertices() {
		return polarium$vertices;
	}

	@Override
	public boolean[] polarium$split() {
		return polarium$split;
	}

	@Override
	public int[] polarium$bucketVertices() {
		return polarium$bucketVertices;
	}

	@Override
	public float[] polarium$bucketPlanes() {
		return polarium$bucketPlanes;
	}

	@Override
	public int polarium$bucketLists() {
		return polarium$bucketLists;
	}

	@Override
	public void polarium$setBucketLists(int first) {
		polarium$bucketLists = first;
	}
}
