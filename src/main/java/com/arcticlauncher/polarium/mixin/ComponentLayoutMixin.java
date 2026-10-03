package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.NameTagCache;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Room on each text for its kept name tag layouts (see {@link NameTagCache}). */
@Mixin(MutableComponent.class)
abstract class ComponentLayoutMixin implements NameTagCache.Holder {
	@Unique
	private NameTagCache.Kept polarium$layouts;

	@Override
	public NameTagCache.Kept polarium$layouts() {
		return polarium$layouts;
	}

	@Override
	public void polarium$layouts(NameTagCache.Kept kept) {
		polarium$layouts = kept;
	}
}
