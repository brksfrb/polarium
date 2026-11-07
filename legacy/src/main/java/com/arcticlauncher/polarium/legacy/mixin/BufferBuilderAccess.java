package com.arcticlauncher.polarium.legacy.mixin;

import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Drops vertices off the end of a buffer being built. */
@Mixin(BufferBuilder.class)
public interface BufferBuilderAccess {
	@Accessor("vertexCount")
	void polarium$setVertexCount(int count);
}
