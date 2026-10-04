package com.arcticlauncher.polarium;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Other players' render states kept from frame to frame, and the parts of
 * them that only change with the game's ticks left as they are between
 * ticks. The game makes a new state for every entity every frame and fills
 * it in from scratch, though its held items' models and its armor and items
 * (copied every frame) only change when the player ticks or is sent new
 * ones: with thousands of players, a big share of making their states.
 *
 * Only while the level's entities are made ({@link #inLevel}), only for
 * other players (the local player is also drawn in menus, with its own
 * state), and everything else in the state is filled in every frame as the
 * game does. Off with -Dpolarium.keptStates=false.
 */
public final class KeptStates {
	public static final boolean ENABLED = !"false".equals(System.getProperty("polarium.keptStates"));
	/** Slots on a state: held items' models (right, left), held items' copies (left, right), armor (head, chest, legs, feet). */
	public static final int RIGHT_MODEL = 0;
	public static final int LEFT_MODEL = 1;
	public static final int LEFT_COPY = 2;
	public static final int RIGHT_COPY = 3;
	public static final int ARMOR = 4;
	/** The swing animation of the item in the attacking hand (a data component looked up every frame). */
	public static final int SWING = 8;
	public static final int SLOTS = 9;
	/**
	 * Copies of the items are made again this often, in ticks, even when the
	 * player holds the same item object (in case it was changed in place:
	 * the server sends new items when they change, so that's rare).
	 */
	public static final int COPY_TICKS = 20;
	/** Per thread: the level's entity states are made on several threads, while the render thread may make others (menus). */
	private static final ThreadLocal<boolean[]> IN_LEVEL = ThreadLocal.withInitial(() -> new boolean[1]);

	/** Room on an entity for its kept state (added to Entity by a mixin). */
	public interface Holder {
		EntityRenderState polarium$keptState();

		EntityRenderer<?, ?> polarium$keptStateRenderer();

		void polarium$keptState(EntityRenderState state, EntityRenderer<?, ?> renderer);

		/** The tick its kept state was last made in full on (see LightStates), or -1. */
		int polarium$fullTick();

		/** Its attributes' version then (see AttributeValues#version). */
		long polarium$fullAttributes();

		void polarium$madeInFull(int tick, long attributes);

		/** What its name tag is made from on its current tick (see NameTagTicks), or null. */
		Object polarium$tagTick();

		void polarium$tagTick(Object kept);

		/** The tick its kept state was last found good for (made in full, or carried over: see LightStates), or -1. */
		int polarium$keptTick();

		void polarium$keptTick(int tick);

		/** What its state was made from when it was last made in full (see TickInputs). */
		long polarium$inputs();

		void polarium$inputs(long inputs);

		/** The server sent something about it since its state was last made in full (see LightStates). */
		boolean polarium$touched();

		void polarium$touched(boolean touched);
	}

	/** Room on a state for what each slot was made from, and on which tick (added to ArmedEntityRenderState by a mixin). */
	public interface Slots {
		Object[] polarium$sources();

		int[] polarium$ticks();

		/** Counts the times its held items' models were made again (not left as they were). */
		int polarium$itemsVersion();

		void polarium$itemsChanged();

		/** The kept swing animation (see {@link #SWING}), or null. */
		Object polarium$swing();

		void polarium$swing(Object swing);
	}

	/** {@link #same} for a slot that holds a copy (made again every {@link #COPY_TICKS} ticks). */
	public static boolean sameCopy(Object state, int slot, Object source, int tick) {
		return same(state, slot, source, tick / COPY_TICKS);
	}

	private KeptStates() {}

	/** This thread is making the level's entities' states (they may be kept). */
	public static void inLevel(boolean making) {
		IN_LEVEL.get()[0] = making;
	}

	public static boolean inLevel() {
		return ENABLED && IN_LEVEL.get()[0];
	}

	/**
	 * Whether a slot of this state already holds what it would be made from
	 * {@code source} now (the same object, on the same tick); if not, it's
	 * noted as made from it now (the caller makes it).
	 */
	public static boolean same(Object state, int slot, Object source, int tick) {
		if (!inLevel() || !(state instanceof Slots slots)) {
			return false;
		}
		Object[] sources = slots.polarium$sources();
		int[] ticks = slots.polarium$ticks();
		if (sources[slot] == source && ticks[slot] == tick) {
			return true;
		}
		sources[slot] = source;
		ticks[slot] = tick;
		return false;
	}
}
