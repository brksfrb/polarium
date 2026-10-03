//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.gpu.GpuEntities;
import com.arcticlauncher.polarium.gpu.GpuBatches;
import com.arcticlauncher.polarium.gpu.GpuBatchesHolder;
import com.arcticlauncher.polarium.gpu.GpuEntitiesHolder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Entity models go to the GPU path instead of being turned into vertices (see {@link GpuEntities}). */
@Mixin(ModelFeatureRenderer.class)
abstract class ModelFeatureRendererMixin implements GpuBatchesHolder, GpuEntitiesHolder {
	@Unique
	private final GpuEntities polarium$gpu = new GpuEntities();

	@Override
	public GpuBatches polarium$batches() {
		return polarium$gpu.batches();
	}

	@Override
	public GpuEntities polarium$entities() {
		return polarium$gpu;
	}

	@WrapOperation(
			method = "buildGroup",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer;prepareModel(Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$Submit;)V"))
	private void polarium$onGpu(ModelFeatureRenderer self, ModelFeatureRenderer.Submit<?> submit, Operation<Void> build) {
		if (!polarium$gpu.capture(submit)) {
			build.call(self, submit);
		}
	}
}
//#endif
