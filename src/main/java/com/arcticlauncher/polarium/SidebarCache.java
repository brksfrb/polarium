//#if MC >= 26.2
package com.arcticlauncher.polarium;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

/**
 * The scoreboard sidebar's lines, kept between frames. The game builds each
 * line's text anew every frame (a team's prefix, the name, its suffix; the
 * score), so nothing about it is ever reused: its laid-out text and its width
 * are worked out again every frame. Here a line made from the same inputs
 * (the same team prefix, suffix and color, an equal name; the same score and
 * number format) is the same text as last frame, so the game's own caches
 * (the text's visual order) apply and its width is measured once. Render
 * thread only.
 */
public final class SidebarCache {
	private SidebarCache() {}

	/** Off with -Dpolarium.sidebarCache=false. */
	public static final boolean ENABLED = !"false".equals(System.getProperty("polarium.sidebarCache"));
	/** Lines kept at most (a sidebar with a clock makes new ones all the time). */
	private static final int MAX = 512;

	private record NameKey(@Nullable Team team, @Nullable Component prefix, @Nullable Component suffix, Object color, Component name) {
		@Override
		public boolean equals(Object o) {
			return o instanceof NameKey k && k.team == team && k.prefix == prefix && k.suffix == suffix && Objects.equals(k.color, color)
					&& k.name.equals(name);
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(team) * 31 + name.hashCode();
		}
	}

	private record ValueKey(int value, @Nullable NumberFormat override, NumberFormat fallback) {}

	private static boolean announced;
	private static final Map<NameKey, MutableComponent> NAMES = new HashMap<>();
	private static final Map<ValueKey, MutableComponent> VALUES = new HashMap<>();
	/** Not measured yet (no text is this wide). */
	private static final int UNMEASURED = Integer.MIN_VALUE;
	/** Widths of the texts kept here (by the text itself: they're made once). */
	private static final Map<Component, Integer> WIDTHS = new IdentityHashMap<>();

	/** {@code PlayerTeam.formatNameForTeam(team, name)}, the same text while its inputs are. */
	public static MutableComponent name(@Nullable Team team, Component name, Supplier<MutableComponent> make) {
		if (!announced) {
			announced = true;
			org.slf4j.LoggerFactory.getLogger("Polarium").info("Polarium: scoreboard sidebar lines kept between frames");
		}
		NameKey key;
		if (!ENABLED) {
			return make.get();
		} else if (team == null) {
			key = new NameKey(null, null, null, null, name);
		} else if (team instanceof PlayerTeam playerTeam) {
			key = new NameKey(team, playerTeam.getPlayerPrefix(), playerTeam.getPlayerSuffix(), playerTeam.getColor(), name);
		} else {
			return make.get();
		}
		MutableComponent kept = NAMES.get(key);
		if (kept == null) {
			trim();
			kept = make.get();
			NAMES.put(key, kept);
			WIDTHS.put(kept, UNMEASURED);
		}
		return kept;
	}

	/** {@code entry.formatValue(fallback)}, the same text while the score and format are. */
	public static MutableComponent value(int value, @Nullable NumberFormat override, NumberFormat fallback, Supplier<MutableComponent> make) {
		if (!ENABLED) {
			return make.get();
		}
		ValueKey key = new ValueKey(value, override, fallback);
		MutableComponent kept = VALUES.get(key);
		if (kept == null) {
			trim();
			kept = make.get();
			VALUES.put(key, kept);
			WIDTHS.put(kept, UNMEASURED);
		}
		return kept;
	}

	/** The width of {@code text}, measured once if it's one kept here. */
	public static int width(Object text, java.util.function.IntSupplier measure) {
		Integer kept = ENABLED ? WIDTHS.get(text) : null;
		if (kept == null) {
			return measure.getAsInt();
		}
		if (kept == UNMEASURED) {
			kept = measure.getAsInt();
			WIDTHS.put((Component) text, kept);
		}
		return kept;
	}

	private static void trim() {
		if (NAMES.size() + VALUES.size() >= MAX) {
			clear();
		}
	}

	/** Fonts changed (widths are stale), or a fresh start. */
	public static void clear() {
		NAMES.clear();
		VALUES.clear();
		WIDTHS.clear();
	}
}
//#endif
