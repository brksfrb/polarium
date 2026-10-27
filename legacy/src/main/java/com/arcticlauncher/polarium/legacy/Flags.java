package com.arcticlauncher.polarium.legacy;

/** The switches, all on unless a JVM flag says otherwise: -Dpolarium.terrain=false and so on. */
public final class Flags {
	/** The terrain visibility walk without per-chunk garbage. */
	public static final boolean TERRAIN = on("terrain");

	/** Chunk sections drawn region by region (a few large calls instead of four per section). */
	public static final boolean REGIONS = on("regions");

	/** Fancy clouds kept in a display list instead of rebuilt every frame. */
	public static final boolean CLOUDS = on("clouds");

	/** Entities found from the entity list instead of by visiting every visible section. */
	public static final boolean ENTITIES = on("entities");

	/** The camera section's open faces kept until a block changes. */
	public static final boolean OPEN_FACES = on("openfaces");

	/** Check every terrain walk against the game's own (slow; for tests). */
	public static final boolean TERRAIN_VERIFY = "true".equalsIgnoreCase(System.getProperty("polarium.terrain.verify"));

	private Flags() {
	}

	private static boolean on(String name) {
		return !"false".equalsIgnoreCase(System.getProperty("polarium." + name));
	}

	public static String describe() {
		return "terrain=" + TERRAIN + " regions=" + REGIONS + " clouds=" + CLOUDS + " entities=" + ENTITIES + " openfaces=" + OPEN_FACES;
	}
}
