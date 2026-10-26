package com.arcticlauncher.polarium.legacy.mixin;

import net.minecraft.client.render.BaseFrustum;
import net.minecraft.client.render.CullingCameraView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What the game's culling camera tests boxes against, and where it stands. */
@Mixin(CullingCameraView.class)
public interface CullingCameraViewAccess {
	@Accessor("clipper")
	BaseFrustum polarium$clipper();

	@Accessor("x")
	double polarium$x();

	@Accessor("y")
	double polarium$y();

	@Accessor("z")
	double polarium$z();
}
