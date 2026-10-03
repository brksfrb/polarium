package com.arcticlauncher.polonium;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entity render states made on several threads. Making one mostly reads the
 * entity (position, pose, equipment) into a fresh state object, so entities
 * can be done at once, as long as their renderer keeps nothing of its own
 * while doing it. Only Minecraft's own living-entity renderers are trusted;
 * any other entity is done on the render thread, as the game does. Mods that
 * change entity textures or models per entity (ETF, EMF) keep their own
 * state while doing this, so with them installed Polonium steps aside.
 *
 * If a parallel frame ever fails, it's redone the game's way and parallel
 * extraction stays off. Turn it off with -Dpolonium.parallelExtract=false.
 */
public final class ParallelExtract {
	private static final Logger LOG = LoggerFactory.getLogger("Polonium");
	/** Below this many visible entities, threads aren't worth waking. */
	private static final int PARALLEL_MIN = 48;
	private static final Map<Class<?>, Boolean> TRUSTED = new ConcurrentHashMap<>();
	/**
	 * Entity Model Features and Figura keep what they work out for an entity
	 * in shared places while making its state. Entity Texture Features alone
	 * only notes which entity a state is of (checked: 7.2), safe on any thread.
	 */
	private static volatile boolean enabled = !"false".equals(System.getProperty("polonium.parallelExtract"))
			&& !loaded("entity_model_features", "figura");
	private static boolean announced;
	/** How many entities the last frame drew (for the benchmark). */
	public static volatile int lastDrawn;

	/** The game's own visibility test for one entity. */
	public interface Visibility {
		boolean visible(Entity entity);
	}

	/**
	 * Which of {@code entities} are visible, and which have a trusted renderer
	 * ({@code trusted}, filled in here). Trusted ones are tested on several
	 * threads (the test reads the entity, the camera and the frustum); the rest
	 * on this thread, in order.
	 */
	public static boolean[] visible(List<Entity> entities, EntityRenderDispatcher dispatcher, Visibility test, boolean[] trusted) {
		int count = entities.size();
		boolean[] visible = new boolean[count];
		if (Timeline.ON) {
			VISIBLE_CALLS[count < PARALLEL_MIN ? 0 : 1]++;
		}
		if (count < PARALLEL_MIN) {
			for (int i = 0; i < count; i++) {
				trusted[i] = trusted(dispatcher.getRenderer(entities.get(i)));
				visible[i] = test.visible(entities.get(i));
			}
			return visible;
		}
		int parts = Workers.PARTS;
		List<Runnable> jobs = new ArrayList<>(parts);
		for (int p = 0; p < parts; p++) {
			int from = count * p / parts;
			int to = count * (p + 1) / parts;
			jobs.add(() -> {
				for (int i = from; i < to; i++) {
					Entity entity = entities.get(i);
					if (trusted(dispatcher.getRenderer(entity))) {
						trusted[i] = true;
						visible[i] = test.visible(entity);
					}
				}
			});
		}
		try {
			Workers.runAll(jobs);
		} catch (RuntimeException | Error e) {
			enabled = false;
			LOG.error("Polonium: testing entity visibility on several threads failed; back to one thread from now on", e);
			java.util.Arrays.fill(trusted, false);
		}
		for (int i = 0; i < count; i++) {
			if (!trusted[i]) {
				visible[i] = test.visible(entities.get(i));
				if (Timeline.ON) {
					VISIBLE_CALLS[2]++;
				}
			}
		}
		return visible;
	}

	/** With -Dpolonium.debugTimeline: visibility tests of short lists, of long lists, and of untrusted entities one by one. */
	public static final long[] VISIBLE_CALLS = new long[3];
	/** With the timeline: helper time in the level's extraction jobs, and in their visibility tests and state making. */
	public static final java.util.concurrent.atomic.LongAdder JOB_NANOS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder VISIBLE_NANOS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder MADE_NANOS = new java.util.concurrent.atomic.LongAdder();

	/** The game's own per-entity extraction (with whatever other mods add to it). */
	public interface Extractor {
		EntityRenderState extract(Entity entity, float partialTicks);
	}

	private ParallelExtract() {}

	public static boolean enabled() {
		return enabled;
	}

	/** States being made on the helpers, added to the frame by {@link #finish}. */
	private record Started(Workers.Started work, EntityRenderState[] states, List<Entity> entities, float[] partials, Extractor extractor,
			boolean[] parallel, List<EntityRenderState> output) {}

	private static Started started;

	/** Which entities the frame shows, besides being visible: the game's camera rules (see LevelExtractorMixin). */
	public interface Shown {
		boolean shown(Entity entity);
	}

