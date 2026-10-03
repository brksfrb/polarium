//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

/** A feature renderer's own GPU side (added by a mixin). */
public interface GpuBatchesHolder {
	GpuFeature polarium$batches();
}
//#endif
