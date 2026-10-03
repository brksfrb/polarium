package com.arcticlauncher.polarium.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** An item's render state, to tell whether it still looks the way it did. */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccess {
	@Accessor("activeLayerCount")
	int polarium$layerCount();

	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] polarium$layers();

	@Accessor("displayContext")
	ItemDisplayContext polarium$displayContext();
}
