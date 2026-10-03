//#if MC >= 26.2
package com.arcticlauncher.polonium.mixin;

import java.util.List;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.ItemModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The models a composite item model draws (see TickInputs). */
@Mixin(CompositeModel.class)
public interface CompositeModelAccess {
	@Accessor("models")
	List<ItemModel> polonium$models();
}
//#endif
