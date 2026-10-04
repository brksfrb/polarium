package com.arcticlauncher.polarium.bench;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development benchmark for everyday frame rates, only with
 * {@code -Dpolarium.bench=terrain}: a normal world from a fixed seed, seen
 * from above the hills at noon with the HUD on, the view turning a full
 * circle while the frame rate is measured; then it quits. No crowds: what
 * any player sees. Tunables: {@code -Dpolarium.bench.seconds} (measured,
 * default 20), {@code -Dpolarium.bench.rd} (render distance, default 12),
 * {@code -Dpolarium.bench.warmup} (seconds for the chunks to load, default 40).
 */
public final class TerrainBench implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("Polarium bench");
	private static final int SECONDS = Integer.getInteger("polarium.bench.seconds", 20);
	private static final int RENDER_DISTANCE = Integer.getInteger("polarium.bench.rd", 12);
	private static final int WARMUP = Integer.getInteger("polarium.bench.warmup", 40);
	private static final long SEED = 20261004L;
	private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "polarium-terrain-bench");
		t.setDaemon(true);
		return t;
	});

	@Override
	public void onInitializeClient() {
		if (Boolean.getBoolean("polarium.logFps")) {
			// Any run (Polarium on or off): the frame rate once a second, for comparing in the log.
			TIMER.scheduleAtFixedRate(() -> run(() -> LOG.info("fps {}", Minecraft.getInstance().getFps())), 1, 1, TimeUnit.SECONDS);
		}
		if (!"terrain".equals(System.getProperty("polarium.bench"))) {
			return;
		}
		LOG.info("terrain bench: render distance {}, {}s measured", RENDER_DISTANCE, SECONDS);
		TIMER.scheduleAtFixedRate(() -> run(() -> {
			if (screen(Minecraft.getInstance()) instanceof PauseScreen) {
				closeScreen(Minecraft.getInstance());
			}
		}), 1, 1, TimeUnit.SECONDS);
		TIMER.schedule(() -> run(TerrainBench::createWorld), 12, TimeUnit.SECONDS);
	}

	private static void run(Runnable task) {
		Minecraft.getInstance().execute(task);
	}

	private static void createWorld() {
		Minecraft mc = Minecraft.getInstance();
		mc.options.framerateLimit().set(260);
		mc.options.enableVsync().set(false);
		mc.options.pauseOnLostFocus = false;
		mc.options.renderDistance().set(RENDER_DISTANCE);
		String name = "Polarium Bench Terrain " + System.currentTimeMillis();
		LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
				new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(SEED, true, false),
				registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(),
				screen(mc));
		waitForWorld(0);
	}

	private static void waitForWorld(int attempt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && mc.player != null && screen(mc) instanceof PauseScreen) {
			closeScreen(mc);
		}
		if (mc.level != null && mc.player != null && screen(mc) == null) {
			LOG.info("terrain bench: in the world after {}s", attempt);
			TIMER.schedule(() -> run(TerrainBench::setUp), 3, TimeUnit.SECONDS);
		} else if (attempt < 180) {
			TIMER.schedule(() -> run(() -> waitForWorld(attempt + 1)), 1, TimeUnit.SECONDS);
		} else {
			LOG.error("terrain bench: FAILED, the world never loaded");
			mc.stop();
		}
	}

	private static void setUp() {
		Minecraft mc = Minecraft.getInstance();
		net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
		String player = mc.player.getGameProfile().name();
		server.execute(() -> {
			for (String command : List.of("gamerule advance_time false", "gamerule advance_weather false", "time set noon", "weather clear",
					"tp " + player + " 0 120 0 0 15")) {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
			}
		});
		mc.player.getAbilities().flying = true;
		mc.player.onUpdateAbilities();
		LOG.info("terrain bench: warming up {}s", WARMUP);
		TIMER.schedule(() -> run(() -> measure(new ArrayList<>(), SECONDS)), WARMUP, TimeUnit.SECONDS);
	}

	private static void measure(List<Integer> samples, int left) {
		Minecraft mc = Minecraft.getInstance();
		// A full turn over the measured time, looking a little down at the hills.
		mc.player.setXRot(15);
		mc.player.setYRot(360f * (SECONDS - left) / SECONDS);
		mc.player.getAbilities().flying = true;
		if (left < SECONDS) {
			samples.add(mc.getFps());
		}
		if (left > 0) {
			TIMER.schedule(() -> run(() -> measure(samples, left - 1)), 1, TimeUnit.SECONDS);
			return;
		}
		double avg = samples.stream().mapToInt(Integer::intValue).average().orElse(0);
		int min = samples.stream().mapToInt(Integer::intValue).min().orElse(0);
		LOG.info(String.format(java.util.Locale.ROOT, "terrain bench: RESULT polarium=%s rd=%d fps_avg=%.1f fps_min=%d samples=%s",
				!Boolean.getBoolean("polarium.off"), RENDER_DISTANCE, avg, min, samples));
		TIMER.schedule(() -> run(mc::stop), 2, TimeUnit.SECONDS);
	}

	private static net.minecraft.client.gui.screens.@org.jspecify.annotations.Nullable Screen screen(Minecraft mc) {
		//#if MC >= 26.2
		return mc.gui.screen();
		//#else
		return mc.screen;
		//#endif
	}

	private static void closeScreen(Minecraft mc) {
		//#if MC >= 26.2
		mc.gui.setScreen(null);
		//#else
		mc.setScreen(null);
		//#endif
	}
}
