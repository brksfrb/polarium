package com.arcticlauncher.polarium;

import com.ibm.icu.lang.UCharacter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.SubStringSource;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Text laid out in visual order (FormattedBidiReorder) without the
 * bidirectional analysis when there is nothing for it to do: in a
 * left-to-right language, text with no character from U+0590 up (where the
 * right-to-left scripts, Arabic shaping and the direction marks are) is one
 * left-to-right run, so the result is the text's styled pieces in order,
 * exactly as the game's reorder makes them. Everything else goes the game's
 * way. -Dpolarium.checkTextOrder=true compares every result with the game's.
 */
public final class TextOrder {
	private TextOrder() {}

	/** Off with -Dpolarium.textOrder=false. */
	public static final boolean ENABLED = !"false".equals(System.getProperty("polarium.textOrder"));
	public static final boolean CHECK = Boolean.getBoolean("polarium.checkTextOrder");
	/** Below this, no character can be right-to-left (or need Arabic shaping). */
	private static final char FIRST_RTL = 0x0590;

	/** The visual order of {@code text}, or null to leave it to the game. */
	public static @Nullable FormattedCharSequence leftToRight(FormattedText text, boolean defaultRightToLeft) {
		if (!ENABLED || defaultRightToLeft) {
			return null;
		}
		SubStringSource source = SubStringSource.create(text, UCharacter::getMirror, s -> s);
		String plain = source.getPlainText();
		for (int i = 0; i < plain.length(); i++) {
			if (plain.charAt(i) >= FIRST_RTL) {
				return null;
			}
		}
		return FormattedCharSequence.composite(source.substring(0, plain.length(), false));
	}

	private static final java.util.concurrent.atomic.AtomicLong CHECKED = new java.util.concurrent.atomic.AtomicLong();
	private static final java.util.concurrent.atomic.AtomicLong DIFFERENT = new java.util.concurrent.atomic.AtomicLong();
	private static volatile long reported = System.nanoTime();

	/** Debug: {@code mine} against the game's {@code theirs}, as (character, style) sequences. */
	public static void check(FormattedText text, FormattedCharSequence mine, FormattedCharSequence theirs) {
		CHECKED.incrementAndGet();
		if (!flatten(mine).equals(flatten(theirs)) && DIFFERENT.incrementAndGet() < 10) {
			org.slf4j.LoggerFactory.getLogger("Polarium").warn("Polarium text order differs for \"{}\"", text.getString());
		}
		long now = System.nanoTime();
		if (now - reported > 10_000_000_000L) {
			reported = now;
			org.slf4j.LoggerFactory.getLogger("Polarium").info("Polarium text order check: {} texts, {} different", CHECKED.getAndSet(0),
					DIFFERENT.getAndSet(0));
		}
	}

	private static java.util.List<Object> flatten(FormattedCharSequence sequence) {
		java.util.List<Object> out = new java.util.ArrayList<>();
		sequence.accept((position, style, codepoint) -> {
			out.add(codepoint);
			out.add(style);
			return true;
		});
		return out;
	}
}
