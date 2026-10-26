package com.arcticlauncher.polarium.legacy.mixin;

import net.minecraft.client.world.BuiltChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The number the game gave a chunk section when it made it (its place in a fixed list). */
@Mixin(BuiltChunk.class)
public interface BuiltChunkIndexAccess {
	@Accessor("field_11079")
	int polarium$index();
}
