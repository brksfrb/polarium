//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.gpu.MapAtlas;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the map atlas when a map's picture changes or its texture is closed (a private class of the game). */
@Mixin(targets = "net.minecraft.client.resources.MapTextureManager$MapInstance")
abstract class MapInstanceMixin {
	@Shadow
	@org.spongepowered.asm.mixin.Final
	private Identifier location;

	@Inject(method = "updateTextureIfNeeded", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
	private void polarium$changed(CallbackInfo ci) {
		MapAtlas.changed(location);
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void polarium$closed(CallbackInfo ci) {
		MapAtlas.gone(location);
	}
}
//#endif
