package com.arcticlauncher.polarium.legacy;

/** The switches, all on unless a JVM flag says otherwise: -Dpolarium.terrain=false and so on. */
public final class Flags {
	/** The terrain visibility walk without per-chunk garbage. */
	public static final boolean TERRAIN = on("terrain");

	private Flags() {
	}

	private static boolean on(String name) {
		return !"false".equalsIgnoreCase(System.getProperty("polarium." + name));
	}

	public static String describe() {
		return "terrain=" + TERRAIN;
	}
}
