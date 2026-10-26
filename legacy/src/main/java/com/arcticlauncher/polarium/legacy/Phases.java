package com.arcticlauncher.polarium.legacy;

/**
 * Where a frame's time goes, for the benchmark only (-Dpolarium.bench): each hooked game method adds its
 * time to a slot. Costs one branch per call when the benchmark is off.
 */
public final class Phases {
	public static final boolean ON = LegacyBench.enabled();

	public static final int TICK = 0;
	public static final int WORLD = 1;
	public static final int SKY = 2;
	public static final int CLOUDS = 3;
	public static final int TERRAIN_SETUP = 4;
	public static final int TERRAIN_DRAW = 5;
	public static final int ENTITIES = 6;
	public static final int PARTICLES = 7;
	public static final int WEATHER = 8;
	public static final int HAND = 9;
	public static final int DISPLAY = 10;
	public static final int OPEN_FACES = 11;
	public static final int WALK = 12;
	private static final String[] NAMES = {"tick", "renderWorld", "sky", "clouds", "terrainSetup", "terrainDraw", "entities",
			"particles", "weather", "hand", "display", "openFaces", "walk"};

	private static final long[] START = new long[NAMES.length];
	private static final long[] TOTAL = new long[NAMES.length];
	private static boolean counting;

	private Phases() {
	}

	public static void begin(int slot) {
		START[slot] = System.nanoTime();
	}

	public static void end(int slot) {
		if (counting) {
			TOTAL[slot] += System.nanoTime() - START[slot];
		}
	}

	public static void startCounting() {
		counting = true;
	}

	/** Average milliseconds per frame in every slot. */
	public static String report(int frames) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < NAMES.length; i++) {
			out.append(String.format("%s=%.2f ", NAMES[i], TOTAL[i] / 1e6 / frames));
		}
		return out.toString().trim();
	}
}
