//#if MC < 26.2
package com.arcticlauncher.polarium.mixin;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(RenderSetup.class)
public interface ChestRenderSetupAccess {
    @Accessor("outputTarget") OutputTarget polarium$outputTarget();
    @Accessor("textureTransform") TextureTransform polarium$textureTransform();
}
//#endif
