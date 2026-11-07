package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Flags;
import com.arcticlauncher.polarium.legacy.OpaqueSprites;
import java.util.Map;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the block atlas is stitched, finds its fully opaque sprites (see {@link OpaqueSprites}) while their texel
 * data is still in memory (the model loader drops it afterwards).
 */
@Mixin(SpriteAtlasTexture.class)
abstract class AtlasOpaqueMixin {
	@Shadow
	@Final
	private Map<String, Sprite> sprites;
	@Shadow
	@Final
	private String name;
	@Shadow
	private int maxTextureSize;

	@Unique
	private int polarium$width;
	@Unique
	private int polarium$height;

	@Redirect(method = "method_7005", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/TextureUtil;prepareImage(IIII)V"))
	private void polarium$size(int id, int maxLevel, int width, int height) {
		TextureUtil.prepareImage(id, maxLevel, width, height);
		polarium$width = width;
		polarium$height = height;
	}

	@Inject(method = "method_7005", at = @At("RETURN"))
	private void polarium$stitched(CallbackInfo ci) {
		if (!Flags.OPAQUE_CUTOUT || !"textures".equals(name)) {
			return;
		}
		try {
			OpaqueSprites.atlasStitched(sprites.values(), polarium$width, polarium$height, maxTextureSize);
		} catch (Throwable t) {
			System.out.println("[Polarium] opaque cutout sprites off: " + t);
		}
	}
}
