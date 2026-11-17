// i18n-ignore-file: no player-facing text.
package com.arcticlauncher.polarium.gpu;

//#if MC >= 26.2
import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlBuffer;
//#if MC >= 26.3
import com.mojang.blaze3d.opengl.GlHeuristics;
//#endif

/**
 * The game's own GL buffer over storage Polarium allocated with GL_ARB_buffer_storage, mapped
 * persistently by the game's code (true below). A subclass reaches the protected constructor without
 * reflection, so nothing here depends on the JVM opening Minecraft's packages.
 */
final class ChestStorageBuffer extends GlBuffer.Direct {
	//#if MC >= 26.3
	ChestStorageBuffer(GlHeuristics heuristics, DirectStateAccess dsa, int usage, long size, int handle) {
		super(heuristics, dsa, usage, size, handle, true);
	}
	//#else
	ChestStorageBuffer(DirectStateAccess dsa, int usage, long size, int handle) {
		super(dsa, usage, size, handle, true);
	}
	//#endif
}
//#endif
