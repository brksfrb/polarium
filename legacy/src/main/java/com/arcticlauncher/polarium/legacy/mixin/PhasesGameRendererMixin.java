package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Phases;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Benchmark timing. */
@Mixin(GameRenderer.class)
abstract class PhasesGameRendererMixin {

	@Inject(method = "renderWorld(IFJ)V", at = @At("HEAD"))
	private void polarium$worldStart(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.begin(Phases.WORLD);
		}
	}

	@Inject(method = "renderWorld(IFJ)V", at = @At("RETURN"))
	private void polarium$worldEnd(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.end(Phases.WORLD);
		}
	}

	@Inject(method = "renderWeather", at = @At("HEAD"))
	private void polarium$weatherStart(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.begin(Phases.WEATHER);
		}
	}

	@Inject(method = "renderWeather", at = @At("RETURN"))
	private void polarium$weatherEnd(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.end(Phases.WEATHER);
		}
	}

	@Inject(method = "renderHand", at = @At("HEAD"))
	private void polarium$handStart(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.begin(Phases.HAND);
		}
	}

	@Inject(method = "renderHand", at = @At("RETURN"))
	private void polarium$handEnd(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.end(Phases.HAND);
		}
	}
}
