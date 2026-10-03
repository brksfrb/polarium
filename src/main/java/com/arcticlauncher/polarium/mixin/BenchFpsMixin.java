//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.bench.CrowdBench;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bench showcase shots only: the frame rate and the crowd's size, drawn over the hidden HUD. */
@Mixin(Gui.class)
abstract class BenchFpsMixin {
	@Inject(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			shift = At.Shift.AFTER))
	private void polarium$fps(DeltaTracker deltaTracker, boolean shouldRenderLevel, boolean resourcesLoaded, CallbackInfo ci,
			@Local GuiGraphicsExtractor graphics) {
		String label = CrowdBench.showcaseLabel();
		if (label == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		String fps = mc.getFps() + " FPS";
		// As the debug screen counts them: entities drawn this frame, of all in the level.
		int drawn = mc.gameRenderer.gameRenderState().levelRenderState.lastEntityRenderStateCount;
		String entities = String.format(java.util.Locale.ROOT, "%,d entities drawn of %,d", drawn, mc.level == null ? 0 : mc.level.getEntityCount());
		int pad = 5;
		int line = mc.font.lineHeight + 2;
		graphics.pose().pushMatrix();
		graphics.pose().translate(10, 10);
		graphics.pose().scale(2.0F, 2.0F);
		int width = Math.max(mc.font.width(fps) * 2, Math.max(mc.font.width(label), mc.font.width(entities)));
		graphics.fill(0, 0, width + pad * 2, pad * 2 + 2 * mc.font.lineHeight + 2 * line, 0xA8000000);
		// The frame rate twice as big as the rest.
		graphics.pose().pushMatrix();
		graphics.pose().translate(pad, pad);
		graphics.pose().scale(2.0F, 2.0F);
		graphics.text(mc.font, fps, 0, 0, 0xFFFFFFFF, true);
		graphics.pose().popMatrix();
		graphics.text(mc.font, label, pad, pad + 2 * mc.font.lineHeight + 2, 0xFFB8D8F0, true);
		graphics.text(mc.font, entities, pad, pad + 2 * mc.font.lineHeight + 2 + line, 0xFFCCCCCC, true);
		graphics.pose().popMatrix();
	}
}
//#endif
