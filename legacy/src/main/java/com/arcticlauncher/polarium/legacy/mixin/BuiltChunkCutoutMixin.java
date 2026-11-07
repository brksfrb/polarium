package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.CutoutRouting;
import com.arcticlauncher.polarium.legacy.Flags;
import com.arcticlauncher.polarium.legacy.OpaqueSprites;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.client.world.ChunkAssemblyHelper;
import net.minecraft.client.world.ChunkBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Moves fully opaque quads of cutout-mipped blocks to the solid layer as each block is built (see {@link CutoutRouting}). */
@Mixin(BuiltChunk.class)
abstract class BuiltChunkCutoutMixin {
	@Shadow
	private BlockPos pos;

	@Shadow
	private void method_10158(BufferBuilder bufferBuilder, BlockPos blockPos) {
	}

	@Redirect(method = "method_10164", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderBlock(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockView;Lnet/minecraft/client/render/BufferBuilder;)Z"))
	private boolean polarium$renderBlock(BlockRenderManager manager, BlockState state, BlockPos blockPos, BlockView view, BufferBuilder buffer,
			float x, float y, float z, ChunkBuilder task) {
		OpaqueSprites sprites;
		if (!Flags.OPAQUE_CUTOUT || state.getBlock().getRenderLayerType() != RenderLayer.CUTOUT_MIPPED || (sprites = OpaqueSprites.current()) == null) {
			return manager.renderBlock(state, blockPos, view, buffer);
		}
		int from = buffer.getVertexCount();
		boolean drew = manager.renderBlock(state, blockPos, view, buffer);
		int[] length = new int[1];
		int[] moved = CutoutRouting.split(buffer, from, sprites, length);
		if (moved == null) {
			return drew;
		}
		((BufferBuilderAccess) buffer).polarium$setVertexCount(buffer.getVertexCount() - length[0] / 7);
		ChunkAssemblyHelper helper = task.getChunkAssemblyHelper();
		BufferBuilder solid = task.getRenderLaterBuffers().get(RenderLayer.SOLID);
		if (!helper.isUnused(RenderLayer.SOLID)) {
			helper.setUnused(RenderLayer.SOLID);
			method_10158(solid, pos);
		}
		int[] exact = length[0] == moved.length ? moved : java.util.Arrays.copyOf(moved, length[0]);
		solid.putArray(exact);
		((AssemblyHelperAccess) helper).polarium$setUsed(RenderLayer.SOLID);
		return drew;
	}
}
