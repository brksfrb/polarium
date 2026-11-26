package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Flags;
import com.arcticlauncher.polarium.legacy.HeldKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opening a screen lets go of every key, and on 1.8.9 a key still held when the screen closes stays let
 * go until it is pressed again (closing the inventory while holding W stops the player). Back in the
 * game, the keys held down right now count as pressed, as Minecraft does from 1.12 on.
 */
@Mixin(MinecraftClient.class)
abstract class HeldKeysMixin {
	@Inject(method = "closeScreen", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
			target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
	private void polarium$pressHeldKeys(CallbackInfo ci) {
		if (Flags.HELD_KEYS && ((MinecraftClient) (Object) this).currentScreen == null) {
			HeldKeys.press(((MinecraftClient) (Object) this).options.allKeys);
		}
	}
}
