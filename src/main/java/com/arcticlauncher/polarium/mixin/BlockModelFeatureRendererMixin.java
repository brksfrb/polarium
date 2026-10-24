//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.gpu.GpuBatches;
import com.arcticlauncher.polarium.gpu.GpuBatchesHolder;
import com.arcticlauncher.polarium.gpu.GpuBlocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Block models drawn as entities (primed TNT, say) go to the GPU path instead of being turned into vertices (see {@link GpuBlocks}). */
@Mixin(BlockModelFeatureRenderer.class)
abstract class BlockModelFeatureRendererMixin implements GpuBatchesHolder {
	@Unique
	private final GpuBlocks polarium$gpu = new GpuBlocks();

	@Override
	public GpuBatches polarium$batches() {
		return polarium$gpu.batches();
	}

	@WrapMethod(method = "buildGroup")
	private void polarium$onGpu(FeatureFrameContext context, List<BlockModelFeatureRenderer.Submit> submits, Operation<Void> build) {
		List<BlockModelFeatureRenderer.Submit> rest = new ArrayList<>();
		for (BlockModelFeatureRenderer.Submit submit : submits) {
			if (!polarium$gpu.capture(submit)) {
				rest.add(submit);
			}
		}
		build.call(context, rest);
	}
}
//#endif
