//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * One fence per slot of a ring of buffers written from the CPU while mapped
 * (the GPU may still be reading a slot from an earlier pass): a slot isn't
 * written again until the GPU is done with the pass that last drew from it.
 * Usually it long is; then this costs nothing. It isn't when passes come
 * faster than frames, as when the inventory draws the player in a picture
 * of its own besides the world.
 */
final class FrameFences {
	private static final long WAIT_NANOS = 1_000_000_000L;
	/** Off with -Dpolarium.fences=false (to compare). */
	private static final boolean ON = !"false".equals(System.getProperty("polarium.fences"));
	private final GpuFence[] fences;

	FrameFences(int slots) {
		fences = new GpuFence[slots];
	}

	/**
	 * Before writing a slot's buffers: wait for the GPU to finish the pass
	 * that last read them. False if that pass is in this very frame (not
	 * sent to the GPU yet, so it can't be waited for): then the caller gives
	 * the slot new buffers (the old ones are freed once drawn from).
	 */
	boolean await(int slot) {
		GpuFence fence = fences[slot];
		if (!ON) {
			return true;
		}
		if (fence == null) {
			return true;
		}
		try {
			while (!fence.awaitCompletion(WAIT_NANOS)) {
				// A very slow frame: keep waiting rather than write over what's being drawn.
			}
		} catch (IllegalStateException e) {
			fence.close();
			fences[slot] = null;
			return false;
		}
		fence.close();
		fences[slot] = null;
		return true;
	}

	/** After this pass's draws from a slot were sent to the GPU. */
	void mark(int slot) {
		if (!ON) {
			return;
		}
		if (fences[slot] != null) {
			fences[slot].close();
		}
		fences[slot] = RenderSystem.getDevice().createCommandEncoder().createFence();
	}
}
//#endif
