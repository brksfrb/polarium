package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.VignetteRing;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Textures can change with a resource reload. */
@Mixin(MinecraftClient.class)
abstract class ResourceReloadMixin {
	@Inject(method = "reloadResources", at = @At("RETURN"))
	private void polarium$reloaded(CallbackInfo ci) {
		VignetteRing.resourcesReloaded();
	}
}
