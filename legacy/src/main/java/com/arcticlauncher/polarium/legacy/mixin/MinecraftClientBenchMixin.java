package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.LegacyBench;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The benchmark's per-frame hook (does nothing unless -Dpolarium.bench is set). */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientBenchMixin {
	@Inject(method = "runGameLoop", at = @At("HEAD"))
	private void polarium$bench(CallbackInfo ci) {
		if (LegacyBench.enabled()) {
			LegacyBench.frame((MinecraftClient) (Object) this);
		}
	}
}
