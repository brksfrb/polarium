package com.arcticlauncher.polarium.legacy;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * The faces of the camera's chunk section you can see out of. The game works them out from the section's
 * 4096 blocks every time the view changes; they only change when a block does, so the answer is kept
 * until the game reports a block, light or chunk-data update, the camera block moves, or the world chunk
 * object is replaced.
 */
public final class OpenFacesCache {
	private int x;
	private int y;
	private int z;
	private Object chunk;
	private long epoch;
	private long cachedEpoch = -1;
	private EnumSet<Direction> result;

	public void blocksChanged() {
		epoch++;
	}

	/** A copy of the kept answer (the game removes entries from it), or null when it is out of date. */
	public Set<Direction> get(BlockPos pos, Object worldChunk) {
		if (result != null && worldChunk == chunk && cachedEpoch == epoch && pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
			return EnumSet.copyOf(result);
		}
		return null;
	}

	public void put(BlockPos pos, Object worldChunk, Set<Direction> faces) {
		EnumSet<Direction> copy = EnumSet.noneOf(Direction.class);
		copy.addAll(faces);
		result = copy;
		chunk = worldChunk;
		cachedEpoch = epoch;
		x = pos.getX();
		y = pos.getY();
		z = pos.getZ();
	}
}
