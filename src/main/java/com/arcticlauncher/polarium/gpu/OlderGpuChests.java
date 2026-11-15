// i18n-ignore-file: shader names, GPU labels and log lines; nothing here is shown to the player.
package com.arcticlauncher.polarium.gpu;

//#if MC < 26.2
import com.arcticlauncher.polarium.BlockEntityOptions;

import com.arcticlauncher.polarium.BlockEntityCulling;
import com.arcticlauncher.polarium.ChestInstancing;
import com.arcticlauncher.polarium.ChestUniformBatches;

import com.mojang.blaze3d.buffers.GpuBuffer;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BrightnessCombiner;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//#if MC >= 26.1
import net.minecraft.client.resources.model.sprite.SpriteId;
//#else
import net.minecraft.client.resources.model.Material;
//#endif
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chests drawn on the GPU (1.21.11 and 26.1.2): the game's chests, trapped chests, ender chests and copper chests,
 * single and double, in batches of up to 256 instead of building vertices for each chest.
 * Vanilla render states remain available until all batches succeed.
 *
 * <p>What is drawn is kept by {@link ChestInstancing}: a chest with its lid shut sits in its section's
 * block of instances, written once; only a chest whose lid moves is written each frame. A chest being
 * broken is left to the game (which draws the cracks on it). The chest models' shapes are taken from
 * the game's own baked models and uploaded once; the vertex shader is made from the game's own entity
 * shader (as it is loaded, resource packs included) with only its inputs worked out per instance, and
 * the draw uses the game's own render type for chests (same fragment shader, textures, light, fog and
 * depth), so the pixels are the game's.
 *
 * <p>The game (or Sodium) still decides which sections' block entities are drawn; this takes each
 * chest at the block entity dispatcher and skips its model submission only after a successful draw.
 * Stays off with shader mods,
 * mods that change chest models or textures, portal mods, or when another mod has replaced the chest
 * renderer, and switches itself off if anything fails. Render thread.
 */
public final class OlderGpuChests implements ChestInstancing.World {
	/** What the block entity mixin adds to every block entity. */
	public interface Slot {
		Object polarium$gpu();

		void polarium$gpu(Object value);

		/** The last lid event told it to be open. */
		boolean polarium$lidOpen();

		void polarium$lidOpen(boolean open);
	}

	private static final Logger LOG = LoggerFactory.getLogger("Polarium");
	/** Marks a block entity that is never drawn this way (not one of the game's chests). */
	private static final Object NEVER = new Object();
	private static final boolean OFF = "false".equals(System.getProperty("polarium.gpuChests"));
	private static final Identifier ENTITY_SHADER = Identifier.withDefaultNamespace("core/entity");
	public static final Identifier SHADER = Identifier.fromNamespaceAndPath("polarium", "core/chest_instanced");
	private static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", com.mojang.blaze3d.vertex.VertexFormatElement.POSITION)
            .add("UV0", com.mojang.blaze3d.vertex.VertexFormatElement.UV0)
            .add("UV1", com.mojang.blaze3d.vertex.VertexFormatElement.UV1)
            .add("Normal", com.mojang.blaze3d.vertex.VertexFormatElement.NORMAL).padding(1).build();
    private static final int BATCH = ChestUniformBatches.CAPACITY;
    private static final int BATCH_BYTES = ChestUniformBatches.BYTES;
    private GpuBuffer cachedInstances;
    private final java.util.Map<BlockPos, ChestInstancing.Entry> captured = new java.util.HashMap<>();
    private boolean drawn;
    private net.minecraft.client.renderer.rendertype.RenderSetup setup;
	private static final ChestType[] TYPES = {ChestType.SINGLE, ChestType.LEFT, ChestType.RIGHT};
	private static final ChestRenderState.ChestMaterialType[] MATERIALS = ChestRenderState.ChestMaterialType.values();
	/** Bytes of the per-draw uniform block (four vec4). */
	private static final int DRAW_BYTES = 64;

	private static final OlderGpuChests INSTANCE = new OlderGpuChests();

