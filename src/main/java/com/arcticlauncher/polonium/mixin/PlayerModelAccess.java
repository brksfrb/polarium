//#if MC >= 26.2
package com.arcticlauncher.polonium.mixin;

import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether a player model has slim arms (its held items hang half a pixel further out). */
@Mixin(PlayerModel.class)
public interface PlayerModelAccess {
	@Accessor("slim")
	boolean polonium$slim();
}
//#endif
