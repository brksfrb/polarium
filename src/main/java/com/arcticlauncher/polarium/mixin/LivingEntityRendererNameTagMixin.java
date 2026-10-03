//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.AttributeValues;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;

/** The name tag distance attributes, kept per entity as values (see {@link AttributeValues}). */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererNameTagMixin {
	@WrapMethod(method = "extractNameTags(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V")
	private void polarium$keptDistances(LivingEntity entity, LivingEntityRenderState state, float partialTicks, Operation<Void> extract) {
		AttributeValues.Holder2 kept = (AttributeValues.Holder2) entity;
		long version = AttributeValues.version(entity);
		if (kept.polarium$version() != version) {
			// As the game reads them.
			kept.polarium$nameDistances(entity.getAttribute(Attributes.NAME_TAG_DISTANCE).getValue(),
					entity.getAttribute(Attributes.BELOW_NAME_DISTANCE).getValue());
			kept.polarium$version(version);
		}
		((EntityRendererNameTagsAccess) this).polarium$extractNameTags(entity, state, partialTicks, kept.polarium$nameDistance(),
				kept.polarium$belowNameDistance());
	}
}
//#endif
