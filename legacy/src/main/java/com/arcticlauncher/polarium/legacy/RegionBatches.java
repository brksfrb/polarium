package com.arcticlauncher.polarium.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.world.ChunkRenderHelperImpl;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

/**
 * Draws one layer of the visible chunk sections as a few large calls: the sections are grouped by region
 * and each group is one translate and one glCallLists of the sections' display lists.
 *
 * <p>The opaque layers are grouped by region wherever the sections sit in the visible list (nothing two
 * sections draw can overlap, so their order does not matter). The translucent layer keeps the game's
 * back to front order exactly: only neighbours in that order that share a region are drawn together.
 */
public final class RegionBatches {
	private static final int TABLE_BITS = 10;
	private static final int TABLE_SIZE = 1 << TABLE_BITS;
	/** More regions than this in one layer (never in practice): fall back to runs, which are always correct. */
	private static final int MAX_GROUPS = TABLE_SIZE / 2;

	private int[] lists = new int[0];
	private int[] group = new int[0];
	private int[] regionX = new int[0];
	private int[] regionZ = new int[0];
	private int[] start = new int[0];
	private int[] fill = new int[0];
	private IntBuffer sorted = allocate(0);

	private final long[] tableKey = new long[TABLE_SIZE];
	private final int[] tableValue = new int[TABLE_SIZE];
	private final int[] tableStamp = new int[TABLE_SIZE];
	private int stamp;

	/** Draws the sections the game collected for this layer, in the game's order of preference. */
	public void render(List<BuiltChunk> sections, RenderLayer layer, double viewX, double viewY, double viewZ) {
		int count = sections.size();
		if (count == 0) {
			return;
		}
		ensure(count);
		boolean ordered = layer == RenderLayer.TRANSLUCENT;
		int groups = ordered ? runs(sections, layer, count) : byRegion(sections, layer, count);
		if (groups < 0) {
			groups = runs(sections, layer, count);
		}
		layout(count, groups);
		for (int g = 0; g < groups; g++) {
			int from = start[g];
			int to = g + 1 < groups ? start[g + 1] : drawn;
			if (to == from) {
				continue;
			}
			sorted.limit(to).position(from);
			GlStateManager.pushMatrix();
			GlStateManager.translate((float) (regionX[g] - viewX), (float) (0 - viewY), (float) (regionZ[g] - viewZ));
			GL11.glCallLists(sorted);
			GlStateManager.popMatrix();
		}
	}

	/** Groups by order of first appearance; returns the group count, or -1 when there are too many regions. */
	private int byRegion(List<BuiltChunk> sections, RenderLayer layer, int count) {
		stamp++;
		int groups = 0;
		for (int i = 0; i < count; i++) {
			BuiltChunk section = sections.get(i);
			lists[i] = listOf(section, layer);
			BlockPos pos = section.getPos();
			int rx = Regions.origin(pos.getX());
			int rz = Regions.origin(pos.getZ());
			long key = (long) rx << 32 ^ (rz & 0xFFFFFFFFL);
			int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> (64 - TABLE_BITS));
			while (tableStamp[slot] == stamp && tableKey[slot] != key) {
				slot = slot + 1 & TABLE_SIZE - 1;
			}
			if (tableStamp[slot] != stamp) {
				if (groups == MAX_GROUPS) {
					return -1;
				}
				tableStamp[slot] = stamp;
				tableKey[slot] = key;
				tableValue[slot] = groups;
				regionX[groups] = rx;
				regionZ[groups] = rz;
				groups++;
			}
			group[i] = tableValue[slot];
		}
		return groups;
	}

	/** Groups consecutive sections of one region; returns the group count. */
	private int runs(List<BuiltChunk> sections, RenderLayer layer, int count) {
		int groups = 0;
		int lastX = 0;
		int lastZ = 0;
		for (int i = 0; i < count; i++) {
			BuiltChunk section = sections.get(i);
			lists[i] = listOf(section, layer);
			BlockPos pos = section.getPos();
			int rx = Regions.origin(pos.getX());
			int rz = Regions.origin(pos.getZ());
			if (i == 0 || rx != lastX || rz != lastZ) {
				regionX[groups] = rx;
				regionZ[groups] = rz;
				groups++;
				lastX = rx;
				lastZ = rz;
			}
			group[i] = groups - 1;
		}
		return groups;
	}

	/** Counting sort of the list ids by group, keeping their order inside a group; drops sections with nothing to draw. */
	private void layout(int count, int groups) {
		for (int g = 0; g < groups; g++) {
			fill[g] = 0;
		}
		for (int i = 0; i < count; i++) {
			if (lists[i] >= 0) {
				fill[group[i]]++;
			}
		}
		int total = 0;
		for (int g = 0; g < groups; g++) {
			int size = fill[g];
			start[g] = total;
			fill[g] = total;
			total += size;
		}
		sorted.clear();
		for (int i = 0; i < count; i++) {
			if (lists[i] >= 0) {
				sorted.put(fill[group[i]]++, lists[i]);
			}
		}
		drawn = total;
	}

	/** How many display lists the last layout holds. */
	private int drawn;

	private static int listOf(BuiltChunk section, RenderLayer layer) {
		ChunkRenderHelperImpl impl = (ChunkRenderHelperImpl) section;
		return impl.method_10153(layer, impl.method_10170());
	}

	private void ensure(int count) {
		if (lists.length >= count) {
			return;
		}
		int size = Math.max(count, 1024);
		lists = new int[size];
		group = new int[size];
		regionX = new int[size];
		regionZ = new int[size];
		start = new int[size];
		fill = new int[size];
		sorted = allocate(size);
	}

	private static IntBuffer allocate(int size) {
		return ByteBuffer.allocateDirect(Math.max(size, 1) * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
	}
}
