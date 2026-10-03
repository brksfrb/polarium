package com.arcticlauncher.polarium;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * With {@code -Dpolarium.off=true}, none of Polarium's changes are applied:
 * the game runs exactly as without the mod (its benchmark still works, for
 * a baseline to compare against).
 */
public final class PolariumMixins implements IMixinConfigPlugin {
	private static final boolean OFF = Boolean.getBoolean("polarium.off");

	@Override
	public void onLoad(String mixinPackage) {}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.endsWith(".BenchTagsMixin") || mixinClassName.endsWith(".BenchFpsMixin")) {
			// Only for the bench's showcase shots (name tags with the HUD hidden), with or without the rest.
			return Boolean.getBoolean("polarium.bench.showcase");
		}
		if (mixinClassName.endsWith(".BenchSkinMixin")) {
			// Only for the crowd bench's players (they have no player list entry to get a skin from).
			return "players".equals(System.getProperty("polarium.bench.kind"));
		}
		return !OFF;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
