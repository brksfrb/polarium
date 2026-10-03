//#if MC >= 26.2
package com.arcticlauncher.polonium.mixin;

import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What a choosing item model chooses by (see TickInputs). */
@Mixin(SelectItemModel.class)
public interface SelectItemModelAccess {
	@Accessor("property")
	SelectItemModelProperty<?> polonium$property();
}
//#endif
