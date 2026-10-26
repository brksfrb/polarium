package com.arcticlauncher.polarium.legacy;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;

/**
 * Same scene every run (-Dpolarium.bench=terrain): a fixed seed world, the player hovering at a fixed
 * spot and turning a fixed step each frame, so the terrain walk runs every frame like it does when you
 * play. Warm-up, then a measured stretch; the result goes to polarium-bench.txt in the game folder and
 * the game closes.
 */
public final class LegacyBench {
	private static final String MODE = System.getProperty("polarium.bench", "");
	private static final long SEED = 20261026L;
	private static final int WARMUP_FRAMES = Integer.getInteger("polarium.bench.warmup", 900);
	private static final int MEASURED_FRAMES = Integer.getInteger("polarium.bench.frames", 3000);
	private static final float YAW_STEP = 0.6f;
	private static final int RENDER_DISTANCE = Integer.getInteger("polarium.bench.distance", 11);

	private static int phase;
	private static int frame;
	private static long last;
	private static double x;
	private static double y;
	private static double z;
	private static long[] times;
	private static int collected;

	private LegacyBench() {
	}

	public static boolean enabled() {
		return !MODE.isEmpty();
	}

	/** Called at the top of every pass of the game loop. */
	public static void frame(MinecraftClient client) {
		long now = System.nanoTime();
		if (phase == 0) {
			if (client.world == null && client.currentScreen instanceof TitleScreen && ++frame > 120) {
				LevelInfo info = new LevelInfo(SEED, LevelInfo.GameMode.CREATIVE, false, false, LevelGeneratorType.DEFAULT);
				client.startIntegratedServer("polarium-bench", "polarium-bench", info.enableCommands());
				phase = 1;
				frame = 0;
			}
			return;
		}
		if (client.world == null || client.player == null) {
			return;
		}
		client.options.viewDistance = RENDER_DISTANCE;
		client.options.vsync = false;
		client.options.maxFramerate = 260;
		client.options.hudHidden = true;
		client.player.abilities.allowFlying = true;
		client.player.abilities.flying = true;
		if (phase == 1) {
			x = client.player.x;
			y = client.player.y + 12;
			z = client.player.z;
			phase = 2;
			frame = 0;
		}
		float yaw = frame * YAW_STEP;
		client.player.updatePositionAndAngles(x, y, z, yaw, 8f);
		client.player.velocityX = 0;
		client.player.velocityY = 0;
		client.player.velocityZ = 0;
		frame++;
		if (phase == 2) {
			if (frame >= WARMUP_FRAMES) {
				phase = 3;
				times = new long[MEASURED_FRAMES];
				System.out.println("[Polarium] bench measuring");
			}
		} else if (phase == 3) {
			times[collected++] = now - last;
			if (collected == MEASURED_FRAMES) {
				finish(client);
			}
		}
		last = now;
	}

	private static void finish(MinecraftClient client) {
		phase = 4;
		long[] sorted = times.clone();
		Arrays.sort(sorted);
		long total = 0;
		for (long t : times) {
			total += t;
		}
		double avgMs = total / 1e6 / times.length;
		double p99Ms = sorted[(int) (sorted.length * 0.99)] / 1e6;
		double worstMs = sorted[sorted.length - 1] / 1e6;
		String line = String.format("flags=%s frames=%d avg=%.2fms (%.1f FPS) p99=%.2fms (%.1f FPS) worst=%.2fms",
				Flags.describe(), times.length, avgMs, 1000 / avgMs, p99Ms, 1000 / p99Ms, worstMs);
		System.out.println("[Polarium] bench " + line);
		try (PrintWriter out = new PrintWriter(new File(client.runDirectory, "polarium-bench.txt"), "UTF-8")) {
			out.println(line);
		} catch (IOException e) {
			e.printStackTrace();
		}
		client.scheduleStop();
	}
}
