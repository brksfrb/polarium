package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.SpriteAccess;
import net.minecraft.client.texture.Sprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** See {@link SpriteAccess}. */
@Mixin(Sprite.class)
abstract class SpriteRotationMixin implements SpriteAccess {
	@Shadow
	protected boolean rotation;

	@Override
	public boolean polarium$rotated() {
		return rotation;
	}
}
