package com.arcticlauncher.polarium.mixin;

//#if MC >= 26.2
import com.arcticlauncher.polarium.gpu.GpuChests;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The frame's chests are drawn where the game draws its solid entities and block entities: after the solid terrain, before anything see-through. */
@Mixin(targets = "net.minecraft.client.renderer.feature.FeatureRenderDispatcher$PreparedFrame")
abstract class GpuChestDrawMixin {
	@Inject(method = "executeSolid", at = @At("HEAD"))
	private void polarium$drawChests(
			//#if MC >= 26.3
			com.mojang.blaze3d.systems.RenderPass pass,
			//#endif
			CallbackInfo ci) {
		//#if MC >= 26.3
		GpuChests.draw(pass);
		//#else
		GpuChests.draw();
		//#endif
	}
}
//#endif
