package com.arcticlauncher.polarium.mixin;

//#if MC >= 26.2
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The backend (GlDevice on OpenGL) behind the game's GPU device. */
//#if MC >= 26.3
@Mixin(com.mojang.renderpearl.frontend.FrontendGpuDevice.class)
//#else
@Mixin(com.mojang.blaze3d.systems.GpuDevice.class)
//#endif
public interface GpuDeviceAccess {
	@Accessor("backend")
	GpuDeviceBackend polarium$backend();
}
//#endif
