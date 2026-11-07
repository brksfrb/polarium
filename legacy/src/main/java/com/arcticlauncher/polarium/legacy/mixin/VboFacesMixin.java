package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.FaceBuckets;
import com.arcticlauncher.polarium.legacy.FaceCulling;
import com.arcticlauncher.polarium.legacy.Flags;
import com.arcticlauncher.polarium.legacy.Pipeline;
import com.arcticlauncher.polarium.legacy.SectionData;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.IntBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexBuffer;
import net.minecraft.client.render.world.VboChunkRenderManager;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Face culling on the vertex buffer path (VBOs on): a split layer's buffer holds its buckets one after the other
 * (see {@link FaceBuckets}), so the buckets that can face the camera are ranges of it, drawn with one
 * glMultiDrawArrays per section (adjacent ranges merged). Otherwise the game's loop as it is.
 */
@Mixin(VboChunkRenderManager.class)
abstract class VboFacesMixin {
	/** Made on first use: field initialisers of this mixin are not run for the game's instance. */
	@Unique
	private FaceCulling polarium$culling;
	@Unique
	private IntBuffer polarium$firsts;
	@Unique
	private IntBuffer polarium$counts;

	@Shadow
	protected abstract void method_9929();

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void polarium$render(RenderLayer layer, CallbackInfo ci) {
		int ordinal = layer.ordinal();
		if (!Flags.FACES || ordinal >= FaceBuckets.LAYERS) {
			return;
		}
		AbstractManagerAccess access = (AbstractManagerAccess) this;
		ci.cancel();
		if (!access.polarium$ready()) {
			return;
		}
		if (polarium$culling == null) {
			polarium$culling = new FaceCulling();
			polarium$firsts = BufferUtils.createIntBuffer(FaceBuckets.BUCKETS);
			polarium$counts = BufferUtils.createIntBuffer(FaceBuckets.BUCKETS);
		}
		MinecraftClient client = MinecraftClient.getInstance();
		polarium$culling.begin(client.width, client.height);
		boolean faces = polarium$culling.usable();
		boolean multi = GLContext.getCapabilities().OpenGL14;
		boolean pipelined = Pipeline.active();
		double viewX = access.polarium$viewX();
		double viewY = access.polarium$viewY();
		double viewZ = access.polarium$viewZ();
		VboChunkRenderManager self = (VboChunkRenderManager) (Object) this;
		for (BuiltChunk section : access.polarium$helpers()) {
			VertexBuffer buffer = section.method_10165(ordinal);
			GlStateManager.pushMatrix();
			self.method_9770(section);
			section.method_10169();
			buffer.bind();
			method_9929();
			SectionData data = (SectionData) section;
			int drawn;
			if (data.polarium$split()[ordinal]) {
				drawn = polarium$drawBuckets(data, ordinal, section.getPos(), faces, multi, viewX, viewY, viewZ);
			} else {
				buffer.draw(GL11.GL_QUADS);
				drawn = ((VertexBufferAccess) buffer).polarium$size();
			}
			if (pipelined) {
				Pipeline.submitted(drawn);
			}
			GlStateManager.popMatrix();
		}
		GLX.gl15BindBuffer(GLX.arrayBuffer, 0);
		GlStateManager.clearColor();
		access.polarium$helpers().clear();
	}

	@Unique
	private int polarium$drawBuckets(SectionData data, int ordinal, BlockPos pos, boolean faces, boolean multi, double viewX, double viewY, double viewZ) {
		int countsAt = ordinal * FaceBuckets.BUCKETS;
		int[] counts = data.polarium$bucketVertices();
		int hidden = 0;
		if (faces) {
			// the buffer is relative to the section's corner, drawn under a translate to it
			float tx = (float) (pos.getX() - viewX);
			float ty = (float) (pos.getY() - viewY);
			float tz = (float) (pos.getZ() - viewZ);
			hidden = polarium$culling.hidden(data.polarium$bucketPlanes(), ordinal * FaceBuckets.AXIS_BUCKETS * 2, counts, countsAt, tx, ty, tz,
					pos.getX() - viewX, pos.getY() - viewY, pos.getZ() - viewZ);
		}
		polarium$firsts.clear();
		polarium$counts.clear();
		int first = 0;
		int runFirst = -1;
		int runCount = 0;
		int drawn = 0;
		for (int b = 0; b < FaceBuckets.BUCKETS; b++) {
			int count = counts[countsAt + b];
			if (count != 0 && (hidden & 1 << b) == 0) {
				if (runFirst < 0) {
					runFirst = first;
				}
				runCount += count;
			} else if (count != 0 && runFirst >= 0) {
				polarium$firsts.put(runFirst);
				polarium$counts.put(runCount);
				drawn += runCount;
				runFirst = -1;
				runCount = 0;
			}
			first += count;
		}
		if (runFirst >= 0) {
			polarium$firsts.put(runFirst);
			polarium$counts.put(runCount);
			drawn += runCount;
		}
		polarium$firsts.flip();
		polarium$counts.flip();
		int runs = polarium$firsts.remaining();
		if (runs == 1 || (runs > 1 && !multi)) {
			for (int r = 0; r < runs; r++) {
				GL11.glDrawArrays(GL11.GL_QUADS, polarium$firsts.get(r), polarium$counts.get(r));
			}
		} else if (runs > 1) {
			GL14.glMultiDrawArrays(GL11.GL_QUADS, polarium$firsts, polarium$counts);
		}
		return drawn;
	}
}
