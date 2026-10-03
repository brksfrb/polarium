//#if MC >= 26.1
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.ItemModels;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Item models that aren't fixed (compass, clock, ...: see ItemModels) worked
 * out one at a time, whichever thread asks: entities' states are made on the
 * helpers while the HUD works out its own items, and a spinning compass's
 * random source is shared. Fixed models (nearly all) go straight through.
 */
@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverLockMixin {
	@WrapOperation(method = "appendItemLayers", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemModel;update(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/renderer/item/ItemModelResolver;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/ItemOwner;I)V"))
	private void polarium$oneAtATime(ItemModel model, ItemStackRenderState output, ItemStack item, ItemModelResolver resolver,
			ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed, Operation<Void> update) {
		if (ItemModels.fixed(model)) {
			update.call(model, output, item, resolver, context, level, owner, seed);
			return;
		}
		synchronized (ItemModels.LOCK) {
			update.call(model, output, item, resolver, context, level, owner, seed);
		}
	}
}
//#endif
