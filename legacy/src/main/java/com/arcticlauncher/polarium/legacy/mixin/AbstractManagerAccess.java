package com.arcticlauncher.polarium.legacy.mixin;

import java.util.List;
import net.minecraft.client.render.world.AbstractChunkRenderManager;
import net.minecraft.client.world.BuiltChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What the game's chunk render manager has collected for the layer it is about to draw, and the camera it was told. */
@Mixin(AbstractChunkRenderManager.class)
public interface AbstractManagerAccess {
	@Accessor("viewX")
	double polarium$viewX();

	@Accessor("viewY")
	double polarium$viewY();

	@Accessor("viewZ")
	double polarium$viewZ();

	@Accessor("helpers")
	List<BuiltChunk> polarium$helpers();

	@Accessor("field_10667")
	boolean polarium$ready();
}
