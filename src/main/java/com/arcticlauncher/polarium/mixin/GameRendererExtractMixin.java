//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.ParallelExtract;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The level's entity states made on the helpers during the frame's extraction are in before the frame is drawn. */
@Mixin(GameRenderer.class)
abstract class GameRendererExtractMixin {
	@Inject(method = "extract", at = @At("HEAD"))
	private void polarium$extractStarts(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		com.arcticlauncher.polarium.Timeline.start(com.arcticlauncher.polarium.Timeline.Step.EXTRACT);
	}

	@Inject(method = "extract", at = @At("TAIL"))
	private void polarium$entitiesIn(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		if (!ParallelExtract.lateJoin()) {
			com.arcticlauncher.polarium.Timeline.start(com.arcticlauncher.polarium.Timeline.Step.EXTRACT_JOIN);
			ParallelExtract.finish();
			com.arcticlauncher.polarium.Timeline.end(com.arcticlauncher.polarium.Timeline.Step.EXTRACT_JOIN);
		}
		com.arcticlauncher.polarium.Timeline.end(com.arcticlauncher.polarium.Timeline.Step.EXTRACT);
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void polarium$entitiesInBeforeDrawing(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		if (!ParallelExtract.lateJoin()) {
			ParallelExtract.finish();
		}
		com.arcticlauncher.polarium.Timeline.start(com.arcticlauncher.polarium.Timeline.Step.RENDER);
		com.arcticlauncher.polarium.Timeline.start(com.arcticlauncher.polarium.Timeline.Step.BEFORE_ENTITIES);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void polarium$frameDrawn(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
		// The level's entity states in, whatever happened (no level drawn this frame, say).
		ParallelExtract.finish();
		com.arcticlauncher.polarium.Timeline.end(com.arcticlauncher.polarium.Timeline.Step.RENDER);
		com.arcticlauncher.polarium.Timeline.frame();
	}
}
//#endif
