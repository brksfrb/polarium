package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Pipeline;
import net.minecraft.client.render.VertexBuffer;
import net.minecraft.client.render.world.VboChunkRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Pipelined submission on the vertex buffer path (VBOs on): the same vertex budget, counted per section buffer. */
@Mixin(VboChunkRenderManager.class)
abstract class PipelineVboManagerMixin {
	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexBuffer;draw(I)V"))
	private void polarium$draw(VertexBuffer buffer, int mode) {
		buffer.draw(mode);
		if (Pipeline.active()) {
			Pipeline.submitted(((VertexBufferAccess) buffer).polarium$size());
		}
	}
}
