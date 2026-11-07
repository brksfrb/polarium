package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Flags;
import com.arcticlauncher.polarium.legacy.VignetteRing;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.border.WorldBorder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The vignette (fancy graphics) without the pixels it cannot change. It multiplies the screen by
 * {@code 1 - texture * colour}; the game's code below is unchanged except for the draw: where the colour is so dark
 * that {@code texture * colour} is below a quarter of one 8-bit step everywhere (in daylight it fades towards
 * zero and stays there) nothing is drawn, and otherwise it is drawn as a ring around the texture's black middle
 * (see {@link VignetteRing}). The GL state afterwards is the game's.
 */
@Mixin(InGameHud.class)
abstract class VignetteMixin {
	/** Brightest colour channel at which the vignette still rounds to no change on an 8-bit screen. */
	private static final float INVISIBLE = 1.0F / 1024.0F;

	@Shadow
	@Final
	private MinecraftClient client;
	@Shadow
	public float vignetteDarkness;
	@Shadow
	@Final
	private static Identifier VIGNETTE;

	@Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
	private void polarium$vignette(float brightness, Window window, CallbackInfo ci) {
		if (!Flags.VIGNETTE) {
			return;
		}
		ci.cancel();
		brightness = 1.0F - brightness;
		brightness = MathHelper.clamp(brightness, 0.0F, 1.0F);
		WorldBorder worldBorder = client.world.getWorldBorder();
		float f = (float) worldBorder.getDistanceInsideBorder(client.player);
		double d = Math.min(worldBorder.getShrinkingSpeed() * worldBorder.getWarningTime() * 1000.0, Math.abs(worldBorder.getTargetSize() - worldBorder.getOldSize()));
		double e = Math.max((double) worldBorder.getWarningBlocks(), d);
		if (f < e) {
			f = 1.0F - (float) (f / e);
		} else {
			f = 0.0F;
		}

		vignetteDarkness = (float) (vignetteDarkness + (brightness - vignetteDarkness) * 0.01);
		GlStateManager.disableDepthTest();
		GlStateManager.depthMask(false);
		GlStateManager.blendFuncSeparate(0, 769, 1, 0);
		float strongest;
		if (f > 0.0F) {
			GlStateManager.color(0.0F, f, f, 1.0F);
			strongest = f;
		} else {
			GlStateManager.color(vignetteDarkness, vignetteDarkness, vignetteDarkness, 1.0F);
			strongest = vignetteDarkness;
		}

		client.getTextureManager().bindTexture(VIGNETTE);
		if (!(strongest >= 0.0F && strongest <= INVISIBLE)
				&& !VignetteRing.draw(VIGNETTE, window.getWidth(), window.getHeight())) {
			Tessellator tessellator = Tessellator.getInstance();
			BufferBuilder bufferBuilder = tessellator.getBuffer();
			bufferBuilder.begin(7, VertexFormats.POSITION_TEXTURE);
			bufferBuilder.vertex(0.0, window.getHeight(), -90.0).texture(0.0, 1.0).next();
			bufferBuilder.vertex(window.getWidth(), window.getHeight(), -90.0).texture(1.0, 1.0).next();
			bufferBuilder.vertex(window.getWidth(), 0.0, -90.0).texture(1.0, 0.0).next();
			bufferBuilder.vertex(0.0, 0.0, -90.0).texture(0.0, 0.0).next();
			tessellator.draw();
		}
		GlStateManager.depthMask(true);
		GlStateManager.enableDepthTest();
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
	}
}
