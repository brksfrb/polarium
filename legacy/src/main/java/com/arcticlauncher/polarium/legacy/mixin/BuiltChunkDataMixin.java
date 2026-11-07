package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.SectionData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.world.BuiltChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The per-section data of {@link SectionData}. */
@Mixin(BuiltChunk.class)
abstract class BuiltChunkDataMixin implements SectionData {
	@Unique
	private final int[] polarium$vertices = new int[RenderLayer.values().length];

	@Override
	public int[] polarium$vertices() {
		return polarium$vertices;
	}
}
