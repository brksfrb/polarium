package com.arcticlauncher.polarium.legacy;

import com.arcticlauncher.polarium.legacy.mixin.BuiltChunkIndexAccess;
import com.arcticlauncher.polarium.legacy.mixin.CullingCameraViewAccess;
import com.arcticlauncher.polarium.legacy.mixin.StorageAccess;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.client.render.BaseFrustum;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.CullingCameraView;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

/**
 * The game's walk through the chunk sections you can see (WorldRenderer.setupTerrain), same order and
 * same answers, without a new object per section: a queue of arrays, the directions travelled kept as
 * bits, the neighbour found by arithmetic and the frustum test cut to one corner per plane.
 *
 * <p>The frustum test: the game keeps a box when any of its 8 corners is inside every plane. The corner
 * that is furthest along a plane's normal has the largest value of the plane equation (the equation is
 * computed the same way, and float rounding keeps the order), so a box is outside a plane exactly when
 * that one corner is.
 */
public final class TerrainWalk {
	private static final Direction[] DIRS = Direction.values();
	private static final int[] OPPOSITE = new int[6];
	private static final int[] DX = new int[6];
	private static final int[] DY = new int[6];
	private static final int[] DZ = new int[6];

	static {
		for (Direction d : DIRS) {
			OPPOSITE[d.ordinal()] = d.getOpposite().ordinal();
			DX[d.ordinal()] = d.getOffsetX() * 16;
			DY[d.ordinal()] = d.getOffsetY() * 16;
			DZ[d.ordinal()] = d.getOffsetZ() * 16;
		}
	}

	/** The chunk sections reached, in the order the game visits them. */
	public BuiltChunk[] visible = new BuiltChunk[0];
	public int visibleCount;

	private BuiltChunk[] queue = new BuiltChunk[0];
	private int[] cameFrom = new int[0];
	private int[] facing = new int[0];
	private int seedCount;
	private int direct;
	private int head;
	private int tail;

	private int frame;
	private boolean cull;
	private int camX;
	private int camZ;
	private int reach;
	private BuiltChunk[] chunks;
	private int sizeX;
	private int sizeY;
	private int sizeZ;

	private CameraView view;
	private boolean fast;
	private final double[] planes = new double[24];
	private double viewX;
	private double viewY;
	private double viewZ;

	/** Starts a walk; the storage's arrays are sized for the most sections it can hold. */
	public void begin(BuiltChunkStorage storage, CameraView view, int frame, boolean cull, BlockPos cameraChunk, int renderDistance) {
		StorageAccess access = (StorageAccess) storage;
		this.chunks = storage.chunks;
		this.sizeX = access.polarium$sizeX();
		this.sizeY = access.polarium$sizeY();
		this.sizeZ = access.polarium$sizeZ();
		if (queue.length < chunks.length) {
			queue = new BuiltChunk[chunks.length];
			cameFrom = new int[chunks.length];
			facing = new int[chunks.length];
			visible = new BuiltChunk[chunks.length];
		}
		head = 0;
		tail = 0;
		visibleCount = 0;
		direct = 0;
		this.frame = frame;
		this.cull = cull;
		this.camX = cameraChunk.getX();
		this.camZ = cameraChunk.getZ();
		this.reach = renderDistance * 16;
		this.view = view;
		this.fast = false;
		if (view instanceof CullingCameraView) {
			CullingCameraViewAccess cv = (CullingCameraViewAccess) view;
			BaseFrustum frustum = cv.polarium$clipper();
			if (frustum != null && frustum.getClass() == net.minecraft.client.render.Frustum.class && frustum.homogeneousCoordinates.length == 6) {
				for (int i = 0; i < 6; i++) {
					float[] p = frustum.homogeneousCoordinates[i];
					for (int j = 0; j < 4; j++) {
						planes[i * 4 + j] = p[j];
					}
				}
				viewX = cv.polarium$x();
				viewY = cv.polarium$y();
				viewZ = cv.polarium$z();
				this.fast = true;
			}
		}
	}

	public boolean inFrustum(Box box) {
		if (!fast) {
			return view.isBoxInFrustum(box);
		}
		double minX = box.minX - viewX;
		double minY = box.minY - viewY;
		double minZ = box.minZ - viewZ;
		double maxX = box.maxX - viewX;
		double maxY = box.maxY - viewY;
		double maxZ = box.maxZ - viewZ;
		double[] p = planes;
		for (int i = 0; i < 24; i += 4) {
			double a = p[i];
			double b = p[i + 1];
			double c = p[i + 2];
			double x = a > 0 ? maxX : minX;
			double y = b > 0 ? maxY : minY;
			double z = c > 0 ? maxZ : minZ;
			if (!(a * x + b * y + c * z + p[i + 3] > 0.0)) {
				return false;
			}
		}
		return true;
	}

	public void setCull(boolean cull) {
		this.cull = cull;
	}

