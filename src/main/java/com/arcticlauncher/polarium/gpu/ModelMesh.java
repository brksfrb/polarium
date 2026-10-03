//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

import com.arcticlauncher.polarium.mixin.ModelPartAccess;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

/**
 * A model's shape on the GPU: every cube face of every part, in the part's
 * own space, tagged with the part's number. Parts are numbered in the order
 * {@link ModelPart#render} visits them, which is the order poses are written
 * in each frame ({@link PartPoses}).
 */
final class ModelMesh extends GpuMesh {
	/** Part-space position, texture coordinates, part number, part-space normal. */
	static final VertexFormat FORMAT = VertexFormat.builder(0)
			.addAttribute("Position", GpuFormat.RGB32_FLOAT)
			.addAttribute("UV0", GpuFormat.RG32_FLOAT)
			.addAttribute("UV1", GpuFormat.RG16_SINT)
			.addAttribute("Normal", GpuFormat.RGB32_FLOAT)
			.build();

	static final net.minecraft.resources.Identifier SHADER =
			net.minecraft.resources.Identifier.fromNamespaceAndPath("polarium", "core/entity_instanced");

	/** Parts in visiting order. */
	final ModelPart[] parts;
	/** For each part: the number after its last descendant (its subtree is [i, end)). */
	final int[] subtreeEnd;
	/**
	 * Drawn in the skeleton format: per entity, the six moving parts' values
	 * ({@link HumanoidPoses}) rather than every part's matrix; the GPU works
	 * the matrices out. Each vertex is tagged with the moving part it moves
	 * with (or {@link #ROOT_SLOT}).
	 */
	final boolean skeleton;
	/** Skeleton format: each part's parent's number (-1: the root), what decides its visibility, the moving parts' initial values. */
	final int @org.jspecify.annotations.Nullable [] parent;
	final byte @org.jspecify.annotations.Nullable [] flag;
	final float @org.jspecify.annotations.Nullable [] rest;

	/** A vertex moving with the root (no moving part above it). */
	static final int ROOT_SLOT = HumanoidPoses.PARTS;
	/**
	 * Per entity in the skeleton format: the header, the root pose (3), which
	 * parts are drawn (1: a bit per part), then per moving part its position,
	 * rotation and scale (3).
	 */
	static final int SKELETON_TEXELS = PartPoses.HEADER_TEXELS + 3 + 1 + HumanoidPoses.PARTS * 3;
	/** The skeleton format's bit per part: up to this many parts. */
	private static final int SKELETON_MAX_PARTS = 24;

	private ModelMesh(ModelPart[] parts, int[] subtreeEnd, int vertexCount, GpuBuffer vertices, int texelsPerInstance, boolean skeleton) {
		super(vertices, vertexCount, texelsPerInstance, SHADER);
		this.parts = parts;
		this.subtreeEnd = subtreeEnd;
		this.skeleton = skeleton;
		this.parent = null;
		this.flag = null;
		this.rest = null;
	}

	private ModelMesh(ModelMesh shape, int[] parent, byte[] flag, float[] rest) {
		super(shape.vertices, shape.vertexCount, shape.texelsPerInstance, SHADER);
		this.parts = shape.parts;
		this.subtreeEnd = shape.subtreeEnd;
		this.skeleton = shape.skeleton;
		this.parent = parent;
		this.flag = flag;
		this.rest = rest;
	}

	static ModelMesh build(Model<?> model) {
		ModelPart[] parts = partsInOrder(model);
		int[] identity = new int[parts.length];
		for (int i = 0; i < identity.length; i++) {
			identity[i] = i;
		}
		// Header (color, overlay and light, texture placement, where the parts are), then each part's pose.
		return build(parts, subtreeEnds(model), identity, null, PartPoses.HEADER_TEXELS + PartPoses.TEXELS_PER_PART * parts.length);
	}