	/** An entity's partial tick this frame (frozen entities have their own). */
	public interface Partial {
		float of(Entity entity);
	}

	/** {@link #extractLevel} at work: everything needed to finish it. */
	private record Level(Workers.Started work, List<Entity> entities, boolean[] untrusted, EntityRenderState[] states,
			EntityRenderDispatcher dispatcher, Visibility test, Shown shown, Partial partial, Extractor extractor, List<EntityRenderState> output) {}

	private static Level level;

	/**
	 * The level's entities' render states, added to {@code output} in the
	 * game's order. On the helpers, in one go per entity: whether its
	 * renderer is trusted, whether it's visible and shown, and its state;
	 * meanwhile this thread goes on with the rest of the frame's extraction
	 * (blocks, particles, sky, the HUD). {@link #finish} waits for them, then
	 * does the untrusted ones here, in order.
	 */
	public static void extractLevel(List<Entity> entities, EntityRenderDispatcher dispatcher, Visibility test, Shown shown, Partial partial,
			Extractor extractor, List<EntityRenderState> output) {
		finish();
		int count = entities.size();
		boolean[] untrusted = new boolean[count];
		EntityRenderState[] states = new EntityRenderState[count];
		if (count < PARALLEL_MIN) {
			java.util.Arrays.fill(untrusted, true);
			level = new Level(null, entities, untrusted, states, dispatcher, test, shown, partial, extractor, output);
			finish();
			return;
		}
		if (!announced) {
			announced = true;
			LOG.info("Polonium: entity render states made on several threads");
		}
		int parts = Workers.PARTS;
		List<Runnable> jobs = new ArrayList<>(parts);
		for (int p = 0; p < parts; p++) {
			int from = count * p / parts;
			int to = count * (p + 1) / parts;
			jobs.add(() -> {
				KeptStates.inLevel(true);
				try {
					boolean timed = Timeline.ON;
					long t0 = timed ? System.nanoTime() : 0;
					long tVisible = 0;
					long tMade = 0;
					for (int i = from; i < to; i++) {
						Entity entity = entities.get(i);
						if (!trusted(dispatcher.getRenderer(entity))) {
							untrusted[i] = true;
							continue;
						}
						long a = timed ? System.nanoTime() : 0;
						boolean visible = test.visible(entity) && shown.shown(entity);
						long b = timed ? System.nanoTime() : 0;
						tVisible += b - a;
						if (visible) {
							states[i] = made(entity, partial, extractor);
							if (timed) {
								tMade += System.nanoTime() - b;
							}
							//#if MC >= 26.2
							com.arcticlauncher.polonium.gpu.Crowd.precheck(states[i], dispatcher);
							//#endif
						}
					}
					if (timed) {
						JOB_NANOS.add(System.nanoTime() - t0);
						VISIBLE_NANOS.add(tVisible);
						MADE_NANOS.add(tMade);
					}
				} finally {
					KeptStates.inLevel(false);
				}
			});
		}
		level = new Level(Workers.start(jobs), entities, untrusted, states, dispatcher, test, shown, partial, extractor, output);
	}

	/** As the game does for an entity it shows: one in its first tick has no previous position yet. */
	private static EntityRenderState made(Entity entity, Partial partial, Extractor extractor) {
		if (entity.tickCount == 0) {
			entity.xOld = entity.getX();
			entity.yOld = entity.getY();
			entity.zOld = entity.getZ();
		}
		return extractor.extract(entity, partial.of(entity));
	}

	private static void finishLevel() {
		Level work = level;
		if (work == null) {
			return;
		}
		level = null;
		boolean[] untrusted = work.untrusted;
		if (work.work != null) {
			try {
				Workers.join(work.work);
			} catch (RuntimeException | Error e) {
				enabled = false;
				LOG.error("Polonium: making entity render states on several threads failed; back to one thread from now on", e);
				java.util.Arrays.fill(work.states, null);
				java.util.Arrays.fill(untrusted, true);
			}
		}
		KeptStates.inLevel(true);
		try {
			for (int i = 0; i < untrusted.length; i++) {
				if (untrusted[i]) {
					Entity entity = work.entities.get(i);
					if (Timeline.ON) {
						VISIBLE_CALLS[2]++;
					}
					if (work.test.visible(entity) && work.shown.shown(entity)) {
						work.states[i] = made(entity, work.partial, work.extractor);
					}
				}
			}
		} finally {
			KeptStates.inLevel(false);
		}
		int drawn = 0;
		for (EntityRenderState state : work.states) {
			if (state != null) {
				work.output.add(state);
				drawn++;
			}
		}
		lastDrawn = drawn;
	}

