package com.arcticlauncher.polarium.legacy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.entity.Entity;

/**
 * Which of the visible chunk sections hold an entity. The game visits every visible section, finds its
 * world chunk and looks at the entity list of that slice; with a hundred entities and five hundred sections
 * it is cheaper to start from the entities. The sections come back in the game's order (their place in the
 * visible list), so what is drawn, and in which order, does not change.
 */
public final class EntitySections {
	private int[] order = new int[0];
	private int[] orderWalk = new int[0];
	private int[] seen = new int[0];
	private int walkId;
	private int seenId;
	private int[] hits = new int[64];
	private final List<Object> out = new ArrayList<>();

	/** A new visible list is being made: forget the old order. */
	public void begin(int sectionCount) {
		if (order.length < sectionCount) {
			order = new int[sectionCount];
			orderWalk = new int[sectionCount];
			seen = new int[sectionCount];
			walkId = 0;
			seenId = 0;
		}
		walkId++;
	}

	/** The section with this index is at place {@code place} of the visible list. */
	public void record(int index, int place) {
		order[index] = place;
		orderWalk[index] = walkId;
	}

	/** The visible list's own entries for the sections that hold an entity, or null when the answer cannot be trusted. */
	public List<Object> sectionsWithEntities(List<Entity> entities, TerrainWalk walk, List<Object> visible) {
		seenId++;
		int count = 0;
		for (int i = 0, n = entities.size(); i < n; i++) {
			Entity entity = entities.get(i);
			if (!entity.updateNeeded) {
				continue;
			}
			BuiltChunk section = walk.sectionAt(entity.chunkX << 4, entity.chunkY << 4, entity.chunkZ << 4);
			if (section == null) {
				continue;
			}
			int index = TerrainWalk.indexOf(section);
			if (index >= order.length || orderWalk[index] != walkId || seen[index] == seenId) {
				continue;
			}
			seen[index] = seenId;
			if (count == hits.length) {
				hits = Arrays.copyOf(hits, count * 2);
			}
			hits[count++] = order[index];
		}
		Arrays.sort(hits, 0, count);
		out.clear();
		for (int i = 0; i < count; i++) {
			out.add(visible.get(hits[i]));
		}
		return out;
	}
}
