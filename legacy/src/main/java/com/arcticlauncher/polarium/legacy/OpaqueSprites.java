package com.arcticlauncher.polarium.legacy;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.texture.Sprite;

/**
 * The block atlas sprites whose every texel is fully opaque (alpha 255 in every frame and every mip level), and a
 * lookup from texture coordinates to them. A quad of an alpha-tested layer that samples only such a sprite (and
 * whose vertex colours are fully opaque too) passes the alpha test on every pixel, so it draws the same in the
 * solid layer, where nothing is alpha-tested and early depth rejection works (see {@link CutoutRouting}).
 *
 * <p>Sampling stays inside a sprite at every mip level only if the sprite sits on the mip grid; sprites that do
 * not, animated ones and ones stored rotated are never counted as opaque.
 */
public final class OpaqueSprites {
	private static final int CELL_SHIFT = 4;
	private static final boolean LITTLE = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

	private static volatile OpaqueSprites current;

	private final int cellsX;
	private final int cellsY;
	private final int width;
	private final int height;
	/** Per cell, the opaque sprites overlapping it: {@link #cellFirst} indexes {@link #cellSprites}. */
	private final int[] cellFirst;
	private final int[] cellSprites;
	/** Per opaque sprite, its texel rectangle: x0, y0, x1, y1 (exclusive). */
	private final int[] rects;
	final int opaqueCount;
	final int total;

	private OpaqueSprites(int width, int height, int[] rects, int count, int total) {
		this.width = width;
		this.height = height;
		this.rects = rects;
		this.opaqueCount = count;
		this.total = total;
		cellsX = (width + (1 << CELL_SHIFT) - 1) >> CELL_SHIFT;
		cellsY = (height + (1 << CELL_SHIFT) - 1) >> CELL_SHIFT;
		int[] sizes = new int[cellsX * cellsY];
		for (int s = 0; s < count; s++) {
			int cx0 = rects[s * 4] >> CELL_SHIFT;
			int cy0 = rects[s * 4 + 1] >> CELL_SHIFT;
			int cx1 = (rects[s * 4 + 2] - 1) >> CELL_SHIFT;
			int cy1 = (rects[s * 4 + 3] - 1) >> CELL_SHIFT;
			for (int cy = cy0; cy <= cy1; cy++) {
				for (int cx = cx0; cx <= cx1; cx++) {
					sizes[cy * cellsX + cx]++;
				}
			}
		}
		cellFirst = new int[cellsX * cellsY + 1];
		for (int c = 0; c < cellsX * cellsY; c++) {
			cellFirst[c + 1] = cellFirst[c] + sizes[c];
		}
		cellSprites = new int[cellFirst[cellsX * cellsY]];
		int[] fill = new int[cellsX * cellsY];
		for (int s = 0; s < count; s++) {
			int cx0 = rects[s * 4] >> CELL_SHIFT;
			int cy0 = rects[s * 4 + 1] >> CELL_SHIFT;
			int cx1 = (rects[s * 4 + 2] - 1) >> CELL_SHIFT;
			int cy1 = (rects[s * 4 + 3] - 1) >> CELL_SHIFT;
			for (int cy = cy0; cy <= cy1; cy++) {
				for (int cx = cx0; cx <= cx1; cx++) {
					int c = cy * cellsX + cx;
					cellSprites[cellFirst[c] + fill[c]++] = s;
				}
			}
		}
	}

	/** The index builder threads use; null until the block atlas has been stitched. */
	public static OpaqueSprites current() {
		return current;
	}

	/** Render thread, after the block atlas is stitched and uploaded (sprite data still in memory). */
	public static void atlasStitched(Collection<Sprite> sprites, int width, int height, int mipLevels) {
		current = null;
		if (width <= 0 || height <= 0) {
			return;
		}
		int grid = 1 << Math.max(0, mipLevels);
		List<int[]> opaque = new ArrayList<int[]>();
		int total = 0;
		for (Sprite sprite : sprites) {
			total++;
			int[] rect = opaqueRect(sprite, grid, mipLevels);
			if (rect != null && rect[2] <= width && rect[3] <= height) {
				opaque.add(rect);
			}
		}
		int[] rects = new int[opaque.size() * 4];
		for (int i = 0; i < opaque.size(); i++) {
			System.arraycopy(opaque.get(i), 0, rects, i * 4, 4);
		}
		OpaqueSprites index = new OpaqueSprites(width, height, rects, opaque.size(), total);
		current = index;
		System.out.println("[Polarium] opaque cutout sprites: " + index.opaqueCount + " of " + total + " are fully opaque");
	}

	private static int[] opaqueRect(Sprite sprite, int grid, int mipLevels) {
		SpriteAccess access = (SpriteAccess) sprite;
		if (sprite.hasMeta() || access.polarium$rotated() || sprite.getSize() != 1) {
			return null;
		}
		int x = sprite.getX();
		int y = sprite.getY();
		int w = sprite.getWidth();
		int h = sprite.getHeight();
		if (w <= 0 || h <= 0 || x % grid != 0 || y % grid != 0 || w % grid != 0 || h % grid != 0) {
			return null;
		}
		int[][] levels = sprite.method_5831(0);
		if (levels == null || levels.length < mipLevels + 1) {
			return null;
		}
		for (int level = 0; level <= mipLevels; level++) {
			int[] texels = levels[level];
			int expected = (w >> level) * (h >> level);
			if (texels == null || texels.length < expected) {
				return null;
			}
			for (int i = 0; i < expected; i++) {
				if (texels[i] >>> 24 != 0xFF) {
					return null;
				}
			}
		}
		return new int[] {x, y, x + w, y + h};
	}

	/** True when the texture rectangle lies inside one fully opaque sprite. */
	public boolean opaque(float minU, float minV, float maxU, float maxV) {
		double x0 = (double) minU * width;
		double y0 = (double) minV * height;
		double x1 = (double) maxU * width;
		double y1 = (double) maxV * height;
		if (!(x0 >= 0) || !(y0 >= 0) || !(x1 < width) || !(y1 < height)) {
			return false;
		}
		int cx = (int) ((x0 + x1) * 0.5) >> CELL_SHIFT;
		int cy = (int) ((y0 + y1) * 0.5) >> CELL_SHIFT;
		int c = cy * cellsX + cx;
		for (int k = cellFirst[c]; k < cellFirst[c + 1]; k++) {
			int s = cellSprites[k] * 4;
			if (x0 >= rects[s] && y0 >= rects[s + 1] && x1 < rects[s + 2] && y1 < rects[s + 3]) {
				return true;
			}
		}
		return false;
	}

	/** Alpha of a vertex colour as the game stores it (RGBA bytes in memory, read as a native int). */
	public static int alpha(int colour) {
		return LITTLE ? colour >>> 24 : colour & 0xFF;
	}
}
