//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The renderer's name tag extraction with the distances given. */
@Mixin(EntityRenderer.class)
public interface EntityRendererNameTagsAccess {
	@Invoker("extractNameTags")
	void polarium$extractNameTags(Entity entity, EntityRenderState state, float partialTicks, double nameTagDistance, double belowNameDistance);

	@Invoker("shouldShowName")
	boolean polarium$shouldShowName(Entity entity, double distanceToCameraSq);

	@Invoker("getNameTag")
	net.minecraft.network.chat.@org.jspecify.annotations.Nullable Component polarium$getNameTag(Entity entity);
}
//#endif
