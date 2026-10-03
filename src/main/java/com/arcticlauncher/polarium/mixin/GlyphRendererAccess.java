//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.feature.NameTagFeatureRenderer$GlyphRenderer")
public interface GlyphRendererAccess {
	@Accessor("displayMode")
	Font.DisplayMode polarium$displayMode();

	@Accessor("pose")
	org.joml.Matrix4f polarium$pose();

	@Accessor("lightCoords")
	int polarium$lightCoords();
}
//#endif
