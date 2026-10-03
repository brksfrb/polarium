//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.gpu.Crowd;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The crowd path ({@link Crowd}) covers the level's entities: it starts with them and hands in its submits once they're all in. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererCrowdMixin {
	@Shadow
	@Final
	private EntityRenderDispatcher entityRenderDispatcher;

	@Inject(method = "submitEntities", at = @At("HEAD"))
	private void polarium$beginCrowd(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output, CallbackInfo ci) {
		com.arcticlauncher.polarium.Timeline.end(com.arcticlauncher.polarium.Timeline.Step.BEFORE_ENTITIES);
		// The entity states made on the helpers are needed from here (see ParallelExtract#lateJoin).
		com.arcticlauncher.polarium.Timeline.start(com.arcticlauncher.polarium.Timeline.Step.EXTRACT_JOIN);
		com.arcticlauncher.polarium.ParallelExtract.finish();
		com.arcticlauncher.polarium.Timeline.end(com.arcticlauncher.polarium.Timeline.Step.EXTRACT_JOIN);
		com.arcticlauncher.polarium.Timeline.start(com.arcticlauncher.polarium.Timeline.Step.SUBMIT_ENTITIES);
		com.arcticlauncher.polarium.Timeline.start(com.arcticlauncher.polarium.Timeline.Step.CROWD_BULK);
		Crowd.beginFrame();
		Crowd.bulkSubmit(levelRenderState.entityRenderStates, levelRenderState.cameraRenderState, poseStack, output, entityRenderDispatcher);
		com.arcticlauncher.polarium.Timeline.end(com.arcticlauncher.polarium.Timeline.Step.CROWD_BULK);
	}

	/** Players the crowd path took in bulk aren't submitted again. */
	@WrapOperation(method = "submitEntities", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
	private void polarium$skipTaken(EntityRenderDispatcher dispatcher, EntityRenderState state, CameraRenderState camera, double x, double y,
			double z, PoseStack poseStack, SubmitNodeCollector output, Operation<Void> submit) {
		if (!Crowd.taken(state)) {
			submit.call(dispatcher, state, camera, x, y, z, poseStack, output);
		}
	}

	@Inject(method = "submitEntities", at = @At("TAIL"))
	private void polarium$endCrowd(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector output, CallbackInfo ci) {
		com.arcticlauncher.polarium.Timeline.start(com.arcticlauncher.polarium.Timeline.Step.CROWD_END);
		Crowd.endSubmits(output);
		com.arcticlauncher.polarium.Timeline.end(com.arcticlauncher.polarium.Timeline.Step.CROWD_END);
		com.arcticlauncher.polarium.Timeline.end(com.arcticlauncher.polarium.Timeline.Step.SUBMIT_ENTITIES);
	}
}
//#endif
