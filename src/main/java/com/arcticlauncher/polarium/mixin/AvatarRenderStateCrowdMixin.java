//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.gpu.CrowdChecked;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Room on a player's render state for the crowd path's check (see {@link CrowdChecked}). */
@Mixin(AvatarRenderState.class)
abstract class AvatarRenderStateCrowdMixin implements CrowdChecked {
	@Unique
	private Object polarium$checked;
	@Unique
	private long polarium$checkedFrame = -1;

	@Override
	public Object polarium$checked() {
		return polarium$checked;
	}

	@Override
	public long polarium$checkedFrame() {
		return polarium$checkedFrame;
	}

	@Unique
	private boolean polarium$liveLayers = true;

	@Override
	public boolean polarium$liveLayers() {
		return polarium$liveLayers;
	}

	@Override
	public void polarium$liveLayers(boolean live) {
		polarium$liveLayers = live;
	}

	@Unique
	private long polarium$taken = -1;

	@Override
	public long polarium$taken() {
		return polarium$taken;
	}

	@Override
	public void polarium$taken(long frame) {
		polarium$taken = frame;
	}

	@Unique
	private long polarium$takeable;

	@Override
	public long polarium$takeable() {
		return polarium$takeable;
	}

	@Override
	public void polarium$takeable(long frame) {
		polarium$takeable = frame;
	}

	@Override
	public void polarium$checked(Object recipe, long frame) {
		polarium$checked = recipe;
		polarium$checkedFrame = frame;
	}
}
//#endif
