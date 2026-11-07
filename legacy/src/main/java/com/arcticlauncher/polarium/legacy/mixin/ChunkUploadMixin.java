package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.SectionData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.client.world.ChunkAssemblyHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Notes how many vertices a section's layer holds when its list or buffer is made on the render thread. */
@Mixin(ChunkBuilder.class)
abstract class ChunkUploadMixin {
	@Inject(method = "upload(Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/BufferBuilder;Lnet/minecraft/client/world/BuiltChunk;Lnet/minecraft/client/world/ChunkAssemblyHelper;)Lcom/google/common/util/concurrent/ListenableFuture;",
			at = @At("HEAD"))
	private void polarium$count(RenderLayer layer, BufferBuilder buffer, BuiltChunk chunk, ChunkAssemblyHelper helper, CallbackInfoReturnable<?> cir) {
		if (MinecraftClient.getInstance().isOnThread()) {
			((SectionData) chunk).polarium$vertices()[layer.ordinal()] = buffer.getVertexCount();
		}
	}
}
