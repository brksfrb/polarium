package com.arcticlauncher.polarium.legacy.mixin;

import net.minecraft.client.render.world.ChunkRenderHelperImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The first of a display-list section's layer lists (one per layer, in layer order). */
@Mixin(ChunkRenderHelperImpl.class)
public interface ListedSectionAccess {
	@Accessor("field_11068")
	int polarium$firstList();
}
