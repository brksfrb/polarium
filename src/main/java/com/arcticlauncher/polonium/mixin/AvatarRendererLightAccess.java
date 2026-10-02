//#if MC >= 26.2
package com.arcticlauncher.polonium.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The player renderer's own per-frame steps, for LightStates. */
@Mixin(AvatarRenderer.class)
public interface AvatarRendererLightAccess {
	@Invoker("extractFlightData")
	void polonium$extractFlightData(Avatar entity, AvatarRenderState state, float partialTicks);

	@Invoker("extractCapeState")
	void polonium$extractCapeState(Avatar entity, AvatarRenderState state, float partialTicks);
}
//#endif
