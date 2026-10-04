//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.gpu.GpuBatches;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Once the game has loaded: with a mod installed that keeps entity models
 * on the game's renderer (a shaders mod, an entity model mod), a notice
 * saying so, so a player doesn't wonder why crowds are still slow.
 */
@Mixin(Minecraft.class)
abstract class StepAsideNoticeMixin {
	@Unique
	private static final SystemToast.SystemToastId POLARIUM$NOTICE = new SystemToast.SystemToastId(12_000L);
	@Unique
	private static boolean polarium$shown;

	@Inject(method = "runTick", at = @At("HEAD"))
	private void polarium$notice(boolean advance, CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		if (polarium$shown || !mc.isGameLoadFinished()) {
			return;
		}
		polarium$shown = true;
		String mod = GpuBatches.steppedAsideFor();
		if (mod != null) {
			org.slf4j.LoggerFactory.getLogger("Polarium").info("Polarium: notice shown (GPU drawing off for {})", mod);
			SystemToast.add(mc.gui.toastManager(), POLARIUM$NOTICE, Component.literal("Polarium: GPU drawing is off"),
					Component.literal(mod + " is installed, so players are drawn the usual way and big crowds stay slow. Polarium's other speed-ups still work."));
		}
	}
}
//#endif
