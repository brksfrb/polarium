package com.arcticlauncher.polarium;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;

/** The game's staging buffer pool (a private class), as Polarium sees it. */
public interface StagingPool {
	GpuBuffer polarium$acquire(GpuDevice device, int minSize);
}
