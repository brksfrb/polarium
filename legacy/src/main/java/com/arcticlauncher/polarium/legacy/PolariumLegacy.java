package com.arcticlauncher.polarium.legacy;

import net.fabricmc.api.ClientModInitializer;

/** Polarium for old Minecraft: the features hook in through mixins; this only reports what is on. */
public final class PolariumLegacy implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		System.out.println("[Polarium] legacy " + Flags.describe());
	}
}
