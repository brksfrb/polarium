//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

import com.arcticlauncher.polarium.Workers;
import com.arcticlauncher.polarium.mixin.LivingEntityRendererAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * This frame's crowd (see {@link Crowd}): the players taken, their members
 * (one per recipe entry), and where each member's instance data goes.
 *
 * While entities are submitted a player is only queued ({@link #queue}); once
 * they're all in, {@link #prepare} works out every player's root, its members
 * and their groups, and its name tags' places, on the helper threads. The GPU
 * paths then say where each member's data goes ({@link #target}), and
 * {@link #computeAll} and {@link #linkBorrowed} write it, on the helper
 * threads too. Render thread only, apart from those helpers.
 */
final class CrowdFrame {
	/** Below this many players the helper threads aren't worth waking. */
	private static final int PARALLEL_MIN = 64;
	/** How often a player's groups are looked up again (see {@link #place}). */
	private static final long GROUPS_FRAMES = 30;

	static long frame;
	private static long computedFrame = -1;
	private static long linkedFrame = -1;

	/** This frame's group submits, built by {@link #prepare}. */
	static final List<Crowd.Bucket> ACTIVE_MODELS = new ArrayList<>();
	static final List<Crowd.ItemBucket> ACTIVE_ITEMS = new ArrayList<>();

	// This frame's players.
	static int entityCount;
	private static CrowdRecipe[] entityRecipe = new CrowdRecipe[256];
	private static AvatarRenderState[] entityState = new AvatarRenderState[256];
	/** The pose the entity was submitted at (its position, before the renderer's own transforms). */
	private static float[] entityPose = new float[256 * 16];
	static float[] entityRoot = new float[256 * 16];
	private static int[] entityLight = new int[256];
	private static int[] entityFirstMember = new int[256];
	private static int[] entityMemberCount = new int[256];

	// This frame's members: one per (player, recipe entry).
	private static int memberCount;
	private static Object[] memberEntry = new Object[1024];
	/** A model member's own bucket (its group's submit covers several buckets: see {@link Crowd.Bucket#groupKey}). */
	private static Crowd.Bucket[] memberBucket = new Crowd.Bucket[1024];
	private static float[] memberDistance = new float[1024];
	/** First target (-1: none, its submit wasn't taken), chained through {@link #targetNext}. */
	private static int[] memberTarget = new int[1024];

	// Where members' instance data goes: set when the GPU path takes the group's submit.
	private static int targetCount;
	private static InstanceData[] targetData = new InstanceData[1024];
	private static int[] targetOffset = new int[1024];
	private static int[] targetNext = new int[1024];
	private static @Nullable ModelMesh[] targetMesh = new ModelMesh[1024];
	private static float[][] targetUv = new float[1024][];

	private CrowdFrame() {}

	static void begin() {
		frame++;
		entityCount = 0;
		memberCount = 0;
		targetCount = 0;
		ACTIVE_MODELS.clear();
		ACTIVE_ITEMS.clear();
	}

	/** A player taken by the crowd path, submitted at {@code pose}; worked out in {@link #prepare}. Returns its number. */
	static int queue(CrowdRecipe recipe, AvatarRenderState state, Matrix4f pose) {
		int e = entityCount++;
		if (e == entityRecipe.length) {
			int size = e * 2;
			entityRecipe = Arrays.copyOf(entityRecipe, size);
			entityState = Arrays.copyOf(entityState, size);
			entityPose = Arrays.copyOf(entityPose, size * 16);
			entityRoot = Arrays.copyOf(entityRoot, size * 16);
			entityLight = Arrays.copyOf(entityLight, size);
			entityFirstMember = Arrays.copyOf(entityFirstMember, size);
			entityMemberCount = Arrays.copyOf(entityMemberCount, size);
		}
		entityRecipe[e] = recipe;
		entityState[e] = state;
		entityLight[e] = state.lightCoords;
		pose.get(entityPose, e * 16);
		entityMemberCount[e] = recipe.models.size() + recipe.items.size();
		return e;
	}

	/** Room for {@code count} players queued with {@link #queueAt} (on any thread); returns the first one's number. */
	static int reserve(int count) {
		int first = entityCount;
		entityCount += count;
		if (entityCount > entityRecipe.length) {
			int size = Math.max(entityRecipe.length * 2, entityCount);
			entityRecipe = Arrays.copyOf(entityRecipe, size);
			entityState = Arrays.copyOf(entityState, size);
			entityPose = Arrays.copyOf(entityPose, size * 16);
			entityRoot = Arrays.copyOf(entityRoot, size * 16);
			entityLight = Arrays.copyOf(entityLight, size);
			entityFirstMember = Arrays.copyOf(entityFirstMember, size);
			entityMemberCount = Arrays.copyOf(entityMemberCount, size);
		}
		return first;
	}

	/** A reserved player (see {@link #queue}). */
	static void queueAt(int e, CrowdRecipe recipe, AvatarRenderState state, Matrix4f pose) {
		entityRecipe[e] = recipe;
		entityState[e] = state;
		entityLight[e] = state.lightCoords;
		pose.get(entityPose, e * 16);
		entityMemberCount[e] = recipe.models.size() + recipe.items.size();
	}

	/** The model's root, as {@link LivingEntityRenderer#submit} works it out, from the pose the entity was submitted at. */
	static Matrix4f root(LivingEntityRenderer<?, ?, ?> renderer, AvatarRenderState state, Matrix4f pose, PoseStack scratch) {
		LivingEntityRendererAccess access = (LivingEntityRendererAccess) renderer;
		scratch.last().setIdentity();
		scratch.last().pose().set(pose);
		float scale = state.scale;
		scratch.scale(scale, scale, scale);
		access.polarium$setupRotations(state, scratch, state.bodyRot, scale);
		scratch.scale(-1.0F, -1.0F, 1.0F);
		access.polarium$scale(state, scratch);
		scratch.translate(0.0F, -1.501F, 0.0F);
		return scratch.last().pose();
	}

	/** One part of the players' groups: the groups it saw, and its members of each, in order. */
	private static final class Part {
		final List<Object> groups = new ArrayList<>();
		final List<IntArrayList> members = new ArrayList<>();
		final FloatList farthest = new FloatList();
		final IntArrayList farthestEntity = new IntArrayList();

		void add(Object group, int member, float distance, int entity) {
			int g = groups.size() - 1;
			while (g >= 0 && groups.get(g) != group) {
				g--;
			}
			if (g < 0) {
				g = groups.size();
				groups.add(group);
				members.add(new IntArrayList());
				farthest.add(-1);
				farthestEntity.add(entity);
			}
			members.get(g).add(member);
			if (distance > farthest.get(g)) {
				farthest.set(g, distance);
				farthestEntity.set(g, entity);
			}
		}
	}

	/** A tiny growable float list. */
	private static final class FloatList {
		private float[] values = new float[8];
		private int size;

		void add(float value) {
			if (size == values.length) {
				values = Arrays.copyOf(values, size * 2);
			}
			values[size++] = value;
		}

		float get(int i) {
			return values[i];
		}

		void set(int i, float value) {
			values[i] = value;
		}
	}

	private static final class Scratch {
		final PoseStack pose = new PoseStack();
		final Matrix4f entity = new Matrix4f();
		final Matrix4f root = new Matrix4f();
		final Matrix4f item = new Matrix4f();
		final Matrix4f tag = new Matrix4f();
		final PoseStack hand = new PoseStack();
		final List<Matrix4f> stack = new ArrayList<>();
		float[] parts = new float[PartPoses.VALUES_PER_PART * 64];
		/** The six moving parts' values (skeleton format), and the body's kept for its held items. */
		final float[] skeleton = new float[HumanoidPoses.PARTS * HumanoidPoses.VALUES];
		final float[] anchorSkeleton = new float[HumanoidPoses.PARTS * HumanoidPoses.VALUES];
		/** This thread's model copies (made on the thread the scratch is for). */
		final ModelCopies.Mine copies = ModelCopies.mine();
	}

	private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

	/**
	 * Every queued player worked out (on the helper threads): its root, its
	 * members and the groups they go in, its name tags' places. Then the
	 * groups this frame, each with its members in order.
	 */
	static void prepare() {
		int count = entityCount;
		int total = 0;
		for (int e = 0; e < count; e++) {
			entityFirstMember[e] = total;
			total += entityMemberCount[e];
		}
		ensureMembers(total);
		memberCount = total;
		CrowdTags.layOut(count);
		int parts = count < PARALLEL_MIN ? 1 : Workers.PARTS;
		Part[] work = new Part[parts];
		List<Runnable> jobs = new ArrayList<>(parts);
		for (int p = 0; p < parts; p++) {
			int from = count * p / parts;
			int to = count * (p + 1) / parts;
			Part part = work[p] = new Part();
			jobs.add(() -> place(from, to, part));
		}
		if (parts == 1) {
			jobs.get(0).run();
		} else {
			Workers.runAll(jobs);
		}
		CrowdTags.finish();
		reportAnimation();
		// The groups, their members in the players' order.
		for (Part part : work) {
			for (int g = 0; g < part.groups.size(); g++) {
				Object key = part.groups.get(g);
				IntArrayList members = part.members.get(g);
				float far = part.farthest.get(g);
				int farEntity = part.farthestEntity.getInt(g);
				if (key instanceof Crowd.Bucket bucket) {
					if (bucket.frame != frame) {
						bucket.frame = frame;
						bucket.members.clear();
						bucket.farthest = -1;
						ACTIVE_MODELS.add(bucket);
					}
					bucket.members.addElements(bucket.members.size(), members.elements(), 0, members.size());
					if (far > bucket.farthest) {
						bucket.farthest = far;
						bucket.farthestEntity = farEntity;
					}
				} else if (key instanceof Crowd.ItemBucket bucket) {
					if (bucket.frame != frame) {
						bucket.frame = frame;
						bucket.members.clear();
						bucket.farthest = -1;
						ACTIVE_ITEMS.add(bucket);
					}
					bucket.members.addElements(bucket.members.size(), members.elements(), 0, members.size());
					if (far > bucket.farthest) {
						bucket.farthest = far;
						bucket.farthestEntity = farEntity;
					}
				}
			}
		}
	}

	/** Players {@code [from, to)}: roots, members, groups (into {@code part}), name tags. */
	private static void place(int from, int to, Part part) {
		Scratch scratch = SCRATCH.get();
		for (int e = from; e < to; e++) {
			CrowdRecipe recipe = entityRecipe[e];
			AvatarRenderState state = entityState[e];
			Matrix4f pose = scratch.entity.set(entityPose, e * 16);
			Matrix4f root = root(recipe.renderer, state, pose, scratch.pose);
			root.get(entityRoot, e * 16);
			float distance = root.m30() * root.m30() + root.m31() * root.m31() + root.m32() * root.m32();
			// Buckets that share batches (skins in the atlas) go in as one group: one submit for all of them.
			// Which group, the player keeps (looked up again now and then: a bucket's group may change).
			Crowd.Bucket[] groups = recipe.groups;
			if (groups == null || frame - recipe.groupsFrame > GROUPS_FRAMES) {
				groups = new Crowd.Bucket[recipe.models.size()];
				for (int j = 0; j < groups.length; j++) {
					groups[j] = recipe.models.get(j).bucket.groupKey();
				}
				recipe.groups = groups;
				recipe.groupsFrame = frame;
			}
			int m = entityFirstMember[e];
			int j = 0;
			for (CrowdRecipe.ModelEntry entry : recipe.models) {
				member(m, entry, distance);
				memberBucket[m] = entry.bucket;
				part.add(groups[j++], m++, distance, e);
			}
			for (CrowdRecipe.ItemEntry entry : recipe.items) {
				member(m, entry, distance);
				part.add(entry.bucket, m++, distance, e);
			}
			CrowdTags.fill(e, state, pose, scratch.tag);
			if (state.id == JITTER_ID) {
				jitter(state);
			}
			if (ANIMATION_CHECK) {
				check(state);
			}
		}
	}

	/** -Dpolarium.debugAnimation=true: count crowd players whose animation looks wrong (logged every 600 frames). */
	private static final boolean ANIMATION_CHECK = Boolean.getBoolean("polarium.debugAnimation");
	private static final java.util.concurrent.atomic.AtomicLong CHECKED = new java.util.concurrent.atomic.AtomicLong();
	private static final java.util.concurrent.atomic.AtomicLong FAST_LEGS = new java.util.concurrent.atomic.AtomicLong();
	private static final java.util.concurrent.atomic.AtomicLong STILL_LEGS = new java.util.concurrent.atomic.AtomicLong();
	private static final java.util.concurrent.atomic.AtomicLong TWISTED_HEAD = new java.util.concurrent.atomic.AtomicLong();
	private static long checkReport;

	private static void check(AvatarRenderState state) {
		CHECKED.incrementAndGet();
		if (state.walkAnimationSpeed > 1.2F) {
			FAST_LEGS.incrementAndGet();
		}
		if (state.walkAnimationSpeed < 0.02F) {
			STILL_LEGS.incrementAndGet();
		}
		if (Math.abs(state.yRot) > 75F) {
			TWISTED_HEAD.incrementAndGet();
		}
	}

	/** The animation counts so far (every ten seconds or so). */
	static void reportAnimation() {
		long now = System.nanoTime();
		if (!ANIMATION_CHECK || now - checkReport < 10_000_000_000L) {
			return;
		}
		checkReport = now;
		long checked = Math.max(1, CHECKED.getAndSet(0));
		org.slf4j.LoggerFactory.getLogger("Polarium").info(String.format(java.util.Locale.ROOT,
				"Polarium animation: %d looked at; legs too fast %.2f%%, legs still %.2f%%, head twisted %.2f%%", checked,
				100.0 * FAST_LEGS.getAndSet(0) / checked, 100.0 * STILL_LEGS.getAndSet(0) / checked, 100.0 * TWISTED_HEAD.getAndSet(0) / checked));
	}

	/** -Dpolarium.debugJitter=ID: how smoothly that entity turns and walks, frame to frame (logged every 600 frames). */
	private static final int JITTER_ID = Integer.getInteger("polarium.debugJitter", Integer.MIN_VALUE);
	private static final float[][] JITTER = new float[3][600];
	private static int jitterAt;

	private static synchronized void jitter(AvatarRenderState state) {
		JITTER[0][jitterAt] = state.bodyRot;
		JITTER[1][jitterAt] = state.bodyRot + state.yRot;
		JITTER[2][jitterAt] = state.walkAnimationPos;
		if (++jitterAt < 600) {
			return;
		}
		jitterAt = 0;
		StringBuilder line = new StringBuilder("Polarium jitter:");
		String[] names = {"body", "head", "walk"};
		for (int k = 0; k < 3; k++) {
			float[] v = JITTER[k];
			double second = 0;
			int reversals = 0;
			for (int i = 2; i < v.length; i++) {
				float d1 = v[i - 1] - v[i - 2];
				float d2 = v[i] - v[i - 1];
				second += Math.abs(d2 - d1);
				if (d1 * d2 < 0) {
					reversals++;
				}
			}
			line.append(String.format(java.util.Locale.ROOT, " %s: mean |accel| %.3f, reversals %d;", names[k], second / (v.length - 2), reversals));
		}
		org.slf4j.LoggerFactory.getLogger("Polarium").info(line.toString());
	}

	private static void ensureMembers(int count) {
		if (count > memberEntry.length) {
			int size = Math.max(memberEntry.length * 2, count);
			memberEntry = Arrays.copyOf(memberEntry, size);
			memberBucket = Arrays.copyOf(memberBucket, size);
			memberDistance = Arrays.copyOf(memberDistance, size);
			memberTarget = Arrays.copyOf(memberTarget, size);
		}
	}

	private static void member(int m, Object entry, float distance) {
		memberEntry[m] = entry;
		memberDistance[m] = distance;
		memberTarget[m] = -1;
	}

	/** A model member's own bucket. */
	static Crowd.Bucket memberBucket(int member) {
		return memberBucket[member];
	}

	/** Translucent groups: their players back to front, as the game sorts translucent submits. */
	static void sortFarToNear(IntArrayList members) {
		sortFarToNear(members.elements(), members.size(), memberDistance);
	}

	private static long[] sortKeys = new long[1024];

	/**
	 * {@code items[0..count)} (indices into {@code distance}) farthest first.
	 * Sorted as longs (distance bits, then the index): a primitive sort, much
	 * quicker than comparing through the distances thousands of times.
	 */
	static void sortFarToNear(int[] items, int count, float[] distance) {
		if (count >= RADIX_MIN) {
			radixFarToNear(items, count, distance);
			return;
		}
		long[] keys = sortKeys.length >= count ? sortKeys : (sortKeys = new long[Math.max(count, sortKeys.length * 2)]);
		for (int i = 0; i < count; i++) {
			// Non-negative floats order as their bits; inverted for farthest first.
			keys[i] = ((long) ~Float.floatToRawIntBits(distance[items[i]]) << 32) | (items[i] & 0xFFFFFFFFL);
		}
		Arrays.sort(keys, 0, count);
		for (int i = 0; i < count; i++) {
			items[i] = (int) keys[i];
		}
	}

	/** From this many items a radix sort is quicker (5,000: 42 µs against 130). */
	private static final int RADIX_MIN = 2048;
	private static int[] radixKeys = new int[0];
	private static int[] radixKeysOut = new int[0];
	private static int[] radixItems = new int[0];
	private static int[] radixItemsOut = new int[0];
	private static final int[] RADIX_COUNT = new int[256];

	/**
	 * The same order as the long sort (distance bits inverted, ties in the
	 * order given: stable), by bytes from the lowest: four passes over the
	 * items, no comparisons.
	 */
	private static void radixFarToNear(int[] items, int count, float[] distance) {
		if (radixKeys.length < count) {
			int size = Math.max(count, radixKeys.length * 2);
			radixKeys = new int[size];
			radixKeysOut = new int[size];
			radixItems = new int[size];
			radixItemsOut = new int[size];
		}
		int[] keys = radixKeys;
		int[] keysOut = radixKeysOut;
		int[] values = radixItems;
		int[] valuesOut = radixItemsOut;
		for (int i = 0; i < count; i++) {
			// Unsigned order of the inverted bits: farthest first, as the long sort's signed order of them.
			keys[i] = ~Float.floatToRawIntBits(distance[items[i]]) ^ 0x80000000;
			values[i] = items[i];
		}
		for (int shift = 0; shift < 32; shift += 8) {
			java.util.Arrays.fill(RADIX_COUNT, 0);
			for (int i = 0; i < count; i++) {
				RADIX_COUNT[(keys[i] >>> shift) & 0xFF]++;
			}
			int sum = 0;
			for (int b = 0; b < 256; b++) {
				int c = RADIX_COUNT[b];
				RADIX_COUNT[b] = sum;
				sum += c;
			}
			for (int i = 0; i < count; i++) {
				int at = RADIX_COUNT[(keys[i] >>> shift) & 0xFF]++;
				keysOut[at] = keys[i];
				valuesOut[at] = values[i];
			}
			int[] t = keys;
			keys = keysOut;
			keysOut = t;
			t = values;
			values = valuesOut;
			valuesOut = t;
		}
		System.arraycopy(values, 0, items, 0, count);
	}

	/**
	 * As {@link #sortFarToNear(int[], int, float[])}, starting from the order
	 * of a frame ago ({@code previous}, if it had as many): a crowd's
	 * distances hardly change from one frame to the next, so that's nearly
	 * sorted already and an insertion sort puts it right in about one pass.
	 * The same order as a full sort (the same keys); if too much changed, a
	 * full sort after all.
	 */
	static void sortFarToNear(int[] items, int count, float[] distance, int @org.jspecify.annotations.Nullable [] previous, int previousCount) {
		if (previous == null || previousCount != count || count < 64) {
			sortFarToNear(items, count, distance);
			return;
		}
		long[] keys = sortKeys.length >= count ? sortKeys : (sortKeys = new long[Math.max(count, sortKeys.length * 2)]);
		for (int i = 0; i < count; i++) {
			int item = previous[i];
			keys[i] = ((long) ~Float.floatToRawIntBits(distance[item]) << 32) | (item & 0xFFFFFFFFL);
		}
		long budget = 8L * count;
		for (int i = 1; i < count; i++) {
			long key = keys[i];
			int j = i - 1;
			while (j >= 0 && keys[j] > key) {
				keys[j + 1] = keys[j];
				j--;
				if (--budget < 0) {
					sortFarToNear(items, count, distance);
					return;
				}
			}
			keys[j + 1] = key;
		}
		for (int i = 0; i < count; i++) {
			items[i] = (int) keys[i];
		}
	}

	/** Room for {@code count} targets, filled in with {@link #targetAt} (on any thread); returns the first. */
	static int reserveTargets(int count) {
		int first = targetCount;
		targetCount += count;
		if (targetCount > targetData.length) {
			int size = Math.max(targetData.length * 2, targetCount);
			targetData = Arrays.copyOf(targetData, size);
			targetOffset = Arrays.copyOf(targetOffset, size);
			targetNext = Arrays.copyOf(targetNext, size);
			targetMesh = Arrays.copyOf(targetMesh, size);
			targetUv = Arrays.copyOf(targetUv, size);
		}
		return first;
	}

	/** A reserved target, as a model member's only one. */
	static void targetAt(int t, int member, InstanceData data, int offset, ModelMesh mesh, float[] uv) {
		targetData[t] = data;
		targetOffset[t] = offset;
		targetMesh[t] = mesh;
		targetUv[t] = uv;
		targetNext[t] = -1;
		memberTarget[member] = t;
	}

	/** A reserved target, as an item member's only one. */
	static void itemTargetAt(int t, int member, InstanceData data, int offset) {
		targetData[t] = data;
		targetOffset[t] = offset;
		targetMesh[t] = null;
		targetUv[t] = null;
		targetNext[t] = -1;
		memberTarget[member] = t;
	}

	/** The GPU path took a member's submit: its instance data goes at {@code offset} texels in {@code data}. */
	static void target(int member, InstanceData data, int offset, @Nullable ModelMesh mesh, float @Nullable [] uv) {
		int t = reserveTargets(1);
		targetData[t] = data;
		targetOffset[t] = offset;
		targetMesh[t] = mesh;
		targetUv[t] = uv;
		targetNext[t] = memberTarget[member];
		memberTarget[member] = t;
	}

	// ---- Instance data (before the GPU path uploads; on helper threads) ----

	/** Every member's instance data, once per frame (whichever GPU path uploads first calls it). */
	static void computeAll() {
		if (computedFrame == frame) {
			return;
		}
		computedFrame = frame;
		if (entityCount == 0 || targetCount == 0) {
			return;
		}
		inParts(entityCount, CrowdFrame::compute);
	}

	private interface Range {
		void run(int from, int to);
	}

	private static void inParts(int count, Range range) {
		if (count < PARALLEL_MIN) {
			range.run(0, count);
			return;
		}
		int parts = Workers.PARTS;
		List<Runnable> chunks = new ArrayList<>(parts);
		for (int p = 0; p < parts; p++) {
			int from = count * p / parts;
			int to = count * (p + 1) / parts;
			chunks.add(() -> range.run(from, to));
		}
		Workers.runAll(chunks);
	}

	/** -Dpolarium.checkPoses=true: each player's pose worked out directly ({@link HumanoidPoses}) checked against the game's. */
	private static final boolean CHECK_POSES = Boolean.getBoolean("polarium.checkPoses");
	private static final java.util.concurrent.atomic.AtomicLong POSES_CHECKED = new java.util.concurrent.atomic.AtomicLong();
	private static final java.util.concurrent.atomic.AtomicLong POSES_DIFFERENT = new java.util.concurrent.atomic.AtomicLong();
	private static volatile long posesReported = System.nanoTime();

	private static void checkPose(net.minecraft.client.model.player.PlayerModel model, AvatarRenderState state, Scratch scratch) {
		float[] mine = new float[HumanoidPoses.PARTS * HumanoidPoses.VALUES];
		float[] game = new float[mine.length];
		HumanoidPoses.pose(state, HumanoidPoses.rest(model), mine);
		HumanoidPoses.read(model, game);
		POSES_CHECKED.incrementAndGet();
		for (int i = 0; i < mine.length; i++) {
			if (Float.floatToIntBits(mine[i]) != Float.floatToIntBits(game[i])) {
				if (POSES_DIFFERENT.incrementAndGet() < 20) {
					org.slf4j.LoggerFactory.getLogger("Polarium").warn("Polarium pose check: part {} value {}: {} vs the game's {} (crouching {}, attack {} {}, arms {} {}, passenger {})",
							i / HumanoidPoses.VALUES, i % HumanoidPoses.VALUES, mine[i], game[i], state.isCrouching, state.attackTime,
							state.swingAnimationType, state.rightArmPose, state.leftArmPose, state.isPassenger);
				}
				break;
			}
		}
		long now = System.nanoTime();
		if (now - posesReported > 10_000_000_000L) {
			posesReported = now;
			org.slf4j.LoggerFactory.getLogger("Polarium").info("Polarium pose check: {} poses, {} different", POSES_CHECKED.getAndSet(0),
					POSES_DIFFERENT.getAndSet(0));
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void compute(int from, int to) {
		Scratch scratch = SCRATCH.get();
		for (int e = from; e < to; e++) {
			AvatarRenderState state = entityState[e];
			Model anchorModel = entityRecipe[e].renderer.getModel();
			Matrix4f root = scratch.root.set(entityRoot, e * 16);
			int light = entityLight[e];
			ModelCopies.Copy anchor = null;
			// The body drawn in the skeleton format without a posed copy: its hand worked out from its values.
			boolean anchorValues = false;
			boolean anchorSlim = false;
			int end = entityFirstMember[e] + entityMemberCount[e];
			for (int m = entityFirstMember[e]; m < end; m++) {
				int t = memberTarget[m];
				if (t < 0) {
					continue;
				}
				if (memberEntry[m] instanceof CrowdRecipe.ModelEntry entry && entry.bucket.owner != null) {
					// Posed exactly like the body: written by linkBorrowed, once the body's place is known.
					continue;
				} else if (memberEntry[m] instanceof CrowdRecipe.ModelEntry entry && targetMesh[t].skeleton) {
					// The six moving parts' values (the GPU works out the matrices): straight from the state if it's a usual one.
					ModelMesh mesh = targetMesh[t];
					float[] values = scratch.skeleton;
					ModelCopies.Copy copy = null;
					int drawn;
					if (HumanoidPoses.mode() == HumanoidPoses.DIRECT && HumanoidPoses.covers(state)) {
						HumanoidPoses.pose(state, mesh.rest, values);
						drawn = HumanoidPoses.drawn(mesh, state);
					} else {
						copy = scratch.copies.copy(entry.model, mesh.parts);
						((Model) copy.model).setupAnim(state);
						HumanoidPoses.read((net.minecraft.client.model.HumanoidModel<?>) copy.model, values);
						drawn = HumanoidPoses.drawn(mesh, copy.parts);
					}
					int entryLight = entry.lightFromState ? light : entry.light;
					for (; t >= 0; t = targetNext[t]) {
						PartPoses.writeSkeleton(entry.color, entry.overlay, entryLight, targetUv[t], root, drawn, values, targetData[t].array(),
								targetOffset[t] * 4);
					}
					if (entry.model == anchorModel) {
						anchor = copy;
						anchorValues = copy == null;
						if (anchorValues) {
							System.arraycopy(values, 0, scratch.anchorSkeleton, 0, values.length);
							anchorSlim = ((com.arcticlauncher.polarium.mixin.PlayerModelAccess) entry.model).polarium$slim();
						}
					}
				} else if (memberEntry[m] instanceof CrowdRecipe.ModelEntry entry) {
					ModelMesh mesh = targetMesh[t];
					ModelCopies.Copy copy = scratch.copies.copy(entry.model, mesh.parts);
					((Model) copy.model).setupAnim(state);
					if (CHECK_POSES && copy.model instanceof net.minecraft.client.model.player.PlayerModel player && HumanoidPoses.covers(state)) {
						checkPose(player, state, scratch);
					}
					if (scratch.parts.length < copy.parts.length * PartPoses.VALUES_PER_PART) {
						scratch.parts = new float[copy.parts.length * PartPoses.VALUES_PER_PART];
					}
					PartPoses.copyParts(copy.parts, scratch.parts, 0);
					int entryLight = entry.lightFromState ? light : entry.light;
					for (; t >= 0; t = targetNext[t]) {
						PartPoses.write(targetMesh[t], scratch.parts, 0, root, entry.color, entry.overlay, entryLight, targetUv[t], 0,
								targetData[t].array(), targetOffset[t] * 4, scratch.stack);
					}
					if (entry.model == anchorModel) {
						anchor = copy;
					}
				} else if (memberEntry[m] instanceof CrowdRecipe.ItemEntry entry && (anchor != null || anchorValues)) {
					// The hand as the game places it this frame (the copy is posed), then the item as recorded relative to it.
					Matrix4f pose;
					if (anchor != null) {
						scratch.hand.last().pose().set(root);
						((ArmedModel) anchor.model).translateToHand(state, entry.arm, scratch.hand);
						pose = scratch.item.set(scratch.hand.last().pose()).mul(entry.local);
					} else {
						HumanoidPoses.hand(root, scratch.anchorSkeleton, entry.arm, anchorSlim, scratch.item);
						pose = scratch.item.mul(entry.local);
					}
					int entryLight = entry.lightFromState ? light : entry.light;
					for (; t >= 0; t = targetNext[t]) {
						GpuItems.write(targetData[t].array(), targetOffset[t] * 4, entry.overlay, entryLight, pose, entry.tints);
					}
				}
			}
		}
	}

	/**
	 * Armor posed exactly like the body (see {@link Crowd#borrow}): its
	 * instance data points at the body's poses. Called by the entity models'
	 * GPU path once its batches have their places in the instance buffer (the
	 * items' path may have computed everything else before).
	 */
	static void linkBorrowed() {
		if (linkedFrame == frame) {
			return;
		}
		linkedFrame = frame;
		inParts(entityCount, CrowdFrame::linkBorrowed);
	}

	private static void linkBorrowed(int from, int to) {
		for (int e = from; e < to; e++) {
			Model<?> anchorModel = entityRecipe[e].renderer.getModel();
			int light = entityLight[e];
			int anchorParts = -1;
			boolean anchorSkeleton = false;
			int end = entityFirstMember[e] + entityMemberCount[e];
			for (int m = entityFirstMember[e]; m < end; m++) {
				int t = memberTarget[m];
				if (t < 0 || !(memberEntry[m] instanceof CrowdRecipe.ModelEntry entry)) {
					continue;
				}
				if (entry.model == anchorModel && entry.bucket.owner == null) {
					anchorParts = targetData[t].base + targetOffset[t] + PartPoses.HEADER_TEXELS;
					anchorSkeleton = targetMesh[t] != null && targetMesh[t].skeleton;
				} else if (entry.bucket.owner != null) {
					// Transparent if the body isn't drawn.
					int entryLight = entry.lightFromState ? light : entry.light;
					int color = anchorParts >= 0 ? entry.color : 0;
					for (; t >= 0; t = targetNext[t]) {
						PartPoses.writeBorrowed(color, entry.overlay, entryLight, targetUv[t], Math.max(anchorParts, 0), anchorSkeleton, targetData[t].array(),
								targetOffset[t] * 4);
					}
				}
			}
		}
	}
}
//#endif
