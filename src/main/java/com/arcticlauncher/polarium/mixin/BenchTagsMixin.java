//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Bench showcase shots only (-Dpolarium.bench.showcase=true): name tags stay with the HUD hidden. */
@Mixin(LivingEntityRenderer.class)
abstract class BenchTagsMixin {
	@WrapOperation(method = "shouldShowName", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;isHidden()Z"))
	private boolean polarium$tagsInShots(Hud hud, Operation<Boolean> isHidden) {
		return false;
	}
}
//#endif
