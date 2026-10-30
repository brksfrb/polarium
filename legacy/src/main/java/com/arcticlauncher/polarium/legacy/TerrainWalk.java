package com.arcticlauncher.polarium.legacy;

import com.arcticlauncher.polarium.legacy.mixin.BuiltChunkIndexAccess;
import com.arcticlauncher.polarium.legacy.mixin.CullingCameraViewAccess;
import com.arcticlauncher.polarium.legacy.mixin.StorageAccess;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.client.render.BaseFrustum;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.CullingCameraView;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.client.world.ChunkAssemblyHelper;
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
	/** What each visited section's visibility data was when the walk read it (a rebuild can swap it any time). */
	private ChunkAssemblyHelper[] helpers = new ChunkAssemblyHelper[0];
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
	/** The frame each storage slot was last reached in (the game keeps this in every section; here it stays in one array). */
	private int[] stamps = new int[0];
	private int sizeX;
	private int sizeY;
	private int sizeZ;

	private CameraView view;
	private boolean fast;
	private final double[] planes = new double[24];
	private double viewX;
	private double viewY;
	private double viewZ;
	/** The planes the frustum test reads: the real ones, or the widened ones while a reusable walk is built. */
	private double[] active = planes;
	private final double[] widened = new double[24];
	/** The unit normals of the six planes now and in the walk before (to see how fast the camera turns). */
	private final double[] normals = new double[18];
	private final double[] prevNormals = new double[18];
	private boolean normalsKnown;
	private double turn;
	private int sectionY;

	/** How far the reusable walk reaches past the real frustum, in blocks. */
	private static final double WIDEN = 32.0;
	/** The camera may move this far (blocks) before the reusable walk is dropped. */
	private static final double MAX_SHIFT = 8.0;
	/** Walks the reusable walk serves before a new one is made, so rebuilt sections show up. */
	private static final int MAX_AGE = 8;
	/** Only build a wide walk while the camera turns slower than this per walk (distance between unit normals). */
	private static final double CALM_TURN = 0.03;

	private boolean cacheValid;
	private BuiltChunk[] cacheList = new BuiltChunk[0];
	private int cacheCount;
	private int cacheKeep;
	private int cacheAge;
	private Object cacheKey;
	private BuiltChunk[] cacheChunks;
	private boolean cacheCull;
	private int cacheCamX;
	private int cacheCamZ;
	private int cacheSectionY;
	private double cacheViewX;
	private double cacheViewY;
	private double cacheViewZ;
	private final double[] cacheNormals = new double[18];
	private double cacheReach;
	/** Sections the game's walk had that the reused one lacked, and the other way round (verify runs). */
	public int missing;
	public int extra;

	/** Starts a walk; the storage's arrays are sized for the most sections it can hold. */
	public void begin(BuiltChunkStorage storage, CameraView view, int frame, boolean cull, BlockPos cameraChunk, int renderDistance) {
		StorageAccess access = (StorageAccess) storage;
		if (this.chunks != storage.chunks || stamps.length != storage.chunks.length) {
			stamps = new int[storage.chunks.length];
			java.util.Arrays.fill(stamps, -1);
		}
		this.chunks = storage.chunks;
		this.sizeX = access.polarium$sizeX();
		this.sizeY = access.polarium$sizeY();
		this.sizeZ = access.polarium$sizeZ();
		if (queue.length < chunks.length) {
			queue = new BuiltChunk[chunks.length];
			cameFrom = new int[chunks.length];
			facing = new int[chunks.length];
			visible = new BuiltChunk[chunks.length];
			helpers = new ChunkAssemblyHelper[chunks.length];
		}
		head = 0;
		tail = 0;
		visibleCount = 0;
		direct = 0;
		this.frame = frame;
		this.cull = cull;
		this.camX = cameraChunk.getX();
		this.camZ = cameraChunk.getZ();
		this.sectionY = cameraChunk.getY();
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
				this.active = planes;
				measureTurn();
			}
		}
	}

	/** Unit normals of the planes, the widened copy of the planes, and how far the normals moved since the last walk. */
	private void measureTurn() {
		System.arraycopy(normals, 0, prevNormals, 0, 18);
		for (int i = 0; i < 6; i++) {
			double a = planes[i * 4];
			double b = planes[i * 4 + 1];
			double c = planes[i * 4 + 2];
			double len = Math.sqrt(a * a + b * b + c * c);
			normals[i * 3] = a / len;
			normals[i * 3 + 1] = b / len;
			normals[i * 3 + 2] = c / len;
			widened[i * 4] = a;
			widened[i * 4 + 1] = b;
			widened[i * 4 + 2] = c;
			widened[i * 4 + 3] = planes[i * 4 + 3] + WIDEN * len;
		}
		turn = normalsKnown ? distance(normals, prevNormals) : Double.MAX_VALUE;
		normalsKnown = true;
	}

	/** The largest distance between matching unit normals of two plane sets. */
	private static double distance(double[] x, double[] y) {
		double worst = 0;
		for (int i = 0; i < 6; i++) {
			double dx = x[i * 3] - y[i * 3];
			double dy = x[i * 3 + 1] - y[i * 3 + 1];
			double dz = x[i * 3 + 2] - y[i * 3 + 2];
			worst = Math.max(worst, Math.sqrt(dx * dx + dy * dy + dz * dz));
		}
		return worst;
	}

	/**
	 * The walk for this frame. A walk made with the frustum widened by {@link #WIDEN} blocks holds every section
	 * the real frustum will want for the next few frames of a small turn; each frame keeps the ones the real frustum
	 * still sees. Sections only reachable through a path outside the real frustum stay in (the game drops them), and
	 * they are hidden behind what the game keeps, so the picture does not change. key: the section the walk starts
	 * from (null: no reuse for this frame). Returns true when the old walk was reused.
	 */
	public boolean runCached(Object key) {
		seedCount = tail;
		if (!Flags.WALK_CACHE || !fast || key == null || tail == 0) {
			cacheValid = false;
			run();
			return false;
		}
		if (cacheValid && usable(key)) {
			cacheAge++;
			filterCache();
			return true;
		}
		if (turn > CALM_TURN) {
			cacheValid = false;
			run();
			return false;
		}
		active = widened;
		run();
		active = planes;
		if (cacheList.length < visible.length) {
			cacheList = new BuiltChunk[visible.length];
		}
		System.arraycopy(visible, 0, cacheList, 0, visibleCount);
		cacheCount = visibleCount;
		cacheKeep = direct + seedCount;
		cacheAge = 0;
		cacheKey = key;
		cacheChunks = chunks;
		cacheCull = cull;
		cacheCamX = camX;
		cacheCamZ = camZ;
		cacheSectionY = sectionY;
		cacheViewX = viewX;
		cacheViewY = viewY;
		cacheViewZ = viewZ;
		System.arraycopy(normals, 0, cacheNormals, 0, 18);
		// the farthest a section of the walk can be from the eye: the storage's reach on both axes, and the world's height
		double horizontal = (reach + 24) * 1.4143;
		cacheReach = Math.sqrt(horizontal * horizontal + 256.0 * 256.0);
		cacheValid = true;
		filterCache();
		return false;
	}

	private boolean usable(Object key) {
		if (cacheKey != key || cacheChunks != chunks || cacheCull != cull || cacheAge >= MAX_AGE
				|| cacheCamX != camX || cacheCamZ != camZ || cacheSectionY != sectionY) {
			return false;
		}
		double dx = viewX - cacheViewX;
		double dy = viewY - cacheViewY;
		double dz = viewZ - cacheViewZ;
		double shift = Math.sqrt(dx * dx + dy * dy + dz * dz);
		return shift <= MAX_SHIFT && cacheReach * distance(normals, cacheNormals) + shift <= WIDEN;
	}

	/** The old walk's sections the real frustum still sees, in the old walk's order; the walk's own start sections stay. */
	private void filterCache() {
		int n = 0;
		for (int i = 0; i < cacheCount; i++) {
			BuiltChunk section = cacheList[i];
			if (i < cacheKeep || inFrustum(section.field_11071)) {
				visible[n] = section;
				helpers[n] = null;
				n++;
			}
		}
		visibleCount = n;
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
		double[] p = active;
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

	/** A section the walk starts from (the game has already checked what it needs to); slot is {@link #slotAt}. */
	public void seed(BuiltChunk chunk, int slot) {
		stamps[slot] = frame;
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
		helpers[visibleCount] = null;
		visible[visibleCount++] = chunk;
		direct++;
	}

	/** Where the game's storage keeps the section at a block position, or -1 when it has none there. */
	public int slotAt(int x, int y, int z) {
		int sy = y >> 4;
		if (sy < 0 || sy >= sizeY) {
			return -1;
		}
		int ix = (x >> 4) % sizeX;
		if (ix < 0) {
			ix += sizeX;
		}
		int iz = (z >> 4) % sizeZ;
		if (iz < 0) {
			iz += sizeZ;
		}
		return (iz * sizeY + sy) * sizeX + ix;
	}

	/** Visits every section reachable from the seeds. */
	public void run() {
		seedCount = tail;
		while (head < tail) {
			BuiltChunk chunk = queue[head];
			int from = cameFrom[head];
			int mask = facing[head];
			head++;
			ChunkAssemblyHelper helper = chunk.method_10170();
			helpers[visibleCount] = helper;
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
				int nx = px + DX[d];
				int ny = py + DY[d];
				int nz = pz + DZ[d];
				int slot = reachSlot(nx, ny, nz);
				// already reached: nothing below can change that, so the visibility lookup is not needed
				if (slot < 0 || stamps[slot] == frame) {
					continue;
				}
				if (cull && from >= 0 && !helper.isVisibleThrough(DIRS[OPPOSITE[from]], DIRS[d])) {
					continue;
				}
				stamps[slot] = frame;
				// the slot may hold a section from the far edge of the storage, so the test uses that section's own box
				BuiltChunk next = chunks[slot];
				if (inFrustum(next.field_11071)) {
					queue[tail] = next;
					cameFrom[tail] = d;
					facing[tail] = mask | 1 << d;
					tail++;
				}
			}
		}
	}

	/** The section the storage keeps for this block position when it really is at that position, else null. */
	public BuiltChunk sectionAt(int x, int y, int z) {
		int slot = slotAt(x, y, z);
		if (slot < 0 || slot >= chunks.length) {
			return null;
		}
		BuiltChunk chunk = chunks[slot];
		BlockPos pos = chunk.getPos();
		return pos.getX() == (x & ~15) && pos.getY() == (y & ~15) && pos.getZ() == (z & ~15) ? chunk : null;
	}

	private int reachSlot(int x, int y, int z) {
		if (Math.abs(camX - x) > reach || y < 0 || y >= 256 || Math.abs(camZ - z) > reach) {
			return -1;
		}
		return slotAt(x, y, z);
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
		// the chunk builder thread may swap a section's visibility data between the two walks: use what this walk saw
		IdentityHashMap<BuiltChunk, ChunkAssemblyHelper> seen = new IdentityHashMap<>();
		for (int i = 0; i < visibleCount; i++) {
			if (helpers[i] != null) {
				seen.put(visible[i], helpers[i]);
			}
		}
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
						&& (!cull || from == null || helperOf(seen, chunk).isVisibleThrough(from.getOpposite(), d))
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
		if (Flags.WALK_CACHE && fast) {
			// the reused walk keeps everything the game's walk keeps, plus some sections only reachable outside the view
			IdentityHashMap<BuiltChunk, Boolean> mine = new IdentityHashMap<>();
			for (int i = 0; i < visibleCount; i++) {
				mine.put(visible[i], Boolean.TRUE);
			}
			int lacking = 0;
			for (BuiltChunk c : expected) {
				if (!mine.containsKey(c)) {
					lacking++;
				}
			}
			missing += lacking;
			extra += Math.max(0, visibleCount - expected.size() + lacking);
			return lacking == 0 ? null : lacking + " of the game's " + expected.size() + " sections are missing from this walk's " + visibleCount;
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

	private static ChunkAssemblyHelper helperOf(IdentityHashMap<BuiltChunk, ChunkAssemblyHelper> seen, BuiltChunk chunk) {
		ChunkAssemblyHelper helper = seen.get(chunk);
		return helper != null ? helper : chunk.method_10170();
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
