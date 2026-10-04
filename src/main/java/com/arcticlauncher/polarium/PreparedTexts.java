//#if MC >= 26.2
package com.arcticlauncher.polarium;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

/**
 * GUI text laid out into glyphs once and drawn from that while it stays on
 * screen. The game prepares every text again each frame (each character's
 * glyph looked up, placed and styled); a text drawn again as the same text
 * object (chat lines, a boss bar's name, the scoreboard's lines, anything
 * whose visual order the game keeps) at the same place in the same color is
 * the same prepared text. Text with obfuscated characters isn't kept: those
 * change every frame. Kept while drawn in this frame or the last; cleared
 * when fonts change. Render thread only.
 */
public final class PreparedTexts {
	private PreparedTexts() {}

	/** Off with -Dpolarium.preparedTexts=false. */
	public static final boolean ENABLED = !"false".equals(System.getProperty("polarium.preparedTexts"));

	private record Key(FormattedCharSequence text, Font font, float x, float y, int color, boolean shadow, boolean includeEmpty, int background) {
		@Override
		public boolean equals(Object o) {
			return o instanceof Key k && k.text == text && k.font == font && Float.floatToIntBits(k.x) == Float.floatToIntBits(x)
					&& Float.floatToIntBits(k.y) == Float.floatToIntBits(y) && k.color == color && k.shadow == shadow
					&& k.includeEmpty == includeEmpty && k.background == background;
		}

		@Override
		public int hashCode() {
			return ((System.identityHashCode(text) * 31 + Float.floatToIntBits(x)) * 31 + Float.floatToIntBits(y)) * 31 + color;
		}
	}

	private static Map<Key, Font.PreparedText> current = new HashMap<>();
	private static Map<Key, Font.PreparedText> previous = new HashMap<>();

	/** {@code font.prepareText(text, ...)}, kept between frames (see the class). */
	public static Font.PreparedText prepared(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow,
			boolean includeEmpty, int background, Supplier<Font.PreparedText> prepare) {
		if (!ENABLED) {
			return prepare.get();
		}
		Key key = new Key(text, font, x, y, color, shadow, includeEmpty, background);
		Font.PreparedText kept = current.get(key);
		if (kept != null) {
			return kept;
		}
		kept = previous.remove(key);
		if (kept == null) {
			kept = prepare.get();
			if (!text.accept((position, style, codepoint) -> !style.isObfuscated())) {
				return kept;
			}
		}
		current.put(key, kept);
		return kept;
	}

	/** A frame was drawn: what wasn't drawn in it or the one before goes. */
	public static void newFrame() {
		Map<Key, Font.PreparedText> old = previous;
		old.clear();
		previous = current;
		current = old;
	}

	/** Fonts changed: glyphs are stale. */
	public static void clear() {
		current.clear();
		previous.clear();
	}
}
//#endif
