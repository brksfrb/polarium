package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.FaceBuckets;
import com.arcticlauncher.polarium.legacy.Flags;
import com.arcticlauncher.polarium.legacy.SplitBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.client.world.ChunkAssemblyHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** When a section's opaque layer is finished on the builder thread, its quads are split by face direction. */
@Mixin(BuiltChunk.class)
abstract class BuiltChunkSplitMixin {
	@Inject(method = "method_10157", at = @At("HEAD"))
	private void polarium$split(RenderLayer layer, float x, float y, float z, BufferBuilder buffer, ChunkAssemblyHelper helper, CallbackInfo ci) {
		if (!Flags.FACES || layer.ordinal() >= FaceBuckets.LAYERS || !FaceBuckets.splittable(buffer)) {
			return;
		}
		SplitBuffer split = (SplitBuffer) buffer;
		FaceBuckets.split(buffer, split.polarium$bucketVertices(), split.polarium$bucketPlanes());
		split.polarium$setSplit(true);
	}
}
