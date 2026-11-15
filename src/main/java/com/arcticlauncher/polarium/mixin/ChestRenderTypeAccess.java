//#if MC < 26.2
package com.arcticlauncher.polarium.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.class)
public interface ChestRenderTypeAccess {
    @Accessor("state") RenderSetup polarium$setup();
}
//#endif
