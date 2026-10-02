//#if MC >= 26.2
package com.arcticlauncher.polonium.mixin;

import com.arcticlauncher.polonium.KeptStates;
import com.arcticlauncher.polonium.LightStates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A kept player state is brought up to the frame instead of made in full when it can be (see LightStates). */
@Mixin(EntityRenderer.class)
abstract class EntityRendererLightMixin {
	@WrapOperation(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V"))
	private void polonium$light(EntityRenderer<?, ?> self, Entity entity, EntityRenderState state, float partialTicks, Operation<Void> extract) {
		boolean kept = KeptStates.inLevel() && entity instanceof KeptStates.Holder holder && holder.polonium$keptState() == state
				&& holder.polonium$keptStateRenderer() == self;
		if (LightStates.light(self, entity, state, kept)) {
			LightStates.bringUp(self, (Avatar) entity, (AvatarRenderState) state, partialTicks);
			LightStates.made(true);
			return;
		}
		LightStates.made(false);
		extract.call(self, entity, state, partialTicks);
		if (kept) {
			LightStates.madeInFull(entity);
		}
	}
}
//#endif
