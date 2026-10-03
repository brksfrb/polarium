package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.RendererHolder;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Room on each entity for its kept renderer (see EntityRendererCacheMixin). */
@Mixin(Entity.class)
abstract class EntityRendererHolderMixin implements RendererHolder {
	@Unique
	private EntityRenderer<?, ?> polarium$renderer;

	@Unique
	private int polarium$rendererTick = -1;

	@Override
	public EntityRenderer<?, ?> polarium$renderer() {
		return polarium$renderer;
	}

	@Override
	public int polarium$rendererTick() {
		return polarium$rendererTick;
	}

	@Override
	public void polarium$renderer(EntityRenderer<?, ?> renderer, int tick) {
		polarium$renderer = renderer;
		polarium$rendererTick = tick;
	}
}
