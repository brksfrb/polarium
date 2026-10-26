package com.arcticlauncher.polarium.legacy.mixin;

import net.minecraft.client.render.BuiltChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** How many chunk sections the game keeps around the camera, per axis. */
@Mixin(BuiltChunkStorage.class)
public interface StorageAccess {
	@Accessor("sizeX")
	int polarium$sizeX();

	@Accessor("sizeY")
	int polarium$sizeY();

	@Accessor("sizeZ")
	int polarium$sizeZ();

	@Invoker("getRenderedChunk")
	BuiltChunk polarium$chunkAt(BlockPos pos);
}
