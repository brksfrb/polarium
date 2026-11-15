package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.BlockEntityCulling;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scope the frustum to this world extraction; GUI previews never inherit it. */
//#if MC >= 26.2
@Mixin(net.minecraft.client.renderer.extract.LevelExtractor.class)
//#else
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
//#endif
abstract class BlockEntityCullLevelMixin {
    //#if MC >= 26.2
    @Inject(method = "extract", at = @At("HEAD"))
    //#elif MC >= 26.1
    @Inject(method = "extractLevel", at = @At("HEAD"))
    //#else
    @Inject(method = "renderLevel", at = @At("HEAD"))
    //#endif
    private void polarium$beginBlockEntities(CallbackInfo ci) {
        BlockEntityCulling.begin();
        com.arcticlauncher.polarium.gpu.GpuChests.begin();
    }

    //#if MC >= 26.2
    @Inject(method = "extract", at = @At("RETURN"))
    //#elif MC >= 26.1
    @Inject(method = "extractLevel", at = @At("RETURN"))
    //#else
    @Inject(method = "renderLevel", at = @At("RETURN"))
    //#endif
    private void polarium$endBlockEntities(CallbackInfo ci) {
        BlockEntityCulling.end();
        //#if MC >= 26.2
        com.arcticlauncher.polarium.gpu.GpuChests.end();
        //#endif
    }
}
