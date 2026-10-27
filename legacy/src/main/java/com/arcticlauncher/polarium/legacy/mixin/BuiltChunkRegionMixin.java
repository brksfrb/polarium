package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Regions;
import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.FloatBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A chunk section's vertices (and the matrix its display list is compiled with) are relative to the region
 * the section is in, not to the section's own corner. See {@link Regions}.
 */
@Mixin(BuiltChunk.class)
abstract class BuiltChunkRegionMixin {
	@Shadow
	private BlockPos pos;
	@Shadow
	@Final
	private FloatBuffer field_11080;

	@Inject(method = "method_10158", at = @At("HEAD"), cancellable = true)
	private void polarium$offset(BufferBuilder bufferBuilder, BlockPos blockPos, CallbackInfo ci) {
		if (!Regions.active()) {
			return;
		}
		bufferBuilder.begin(7, VertexFormats.BLOCK);
		bufferBuilder.offset(-Regions.origin(blockPos.getX()), 0, -Regions.origin(blockPos.getZ()));
		ci.cancel();
	}

	/** The game's tiny scale about the section's centre, hiding cracks between sections; the centre moves with the origin. */
	@Inject(method = "method_10174", at = @At("HEAD"), cancellable = true)
	private void polarium$matrix(CallbackInfo ci) {
		if (!Regions.active()) {
			return;
		}
		float centreX = pos.getX() - Regions.origin(pos.getX()) + 8.0F;
		float centreY = pos.getY() + 8.0F;
		float centreZ = pos.getZ() - Regions.origin(pos.getZ()) + 8.0F;
		float scale = 1.000001F;
		GlStateManager.pushMatrix();
		GlStateManager.loadIdentity();
		GlStateManager.translate(-centreX, -centreY, -centreZ);
		GlStateManager.scale(scale, scale, scale);
		GlStateManager.translate(centreX, centreY, centreZ);
		GlStateManager.getFloat(2982, field_11080);
		GlStateManager.popMatrix();
		ci.cancel();
	}
}