	/** {@code model}'s shape in the skeleton format ({@link #skeleton}), or null if its parts aren't laid out for it. */
	static @org.jspecify.annotations.Nullable ModelMesh buildSkeleton(net.minecraft.client.model.player.PlayerModel model) {
		byte[] slots = slots(model);
		if (slots == null) {
			return null;
		}
		ModelPart[] parts = partsInOrder(model);
		int[] identity = new int[parts.length];
		for (int i = 0; i < identity.length; i++) {
			identity[i] = i;
		}
		int[] ends = subtreeEnds(model);
		ModelMesh shape = build(parts, ends, identity, slots, SKELETON_TEXELS);
		int[] parent = new int[parts.length];
		for (int p = 0; p < parts.length; p++) {
			parent[p] = -1;
			for (int q = p - 1; q >= 0; q--) {
				if (ends[q] > p) {
					parent[p] = q;
					break;
				}
			}
		}
		return new ModelMesh(shape, parent, HumanoidPoses.flags((net.minecraft.client.model.player.PlayerModel) model, parts),
				HumanoidPoses.rest((net.minecraft.client.model.HumanoidModel<?>) model));
	}

	/**
	 * For a humanoid model: each part's moving part (0-5 in {@link HumanoidPoses}'s
	 * order, or {@link #ROOT_SLOT}), in visiting order. Null unless every
	 * other part sits exactly on its moving part (or the root): its initial
	 * pose is none at all, and no moving part is under another.
	 */
	static byte @org.jspecify.annotations.Nullable [] slots(Model<?> model) {
		if (!(model instanceof net.minecraft.client.model.HumanoidModel<?> humanoid)) {
			return null;
		}
		ModelPart[] moving = {humanoid.head, humanoid.body, humanoid.rightArm, humanoid.leftArm, humanoid.rightLeg, humanoid.leftLeg};
		List<Byte> out = new ArrayList<>();
		if (!slots(model.root(), (byte) ROOT_SLOT, moving, out) || out.size() > SKELETON_MAX_PARTS) {
			return null;
		}
		byte[] slots = new byte[out.size()];
		for (int i = 0; i < slots.length; i++) {
			slots[i] = out.get(i);
		}
		return slots;
	}

	private static boolean slots(ModelPart part, byte above, ModelPart[] moving, List<Byte> out) {
		byte slot = above;
		for (int i = 0; i < moving.length; i++) {
			if (moving[i] == part) {
				if (above != ROOT_SLOT) {
					return false;
				}
				slot = (byte) i;
			}
		}
		if (slot == above && !none(part.getInitialPose())) {
			return false;
		}
		out.add(slot);
		for (ModelPart child : children(part)) {
			if (!slots(child, slot, moving, out)) {
				return false;
			}
		}
		return true;
	}

	private static boolean none(net.minecraft.client.model.geom.PartPose pose) {
		return pose.x() == 0 && pose.y() == 0 && pose.z() == 0 && pose.xRot() == 0 && pose.yRot() == 0 && pose.zRot() == 0
				&& pose.xScale() == 1 && pose.yScale() == 1 && pose.zScale() == 1;
	}

