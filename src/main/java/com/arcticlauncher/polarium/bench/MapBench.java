package com.arcticlauncher.polarium.bench;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development benchmark, only with {@code -Dpolarium.bench=maps}: a flat creative world with a
 * wall of item frames, each holding a different filled map (like the billboards servers build
 * out of maps), seen from a distance with all of them in view. Measures the frame rate for a
 * while, saves a screenshot and the numbers ({@code polarium-bench.txt}), then quits.
 *
 * Tunables: {@code -Dpolarium.bench.count} (maps, default 350), {@code -Dpolarium.bench.seconds}
 * (measured, default 20), {@code -Dpolarium.bench.columns} (wall width, default 35).
 */
public final class MapBench implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("Polarium bench");
	private static final int COUNT = Integer.getInteger("polarium.bench.count", 350);
	private static final int COLUMNS = Math.max(1, Integer.getInteger("polarium.bench.columns", 35));
	private static final int SECONDS = Integer.getInteger("polarium.bench.seconds", 20);
	private static final int WARMUP_SECONDS = 12;
	/** Ground level of the default flat world. */
	private static final int GROUND = -60;
	/** The wall's distance from the player (blocks). */
	private static final int DISTANCE = Integer.getInteger("polarium.bench.distance", 30);
	private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "polarium-bench");
		t.setDaemon(true);
		return t;
	});

	@Override
	public void onInitializeClient() {
		if (!"maps".equals(System.getProperty("polarium.bench"))) {
			return;
		}
		LOG.info("maps bench: {} maps in a wall {} wide, {}s measured", COUNT, COLUMNS, SECONDS);
		TIMER.scheduleAtFixedRate(() -> run(() -> {
			if (screen(Minecraft.getInstance()) instanceof PauseScreen) {
				closeScreen(Minecraft.getInstance());
			}
		}), 1, 1, TimeUnit.SECONDS);
		TIMER.schedule(() -> run(MapBench::createWorld), 12, TimeUnit.SECONDS);
	}

	private static void run(Runnable task) {
		Minecraft.getInstance().execute(task);
	}

	private static void createWorld() {
		Minecraft mc = Minecraft.getInstance();
		mc.options.framerateLimit().set(260);
		mc.options.enableVsync().set(false);
		mc.options.pauseOnLostFocus = false;
		mc.options.renderDistance().set(8);
		String name = "Polarium Maps Bench " + System.currentTimeMillis();
		LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
				new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
		LOG.info("maps bench: creating {}", name);
		mc.createWorldOpenFlows().createFreshLevel(name, settings, WorldOptions.defaultWithRandomSeed(),
				registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
				screen(mc));
		waitForWorld(0);
	}

	private static void waitForWorld(int attempt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && mc.player != null && screen(mc) instanceof PauseScreen) {
			closeScreen(mc);
		}
		if (mc.level != null && mc.player != null && screen(mc) == null) {
			LOG.info("maps bench: in the world after {}s", attempt);
			TIMER.schedule(() -> run(MapBench::setUp), 5, TimeUnit.SECONDS);
		} else if (attempt < 180) {
			TIMER.schedule(() -> run(() -> waitForWorld(attempt + 1)), 1, TimeUnit.SECONDS);
		} else {
			LOG.error("maps bench: FAILED, the world never loaded");
			Minecraft.getInstance().stop();
		}
	}

	private static void setUp() {
		Minecraft mc = Minecraft.getInstance();
		net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
		String player = mc.player.getGameProfile().name();
		int rows = (COUNT + COLUMNS - 1) / COLUMNS;
		int left = -COLUMNS / 2;
		server.execute(() -> {
			for (String command : List.of("gamerule advance_time false", "gamerule advance_weather false", "time set noon",
					"fill " + left + " " + GROUND + " " + DISTANCE + " " + (left + COLUMNS - 1) + " " + (GROUND + rows + 1) + " " + DISTANCE + " minecraft:stone",
					"tp " + player + " 0 " + (GROUND + 1 + rows / 2) + " 0 0 0")) {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
			}
			ServerLevel level = server.overworld();
			for (int i = 0; i < COUNT; i++) {
				ItemStack map = MapItem.create(level, 0, 0, (byte) 0, false, false);
				MapItemSavedData data = MapItem.getSavedData(map, level);
				if (data != null) {
					paint(data, i);
				}
				BlockPos pos = new BlockPos(left + i % COLUMNS, GROUND + 1 + i / COLUMNS, DISTANCE - 1);
				ItemFrame frame = new ItemFrame(level, pos, Direction.NORTH);
				frame.setItem(map, false);
				level.addFreshEntity(frame);
			}
			LOG.info("maps bench: {} frames with maps made", COUNT);
		});
		TIMER.schedule(() -> run(() -> measure(new ArrayList<>(), SECONDS)), WARMUP_SECONDS, TimeUnit.SECONDS);
	}

	/** Every map its own picture: stripes and checks that differ from map to map. */
	private static void paint(MapItemSavedData data, int index) {
		for (int y = 0; y < 128; y++) {
			for (int x = 0; x < 128; x++) {
				int shade = ((x / (4 + index % 7)) + (y / (3 + index % 5)) + index) % 58;
				data.colors[x + y * 128] = (byte) (4 + shade);
			}
		}
	}

	private static void measure(List<Integer> samples, int left) {
		Minecraft mc = Minecraft.getInstance();
		mc.player.setXRot(0);
		mc.player.setYRot(0);
		if (left > 0) {
			samples.add(mc.getFps());
			TIMER.schedule(() -> run(() -> measure(samples, left - 1)), 1, TimeUnit.SECONDS);
			return;
		}
		finish(samples);
	}

	private static void finish(List<Integer> samples) {
		Minecraft mc = Minecraft.getInstance();
		double average = samples.stream().mapToInt(Integer::intValue).average().orElse(0);
		int min = samples.stream().mapToInt(Integer::intValue).min().orElse(0);
		int max = samples.stream().mapToInt(Integer::intValue).max().orElse(0);
		String polarium = FabricLoader.getInstance().getModContainer("polarium")
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
		String label = System.getProperty("polarium.bench.label", "");
		String line = String.format(java.util.Locale.ROOT,
				"%s polarium=%s label=%s maps=%d entities=%d fps_avg=%.1f fps_min=%d fps_max=%d samples=%s%n",
				java.time.LocalDateTime.now().withNano(0), polarium, label, COUNT, mc.level.getEntityCount(), average, min, max, samples);
		LOG.info("maps bench: RESULT {}", line.trim());
		try {
			Files.writeString(new File(mc.gameDirectory, "polarium-bench.txt").toPath(), line, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			LOG.error("maps bench: couldn't save the result", e);
		}
		screenshot(mc);
		TIMER.schedule(() -> run(mc::stop), 3, TimeUnit.SECONDS);
	}

	// ---- What differs between Minecraft versions ----

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

	private static void screenshot(Minecraft mc) {
		//#if MC >= 26.2
		Screenshot.grab(mc, false);
		//#else
		Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), message -> {});
		//#endif
	}
}