	/**
	 * Every visible entity's render state, added to {@code output} in order
	 * ({@code trusted}: per entity, from {@link #visible}). With many
	 * entities, those with trusted renderers are made on the helpers while
	 * this thread goes on with the rest of the frame's extraction (blocks,
	 * particles, sky, the HUD), and added by {@link #finish}: nothing reads
	 * the level's entity states before the frame is drawn.
	 */
	public static void extract(List<Entity> entities, float[] partials, boolean[] trusted, Extractor extractor, List<EntityRenderState> output) {
		finish();
		int count = entities.size();
		lastDrawn = count;
		EntityRenderState[] states = new EntityRenderState[count];
		if (count < PARALLEL_MIN) {
			serialInLevel(entities, partials, extractor, states, null);
			addAll(states, output);
			return;
		}
		if (!announced) {
			announced = true;
			LOG.info("Polonium: entity render states made on several threads");
		}
		// Untrusted renderers first, on this thread, in order.
		boolean[] parallel = new boolean[count];
		List<Integer> queue = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			if (trusted[i]) {
				parallel[i] = true;
				queue.add(i);
			}
		}
		boolean[] untrusted = new boolean[count];
		for (int i = 0; i < count; i++) {
			untrusted[i] = !parallel[i];
		}
		serialInLevel(entities, partials, extractor, states, untrusted);
		int parts = Workers.PARTS;
		List<Runnable> jobs = new ArrayList<>(parts);
		for (int p = 0; p < parts; p++) {
			int from = queue.size() * p / parts;
			int to = queue.size() * (p + 1) / parts;
			jobs.add(() -> {
				KeptStates.inLevel(true);
				try {
					for (int k = from; k < to; k++) {
						int i = queue.get(k);
						states[i] = extractor.extract(entities.get(i), partials[i]);
						//#if MC >= 26.2
						com.arcticlauncher.polonium.gpu.Crowd.precheck(states[i]);
						//#endif
					}
				} finally {
					KeptStates.inLevel(false);
				}
			});
		}
		started = new Started(Workers.start(jobs), states, entities, partials, extractor, parallel, output);
	}

	/** The entity states {@link #extract} or {@link #extractLevel} left on the helpers, waited for and added to the frame (if any are left). */
	public static void finish() {
		finishLevel();
		Started work = started;
		if (work == null) {
			return;
		}
		started = null;
		try {
			Workers.join(work.work);
		} catch (RuntimeException | Error e) {
			enabled = false;
			LOG.error("Polonium: making entity render states on several threads failed; back to one thread from now on", e);
			serialInLevel(work.entities, work.partials, work.extractor, work.states, work.parallel);
		}
		addAll(work.states, work.output);
	}

	private static void addAll(EntityRenderState[] states, List<EntityRenderState> output) {
		for (EntityRenderState state : states) {
			output.add(state);
		}
	}

	private static void serialInLevel(List<Entity> entities, float[] partials, Extractor extractor, EntityRenderState[] states, boolean[] only) {
		KeptStates.inLevel(true);
		try {
			serial(entities, partials, extractor, states, only);
		} finally {
			KeptStates.inLevel(false);
		}
	}

	/** On this thread: every entity, or only those marked in {@code only}. */
	private static void serial(List<Entity> entities, float[] partials, Extractor extractor, EntityRenderState[] states,
			boolean[] only) {
		for (int i = 0; i < entities.size(); i++) {
			if (only == null || only[i]) {
				states[i] = extractor.extract(entities.get(i), partials[i]);
			}
		}
	}

	/** The last renderer class asked about, and the answer: a crowd is mostly one kind of entity (several threads ask at once). */
	private static volatile Last last = new Last(Object.class, false);

	private record Last(Class<?> type, boolean trusted) {}

	/** Minecraft's own living-entity renderers only. */
	private static boolean trusted(EntityRenderer<?, ?> renderer) {
		Class<?> type = renderer.getClass();
		Last known = last;
		if (known.type == type) {
			return known.trusted;
		}
		boolean trusted = TRUSTED.computeIfAbsent(type,
				t -> LivingEntityRenderer.class.isAssignableFrom(t) && t.getName().startsWith("net.minecraft."));
		last = new Last(type, trusted);
		return trusted;
	}

	private static boolean loaded(String... ids) {
		for (String id : ids) {
			if (FabricLoader.getInstance().isModLoaded(id)) {
				return true;
			}
		}
		return false;
	}
}
