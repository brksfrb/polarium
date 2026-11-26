package com.arcticlauncher.polarium.legacy;

import java.util.Locale;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Marks the key bindings whose keyboard key is held down as pressed (see HeldKeysMixin). */
public final class HeldKeys {
	/** Not done on macOS, where LWJGL 2's held-key state is not known to be reliable; the game's own behaviour stays. */
	private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

	private HeldKeys() {
	}

	public static void press(KeyBinding[] bindings) {
		if (MAC || bindings == null) {
			return;
		}
		for (KeyBinding binding : bindings) {
			int code = binding.getCode();
			// Mouse buttons are negative codes: a click that closed a screen must not start an attack.
			if (code > 0 && code < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(code)) {
				KeyBinding.setKeyPressed(code, true);
			}
		}
	}
}
