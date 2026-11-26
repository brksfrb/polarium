package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Flags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The block or entity under the crosshair is found along the player's look, and on 1.8.9 a living
 * entity looks along its head yaw. The player's head yaw only follows the mouse once a tick, so between
 * ticks the target (and so what a click hits) lags behind the crosshair. The player's own look uses the
 * yaw and pitch the camera is drawn with, as Minecraft does from 1.12 on.
 */
@Mixin(LivingEntity.class)
abstract class ClientPlayerLookMixin extends Entity {
	ClientPlayerLookMixin(World world) {
		super(world);
	}

	@Inject(method = "getRotationVector(F)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
	private void polarium$lookWithCamera(float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		if (Flags.LOOK && (Object) this instanceof ClientPlayerEntity) {
			cir.setReturnValue(super.getRotationVector(tickDelta));
		}
	}
}
