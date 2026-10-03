package com.arcticlauncher.polarium;

import net.minecraft.client.renderer.entity.EntityRenderer;

/** Room on each entity for its renderer and the tick it was found in (added to Entity by a mixin). */
public interface RendererHolder {
	EntityRenderer<?, ?> polarium$renderer();

	int polarium$rendererTick();

	void polarium$renderer(EntityRenderer<?, ?> renderer, int tick);
}
