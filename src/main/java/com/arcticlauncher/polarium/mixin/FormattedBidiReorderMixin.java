package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.TextOrder;
import net.minecraft.client.resources.language.FormattedBidiReorder;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Left-to-right text without the bidirectional analysis (see TextOrder). */
@Mixin(FormattedBidiReorder.class)
abstract class FormattedBidiReorderMixin {
	@Unique
	private static final ThreadLocal<Boolean> POLARIUM$CHECKING = ThreadLocal.withInitial(() -> false);

	@Inject(method = "reorder", at = @At("HEAD"), cancellable = true)
	private static void polarium$leftToRight(FormattedText text, boolean defaultRightToLeft, CallbackInfoReturnable<FormattedCharSequence> cir) {
		if (TextOrder.CHECK && POLARIUM$CHECKING.get()) {
			return;
		}
		FormattedCharSequence mine = TextOrder.leftToRight(text, defaultRightToLeft);
		if (mine == null) {
			return;
		}
		if (TextOrder.CHECK) {
			POLARIUM$CHECKING.set(true);
			try {
				TextOrder.check(text, mine, FormattedBidiReorder.reorder(text, defaultRightToLeft));
			} finally {
				POLARIUM$CHECKING.set(false);
			}
		}
		cir.setReturnValue(mine);
	}
}
