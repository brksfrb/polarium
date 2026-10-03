package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.RendererHolder;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Which renderer draws a player or mannequin, kept for the rest of its tick.
 * Finding it means resolving the skin (to choose the wide or slim model), and
 * it's asked for every entity many times a frame (culling mods ask on every
 * tick too); the answer only changes with the skin's model type.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRendererCacheMixin {
	@WrapMethod(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;")
	private EntityRenderer<?, ?> polarium$kept(Entity entity, Operation<EntityRenderer<?, ?>> find) {
		if (!(entity instanceof Avatar) || !(entity instanceof RendererHolder holder)) {
			return find.call(entity);
		}
		EntityRenderer<?, ?> kept = holder.polarium$renderer();
		if (kept != null && holder.polarium$rendererTick() == entity.tickCount) {
			return kept;
		}
		EntityRenderer<?, ?> found = find.call(entity);
		holder.polarium$renderer(found, entity.tickCount);
		return found;
	}
}
