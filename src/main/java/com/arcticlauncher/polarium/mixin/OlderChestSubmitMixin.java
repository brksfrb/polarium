//#if MC < 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.gpu.OlderGpuChests;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class OlderChestSubmitMixin {
    @Inject(method = "submitBlockEntities", at = @At("HEAD"))
    private void polarium$drawBeforeSubmit(CallbackInfo ci) { OlderGpuChests.beforeSubmit(); }
    @Inject(method = "submitBlockEntities", at = @At("RETURN"))
    private void polarium$finishSubmit(CallbackInfo ci) { OlderGpuChests.afterSubmit(); }
}
//#endif
