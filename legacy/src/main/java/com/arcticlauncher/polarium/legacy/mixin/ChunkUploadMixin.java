package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.FaceBuckets;
import com.arcticlauncher.polarium.legacy.SectionData;
import com.arcticlauncher.polarium.legacy.SplitBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.world.ChunkRenderHelperImpl;
import net.minecraft.client.util.GlAllocationUtils;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.client.world.ChunkAssemblyHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When a section's layer is uploaded on the render thread: notes its vertex count and, when the layer was split
 * by face direction (see {@link FaceBuckets}), the buckets; a display list layer is then compiled as one list per
 * bucket, with the game's list calling them.
 */
@Mixin(ChunkBuilder.class)
abstract class ChunkUploadMixin {
	@Inject(method = "upload(Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/BufferBuilder;Lnet/minecraft/client/world/BuiltChunk;Lnet/minecraft/client/world/ChunkAssemblyHelper;)Lcom/google/common/util/concurrent/ListenableFuture;",
			at = @At("HEAD"))
	private void polarium$count(RenderLayer layer, BufferBuilder buffer, BuiltChunk chunk, ChunkAssemblyHelper helper, CallbackInfoReturnable<?> cir) {
		if (!MinecraftClient.getInstance().isOnThread()) {
			return;
		}
		SectionData data = (SectionData) chunk;
		int ordinal = layer.ordinal();
		data.polarium$vertices()[ordinal] = buffer.getVertexCount();
		SplitBuffer split = (SplitBuffer) buffer;
		boolean isSplit = ordinal < FaceBuckets.LAYERS && split.polarium$isSplit();
		data.polarium$split()[ordinal] = isSplit;
		if (isSplit) {
			System.arraycopy(split.polarium$bucketVertices(), 0, data.polarium$bucketVertices(), ordinal * FaceBuckets.BUCKETS, FaceBuckets.BUCKETS);
			System.arraycopy(split.polarium$bucketPlanes(), 0, data.polarium$bucketPlanes(), ordinal * FaceBuckets.AXIS_BUCKETS * 2, FaceBuckets.AXIS_BUCKETS * 2);
		}
	}

	@Inject(method = "uploadGlList", at = @At("HEAD"), cancellable = true)
	private void polarium$compileSplit(BufferBuilder buffer, int list, BuiltChunk chunk, CallbackInfo ci) {
		if (!((SplitBuffer) buffer).polarium$isSplit() || !(chunk instanceof ChunkRenderHelperImpl)) {
			return;
		}
		int ordinal = list - ((ListedSectionAccess) chunk).polarium$firstList();
		SectionData data = (SectionData) chunk;
		if (ordinal < 0 || ordinal >= FaceBuckets.LAYERS || !data.polarium$split()[ordinal]) {
			return;
		}
		if (data.polarium$bucketLists() == 0) {
			data.polarium$setBucketLists(GlAllocationUtils.genLists(FaceBuckets.LAYERS * FaceBuckets.BUCKETS));
		}
		FaceBuckets.compile(buffer, list, data.polarium$bucketLists() + ordinal * FaceBuckets.BUCKETS, data.polarium$bucketVertices(),
				ordinal * FaceBuckets.BUCKETS, chunk);
		ci.cancel();
	}
}
