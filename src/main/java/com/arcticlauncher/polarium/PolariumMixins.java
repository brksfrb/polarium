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
		String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
		if (name.equals("BlockEntityBenchFrameMixin") || name.equals("ChestChristmasBenchMixin")) {
			// Only for the block entity scene: nothing per frame otherwise.
			return !OFF && "blocks".equals(System.getProperty("polarium.bench"));
		}
		if (BLOCK_ENTITY_MIXINS.contains(name)) {
			return !OFF && blockEntities(name);
		}
		return !OFF;
	}

	/** Every mixin of the block entity path (culling and GPU chests). */
	private static final Set<String> BLOCK_ENTITY_MIXINS = Set.of("BlockEntityCullMixin", "BlockEntityCullLevelMixin",
			"BlockEntityFrustumMixin", "GpuChestDrawMixin", "GpuChestRecoveryMixin", "GpuChestPassMixin", "GpuChestDirtyMixin",
			"GpuChestSlotMixin", "GpuChestLidMixin", "GpuChestLightMixin", "ChestRenderTypeAccess", "ChestRenderSetupAccess",
			"OlderChestSubmitMixin");
	private static boolean loggedAside;

	private static boolean blockEntities(String mixin) {
		boolean culling = !"false".equals(System.getProperty("polarium.blockEntityCulling"));
		boolean chests = !"false".equals(System.getProperty("polarium.gpuChests"));
		String owner = BlockEntityMods.replacing();
		if (owner != null) {
			// That mod draws block entities its own way: not a single hook of ours in its path.
			if (!loggedAside) {
				loggedAside = true;
				org.slf4j.LoggerFactory.getLogger("Polarium").info(
						"Polarium: {} handles block entities; Polarium's block entity culling and GPU chests are left out", owner);
			}
			return false;
		}
		if (mixin.equals("BlockEntityFrustumMixin")) {
			// The view is only needed for Polarium's own culling.
			return culling && !BlockEntityMods.culledElsewhere();
		}
		if (mixin.equals("BlockEntityCullMixin") || mixin.equals("BlockEntityCullLevelMixin")) {
			return culling || chests;
		}
		return chests;
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
