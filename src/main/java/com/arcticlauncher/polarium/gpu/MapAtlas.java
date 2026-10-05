//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Map pictures side by side in a few big textures, so item frames and hands
 * showing maps are drawn together instead of one draw per map. The game gives
 * every map its own 128×128 texture (and so its own draw): a lobby with a few
 * hundred map billboards is a few hundred draws, where a handful is enough.
 *
 * A map is copied in the first time it's drawn and again whenever the game
 * changes its picture (the map's own texture stays the truth); drawing is the
 * game's own, only reading its pixels from here. Each picture sits in its own
 * cell with a gap around it. Render thread only, except {@link #changed} and
 * {@link #gone}, which the game calls from wherever it updates maps.
 */
public final class MapAtlas {
	/** A map's picture is this many pixels on each side. */
	private static final int PICTURE = 128;
	/** One pixel of gap after each picture, so neighbors never mix. */
	private static final int STRIDE = PICTURE + 1;
	private static final int PAGE_SIZE = 2048;
	private static final int PER_ROW = PAGE_SIZE / STRIDE;
	private static final int PER_PAGE = PER_ROW * PER_ROW;
	/** More maps than this at once (about 1,800) keep the game's own draws. */
	private static final int MAX_PAGES = 8;
	/** Sampling is kept this far inside a picture's edge (in pixels): no sample ever lands in the gap. */
	private static final float INSET = 0.01f;
	/** On with -Dpolarium.mapAtlas=true; measured no faster than the game's own draws, so off. */
	private static final boolean ENABLED = "true".equals(System.getProperty("polarium.mapAtlas"));

	/** Where a map's picture is: the atlas texture to draw with, and its place in it (0 to 1). */
	public record Place(Identifier atlas, float u, float v, float width, float height) {}

	private static final class Page {
		final Identifier id;
		final GpuTexture texture;
		int used;

		Page(Identifier id, GpuTexture texture) {
			this.id = id;
			this.texture = texture;
		}
	}

	private static final class Cell {
		final Page page;
		final int x;
		final int y;
		final Place place;

		Cell(Page page, int index) {
			this.page = page;
			this.x = index % PER_ROW * STRIDE;
			this.y = index / PER_ROW * STRIDE;
			this.place = new Place(page.id, (x + INSET) / PAGE_SIZE, (y + INSET) / PAGE_SIZE,
					(PICTURE - 2 * INSET) / PAGE_SIZE, (PICTURE - 2 * INSET) / PAGE_SIZE);
		}
	}

	private static final List<Page> PAGES = new ArrayList<>();
	private static final Map<Identifier, Cell> CELLS = new HashMap<>();
	/** Cells of maps that were closed, ready for others. */
	private static final List<Cell> FREE = new ArrayList<>();
	/** Maps whose picture the game changed since they were copied in. */
	private static final Set<Identifier> CHANGED = ConcurrentHashMap.newKeySet();
	/** Maps the game closed. */
	private static final Set<Identifier> GONE = ConcurrentHashMap.newKeySet();
	private static boolean failed;

	private MapAtlas() {}

	/** The game changed this map's picture. Any thread. */
	public static void changed(Identifier map) {
		CHANGED.add(map);
	}

	/** The game closed this map's texture. Any thread. */
	public static void gone(Identifier map) {
		GONE.add(map);
		CHANGED.remove(map);
	}

	/**
	 * Where this map's picture is in the atlas (copying it in if it's new or
	 * changed), or null to draw it the game's own way.
	 */
	public static @Nullable Place place(Identifier map) {
		if (!ENABLED || failed || !RenderSystem.isOnRenderThread()) {
			return null;
		}
		try {
			releaseClosed();
			AbstractTexture source = Minecraft.getInstance().getTextureManager().getTexture(map);
			if (!(source instanceof DynamicTexture dynamic)) {
				return null;
			}
			NativeImage pixels = dynamic.getPixels();
			if (pixels == null || pixels.getWidth() != PICTURE || pixels.getHeight() != PICTURE) {
				return null;
			}
			Cell cell = CELLS.get(map);
			boolean fresh = cell == null;
			if (fresh) {
				cell = allocate(dynamic.getSampler());
				if (cell == null) {
					return null;
				}
				CELLS.put(map, cell);
			}
			if (fresh | CHANGED.remove(map)) {
				RenderSystem.getDevice().createCommandEncoder().writeToTexture(cell.page.texture, pixels, 0, 0, cell.x, cell.y);
			}
			return cell.place;
		} catch (RuntimeException e) {
			failed = true;
			org.slf4j.LoggerFactory.getLogger("Polarium").warn("Polarium: maps can't share a texture here; one draw per map", e);
			return null;
		}
	}

	/** Cells of closed maps go back to the pool. */
	private static void releaseClosed() {
		if (GONE.isEmpty()) {
			return;
		}
		for (Identifier map : List.copyOf(GONE)) {
			GONE.remove(map);
			Cell cell = CELLS.remove(map);
			if (cell != null) {
				FREE.add(cell);
			}
		}
	}

	private static @Nullable Cell allocate(GpuSampler sampler) {
		if (!FREE.isEmpty()) {
			return FREE.remove(FREE.size() - 1);
		}
		Page page = PAGES.isEmpty() ? null : PAGES.get(PAGES.size() - 1);
		if (page == null || page.used == PER_PAGE) {
			if (PAGES.size() == MAX_PAGES) {
				return null;
			}
			page = newPage(sampler);
		}
		return new Cell(page, page.used++);
	}

	private static Page newPage(GpuSampler sampler) {
		Identifier id = Identifier.fromNamespaceAndPath("polarium", "map_atlas_" + PAGES.size());
		GpuTexture texture = RenderSystem.getDevice().createTexture(() -> "Polarium map atlas " + id, GpuTexture.USAGE_COPY_DST
				| GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, PAGE_SIZE, PAGE_SIZE, 1, 1);
		Minecraft.getInstance().getTextureManager().register(id, new MapAtlasTexture(texture, sampler));
		Page page = new Page(id, texture);
		PAGES.add(page);
		return page;
	}

	/** An atlas page as the game's texture manager knows textures. */
	private static final class MapAtlasTexture extends AbstractTexture {
		MapAtlasTexture(GpuTexture texture, GpuSampler sampler) {
			this.texture = texture;
			this.textureView = RenderSystem.getDevice().createTextureView(texture);
			this.sampler = sampler;
		}
	}
}
//#endif
