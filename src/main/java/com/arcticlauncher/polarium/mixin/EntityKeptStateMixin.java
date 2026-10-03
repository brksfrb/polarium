package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.KeptStates;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Room on an entity for its kept render state (see {@link KeptStates}). */
@Mixin(Entity.class)
abstract class EntityKeptStateMixin implements KeptStates.Holder {
	@Unique
	private EntityRenderState polarium$keptState;
	@Unique
	private EntityRenderer<?, ?> polarium$keptStateRenderer;
	@Unique
	private int polarium$fullTick = -1;
	@Unique
	private long polarium$fullAttributes;
	@Unique
	private boolean polarium$touched;
	@Unique
	private int polarium$keptTick = -1;
	@Unique
	private long polarium$inputs = Long.MIN_VALUE;

	@Override
	public int polarium$keptTick() {
		return polarium$keptTick;
	}

	@Override
	public void polarium$keptTick(int tick) {
		polarium$keptTick = tick;
	}

	@Override
	public long polarium$inputs() {
		return polarium$inputs;
	}

	@Override
	public void polarium$inputs(long inputs) {
		polarium$inputs = inputs;
	}

	@Override
	public int polarium$fullTick() {
		return polarium$fullTick;
	}

	@Override
	public long polarium$fullAttributes() {
		return polarium$fullAttributes;
	}

	@Override
	public void polarium$madeInFull(int tick, long attributes) {
		polarium$fullTick = tick;
		polarium$keptTick = tick;
		polarium$fullAttributes = attributes;
		polarium$touched = false;
	}

	@Override
	public boolean polarium$touched() {
		return polarium$touched;
	}

	@Override
	public void polarium$touched(boolean touched) {
		polarium$touched = touched;
	}

	@Override
	public EntityRenderState polarium$keptState() {
		return polarium$keptState;
	}

	@Override
	public EntityRenderer<?, ?> polarium$keptStateRenderer() {
		return polarium$keptStateRenderer;
	}

	@Override
	public void polarium$keptState(EntityRenderState state, EntityRenderer<?, ?> renderer) {
		polarium$keptState = state;
		polarium$keptStateRenderer = renderer;
	}
}
