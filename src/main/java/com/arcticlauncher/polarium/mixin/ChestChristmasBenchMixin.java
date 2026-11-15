package com.arcticlauncher.polarium.mixin;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** The opt-in scene can exercise vanilla's December selection without changing the host clock. */
@Mixin(ChestRenderer.class)
abstract class ChestChristmasBenchMixin {
    @Inject(method = "xmasTextures", at = @At("HEAD"), cancellable = true)
    private static void polarium$december(CallbackInfoReturnable<Boolean> cir) {
        if ("blocks".equals(System.getProperty("polarium.bench")) && Boolean.getBoolean("polarium.bench.christmas")) cir.setReturnValue(true);
    }
}
