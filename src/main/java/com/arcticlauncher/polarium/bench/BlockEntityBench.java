package com.arcticlauncher.polarium.bench;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in paired vanilla/GPU screenshots: -Dpolarium.bench=blocks. Creates an offline test world. */
public final class BlockEntityBench implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("Polarium bench");
	private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "polarium-bench");
		t.setDaemon(true);
		return t;
	});

	@Override
	public void onInitializeClient() {
		if (!"blocks".equals(System.getProperty("polarium.bench"))) {
			return;
		}
		if (Boolean.getBoolean("polarium.bench.compatOnly")) {
            TIMER.schedule(() -> run(() -> {
                LOG.info("blocks bench: compatible={}", com.arcticlauncher.polarium.BlockEntityOptions.compatible());
                Minecraft.getInstance().stop();
            }), 12, TimeUnit.SECONDS);
            return;
        }
		LOG.info("blocks bench: paired vanilla/GPU images, shut, open and breaking");
		TIMER.scheduleAtFixedRate(() -> run(() -> {
			if (screen(Minecraft.getInstance()) instanceof PauseScreen) {
				closeScreen(Minecraft.getInstance());
			}
		}), 1, 1, TimeUnit.SECONDS);
		TIMER.schedule(() -> run(BlockEntityBench::createWorld), 12, TimeUnit.SECONDS);
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
		String name = "Polarium Blocks Bench " + System.currentTimeMillis();
		LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
				//#if MC >= 26.1
				new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
		//#else
				false, Difficulty.PEACEFUL, true, new net.minecraft.world.level.gamerules.GameRules(
						WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
		//#endif
		LOG.info("blocks bench: creating {}", name);
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
			LOG.info("blocks bench: in the world after {}s", attempt);
			TIMER.schedule(() -> run(BlockEntityBench::setUp), 5, TimeUnit.SECONDS);
		} else if (attempt < 180) {
			TIMER.schedule(() -> run(() -> waitForWorld(attempt + 1)), 1, TimeUnit.SECONDS);
		} else {
			LOG.error("blocks bench: FAILED, the world never loaded");
			Minecraft.getInstance().stop();
		}
	}

	private static void setUp() {
		Minecraft mc = Minecraft.getInstance();
		var server = mc.getSingleplayerServer();
		server.execute(() -> {
			var source = server.createCommandSourceStack();
			for (String command : List.of("gamerule advance_time false", "gamerule advance_weather false", "time set noon",
					"weather clear", "fill -20 -60 -4 20 -60 28 stone", "tp @a 0.5 -59 0.5 0 12")) {
				server.getCommands().performPrefixedCommand(source, command);
			}
			String[] kinds = {"chest", "trapped_chest", "ender_chest", "copper_chest", "exposed_copper_chest",
					"weathered_copper_chest", "oxidized_copper_chest", "waxed_copper_chest"};
			String[] facings = {"north", "south", "east", "west"};
			for (int k = 0; k < kinds.length; k++) {
				for (int f = 0; f < 4; f++) {
					server.getCommands().performPrefixedCommand(source, "setblock " + (k * 2 - 8) + " -59 " + (6 + f * 3)
							+ " " + kinds[k] + "[facing=" + facings[f] + "]");
				}
			}
			for (String command : List.of(
					"setblock -3 -59 4 chest[facing=north,type=left]", "setblock -2 -59 4 chest[facing=north,type=right]",
					"setblock 2 -59 4 trapped_chest[facing=south,type=right]", "setblock 3 -59 4 trapped_chest[facing=south,type=left]",
					"setblock -6 -59 3 shulker_box", "setblock 5 -59 3 white_bed", "setblock -5 -59 2 oak_sign",
					"setblock 6 -59 2 white_banner", "setblock -7 -59 2 bell", "setblock 7 -59 2 decorated_pot",
					"fill -6 -59 19 6 -55 19 chest[facing=north]", "fill -10 -56 8 -8 -56 12 stone",
					"setblock -9 -59 9 chest[facing=north]", "setblock -10 -59 9 torch")) {
				server.getCommands().performPrefixedCommand(source, command);
			}
		});
		// Paired images in a fixed world: shut, moving lids and cracks, vanilla then GPU.
		later(8, () -> pair(0));
	}

	private static void later(int seconds, Runnable action) {
		TIMER.schedule(() -> run(action), seconds, TimeUnit.SECONDS);
	}

	private static void pair(int phase) {
		Minecraft mc = Minecraft.getInstance();
		if (phase == 1) {
			for (int x : new int[] {-3, -2, -8}) {
				var be = mc.level.getBlockEntity(new BlockPos(x, -59, x == -8 ? 6 : 4));
				if (be != null) be.triggerEvent(1, 1);
			}
		}
		if (phase == 2) mc.level.destroyBlockProgress(4711, new BlockPos(-3, -59, 4), 6);
		com.arcticlauncher.polarium.BlockEntityOptions.get().chestInstancing = false;
		later(3, () -> screenshot(mc));
		later(4, () -> com.arcticlauncher.polarium.BlockEntityOptions.get().chestInstancing = true);
		later(7, () -> screenshot(mc));
		if (phase < 2) later(9, () -> pair(phase + 1));
		else later(10, mc::stop);
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
