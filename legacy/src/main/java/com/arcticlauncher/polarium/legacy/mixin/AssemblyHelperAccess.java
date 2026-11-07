package com.arcticlauncher.polarium.legacy.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.world.ChunkAssemblyHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Marks a layer of a section as having something to draw. */
@Mixin(ChunkAssemblyHelper.class)
public interface AssemblyHelperAccess {
	@Invoker("setUsed")
	void polarium$setUsed(RenderLayer layer);
}
