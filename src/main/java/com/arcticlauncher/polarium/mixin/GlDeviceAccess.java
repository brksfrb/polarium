package com.arcticlauncher.polarium.mixin;

//#if MC >= 26.2
import com.mojang.blaze3d.opengl.DirectStateAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//#if MC >= 26.3
import com.mojang.blaze3d.opengl.GlHeuristics;
//#endif

/**
 * The OpenGL device's buffer entry points, for chest buffers made with GL_ARB_buffer_storage when
 * Mojang's own persistent mapping is off (Sodium turns it off on NVIDIA). GlDevice and the
 * DirectStateAccess implementations are package-private, so reflection into them fails on Java 25.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public interface GlDeviceAccess {
	@Accessor("directStateAccess")
	DirectStateAccess polarium$directStateAccess();

	//#if MC >= 26.3
	@Accessor("heuristics")
	GlHeuristics polarium$heuristics();
	//#endif
}
//#endif
