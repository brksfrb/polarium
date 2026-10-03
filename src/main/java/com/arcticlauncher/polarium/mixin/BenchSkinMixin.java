package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.bench.CrowdBench;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Development only (the crowd bench's players): each bench player's own skin. Not applied outside the bench. */
@Mixin(AbstractClientPlayer.class)
abstract class BenchSkinMixin {
	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void polarium$benchSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerSkin skin = CrowdBench.skinOf(((Entity) (Object) this).getUUID());
		if (skin != null) {
			cir.setReturnValue(skin);
		}
	}
}
