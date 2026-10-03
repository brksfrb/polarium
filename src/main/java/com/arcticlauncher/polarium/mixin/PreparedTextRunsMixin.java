//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.GlyphRuns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Room on each laid-out text for its recorded glyph runs, one set per display mode (see {@link GlyphRuns}). */
@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
abstract class PreparedTextRunsMixin implements GlyphRuns.Holder {
	@Unique
	private final GlyphRuns.Run[][] polarium$runs = new GlyphRuns.Run[3][];

	@Override
	public GlyphRuns.Run[][] polarium$runs() {
		return polarium$runs;
	}
}
//#endif
