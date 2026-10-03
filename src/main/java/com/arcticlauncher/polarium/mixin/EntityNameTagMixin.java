//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.NameTags;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Room on each entity for its kept name tag text (see {@link NameTags}). */
@Mixin(Entity.class)
abstract class EntityNameTagMixin implements NameTags.Holder {
	@Unique
	private NameTags.Kept polarium$nameTag;

	@Override
	public NameTags.Kept polarium$nameTag() {
		return polarium$nameTag;
	}

	@Override
	public void polarium$nameTag(NameTags.Kept kept) {
		polarium$nameTag = kept;
	}
}
//#endif
