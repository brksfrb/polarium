//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.PreparedTexts;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** GUI text prepared once while it stays on screen (see PreparedTexts). */
@Mixin(GuiTextRenderState.class)
abstract class GuiTextRenderStateMixin {
	@WrapOperation(method = "ensurePrepared", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"))
	private Font.PreparedText polarium$kept(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow,
			boolean includeEmpty, int background, Operation<Font.PreparedText> prepare) {
		return PreparedTexts.prepared(font, text, x, y, color, shadow, includeEmpty, background,
				() -> prepare.call(font, text, x, y, color, shadow, includeEmpty, background));
	}
}
//#endif
