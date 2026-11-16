package com.arcticlauncher.polarium;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Other mods that handle block entities, by mod id. No game classes here: the mixin plugin reads this
 * before the game is loaded.
 *
 * <p>A mod that draws block entities itself (Better Block Entities bakes chests into the terrain, shader
 * and portal mods draw the world again) owns them: Polarium's whole block entity path steps aside, its
 * mixins included, so it costs nothing and can't get in that mod's way. A mod that only leaves some out
 * (EntityCulling: frustum and occlusion, never a change to how one is drawn) is fine with the GPU chests;
 * Polarium's own frustum culling then steps aside, as that mod already tests each one. BadOptimizations
 * only caches the dispatcher's renderer lookup (same renderers): fine too.
 */
public final class BlockEntityMods {
	private static final String[] REPLACING = {"betterblockentities", "enhancedblockentities", "fastchest", "iris", "oculus",
			"optifabric", "immersive_portals", "imm_ptl_core", "entity_model_features", "entity_texture_features", "canvas", "vulkanmod"};
	/** Mods whose mixins on the block entity dispatcher only skip some block entities. */
	private static final String[] CULLING = {"entityculling"};
	/** Mods whose mixins there change nothing about what is drawn (BadOptimizations caches getRenderer). */
	private static final String[] HARMLESS = {"badoptimizations"};
	private static volatile String replacing;
	private static volatile Boolean culledElsewhere;

	private BlockEntityMods() {}

	/** The first loaded mod that draws block entities itself, or null. */
	public static String replacing() {
		String found = replacing;
		if (found == null) {
			found = "";
			for (String id : REPLACING) {
				if (FabricLoader.getInstance().isModLoaded(id)) {
					found = id;
					break;
				}
			}
			replacing = found;
		}
		return found.isEmpty() ? null : found;
	}

	/** Do this mod's mixins on the block entity renderers leave how they draw alone (culling, caching)? */
	public static boolean drawsAsBefore(String modId) {
		for (String id : CULLING) {
			if (id.equals(modId)) {
				return true;
			}
		}
		for (String id : HARMLESS) {
			if (id.equals(modId)) {
				return true;
			}
		}
		return false;
	}

	/** A loaded mod already culls each block entity by the view: Polarium's own culling would test it again. */
	public static boolean culledElsewhere() {
		Boolean found = culledElsewhere;
		if (found == null) {
			found = false;
			for (String id : CULLING) {
				found |= FabricLoader.getInstance().isModLoaded(id);
			}
			culledElsewhere = found;
		}
		return found;
	}
}