	private final ChestInstancing chests = new ChestInstancing();
	private boolean failed;
	/** Inside the world's extraction, with the path on. */
	private boolean inFrame;
	private boolean frameBegun;
	private float partialTick;
	private ClientLevel level;
	private ChestInstancing.Frame frame;
	/** A frame's instances are ready; the world's main pass hasn't been set up yet. */
	private boolean pending;
	// On the GPU; made again after every resource reload.
	private boolean ready;
	private final GpuBuffer[] meshes = new GpuBuffer[ChestInstancing.KINDS];
	private final int[] meshVertices = new int[ChestInstancing.KINDS];
	private final Vector3f hinge = new Vector3f();
	private RenderPipeline pipeline;
	private boolean christmas;
	private long lastReport;

	private OlderGpuChests() {}

	private boolean on() {
		BlockEntityOptions config = BlockEntityOptions.get();
		return !OFF && BlockEntityOptions.compatible() && !failed && config != null && config.chestInstancing;
	}

	// ---- the frame ----

	/** The world's extraction starts. */
	public static void begin() {
		OlderGpuChests g = INSTANCE;
		g.captured.clear();
        g.drawn = false;
        g.inFrame = false;
		g.frameBegun = false;
		g.pending = false;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != g.level) {
			// Another world: nothing kept from the last one (also when the option is off, so nothing of it stays in memory).
			g.chests.clear();
            if (g.cachedInstances != null) { g.cachedInstances.close(); g.cachedInstances = null; }
			g.level = minecraft.level;
		}
		if (g.level == null || !g.on()) {
			return;
		}
		try {
			g.prepare(minecraft);
			g.inFrame = true;
		} catch (RuntimeException | LinkageError | ReflectiveOperationException e) {
			g.fail("couldn't set up", e);
		}
	}

	/**
	 * The game is about to make this block entity's render state. True: it is a chest drawn from here
	 * this frame, and the game makes nothing for it.
	 */
	public static boolean capture(BlockEntity entity, float partialTick, Object breaking, boolean global) {
        try { return captureChecked(entity, partialTick, breaking, global); }
        catch (RuntimeException | LinkageError e) {
            INSTANCE.fail("couldn't capture", e);
            return false;
        }
    }
    private static boolean captureChecked(BlockEntity entity, float partialTick, Object breaking, boolean global) {
		OlderGpuChests g = INSTANCE;
		if (!g.inFrame || !BlockEntityOptions.compatible() || global || entity == null) {
			return false;
		}
		Object slot = ((Slot) entity).polarium$gpu();
		if (slot == NEVER) {
			return false;
		}
		ChestInstancing.Entry entry = slot instanceof ChestInstancing.Entry kept && kept.live() ? kept : g.register(entity);
		if (entry == null) {
			return false;
		}
		if (!g.frameBegun) {
			g.frameBegun = true;
			g.partialTick = partialTick;
			Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
			g.chests.beginFrame(camera.x, camera.y, camera.z, BlockEntityCulling.distance(BlockEntityOptions.get()));
		}
		boolean accepted = g.chests.see(entry, breaking != null);
        if (accepted) g.captured.put(entity.getBlockPos(), entry);
        // Keep vanilla's state until the actual draw succeeds.
        return false;
	}

	/** The world's extraction is over: lay the frame's chests out. */
	public static void end() {
		OlderGpuChests g = INSTANCE;
		if (!g.inFrame) {
			return;
		}
		g.inFrame = false;
		if (!g.frameBegun) {
			return;
		}
		try {
			g.frame = g.chests.endFrame(g, g.partialTick);
            g.captured.values().removeIf(entry -> !entry.live());
			g.pending = g.frame.total() > 0;
		} catch (RuntimeException | LinkageError e) {
			g.fail("couldn't lay out a frame", e);
		}
	}

    public static void beforeSubmit() {
        OlderGpuChests g = INSTANCE;
        if (!g.inFrame || !g.frameBegun) return;
        end();
        if (!g.pending) return;
        try { g.drawFrame(); g.drawn = true; }
        catch (RuntimeException | LinkageError e) {
            g.fail("couldn't draw", e);
            LOG.info("Polarium: {} retained vanilla chest states remain for this same frame", g.captured.size());
        }
    }
    public static void afterSubmit() { INSTANCE.drawn = false; }
    public static boolean submitted(net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState state) {
        return INSTANCE.drawn && state instanceof ChestRenderState && INSTANCE.captured.containsKey(state.blockPos);
    }

	private void fail(String what, Throwable e) {
		if (!failed) {
			failed = true;
			LOG.error("Polarium: {} chests on the GPU; they are drawn the game's way from now on", what, e);
		}
		inFrame = false;
		pending = false;
	}

	// ---- which chests ----

	private ChestInstancing.Entry register(BlockEntity entity) {
		Class<?> type = entity.getClass();
		if (type != ChestBlockEntity.class && type != TrappedChestBlockEntity.class && type != EnderChestBlockEntity.class) {
			((Slot) entity).polarium$gpu(NEVER);
			return null;
		}
		Object renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
		if (!BlockEntityOptions.rendererSupported(renderer) || renderer.getClass() != ChestRenderer.class || entity.getLevel() != level || entity.isRemoved()
				|| !entity.getType().isValid(entity.getBlockState())) {
			return null;
		}
		BlockPos pos = entity.getBlockPos();
		ChestInstancing.Entry entry = chests.add(entity, pos.getX(), pos.getY(), pos.getZ(), !shut(entity));
		((Slot) entity).polarium$gpu(entry);
		return entry;
	}

	/** The block entity was removed or its block changed: it is looked at afresh when next drawn. */
	public static void gone(BlockEntity entity) {
		Slot slot = (Slot) entity;
		if (slot.polarium$gpu() instanceof ChestInstancing.Entry entry) {
			INSTANCE.chests.remove(entry);
			slot.polarium$gpu(null);
		}
	}

	/** A chest's lid was told to open or shut (the game's block event 1). */
	public static void lidEvent(BlockEntity entity, int event, int count) {
		if (event != 1 || entity.getLevel() == null || !entity.getLevel().isClientSide()) {
			return;
		}
		Slot slot = (Slot) entity;
		slot.polarium$lidOpen(count > 0);
		wake(slot);
		BlockEntity other = otherHalf(entity);
		if (other != null) {
			wake((Slot) other);
		}
	}

	private static void wake(Slot slot) {
		if (slot.polarium$gpu() instanceof ChestInstancing.Entry entry) {
			INSTANCE.chests.wake(entry);
		}
	}

	/** Light changed in this section. */
	public static void lightChanged(int sx, int sy, int sz) {
		INSTANCE.chests.markDirtyAround(sx, sy, sz);
	}

	/** Resources were reloaded: models, textures and shaders are taken again. */
	public static void reload() {
		INSTANCE.ready = false;
        INSTANCE.chests.clear();
	}

	private static BlockEntity otherHalf(BlockEntity entity) {
		BlockState state = entity.getBlockState();
		if (!(state.getBlock() instanceof ChestBlock) || !state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
				|| entity.getLevel() == null) {
			return null;
		}
		return entity.getLevel().getBlockEntity(ChestBlock.getConnectedBlockPos(entity.getBlockPos(), state));
	}

	/** The lid as the game's renderer reads it: a double chest's halves move together. */
	private static float openness(BlockEntity entity, float partialTick) {
		BlockState state = entity.getBlockState();
		DoubleBlockCombiner.NeighborCombineResult<? extends ChestBlockEntity> combined;
		if (entity.getLevel() != null && state.getBlock() instanceof ChestBlock chest) {
			combined = chest.combine(state, entity.getLevel(), entity.getBlockPos(), true);
		} else {
			combined = DoubleBlockCombiner.Combiner::acceptNone;
		}
		return combined.apply(ChestBlock.opennessCombiner((LidBlockEntity) entity)).get(partialTick);
	}

	private static boolean shut(BlockEntity entity) {
		if (((Slot) entity).polarium$lidOpen()) {
			return false;
		}
		BlockEntity other = otherHalf(entity);
		if (other != null && ((Slot) other).polarium$lidOpen()) {
			return false;
		}
		return openness(entity, 0f) == 0f && openness(entity, 1f) == 0f;
	}

	@Override
	public boolean describe(ChestInstancing.Entry entry) {
		BlockEntity entity = (BlockEntity) entry.owner;
		if (entity.isRemoved() || entity.getLevel() != level || level == null) {
			return false;
		}
		BlockState state = entity.getBlockState();
		if (!entity.getType().isValid(state) || !state.hasProperty(ChestBlock.FACING)) {
			return false;
		}
		ChestType type = state.hasProperty(ChestBlock.TYPE) ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
		Direction facing = state.getValue(ChestBlock.FACING);
		entry.kind = type == ChestType.SINGLE ? 0 : type == ChestType.LEFT ? 1 : 2;
		entry.facing = Math.round(facing.toYRot() / 90f) & 3;
		entry.sprite = material(entity, state).ordinal() * ChestInstancing.KINDS + entry.kind;
		BlockPos pos = entity.getBlockPos();
		//#if MC >= 26.1
        int light = net.minecraft.client.renderer.LevelRenderer.getLightCoords(level, pos);
        //#else
        int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos);
        //#endif
		if (type != ChestType.SINGLE && state.getBlock() instanceof ChestBlock chest) {
			light = chest.combine(state, level, pos, true).apply(new BrightnessCombiner<>()).applyAsInt(light);
		}
		entry.lightU = light & 0xFFFF;
		entry.lightV = light >>> 16;
		return true;
	}

	/** As ChestRenderer.getChestMaterial. */
	private ChestRenderState.ChestMaterialType material(BlockEntity entity, BlockState state) {
        if (entity instanceof EnderChestBlockEntity) return ChestRenderState.ChestMaterialType.ENDER_CHEST;
        //#if MC < 26.1
        if (christmas) return ChestRenderState.ChestMaterialType.CHRISTMAS;
        //#endif
		if (state.getBlock() instanceof CopperChestBlock copper) {
			return switch (copper.getState()) {
				case UNAFFECTED -> ChestRenderState.ChestMaterialType.COPPER_UNAFFECTED;
				case EXPOSED -> ChestRenderState.ChestMaterialType.COPPER_EXPOSED;
				case WEATHERED -> ChestRenderState.ChestMaterialType.COPPER_WEATHERED;
				case OXIDIZED -> ChestRenderState.ChestMaterialType.COPPER_OXIDIZED;
			};
		}
        if (christmas) return ChestRenderState.ChestMaterialType.CHRISTMAS;
		return entity instanceof TrappedChestBlockEntity ? ChestRenderState.ChestMaterialType.TRAPPED : ChestRenderState.ChestMaterialType.REGULAR;
	}

	@Override
	public float open(ChestInstancing.Entry entry, float partialTick) {
		// As ChestRenderer.submit eases it.
		float open = 1f - openness((BlockEntity) entry.owner, partialTick);
		return 1f - open * open * open;
	}

	@Override
	public boolean shut(ChestInstancing.Entry entry) {
		return shut((BlockEntity) entry.owner);
	}

	@Override
	public void dropped(ChestInstancing.Entry entry) {
		((Slot) entry.owner).polarium$gpu(null);
	}

	// ---- on the GPU ----

	/** Models, textures and the pipeline, made after a reload; the pipeline is checked every frame (a cleared cache compiles it again from here). */
	private void prepare(Minecraft minecraft) throws ReflectiveOperationException {
		if (!ready) {
			build(minecraft);
			ready = true;
		}
		if (!RenderSystem.getDevice().precompilePipeline(pipeline).isValid()) {
			throw new IllegalStateException("the GPU couldn't compile the instanced chest shader");
		}
	}

	private void build(Minecraft minecraft) throws ReflectiveOperationException {
		for (int kind = 0; kind < ChestInstancing.KINDS; kind++) {
			if (meshes[kind] != null) {
				meshes[kind].close();
				meshes[kind] = null;
			}
		}
		christmas = ChestRenderer.xmasTextures();
		for (int kind = 0; kind < ChestInstancing.KINDS; kind++) {
			ModelPart root = minecraft.getEntityModels().bakeLayer((kind == 0 ? ModelLayers.CHEST : kind == 1 ? ModelLayers.DOUBLE_CHEST_LEFT : ModelLayers.DOUBLE_CHEST_RIGHT));
			mesh(kind, root);
		}
		if (MATERIALS.length * ChestInstancing.KINDS > ChestInstancing.SPRITES) {
			throw new IllegalStateException("more chest textures than the table holds");
		}
		RenderType type = null;
		for (ChestRenderState.ChestMaterialType material : MATERIALS) {
			for (int kind = 0; kind < ChestInstancing.KINDS; kind++) {
				//#if MC >= 26.1
                SpriteId id = Sheets.chooseSprite(material, TYPES[kind]);
                //#else
                Material id = Sheets.chooseMaterial(material, TYPES[kind]);
                //#endif
				RenderType spriteType = id.renderType(RenderTypes::entityCutout);
				if (type == null) {
					type = spriteType;
				} else if (type != spriteType) {
					throw new IllegalStateException("chest textures aren't in one atlas");
				}
				TextureAtlasSprite sprite = minecraft.getAtlasManager().get(id);
				chests.sprite(material.ordinal() * ChestInstancing.KINDS + kind, sprite.getU0(), sprite.getV0(), sprite.getU1() - sprite.getU0(),
						sprite.getV1() - sprite.getV0());
			}
		}
		RenderPipeline original = type.pipeline();
        if (!ENTITY_SHADER.equals(original.getVertexShader()) || !
                //#if MC >= 26.1
                DefaultVertexFormat.ENTITY
                //#else
                DefaultVertexFormat.NEW_ENTITY
                //#endif
                .equals(original.getVertexFormat())
                || original.getVertexFormatMode() != VertexFormat.Mode.QUADS) throw new IllegalStateException("unknown chest pipeline");
        setup = ((com.arcticlauncher.polarium.mixin.ChestRenderTypeAccess)(Object)type).polarium$setup();
        pipeline = OlderChestPipeline.twin(original, SHADER, FORMAT);

		chests.markAllDirty();
		LOG.info("Polarium: chests are drawn on the GPU ({} + {} + {} vertices a model)", meshVertices[0], meshVertices[1], meshVertices[2]);
	}

	/** A chest model's faces, lid shut, in the block's space; each vertex says whether it moves with the lid. */
	private void mesh(int kind, ModelPart root) {
		ModelPart lid = root.getChild("lid");
		ModelPart lock = root.getChild("lock");
		if (lid.x != lock.x || lid.y != lock.y || lid.z != lock.z) {
			throw new IllegalStateException("the chest's lid and lock don't share a hinge");
		}
		if (kind == 0) {
			hinge.set(lid.x / 16f, lid.y / 16f, lid.z / 16f);
		} else if (hinge.x != lid.x / 16f || hinge.y != lid.y / 16f || hinge.z != lid.z / 16f) {
			throw new IllegalStateException("the chest models' hinges differ");
		}
		List<float[]> vertices = new ArrayList<>();
		root.visit(new PoseStack(), (pose, path, index, cube) -> {
			float moves = path.endsWith("/lid") || path.endsWith("/lock") ? 1f : 0f;
			for (ModelPart.Polygon polygon : cube.polygons) {
				Vector3f normal = pose.transformNormal(polygon.normal().x(), polygon.normal().y(), polygon.normal().z(), new Vector3f());
				for (ModelPart.Vertex vertex : polygon.vertices()) {
					Vector3f at = pose.pose().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f());
					vertices.add(new float[] {at.x, at.y, at.z, vertex.u(), vertex.v(), moves, normal.x, normal.y, normal.z});
				}
			}
		});
		if (vertices.isEmpty() || vertices.size() % 4 != 0) {
			throw new IllegalStateException("the chest model has no faces");
		}
		ByteBuffer data = MemoryUtil.memAlloc(vertices.size() * FORMAT.getVertexSize()).order(ByteOrder.nativeOrder());
		try {
			for (float[] v : vertices) {
				data.putFloat(v[0]).putFloat(v[1]).putFloat(v[2]);
				data.putFloat(v[3]).putFloat(v[4]);
				data.putShort((short) v[5]).putShort((short) 0);
				data.put((byte)Math.round(v[6]*127)).put((byte)Math.round(v[7]*127)).put((byte)Math.round(v[8]*127)).put((byte)0);
			}
			data.flip();
			meshes[kind] = RenderSystem.getDevice().createBuffer(() -> "Polarium chest model", GpuBuffer.USAGE_VERTEX, data);
			meshVertices[kind] = vertices.size();
		} finally {
			MemoryUtil.memFree(data);
		}
	}

    private void drawFrame() {
        ChestInstancing.Frame f = frame;
        if (f == null || f.total() == 0) return;
        var device = RenderSystem.getDevice();
        int alignment = device.getUniformOffsetAlignment();
        int stride = (BATCH_BYTES + alignment - 1) / alignment * alignment;
        int batches = ChestUniformBatches.count(f);
        ByteBuffer data = null;
        ByteBuffer draw = MemoryUtil.memCalloc(Math.max(alignment, DRAW_BYTES) * batches).order(ByteOrder.nativeOrder());
        GpuBuffer draws = null;
        try {
            if (f.changed || cachedInstances == null) {
                data = MemoryUtil.memCalloc(stride * batches).order(ByteOrder.nativeOrder());
                ChestUniformBatches.pack(data, f, stride);
                GpuBuffer next = device.createBuffer(() -> "Polarium chest batches", GpuBuffer.USAGE_UNIFORM, data);
                if (cachedInstances != null) cachedInstances.close();
                cachedInstances = next;
            }
            GpuBuffer instances = cachedInstances;
            int slot = 0;
            int drawStride = Math.max(alignment, DRAW_BYTES);
            for (int kind = 0; kind < ChestInstancing.KINDS; kind++) {
                for (int offset = 0; offset < f.count[kind]; offset += BATCH) {
                    int at = slot * drawStride;
                    draw.putInt(at, ChestInstancing.SPRITES);
                    draw.putFloat(at+16, f.originX).putFloat(at+20, f.originY).putFloat(at+24, f.originZ)
                        .putFloat(at+28, (float)Math.pow(BlockEntityCulling.distance(BlockEntityOptions.get()), 2));
                    draw.putFloat(at+32, f.fracX).putFloat(at+36, f.fracY).putFloat(at+40, f.fracZ);
                    draw.putFloat(at+48, hinge.x).putFloat(at+52, hinge.y).putFloat(at+56, hinge.z);
                    slot++;
                }
            }
            draw.clear();
            draws = device.createBuffer(() -> "Polarium chest draws", GpuBuffer.USAGE_UNIFORM, draw);
            var transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(),
                new org.joml.Vector4f(1f), new Vector3f(), ((com.arcticlauncher.polarium.mixin.ChestRenderSetupAccess)(Object)setup).polarium$textureTransform().getMatrix());
            var textures = setup.getTextures();
            var target = ((com.arcticlauncher.polarium.mixin.ChestRenderSetupAccess)(Object)setup).polarium$outputTarget().getRenderTarget();
            var color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
            var depth = target.useDepth ? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView()) : null;
            var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            var indexBuffer = indices.getBuffer(108);
            if (Boolean.getBoolean("polarium.gpuChests.failDraw")) throw new IllegalStateException("injected chest draw failure");
            try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Polarium instanced chests", color,
                    java.util.OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                for (var texture : textures.entrySet()) pass.bindTexture(texture.getKey(), texture.getValue().textureView(), texture.getValue().sampler());
                pass.setIndexBuffer(indexBuffer, indices.type());
                slot = 0;
                for (int kind = 0; kind < ChestInstancing.KINDS; kind++) {
                    for (int offset = 0; offset < f.count[kind]; offset += BATCH) {
                        pass.setUniform("PolariumChests", instances.slice((long)slot * stride, BATCH_BYTES));
                        pass.setUniform("PolariumChestDraw", draws.slice((long)slot * drawStride, DRAW_BYTES));
                        pass.setVertexBuffer(0, meshes[kind]);
                        pass.drawIndexed(0, 0, meshVertices[kind] / 4 * 6, Math.min(BATCH, f.count[kind] - offset));
                        slot++;
                    }
                }
            }
            if (Boolean.getBoolean("polarium.gpuChests.report") && System.nanoTime() - lastReport > 10_000_000_000L) {
                lastReport = System.nanoTime();
                LOG.info("Polarium: {} chests in {} GPU draws this frame", f.total(), batches);
            }
        } finally {
            if (draws != null) draws.close();
            if (data != null) MemoryUtil.memFree(data);
            MemoryUtil.memFree(draw);
        }
    }
}
//#endif
