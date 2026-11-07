package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Pipeline;
import net.minecraft.client.render.world.ListedChunkRenderManager;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Pipelined submission on the game's own section-by-section list path (regions switched off). */
@Mixin(ListedChunkRenderManager.class)
abstract class PipelineChunkManagersMixin {
	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glCallList(I)V", remap = false))
	private void polarium$callList(int list) {
		GL11.glCallList(list);
		if (Pipeline.active()) {
			Pipeline.submittedCall();
		}
	}
}
