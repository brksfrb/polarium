//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.gpu.MapAtlas;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.MapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A map's picture is drawn with the texture of its place in the shared map atlas
 * (see {@link MapAtlas}) instead of its own, so maps share draws. Everything
 * else about the drawing, the shape, light and markers, is the game's.
 */
@Mixin(MapRenderer.class)
abstract class MapRendererMixin {
	/** The map being drawn (the game draws one at a time, on the render thread). */
	private static final ThreadLocal<MapRenderState> polarium$current = new ThreadLocal<>();

	@Inject(method = "render", at = @At("HEAD"))
	private void polarium$begin(MapRenderState state, PoseStack pose, SubmitNodeCollector collector, boolean inHand, int light, CallbackInfo ci) {
		polarium$current.set(state);
	}

	@Redirect(method = "render", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"))
	private void polarium$picture(SubmitNodeCollector collector, PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer picture) {
		MapRenderState state = polarium$current.get();
		MapAtlas.Place place = state == null || state.texture == null ? null : MapAtlas.place(state.texture);
		if (place == null) {
			collector.submitCustomGeometry(pose, type, picture);
			return;
		}
		collector.submitCustomGeometry(pose, RenderTypes.text(place.atlas()), (p, consumer) -> picture.render(p, new Remap(consumer, place)));
	}

	/** The quad's texture coordinates (0 to 1 over the map) moved into the map's place in the atlas. */
	private static final class Remap implements VertexConsumer {
		private final VertexConsumer to;
		private final float u;
		private final float v;
		private final float width;
		private final float height;

		Remap(VertexConsumer to, MapAtlas.Place place) {
			this.to = to;
			this.u = place.u();
			this.v = place.v();
			this.width = place.width();
			this.height = place.height();
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			to.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			to.setColor(r, g, b, a);
			return this;
		}

		@Override
		public VertexConsumer setColor(int argb) {
			to.setColor(argb);
			return this;
		}

		@Override
		public VertexConsumer setUv(float s, float t) {
			to.setUv(u + s * width, v + t * height);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int a, int b) {
			to.setUv1(a, b);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int a, int b) {
			to.setUv2(a, b);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			to.setNormal(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float w) {
			to.setLineWidth(w);
			return this;
		}

		@Override
		public void addVertex(float x, float y, float z, int color, float s, float t, int overlay, int light, float nx, float ny, float nz) {
			to.addVertex(x, y, z, color, u + s * width, v + t * height, overlay, light, nx, ny, nz);
		}
	}
}
//#endif
