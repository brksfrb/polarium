package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Phases;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Benchmark timing. */
@Mixin(MinecraftClient.class)
abstract class PhasesMinecraftClientMixin {

	@Inject(method = "tick", at = @At("HEAD"))
	private void polarium$tickStart(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.begin(Phases.TICK);
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void polarium$tickEnd(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.end(Phases.TICK);
		}
	}

	@Inject(method = "updateDisplay", at = @At("HEAD"))
	private void polarium$displayStart(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.begin(Phases.DISPLAY);
		}
	}

	@Inject(method = "updateDisplay", at = @At("RETURN"))
	private void polarium$displayEnd(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.end(Phases.DISPLAY);
		}
	}
}
