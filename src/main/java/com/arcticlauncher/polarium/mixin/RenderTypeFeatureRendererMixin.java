//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.gpu.GpuBatches;
import com.arcticlauncher.polarium.gpu.GpuBatchesHolder;
import java.util.List;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The group bookkeeping for {@link GpuBatches}: which group the model and
 * item renderers are preparing, drawing its GPU batches when the game draws that
 * group (before the game's own draws, so effects like enchantment glint that
 * go on top still do), and starting over each frame.
 */
@Mixin(RenderTypeFeatureRenderer.class)
abstract class RenderTypeFeatureRendererMixin {
	@Inject(method = "prepareGroup", at = @At("HEAD"))
	private void polarium$beginGroup(FeatureFrameContext context, List<?> submits, boolean strictlyOrdered, CallbackInfo ci) {
		if (this instanceof GpuBatchesHolder holder) {
			GpuBatches.announce();
			holder.polarium$batches().beginGroup(strictlyOrdered);
		}
	}

	@Inject(method = "prepareGroup", at = @At("TAIL"))
	private void polarium$endGroup(FeatureFrameContext context, List<?> submits, boolean strictlyOrdered, CallbackInfo ci) {
		if (this instanceof GpuBatchesHolder holder) {
			holder.polarium$batches().endGroup();
		}
	}

	//#if MC >= 26.3
	// 26.3 opens the render pass itself and hands it over, with the transparency (OIT) stage.
	@Inject(method = "executeGroup", at = @At("HEAD"))
	private void polarium$drawGroup(FeatureFrameContext context, net.minecraft.client.renderer.oit.@org.jspecify.annotations.Nullable OitStage stage,
			com.mojang.renderpearl.api.commands.RenderPass renderPass, int groupIndex, List<?> submits, boolean strictlyOrdered, CallbackInfo ci) {
		if (this instanceof GpuBatchesHolder holder) {
			holder.polarium$batches().executeGroup(groupIndex, renderPass, stage);
		}
	}
	//#else
	@Inject(method = "executeGroup", at = @At("HEAD"))
	private void polarium$drawGroup(FeatureFrameContext context, int groupIndex, List<?> submits, boolean strictlyOrdered, CallbackInfo ci) {
		if (this instanceof GpuBatchesHolder holder) {
			holder.polarium$batches().executeGroup(groupIndex);
		}
	}
	//#endif

	@Inject(method = "finishExecute", at = @At("HEAD"))
	private void polarium$endFrame(FeatureFrameContext context, CallbackInfo ci) {
		if (this instanceof GpuBatchesHolder holder) {
			holder.polarium$batches().endFrame();
		}
	}
}
//#endif
