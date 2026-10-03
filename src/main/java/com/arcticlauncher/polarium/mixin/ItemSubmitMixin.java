//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.ItemTranslucency;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** An item's "needs blending" answer, kept per model (see {@link ItemTranslucency}). */
@Mixin(ItemFeatureRenderer.Submit.class)
abstract class ItemSubmitMixin {
	@WrapMethod(method = "hasTranslucency")
	private boolean polarium$kept(Operation<Boolean> compute) {
		return ItemTranslucency.of(((ItemFeatureRenderer.Submit) (Object) this).quads(), compute::call);
	}
}
//#endif
