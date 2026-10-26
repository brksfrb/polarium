package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Phases;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Benchmark timing of the world renderer's big steps. */
@Mixin(WorldRenderer.class)
abstract class PhasesWorldRendererMixin {
	@Inject(method = "setupTerrain", at = @At("HEAD"))
	private void polarium$setupStart(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.begin(Phases.TERRAIN_SETUP);
		}
	}

	@Inject(method = "setupTerrain", at = @At("RETURN"))
	private void polarium$setupEnd(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.end(Phases.TERRAIN_SETUP);
		}
	}

	@Inject(method = "renderLayer(Lnet/minecraft/client/render/RenderLayer;DILnet/minecraft/entity/Entity;)I", at = @At("HEAD"))
	private void polarium$layerStart(CallbackInfoReturnable<Integer> cir) {
		if (Phases.ON) {
			Phases.begin(Phases.TERRAIN_DRAW);
		}
	}

	@Inject(method = "renderLayer(Lnet/minecraft/client/render/RenderLayer;DILnet/minecraft/entity/Entity;)I", at = @At("RETURN"))
	private void polarium$layerEnd(CallbackInfoReturnable<Integer> cir) {
		if (Phases.ON) {
			Phases.end(Phases.TERRAIN_DRAW);
		}
	}

	@Inject(method = "renderEntities", at = @At("HEAD"))
	private void polarium$entitiesStart(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.begin(Phases.ENTITIES);
		}
	}

	@Inject(method = "renderEntities", at = @At("RETURN"))
	private void polarium$entitiesEnd(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.end(Phases.ENTITIES);
		}
	}

	@Inject(method = "renderSky(FI)V", at = @At("HEAD"))
	private void polarium$skyStart(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.begin(Phases.SKY);
		}
	}

	@Inject(method = "renderSky(FI)V", at = @At("RETURN"))
	private void polarium$skyEnd(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.end(Phases.SKY);
		}
	}

	@Inject(method = "renderClouds(FI)V", at = @At("HEAD"))
	private void polarium$cloudsStart(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.begin(Phases.CLOUDS);
		}
	}

	@Inject(method = "renderClouds(FI)V", at = @At("RETURN"))
	private void polarium$cloudsEnd(CallbackInfo ci) {
		if (Phases.ON) {
			Phases.end(Phases.CLOUDS);
		}
	}
}
