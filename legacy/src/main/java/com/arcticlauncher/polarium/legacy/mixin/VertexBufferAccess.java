package com.arcticlauncher.polarium.legacy.mixin;

import net.minecraft.client.render.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How many vertices a vertex buffer holds. */
@Mixin(VertexBuffer.class)
public interface VertexBufferAccess {
	@Accessor("size")
	int polarium$size();
}
