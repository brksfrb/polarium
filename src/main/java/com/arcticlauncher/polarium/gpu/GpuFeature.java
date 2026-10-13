//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

/**
 * A feature renderer's GPU side: told which of the game's groups is being
 * prepared, draws its part of a group when the game draws that group, and
 * starts over each frame (see RenderTypeFeatureRendererMixin).
 */
public interface GpuFeature {
	void beginGroup(boolean strictlyOrdered);

	void endGroup();

	void executeGroup(int groupIndex);

	/**
	 * Lay out and upload the frame's GPU data, once (later calls do nothing). On 26.3 the game
	 * draws inside a render pass it opens, where buffers can't be written: this runs when the
	 * frame's groups are prepared, before any pass. Earlier versions do it at the first group.
	 */
	void prepareDraws();

	//#if MC >= 26.3
	/** 26.3: the game opens the group's render pass (and names its transparency stage, if any). */
	void executeGroup(int groupIndex, com.mojang.renderpearl.api.commands.RenderPass pass,
			net.minecraft.client.renderer.oit.@org.jspecify.annotations.Nullable OitStage stage);
	//#endif

	void endFrame();
}
//#endif