	/** A section the walk starts from (the game has already checked what it needs to). */
	public void seed(BuiltChunk chunk) {
		if (seeds.length < tail + 1) {
			seeds = java.util.Arrays.copyOf(seeds, Math.max(16, seeds.length * 2));
		}
		seeds[tail] = chunk;
		queue[tail] = chunk;
		cameFrom[tail] = -1;
		facing[tail] = 0;
		tail++;
	}

	public void addVisible(BuiltChunk chunk) {
		visible[visibleCount++] = chunk;
		direct++;
	}

	public boolean mark(BuiltChunk chunk) {
		return chunk.method_10156(frame);
	}

	/** Visits every section reachable from the seeds. */
	public void run() {
		seedCount = tail;
		while (head < tail) {
			BuiltChunk chunk = queue[head];
			int from = cameFrom[head];
			int mask = facing[head];
			head++;
			visible[visibleCount++] = chunk;
			BlockPos pos = chunk.getPos();
			int px = pos.getX();
			int py = pos.getY();
			int pz = pos.getZ();
			for (int d = 0; d < 6; d++) {
				int opposite = OPPOSITE[d];
				if (cull && (mask >> opposite & 1) != 0) {
					continue;
				}
				if (cull && from >= 0 && !chunk.method_10170().isVisibleThrough(DIRS[OPPOSITE[from]], DIRS[d])) {
					continue;
				}
				BuiltChunk next = neighbour(px + DX[d], py + DY[d], pz + DZ[d]);
				if (next != null && next.method_10156(frame) && inFrustum(next.field_11071)) {
					queue[tail] = next;
					cameFrom[tail] = d;
					facing[tail] = mask | 1 << d;
					tail++;
				}
			}
		}
	}

	private BuiltChunk neighbour(int x, int y, int z) {
		if (Math.abs(camX - x) > reach || y < 0 || y >= 256 || Math.abs(camZ - z) > reach) {
			return null;
		}
		int ix = (x >> 4) % sizeX;
		if (ix < 0) {
			ix += sizeX;
		}
		int iz = (z >> 4) % sizeZ;
		if (iz < 0) {
			iz += sizeZ;
		}
		return chunks[(iz * sizeY + (y >> 4)) * sizeX + ix];
	}

	/** For the pool of per-section records the game's own lists want. */
	public static int indexOf(BuiltChunk chunk) {
		return ((BuiltChunkIndexAccess) chunk).polarium$index();
	}

	/**
	 * -Dpolarium.terrain.verify=true: walks again the way the game's own code does (sets, a linked
	 * queue, the game's neighbour lookup and its own frustum test) from the same seeds, and compares.
	 * Returns what differs, or null when the two walks agree.
	 */
	public String verify(int referenceMark, BuiltChunkStorage storage, BlockPos cameraChunk, int renderDistance) {
		List<BuiltChunk> expected = new ArrayList<>();
		Queue<Object[]> queue = new LinkedList<>();
		for (int i = 0; i < direct; i++) {
			expected.add(visible[i]);
		}
		for (int i = 0; i < seedCount; i++) {
			BuiltChunk seed = visibleSeed(i);
			seed.method_10156(referenceMark);
			queue.add(new Object[] {seed, null, EnumSet.noneOf(Direction.class)});
		}
		while (!queue.isEmpty()) {
			Object[] entry = queue.poll();
			BuiltChunk chunk = (BuiltChunk) entry[0];
			Direction from = (Direction) entry[1];
			@SuppressWarnings("unchecked")
			Set<Direction> travelled = (Set<Direction>) entry[2];
			expected.add(chunk);
			for (Direction d : Direction.values()) {
				BuiltChunk next = referenceNeighbour(storage, cameraChunk, renderDistance, chunk, d);
				if ((!cull || !travelled.contains(d.getOpposite()))
						&& (!cull || from == null || chunk.method_10170().isVisibleThrough(from.getOpposite(), d))
						&& next != null
						&& next.method_10156(referenceMark)
						&& view.isBoxInFrustum(next.field_11071)) {
					Set<Direction> set = EnumSet.noneOf(Direction.class);
					set.addAll(travelled);
					set.add(d);
					queue.add(new Object[] {next, d, set});
				}
			}
		}
		if (expected.size() != visibleCount) {
			return "game's walk saw " + expected.size() + " sections, this one " + visibleCount;
		}
		for (int i = 0; i < visibleCount; i++) {
			if (expected.get(i) != visible[i]) {
				return "section " + i + " differs";
			}
		}
		return null;
	}

	private BuiltChunk[] seeds = new BuiltChunk[0];

	private BuiltChunk visibleSeed(int i) {
		return seeds[i];
	}

	private static BuiltChunk referenceNeighbour(BuiltChunkStorage storage, BlockPos camera, int renderDistance, BuiltChunk chunk, Direction d) {
		BlockPos pos = chunk.method_10161(d);
		if (Math.abs(camera.getX() - pos.getX()) > renderDistance * 16) {
			return null;
		} else if (pos.getY() < 0 || pos.getY() >= 256) {
			return null;
		} else {
			return Math.abs(camera.getZ() - pos.getZ()) > renderDistance * 16 ? null : ((StorageAccess) storage).polarium$chunkAt(pos);
		}
	}
}
