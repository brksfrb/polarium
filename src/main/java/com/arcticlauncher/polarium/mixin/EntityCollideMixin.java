package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.CollidableEntities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Primed TNT doesn't go through every nearby entity looking for something to bump into when there is nothing of the kind (see {@link CollidableEntities}). */
@Mixin(Entity.class)
abstract class EntityCollideMixin {
	@WrapOperation(method = "collide",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
	private List<VoxelShape> polarium$noneToBumpInto(Level level, Entity entity, AABB box, Operation<List<VoxelShape>> search) {
		return CollidableEntities.skips(level, entity) ? List.of() : search.call(level, entity, box);
	}
}
