package com.arcticlauncher.polarium.mixin;
import com.arcticlauncher.polarium.bench.BlockEntityBench;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Opt-in whole-frame timing, including software rendering and frame pacing. */
@Mixin(Minecraft.class)
abstract class BlockEntityBenchFrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void polarium$start(CallbackInfo ci) { BlockEntityBench.frameStart(); }
    @Inject(method = "runTick", at = @At("RETURN"))
    private void polarium$end(CallbackInfo ci) { BlockEntityBench.frameEnd(); }
}
