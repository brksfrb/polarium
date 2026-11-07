package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Pipeline;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pipelined submission (see {@link Pipeline}): the passes that are mostly pixels hand their work to the
 * rasterizer when they end, so it fills them while the game walks the terrain or sets up the next pass.
 */
@Mixin(WorldRenderer.class)
abstract class PipelineWorldRendererMixin {
	@Inject(method = "renderSky(FI)V", at = @At("RETURN"))
	private void polarium$skyDone(CallbackInfo ci) {
		Pipeline.passEnd();
	}

	@Inject(method = "renderClouds(FI)V", at = @At("RETURN"))
	private void polarium$cloudsDone(CallbackInfo ci) {
		Pipeline.passEnd();
	}

	@Inject(method = "renderEntities", at = @At("RETURN"))
	private void polarium$entitiesDone(CallbackInfo ci) {
		Pipeline.passEnd();
	}
}
