package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.FaceBuckets;
import com.arcticlauncher.polarium.legacy.SplitBuffer;
import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carries a split layer's bucket sizes from the builder thread to the upload (see {@link SplitBuffer}). */
@Mixin(BufferBuilder.class)
abstract class BufferBuilderSplitMixin implements SplitBuffer {
	@Unique
	private boolean polarium$split;
	@Unique
	private int[] polarium$bucketVertices;
	@Unique
	private float[] polarium$bucketPlanes;

	@Inject(method = "begin", at = @At("HEAD"))
	private void polarium$begin(CallbackInfo ci) {
		polarium$split = false;
	}

	@Override
	public boolean polarium$isSplit() {
		return polarium$split;
	}

	@Override
	public void polarium$setSplit(boolean split) {
		polarium$split = split;
	}

	@Override
	public int[] polarium$bucketVertices() {
		if (polarium$bucketVertices == null) {
			polarium$bucketVertices = new int[FaceBuckets.BUCKETS];
		}
		return polarium$bucketVertices;
	}

	@Override
	public float[] polarium$bucketPlanes() {
		if (polarium$bucketPlanes == null) {
			polarium$bucketPlanes = new float[FaceBuckets.AXIS_BUCKETS * 2];
		}
		return polarium$bucketPlanes;
	}
}
