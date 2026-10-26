package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Phases;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Benchmark timing. */
@Mixin(ParticleManager.class)
abstract class PhasesParticleManagerMixin {

	@Inject(method = "renderParticles", at = @At("HEAD"))
	private void polarium$particlesStart(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.begin(Phases.PARTICLES);
		}
	}

	@Inject(method = "renderParticles", at = @At("RETURN"))
	private void polarium$particlesEnd(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.end(Phases.PARTICLES);
		}
	}
}
