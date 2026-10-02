package com.arcticlauncher.polonium.mixin;

import com.arcticlauncher.polonium.KeptStates;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Room on an entity for its kept render state (see {@link KeptStates}). */
@Mixin(Entity.class)
abstract class EntityKeptStateMixin implements KeptStates.Holder {
	@Unique
	private EntityRenderState polonium$keptState;
	@Unique
	private EntityRenderer<?, ?> polonium$keptStateRenderer;
	@Unique
	private int polonium$fullTick = -1;
	@Unique
	private long polonium$fullAttributes;
	@Unique
	private boolean polonium$touched;

	@Override
	public int polonium$fullTick() {
		return polonium$fullTick;
	}

	@Override
	public long polonium$fullAttributes() {
		return polonium$fullAttributes;
	}

	@Override
	public void polonium$madeInFull(int tick, long attributes) {
		polonium$fullTick = tick;
		polonium$fullAttributes = attributes;
		polonium$touched = false;
	}

	@Override
	public boolean polonium$touched() {
		return polonium$touched;
	}

	@Override
	public void polonium$touched(boolean touched) {
		polonium$touched = touched;
	}

	@Override
	public EntityRenderState polonium$keptState() {
		return polonium$keptState;
	}

	@Override
	public EntityRenderer<?, ?> polonium$keptStateRenderer() {
		return polonium$keptStateRenderer;
	}

	@Override
	public void polonium$keptState(EntityRenderState state, EntityRenderer<?, ?> renderer) {
		polonium$keptState = state;
		polonium$keptStateRenderer = renderer;
	}
}
