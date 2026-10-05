//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * An item frame's wooden frame is the same four models (plain or glow, with or without a map)
 * for every frame, but the game looks the model up again for each frame, every frame. With
 * hundreds of frames (map billboards) that is a large part of the render thread. Each of the
 * four is looked up once and shared; the renderer is rebuilt on a resource reload, so changed
 * models are picked up.
 */
@Mixin(ItemFrameRenderer.class)
abstract class ItemFrameModelMixin {
	@Shadow
	@Final
	private BlockModelResolver blockModelResolver;

	@Unique
	private final BlockModelRenderState[] polarium$models = new BlockModelRenderState[4];
	@Unique
	private final BlockModelRenderState polarium$none = new BlockModelRenderState();

	/** The game's lookup is skipped; the shared one is used when drawing. */
	@Redirect(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockModelResolver;updateForItemFrame(Lnet/minecraft/client/renderer/block/BlockModelRenderState;ZZ)V"))
	private void polarium$skipLookup(BlockModelResolver resolver, BlockModelRenderState state, boolean glowing, boolean map) {
	}

	/**
	 * A frame showing a map draws the map, never the item; the game still builds the item's model
	 * for it every frame. Skipped when the map is there to draw (without it the item is drawn).
	 */
	@Redirect(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemModelResolver;updateForNonLiving(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/Entity;)V"))
	private void polarium$skipItemForMap(ItemModelResolver resolver, ItemStackRenderState output, ItemStack item, ItemDisplayContext context, Entity entity) {
		if (!item.isEmpty() && entity instanceof ItemFrame frame) {
			var map = frame.getFramedMapId(item);
			if (map != null && frame.level().getMapData(map) != null) {
				return;
			}
		}
		resolver.updateForNonLiving(output, item, context, entity);
	}

	@Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETFIELD,
					target = "Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;frameModel:Lnet/minecraft/client/renderer/block/BlockModelRenderState;"))
	private BlockModelRenderState polarium$sharedModel(ItemFrameRenderState state) {
		if (state.isInvisible) {
			return polarium$none;
		}
		int key = (state.isGlowFrame ? 2 : 0) | (state.mapId != null ? 1 : 0);
		BlockModelRenderState model = polarium$models[key];
		if (model == null) {
			synchronized (polarium$models) {
				model = polarium$models[key];
				if (model == null) {
					model = new BlockModelRenderState();
					blockModelResolver.updateForItemFrame(model, state.isGlowFrame, state.mapId != null);
					polarium$models[key] = model;
				}
			}
		}
		return model;
	}
}
//#endif
