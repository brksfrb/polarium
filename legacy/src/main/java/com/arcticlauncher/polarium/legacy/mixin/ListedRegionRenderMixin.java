package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Flags;
import com.arcticlauncher.polarium.legacy.RegionBatches;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.world.ListedChunkRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the display lists of a layer region by region (see {@link RegionBatches}). */
@Mixin(ListedChunkRenderManager.class)
abstract class ListedRegionRenderMixin {
	@Unique
	private final RegionBatches polarium$batches = new RegionBatches();

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void polarium$render(RenderLayer layer, CallbackInfo ci) {
		if (!Flags.REGIONS) {
			return;
		}
		ci.cancel();
		AbstractManagerAccess access = (AbstractManagerAccess) this;
		if (access.polarium$ready()) {
			polarium$batches.render(access.polarium$helpers(), layer, access.polarium$viewX(), access.polarium$viewY(), access.polarium$viewZ());
			GlStateManager.clearColor();
			access.polarium$helpers().clear();
		}
	}
}
