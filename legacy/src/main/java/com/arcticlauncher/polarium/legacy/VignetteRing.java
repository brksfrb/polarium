package com.arcticlauncher.polarium.legacy;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureUtil;
import net.minecraft.util.Identifier;

/**
 * The vignette is a full-screen quad blended as {@code dst * (1 - src)}: where its texture is black the result is
 * the pixel as it was, exactly. This finds the black middle of the texture (whatever resource pack is loaded) and
 * draws the vignette as a ring around it, as horizontal bands, so the middle is not touched. Texture coordinates
 * are the same linear function of the screen position as the game's quad.
 *
 * <p>A texel only counts as inside when every texel the sampler could read around it (two each way, which covers
 * linear filtering and wrap-free rounding) is black too.
 */
public final class VignetteRing {
	/** Texture rows per band. */
	private static final int BAND = 8;
	/** Texels of slack around a sample. */
	private static final int SLACK = 2;

	private static Object loadedFor;
	/** Bands of the black middle in texture coordinates (0 to 1): v0, v1, u0, u1 per band; null if none. */
	private static float[] bands;

	private VignetteRing() {
	}

	/** Called when resources reload: the texture may have changed. */
	public static void resourcesReloaded() {
		loadedFor = null;
	}

	private static float[] bands(Identifier texture) {
		Object key = MinecraftClient.getInstance().getTextureManager().getTexture(texture);
		if (key != null && key == loadedFor) {
			return bands;
		}
		loadedFor = key;
		bands = null;
		try {
			InputStream in = MinecraftClient.getInstance().getResourceManager().getResource(texture).getInputStream();
			BufferedImage image;
			try {
				image = TextureUtil.create(in);
			} finally {
				in.close();
			}
			bands = compute(image);
		} catch (Throwable t) {
			System.out.println("[Polarium] vignette drawn whole: " + t);
		}
		return bands;
	}

	static float[] compute(BufferedImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
		// per row: the black run through the middle column, as [lo, hi] inclusive, or lo > hi
		int[] lo = new int[h];
		int[] hi = new int[h];
		int mid = w / 2;
		for (int y = 0; y < h; y++) {
			if ((argb[y * w + mid] & 0xFFFFFF) != 0) {
				lo[y] = 1;
				hi[y] = 0;
				continue;
			}
			int a = mid;
			while (a > 0 && (argb[y * w + a - 1] & 0xFFFFFF) == 0) {
				a--;
			}
			int b = mid;
			while (b < w - 1 && (argb[y * w + b + 1] & 0xFFFFFF) == 0) {
				b++;
			}
			lo[y] = a;
			hi[y] = b;
		}
		float[] out = new float[(h / BAND + 1) * 4];
		int n = 0;
		for (int y0 = 0; y0 < h; y0 += BAND) {
			int y1 = Math.min(h, y0 + BAND);
			int a = 0;
			int b = w - 1;
			boolean ok = y0 - SLACK >= 0 && y1 + SLACK <= h;
			for (int y = y0 - SLACK; ok && y < y1 + SLACK; y++) {
				a = Math.max(a, lo[y]);
				b = Math.min(b, hi[y]);
			}
			// sample positions s (texels) whose neighbourhood [s - SLACK, s + SLACK] is black: s in [a + SLACK, b + 1 - SLACK]
			int s0 = a + SLACK;
			int s1 = b + 1 - SLACK;
			if (!ok || s1 <= s0 || a <= 0 || b >= w - 1) {
				continue;
			}
			out[n++] = (float) y0 / h;
			out[n++] = (float) y1 / h;
			out[n++] = (float) s0 / w;
			out[n++] = (float) s1 / w;
		}
		if (n == 0) {
			return null;
		}
		// keep one run of consecutive bands (the middle): the ring is drawn as a stack of bands
		float[] run = new float[n];
		int m = 0;
		for (int i = 0; i < n; i += 4) {
			if (m > 0 && out[i] != run[m - 3]) {
				break;
			}
			System.arraycopy(out, i, run, m, 4);
			m += 4;
		}
		float[] result = new float[m];
		System.arraycopy(run, 0, result, 0, m);
		return result;
	}

	/**
	 * Draws the game's vignette quad (screen {@code width} by {@code height}, z -90, texture 0 to 1) minus the black
	 * middle. Same state as the game's draw; returns false when it cannot (the caller then draws the quad).
	 */
	public static boolean draw(Identifier texture, double width, double height) {
		float[] b = bands(texture);
		if (b == null) {
			return false;
		}
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();
		buffer.begin(7, VertexFormats.POSITION_TEXTURE);
		double top = b[0];
		double bottom = b[b.length - 3];
		quad(buffer, 0.0, 0.0, 1.0, top, width, height);
		for (int i = 0; i < b.length; i += 4) {
			quad(buffer, 0.0, b[i], b[i + 2], b[i + 1], width, height);
			quad(buffer, b[i + 3], b[i], 1.0, b[i + 1], width, height);
		}
		quad(buffer, 0.0, bottom, 1.0, 1.0, width, height);
		tessellator.draw();
		return true;
	}

	/** One quad from texture (u0, v0) to (u1, v1), placed where the full-screen quad has those coordinates. */
	private static void quad(BufferBuilder buffer, double u0, double v0, double u1, double v1, double width, double height) {
		if (u1 <= u0 || v1 <= v0) {
			return;
		}
		// same corner order and winding as the game's quad: bottom left, bottom right, top right, top left
		buffer.vertex(u0 * width, v1 * height, -90.0).texture(u0, v1).next();
		buffer.vertex(u1 * width, v1 * height, -90.0).texture(u1, v1).next();
		buffer.vertex(u1 * width, v0 * height, -90.0).texture(u1, v0).next();
		buffer.vertex(u0 * width, v0 * height, -90.0).texture(u0, v0).next();
	}
}