	/**
	 * For drawing {@code model} with {@code owner}'s parts' poses (see
	 * {@link PartPoses#writeBorrowed}): each of its parts' number in
	 * {@code owner}, by place (the same names from the root). Null if a part
	 * with cubes has no counterpart there that's drawn (with cubes or children
	 * of its own).
	 */
	static int @org.jspecify.annotations.Nullable [] borrowIndex(Model<?> model, Model<?> owner) {
		ModelPart[] ownerParts = partsInOrder(owner);
		int[] ownerEnds = subtreeEnds(owner);
		List<String> ownerPaths = new ArrayList<>();
		paths(owner.root(), "", ownerPaths);
		java.util.Map<String, Integer> ownerIndex = new java.util.HashMap<>();
		for (int i = 0; i < ownerPaths.size(); i++) {
			ownerIndex.put(ownerPaths.get(i), i);
		}
		ModelPart[] parts = partsInOrder(model);
		List<String> paths = new ArrayList<>();
		paths(model.root(), "", paths);
		int[] index = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			Integer at = ownerIndex.get(paths.get(i));
			if (!cubes(parts[i]).isEmpty()
					&& (at == null || cubes(ownerParts[at]).isEmpty() && ownerEnds[at] == at + 1)) {
				return null;
			}
			index[i] = at == null ? 0 : at;
		}
		return index;
	}

	/** {@code model}'s shape, its parts numbered as {@link #borrowIndex} says: drawn with another entity's poses. */
	static ModelMesh borrowing(Model<?> model, int[] index, byte @org.jspecify.annotations.Nullable [] ownerSlots) {
		byte[] slots = null;
		if (ownerSlots != null) {
			slots = new byte[index.length];
			for (int i = 0; i < index.length; i++) {
				slots[i] = ownerSlots[index[i]];
			}
		}
		return build(partsInOrder(model), subtreeEnds(model), index, slots, PartPoses.HEADER_TEXELS);
	}

	/** Each part's path of child names from the root, in visiting order (the root is ""). */
	static void paths(ModelPart part, String path, List<String> out) {
		out.add(path);
		for (java.util.Map.Entry<String, ModelPart> child : ((ModelPartAccess) (Object) part).polarium$children().entrySet()) {
			paths(child.getValue(), path + "/" + child.getKey(), out);
		}
	}

	/** The model's parts in {@link ModelPart#render}'s order. */
	static ModelPart[] partsInOrder(Model<?> model) {
		List<ModelPart> order = new ArrayList<>();
		collect(model.root(), order, new ArrayList<>());
		return order.toArray(new ModelPart[0]);
	}

	private static int[] subtreeEnds(Model<?> model) {
		List<Integer> ends = new ArrayList<>();
		collect(model.root(), new ArrayList<>(), ends);
		return ends.stream().mapToInt(Integer::intValue).toArray();
	}

	/** Vertices of {@code parts}, each tagged with {@code partIndex} of its part. */
	private static ModelMesh build(ModelPart[] parts, int[] subtreeEnd, int[] partIndex, byte @org.jspecify.annotations.Nullable [] slots,
			int texelsPerInstance) {
		int vertexCount = 0;
		for (ModelPart part : parts) {
			for (ModelPart.Cube cube : cubes(part)) {
				for (ModelPart.Polygon polygon : cube.polygons) {
					vertexCount += polygon.vertices().length;
				}
			}
		}
		int stride = FORMAT.getVertexSize();
		ByteBuffer data = MemoryUtil.memAlloc(Math.max(1, vertexCount) * stride).order(ByteOrder.nativeOrder());
		try {
			for (int index = 0; index < parts.length; index++) {
				for (ModelPart.Cube cube : cubes(parts[index])) {
					for (ModelPart.Polygon polygon : cube.polygons) {
						Vector3fc normal = polygon.normal();
						for (ModelPart.Vertex vertex : polygon.vertices()) {
							data.putFloat(vertex.worldX()).putFloat(vertex.worldY()).putFloat(vertex.worldZ());
							data.putFloat(vertex.u()).putFloat(vertex.v());
							data.putShort((short) partIndex[index]).putShort((short) (slots == null ? 0 : slots[index]));
							data.putFloat(normal.x()).putFloat(normal.y()).putFloat(normal.z());
						}
					}
				}
			}
			data.flip();
			GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Polarium model mesh", GpuBuffer.USAGE_VERTEX, data);
			return new ModelMesh(parts, subtreeEnd, vertexCount, buffer, texelsPerInstance, slots != null && texelsPerInstance == SKELETON_TEXELS);
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	/** Parts in {@link ModelPart#render}'s order (a part, then its children in map order). */
	private static void collect(ModelPart part, List<ModelPart> order, List<Integer> ends) {
		int index = order.size();
		order.add(part);
		ends.add(0);
		for (ModelPart child : children(part)) {
			collect(child, order, ends);
		}
		ends.set(index, order.size());
	}

	static List<ModelPart.Cube> cubes(ModelPart part) {
		return ((ModelPartAccess) (Object) part).polarium$cubes();
	}

	static Iterable<ModelPart> children(ModelPart part) {
		return ((ModelPartAccess) (Object) part).polarium$children().values();
	}

}
//#endif
