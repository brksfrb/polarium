package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.BlockEntityCull;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/**
 * Chests, signs and the like outside the view are not drawn (the game draws every one in a section
 * that is partly on screen). Before the renderer is looked up and its state made, so a skipped one
 * costs nothing more; what is drawn is drawn exactly as before.
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityCullMixin {
	//#if MC >= 26.2
	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private void polarium$cull(BlockEntity entity, float partialTick, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling,
			boolean global, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Object> cir) {
		// A chest drawn on the GPU needs no render state (GpuChests): shut ones are already in their section's instances.
		if (com.arcticlauncher.polarium.gpu.GpuChests.capture(entity, partialTick, crumbling, global)) {
			cir.setReturnValue(null);
			return;
		}
		if (!global && BlockEntityCull.skip(entity)) {
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "onResourceManagerReload", at = @At("RETURN"))
	private void polarium$reloadChests(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		com.arcticlauncher.polarium.gpu.GpuChests.reload();
	}
	//#else
	@Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
	private void polarium$cull(BlockEntity entity, float partialTick, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Object> cir) {
        com.arcticlauncher.polarium.gpu.GpuChests.capture(entity, partialTick, crumbling, false);
		if (BlockEntityCull.skip(entity)) {
			cir.setReturnValue(null);
		}
	}
    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void polarium$reloadOlderChests(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.arcticlauncher.polarium.gpu.OlderGpuChests.reload();
    }
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void polarium$skipDrawn(net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState state,
            com.mojang.blaze3d.vertex.PoseStack poses, net.minecraft.client.renderer.SubmitNodeCollector collector,
            net.minecraft.client.renderer.state.level.CameraRenderState camera, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (com.arcticlauncher.polarium.gpu.OlderGpuChests.submitted(state)) ci.cancel();
    }
	//#endif
}
