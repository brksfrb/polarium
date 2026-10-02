//#if MC >= 26.2
package com.arcticlauncher.polonium.mixin;

import com.arcticlauncher.polonium.Timeline;
import com.arcticlauncher.polonium.Timeline.Step;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The game loop's own steps for -Dpolonium.debugTimeline (see Timeline): ticks, queued tasks, and waiting on the GPU. */
@Mixin(Minecraft.class)
abstract class MinecraftTimelineMixin {
	@WrapOperation(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V"))
	private void polonium$runTick(Minecraft self, boolean advance, Operation<Void> run) {
		Timeline.start(Step.RUN_TICK);
		run.call(self, advance);
		Timeline.end(Step.RUN_TICK);
	}

	@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;renderFrame(Z)V"))
	private void polonium$renderFrame(Minecraft self, boolean advance, Operation<Void> render) {
		Timeline.start(Step.RENDER_FRAME);
		render.call(self, advance);
		Timeline.end(Step.RENDER_FRAME);
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;update()V"))
	private void polonium$gui(net.minecraft.client.gui.Gui gui, Operation<Void> update) {
		Timeline.start(Step.GUI_UPDATE);
		update.call(gui);
		Timeline.end(Step.GUI_UPDATE);
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;update()V"))
	private void polonium$level(net.minecraft.client.multiplayer.ClientLevel level, Operation<Void> update) {
		Timeline.start(Step.LEVEL_UPDATE);
		update.call(level);
		Timeline.end(Step.LEVEL_UPDATE);
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;update(Lnet/minecraft/client/DeltaTracker;)V"))
	private void polonium$camera(net.minecraft.client.renderer.GameRenderer renderer, net.minecraft.client.DeltaTracker delta, Operation<Void> update) {
		Timeline.start(Step.CAMERA_UPDATE);
		update.call(renderer, delta);
		Timeline.end(Step.CAMERA_UPDATE);
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;executePendingTasks()V"))
	private void polonium$pending(Operation<Void> run) {
		Timeline.start(Step.PENDING_TASKS);
		run.call();
		Timeline.end(Step.PENDING_TASKS);
	}

	@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V"))
	private void polonium$tasks(Minecraft self, Operation<Void> run) {
		Timeline.start(Step.RUN_TASKS);
		run.call(self);
		Timeline.end(Step.RUN_TASKS);
	}

	@WrapOperation(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V"))
	private void polonium$tick(Minecraft self, Operation<Void> tick) {
		Timeline.start(Step.TICK);
		tick.call(self);
		Timeline.end(Step.TICK);
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pick(F)V"))
	private void polonium$pick(Minecraft self, float partial, Operation<Void> pick) {
		Timeline.start(Step.PICK);
		pick.call(self, partial);
		Timeline.end(Step.PICK);
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface;acquireNextTexture()V"))
	private void polonium$acquire(GpuSurface surface, Operation<Void> acquire) {
		Timeline.start(Step.ACQUIRE);
		acquire.call(surface);
		Timeline.end(Step.ACQUIRE);
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/GpuSurface;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"))
	private void polonium$blit(GpuSurface surface, CommandEncoder encoder, GpuTextureView texture, Operation<Void> blit) {
		Timeline.start(Step.BLIT);
		blit.call(surface, encoder, texture);
		Timeline.end(Step.BLIT);
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;submit()V"))
	private void polonium$submit(CommandEncoder encoder, Operation<Void> submit) {
		Timeline.start(Step.SUBMIT);
		submit.call(encoder);
		Timeline.end(Step.SUBMIT);
	}

	@WrapOperation(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface;present()V"))
	private void polonium$present(GpuSurface surface, Operation<Void> present) {
		Timeline.start(Step.PRESENT);
		present.call(surface);
		Timeline.end(Step.PRESENT);
	}
}
//#endif
