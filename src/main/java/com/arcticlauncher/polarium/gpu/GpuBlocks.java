//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Block models drawn as entities (primed TNT, falling blocks, and the like) on
 * the GPU: a model's quads are uploaded once ({@link ItemMesh}, the same shape
 * and shader as held items), and per block only its pose, light, overlay (TNT's
 * white flash) and tint colors go up. Blocks with a decal, a see-through render
 * type or a base tint stay on the game's path. One of these per
 * {@link BlockModelFeatureRenderer}; render thread only.
 */
public final class GpuBlocks {
	private static final Logger LOG = LoggerFactory.getLogger("Polarium");
	/** Off with -Dpolarium.blocks=false: block models stay on the game's path. */
	private static final boolean ON = !"false".equals(System.getProperty("polarium.blocks"));
	private static final Identifier ITEM_SHADER = Identifier.withDefaultNamespace("core/item");
	private static final Direction[] DIRECTIONS = Direction.values();
	/** More distinct models than this in use (models rebuilt every frame by a mod, say) and new ones stay on the game's path. */
	private static final int MAX_MODELS = 512;
	/** Meshes by the model parts and render type they were made from (a null mesh: can't be drawn this way). */
	private final Map<PartsKey, ItemMesh> meshes = new HashMap<>();
	private final PartsKey probe = new PartsKey();
	private boolean loggedUnsupported;
	private final GpuBatches batches = new GpuBatches("block models", this::evictIdle);

	public GpuBatches batches() {
		return batches;
	}

	/** Take this block onto the GPU path; false to let the game build its vertices. */
	public boolean capture(BlockModelFeatureRenderer.Submit submit) {
		if (!ON || !batches.preparing() || submit.sheetedDecalPose() != null || submit.tintColor() != -1
				|| submit.modelParts().isEmpty()) {
			return false;
		}
		RenderType type = submit.renderType();
		if (type.hasBlending() || !InstancedPipelines.supports(type.pipeline(), ITEM_SHADER)) {
			if (!loggedUnsupported) {
				loggedUnsupported = true;
				LOG.info("Block models with the render type {} stay on the game's path", type);
			}
			return false;
		}
		try {
			ItemMesh mesh = mesh(submit.modelParts(), type);
			if (mesh == null) {
				return false;
			}
			InstanceData out = batches.add(type, mesh);
			int overlay = submit.overlayCoords();
			int light = submit.lightCoords();
			out.put(overlay & 0xFFFF, (overlay >>> 16) & 0xFFFF, light & 0xFFFF, (light >>> 16) & 0xFFFF);
			Matrix4f m = submit.pose().pose();
			out.put(m.m00(), m.m10(), m.m20(), m.m30());
			out.put(m.m01(), m.m11(), m.m21(), m.m31());
			out.put(m.m02(), m.m12(), m.m22(), m.m32());
			int[] tints = submit.tintLayers();
			for (int layer = 0; layer < ItemMesh.TINT_SLOTS; layer++) {
				// As the game: a layer it has no color for is white.
				int color = layer < tints.length ? tints[layer] : -1;
				out.put(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, ((color >>> 24) & 0xFF) / 255f);
			}
			return true;
		} catch (RuntimeException | LinkageError e) {
			GpuBatches.disable("couldn't take a block model onto the GPU", e);
			return false;
		}
	}

	private ItemMesh mesh(List<BlockStateModelPart> parts, RenderType type) {
		probe.look(parts, type);
		if (meshes.containsKey(probe)) {
			return meshes.get(probe);
		}
		if (meshes.size() >= MAX_MODELS) {
			return null;
		}
		ItemMesh built = build(parts, type);
		LOG.info("A block model ({} parts) is drawn on the GPU: {}", parts.size(), built != null ? built.vertexCount / 4 + " quads" : "no, it stays on the game's path");
		meshes.put(new PartsKey(parts.toArray(new BlockStateModelPart[0]), type, probe.hash), built);
		return built;
	}

	/** The parts' quads in the order the game puts them (per part: each side, then the unculled ones), as one mesh; null if any can't be drawn. */
	private static ItemMesh build(List<BlockStateModelPart> parts, RenderType type) {
		List<BakedQuad> quads = new ArrayList<>();
		for (BlockStateModelPart part : parts) {
			for (Direction side : DIRECTIONS) {
				quads.addAll(part.getQuads(side));
			}
			quads.addAll(part.getQuads(null));
		}
		if (quads.isEmpty()) {
			return null;
		}
		for (BakedQuad quad : quads) {
			BakedQuad.MaterialInfo material = quad.materialInfo();
			if (material.isTinted() && material.tintIndex() >= ItemMesh.TINT_SLOTS) {
				return null;
			}
		}
		return ItemMesh.buildAs(quads.toArray(new BakedQuad[0]), type);
	}

	private void evictIdle() {
		long frame = batches.frame();
		meshes.values().removeIf(mesh -> {
			boolean idle = mesh == null || frame - mesh.lastUsedFrame > GpuBatches.MESH_IDLE_FRAMES;
			if (idle && mesh != null) {
				mesh.close();
			}
			return idle;
		});
	}

	/** A model (its parts, by identity: they are shared baked objects) drawn with a render type. */
	private static final class PartsKey {
		private BlockStateModelPart[] parts;
		private List<BlockStateModelPart> list;
		private RenderType type;
		int hash;

		PartsKey() {}

		PartsKey(BlockStateModelPart[] parts, RenderType type, int hash) {
			this.parts = parts;
			this.type = type;
			this.hash = hash;
		}

		void look(List<BlockStateModelPart> list, RenderType type) {
			this.list = list;
			this.parts = null;
			this.type = type;
			int h = System.identityHashCode(type);
			for (int i = 0; i < list.size(); i++) {
				h = h * 31 + System.identityHashCode(list.get(i));
			}
			this.hash = h;
		}

		private int size() {
			return parts != null ? parts.length : list.size();
		}

		private BlockStateModelPart get(int i) {
			return parts != null ? parts[i] : list.get(i);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof PartsKey key) || key.hash != hash || key.type != type || key.size() != size()) {
				return false;
			}
			for (int i = 0; i < size(); i++) {
				if (key.get(i) != get(i)) {
					return false;
				}
			}
			return true;
		}
	}
}
//#endif
