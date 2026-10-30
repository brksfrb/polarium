package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.CloudCache;
import com.arcticlauncher.polarium.legacy.Flags;
import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.FloatBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fancy clouds from a display list (see {@link CloudCache}). Everything before the geometry is the game's
 * own code; the shift inside the cloud cell and the height are applied as a translate when the list is
 * drawn instead of being baked into the vertices.
 */
@Mixin(WorldRenderer.class)
abstract class WorldRendererCloudsMixin {
	@Shadow
	private MinecraftClient client;
	@Shadow
	private ClientWorld world;
	@Shadow
	private int ticks;
	@Shadow
	@Final
	private TextureManager textureManager;
	@Shadow
	@Final
	private static Identifier CLOUDS;

	@Unique
	private final CloudCache polarium$clouds = new CloudCache();
	/** GL's current colour and normal (GL_CURRENT_COLOR, GL_CURRENT_NORMAL), kept around the cloud list. */
	@Unique
	private FloatBuffer polarium$colour;
	@Unique
	private FloatBuffer polarium$normal;
	/** Set when the cached clouds threw: the game's own clouds draw from then on. */
	@Unique
	private boolean polarium$cloudsFailed;

	@Inject(method = "renderFancyClouds", at = @At("HEAD"), cancellable = true)
	private void polarium$fancyClouds(float tickDelta, int anaglyphFilter, CallbackInfo ci) {
		if (!Flags.CLOUDS || polarium$cloudsFailed) {
			return;
		}
		// the matrix is the caller's again whichever way this goes
		GlStateManager.pushMatrix();
		try {
			polarium$drawClouds(tickDelta, anaglyphFilter);
			ci.cancel();
		} catch (Throwable t) {
			polarium$cloudsFailed = true;
			System.out.println("[Polarium] cached clouds off, the game's clouds draw instead: " + t);
			t.printStackTrace();
			GlStateManager.colorMask(true, true, true, true);
		} finally {
			GlStateManager.popMatrix();
		}
	}

	@Unique
	private void polarium$drawClouds(float tickDelta, int anaglyphFilter) {
		GlStateManager.disableCull();
		float f = (float) (client.getCameraEntity().prevTickY + (client.getCameraEntity().y - client.getCameraEntity().prevTickY) * tickDelta);
		double d = ticks + tickDelta;
		double e = (client.getCameraEntity().prevX + (client.getCameraEntity().x - client.getCameraEntity().prevX) * tickDelta + d * 0.03F) / 12.0;
		double i = (client.getCameraEntity().prevZ + (client.getCameraEntity().z - client.getCameraEntity().prevZ) * tickDelta) / 12.0 + 0.33F;
		float j = world.dimension.getCloudHeight() - f + 0.33F;
		int k = MathHelper.floor(e / 2048.0);
		int l = MathHelper.floor(i / 2048.0);
		e -= k * 2048;
		i -= l * 2048;
		textureManager.bindTexture(CLOUDS);
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
		Vec3d cloudColour = world.getCloudColor(tickDelta);
		float m = (float) cloudColour.x;
		float n = (float) cloudColour.y;
		float o = (float) cloudColour.z;
		if (anaglyphFilter != 2) {
			float p = (m * 30.0F + n * 59.0F + o * 11.0F) / 100.0F;
			float q = (m * 30.0F + n * 70.0F) / 100.0F;
			float r = (m * 30.0F + o * 70.0F) / 100.0F;
			m = p;
			n = q;
			o = r;
		}

		float ab = (float) (e - MathHelper.floor(e));
		float ac = (float) (i - MathHelper.floor(i));
		int list = polarium$clouds.list(MathHelper.floor(e), MathHelper.floor(i), m, n, o, j > -5.0F, j <= 5.0F);
		// a display list sets the current colour and normal to its last vertex's (the game's array draw leaves them alone):
		// hold them and put them back, or the hand and whatever is drawn next picks up a cloud's shade
		if (polarium$colour == null) {
			polarium$colour = BufferUtils.createFloatBuffer(16);
			polarium$normal = BufferUtils.createFloatBuffer(16);
		}
		polarium$colour.clear();
		polarium$normal.clear();
		GL11.glGetFloat(GL11.GL_CURRENT_COLOR, polarium$colour);
		GL11.glGetFloat(GL11.GL_CURRENT_NORMAL, polarium$normal);
		GlStateManager.scale(12.0F, 1.0F, 12.0F);
		GlStateManager.translate(-ab, j, -ac);

		for (int pass = 0; pass < 2; pass++) {
			if (pass == 0) {
				GlStateManager.colorMask(false, false, false, false);
			} else {
				switch (anaglyphFilter) {
					case 0:
						GlStateManager.colorMask(false, true, true, true);
						break;
					case 1:
						GlStateManager.colorMask(true, false, false, true);
						break;
					case 2:
						GlStateManager.colorMask(true, true, true, true);
						break;
					default:
						break;
				}
			}
			GL11.glCallList(list);
		}

		GL11.glColor4f(polarium$colour.get(0), polarium$colour.get(1), polarium$colour.get(2), polarium$colour.get(3));
		GL11.glNormal3f(polarium$normal.get(0), polarium$normal.get(1), polarium$normal.get(2));
		GlStateManager.clearColor();
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.disableBlend();
		GlStateManager.enableCull();
	}
}
