package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.KeptStates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The game's packet handlers find the entity a packet is about here: such an
 * entity is marked, so its next render state is made in full (LightStates).
 * Anything else looking an entity up marks it too, which only costs speed.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelTouchedMixin {
	@Inject(method = "getEntity(I)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"))
	private void polarium$touched(int id, CallbackInfoReturnable<Entity> cir) {
		if (cir.getReturnValue() instanceof KeptStates.Holder holder) {
			holder.polarium$touched(true);
		}
	}
}
