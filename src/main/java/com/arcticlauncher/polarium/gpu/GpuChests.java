// i18n-ignore-file: shader names, GPU labels and log lines; nothing here is shown to the player.
package com.arcticlauncher.polarium.gpu;

//#if MC >= 26.2
import com.arcticlauncher.polarium.BlockEntityOptions;

import com.arcticlauncher.polarium.BlockEntityCulling;
import com.arcticlauncher.polarium.ChestInstancing;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;


import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BrightnessCombiner;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
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
 * Chests drawn on the GPU (26.2+): the game's chests, trapped chests, ender chests and copper chests,
 * single and double, as three instanced draws a frame instead of a renderer call, a render state and
 * 72 vertices each.
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
 * chest at the block entity dispatcher, before its render state is made. Stays off with shader mods,
 * mods that change chest models or textures, portal mods, or when another mod has replaced the chest
 * renderer, and switches itself off if anything fails. Render thread.
 */
public final class GpuChests implements ChestInstancing.World {
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
	private static final VertexFormat FORMAT = ModelMesh.FORMAT;
	private static final BindGroupLayout LAYOUT = BindGroupLayout.builder()
			.withUniform("PolariumChestDraw", UniformType.UNIFORM_BUFFER)
			.withUniform("PolariumChests", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
			.build();
	private static final ChestType[] TYPES = {ChestType.SINGLE, ChestType.LEFT, ChestType.RIGHT};
	private static final ChestRenderState.ChestMaterialType[] MATERIALS = ChestRenderState.ChestMaterialType.values();
	/** Bytes of the per-draw uniform block (four vec4). */
	private static final int DRAW_BYTES = 64;
	private static final int RING = 3;


	private static final GpuChests INSTANCE = new GpuChests();

	private final ChestInstancing chests = new ChestInstancing();
	private boolean failed;
    private final List<BlockEntity> captured = new ArrayList<>();
    private boolean recoverFrame;
	/** Inside the world's extraction, with the path on. */
	private boolean inFrame;
	private boolean frameBegun;
	private float partialTick;
	private ClientLevel level;
	private ChestInstancing.Frame frame;
	/** A frame's instances are ready; the world's main pass hasn't been set up yet. */
	private boolean pending;
	/** The world's main pass is set up: draw at its solid features. */
	private boolean armed;

	// On the GPU; made again after every resource reload.
	private boolean ready;
	private final GpuBuffer[] meshes = new GpuBuffer[ChestInstancing.KINDS];
	private final int[] meshVertices = new int[ChestInstancing.KINDS];
	private final Vector3f hinge = new Vector3f();
	private RenderType renderType;
	private RenderPipeline pipeline;
	private boolean christmas;
	private final GpuBuffer[] instanceBuffers = new GpuBuffer[RING];
	private final GpuBuffer[] drawBuffers = new GpuBuffer[RING];
	private int instanceSlot;
	private int drawSlot;
	private final FrameFences instanceFences = new FrameFences(RING);
	private final FrameFences drawFences = new FrameFences(RING);
	/** The frame's instances aren't on the GPU yet. */
	private boolean needUpload = true;
	private long lastReport;


	private GpuChests() {}

	private boolean on() {
		BlockEntityOptions config = BlockEntityOptions.get();
		return !OFF && BlockEntityOptions.compatible() && !failed && !legacyDriver && config != null && config.chestInstancing;
	}

	/** The driver can't map buffers persistently (no GL 4.4 buffer storage): an old driver, left to the game's renderer. */
	private boolean legacyDriver;

	private boolean driverSupported() {
		if (Boolean.getBoolean("polarium.gpuChests.anyDriver")) {
			return true;
		}
		var info = RenderSystem.getDevice().getDeviceInfo();
		if (info.features().persistentMapping()) {
			return true;
		}
		legacyDriver = true;
		LOG.info("Polarium: {} ({}) can't map buffers persistently; chests stay on the game's renderer", info.name(), info.driverInfo());
		return false;
	}

	// ---- the frame ----

	/** The world's extraction starts. */
	public static void begin() {
		GpuChests g = INSTANCE;
		g.fenceLastFrame();
		g.captured.clear();
        g.recoverFrame = false;
        g.inFrame = false;
		g.frameBegun = false;
		g.pending = false;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != g.level) {
			// Another world: nothing kept from the last one (also when the option is off, so nothing of it stays in memory).
			g.chests.clear();
			g.level = minecraft.level;
		}
		if (g.level == null || !g.on()) {
			return;
		}
		try {
			if (!g.ready && !g.driverSupported()) {
				return;
			}
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
		GpuChests g = INSTANCE;
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
			Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
			g.chests.beginFrame(camera.x, camera.y, camera.z, BlockEntityCulling.distance(BlockEntityOptions.get()));
		}
		boolean accepted = g.chests.see(entry, breaking != null);
        if (accepted) g.captured.add(entity);
        return accepted;
	}

	/** The world's extraction is over: lay the frame's chests out. */
	public static void end() {
		GpuChests g = INSTANCE;
		if (!g.inFrame) {
			return;
		}
		g.inFrame = false;
		if (!g.frameBegun) {
			return;
		}
		try {
			g.frame = g.chests.endFrame(g, g.partialTick);
			g.needUpload |= g.frame.changed;
			g.pending = g.frame.total() > 0;
		} catch (RuntimeException | LinkageError e) {
			g.fail("couldn't lay out a frame", e);
		}
	}

	/** The world's main pass is being set up (it runs after this, once). */
	public static void mainPass() {
		GpuChests g = INSTANCE;
		g.armed = g.pending;
		g.pending = false;
	}

	/** The world's solid features are about to be drawn, after the solid terrain: the chests go here. */
	//#if MC >= 26.3
	public static void draw(RenderPass pass) {
	//#else
	public static void draw() {
	//#endif
		GpuChests g = INSTANCE;
		if (!g.armed) {
			return;
		}
		g.armed = false;
		try {
			//#if MC >= 26.3
			if (Boolean.getBoolean("polarium.gpuChests.failDraw")) throw new IllegalStateException("injected chest draw failure");
            g.drawFrame(pass);
			//#else
			g.uploadFrame();
            if (Boolean.getBoolean("polarium.gpuChests.failDraw")) throw new IllegalStateException("injected chest draw failure");
			g.drawFrame();
			//#endif
		} catch (RuntimeException | LinkageError e) {
			g.fail("couldn't draw", e);
		}
	}

	private void fail(String what, Throwable e) {
        recoverFrame = !captured.isEmpty();
		if (!failed) {
			failed = true;
			LOG.error("Polarium: {} chests on the GPU; they are drawn the game's way from now on", what, e);
		}
		inFrame = false;
		pending = false;
		armed = false;
	}

    /** Called after the normal feature frame has closed, with no render pass open.
     * Only the exceptional frame pays for vanilla extraction and model preparation.
     */
    public static void recover(net.minecraft.client.renderer.feature.FeatureRenderDispatcher features,
            net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        GpuChests g = INSTANCE;
        if (!g.recoverFrame) return;
        g.recoverFrame = false;
        var minecraft = Minecraft.getInstance();
        var dispatcher = minecraft.getBlockEntityRenderDispatcher();
        var storage = new net.minecraft.client.renderer.SubmitNodeStorage();
        var poses = new PoseStack();
        var view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.mul(camera.viewRotationMatrix);
        try {
            for (BlockEntity entity : g.captured) {
                var state = dispatcher.tryExtractRenderState(entity, g.partialTick, null, false);
                if (state == null) continue;
                var pos = entity.getBlockPos();
                poses.pushPose();
                poses.translate(pos.getX() - camera.pos.x, pos.getY() - camera.pos.y, pos.getZ() - camera.pos.z);
                dispatcher.submit(state, poses, storage, camera);
                poses.popPose();
            }
            var backup = features.prepareFrame(storage);
            try {
                //#if MC >= 26.3
                var target = minecraft.gameRenderer.mainRenderTarget();
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Polarium vanilla chest recovery", target.getColorTextureView(), Optional.empty(),
                        target.getDepthTextureView(), OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    backup.executeSolid(pass);
                }
                //#else
                backup.executeSolid();
                //#endif
            } finally { backup.close(); }
            LOG.info("Polarium: recovered {} captured chests through vanilla in the failed frame", g.captured.size());
        } finally { view.popMatrix(); g.captured.clear(); }
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
		int light = LightCoordsUtil.getLightCoords(level, pos);
		if (type != ChestType.SINGLE && state.getBlock() instanceof ChestBlock chest) {
			light = chest.combine(state, level, pos, true).apply(new BrightnessCombiner<>()).applyAsInt(light);
		}
		entry.lightU = light & 0xFFFF;
		entry.lightV = light >>> 16;
		return true;
	}

	/** As ChestRenderer.getChestMaterial. */
	private ChestRenderState.ChestMaterialType material(BlockEntity entity, BlockState state) {
		if (state.getBlock() instanceof CopperChestBlock copper) {
			return switch (copper.getState()) {
				case UNAFFECTED -> ChestRenderState.ChestMaterialType.COPPER_UNAFFECTED;
				case EXPOSED -> ChestRenderState.ChestMaterialType.COPPER_EXPOSED;
				case WEATHERED -> ChestRenderState.ChestMaterialType.COPPER_WEATHERED;
				case OXIDIZED -> ChestRenderState.ChestMaterialType.COPPER_OXIDIZED;
			};
		}
		if (entity instanceof EnderChestBlockEntity) {
			return ChestRenderState.ChestMaterialType.ENDER_CHEST;
		}
		if (christmas) {
			return ChestRenderState.ChestMaterialType.CHRISTMAS;
		}
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
		if (!InstancedPipelines.compiles(pipeline)) {
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
		ChestModel model = null;
		for (int kind = 0; kind < ChestInstancing.KINDS; kind++) {
			ModelPart root = minecraft.getEntityModels().bakeLayer(ChestRenderer.LAYERS.select(TYPES[kind]));
			model = new ChestModel(root);
			mesh(kind, root);
		}
		if (MATERIALS.length * ChestInstancing.KINDS > ChestInstancing.SPRITES) {
			throw new IllegalStateException("more chest textures than the table holds");
		}
		RenderType type = null;
		for (ChestRenderState.ChestMaterialType material : MATERIALS) {
			for (int kind = 0; kind < ChestInstancing.KINDS; kind++) {
				SpriteId id = Sheets.chooseSprite(material, TYPES[kind]);
				RenderType spriteType = id.renderType(model.renderType());
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
		if (!ENTITY_SHADER.equals(InstancedPipelines.vertexShader(original)) || original.getPrimitiveTopology() != PrimitiveTopology.QUADS
				|| !DefaultVertexFormat.ENTITY.equals(original.getVertexFormatBinding(0)) || type.hasBlending()) {
			throw new IllegalStateException("the chest render type isn't the game's own: " + type);
		}
		renderType = type;
		pipeline = InstancedPipelines.twin(original, SHADER, FORMAT, LAYOUT);
		chests.markAllDirty();
		needUpload = true;
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
				data.putFloat(v[6]).putFloat(v[7]).putFloat(v[8]);
			}
			data.flip();
			meshes[kind] = RenderSystem.getDevice().createBuffer(() -> "Polarium chest model", GpuBuffer.USAGE_VERTEX, data);
			meshVertices[kind] = vertices.size();
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	/** Upload before 26.3 opens its render pass, as the entity path does. */
	public static void prepareDraws() {
		GpuChests g = INSTANCE;
		if (g.pending) {
			try { g.uploadFrame(); } catch (RuntimeException | LinkageError e) { g.fail("couldn't upload", e); }
		}
	}

	private PreparedRenderType prepared;
	private int alignment;
	/** The ring slots last frame's draws read, still to be fenced (-1: none). */
	private int drewInstances = -1;
	private int drewDraws = -1;

	/** Fences what last frame drew from, now that its passes are closed and sent. */
	private void fenceLastFrame() {
		if (drewInstances >= 0) {
			instanceFences.mark(drewInstances);
			drewInstances = -1;
		}
		if (drewDraws >= 0) {
			drawFences.mark(drewDraws);
			drewDraws = -1;
		}
	}

	/**
	 * The frame's instances and per-draw data into buffers written while mapped (persistently where the
	 * driver can), never with glBufferSubData: a big SubData into a buffer the GPU may still read crashed
	 * Mesa's no-op driver and hung an old AMD driver. Each slot of the ring is fenced; a slot the GPU
	 * isn't done with gets a fresh buffer instead of a wait.
	 */
	private void uploadFrame() {
		ChestInstancing.Frame f = frame;
		if (f == null || f.total() == 0 || !ready) {
			return;
		}
		GpuDevice device = RenderSystem.getDevice();
		if (needUpload || instanceBuffers[instanceSlot] == null) {
			// A buffer the GPU may still be drawing last frame from is left alone: the next one in the ring is written.
			instanceSlot = (instanceSlot + 1) % RING;
			if (!instanceFences.await(instanceSlot)) {
				instanceBuffers[instanceSlot] = GpuBatches.closed(instanceBuffers[instanceSlot]);
			}
			long bytes = (long) f.floats * 4;
			instanceBuffers[instanceSlot] = ensure(device, instanceBuffers[instanceSlot], bytes,
					GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_MAP_WRITE, "Polarium chest instances");
			try (GpuBufferSlice.MappedView view = instanceBuffers[instanceSlot].slice(0, bytes).map(false, true)) {
				view.data().order(ByteOrder.nativeOrder()).asFloatBuffer().put(0, f.data, 0, f.floats);
			}
			needUpload = false;
		}

		alignment = Math.max(DRAW_BYTES, device.getDeviceInfo().limits().minUniformOffsetAlignment());
		alignment = (alignment + 15) / 16 * 16;
		int drawBytes = alignment * ChestInstancing.KINDS;
		drawSlot = (drawSlot + 1) % RING;
		if (!drawFences.await(drawSlot)) {
			drawBuffers[drawSlot] = GpuBatches.closed(drawBuffers[drawSlot]);
		}
		drawBuffers[drawSlot] = ensure(device, drawBuffers[drawSlot], drawBytes, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
				"Polarium chest draws");
		float distance = BlockEntityCulling.distance(BlockEntityOptions.get());
		try (GpuBufferSlice.MappedView view = drawBuffers[drawSlot].slice(0, drawBytes).map(false, true)) {
			ByteBuffer draw = view.data().order(ByteOrder.nativeOrder());
			for (int kind = 0; kind < ChestInstancing.KINDS; kind++) {
				int at = kind * alignment;
				draw.putInt(at, f.first[kind]).putInt(at + 4, 0).putInt(at + 8, 0).putInt(at + 12, 0);
				draw.putFloat(at + 16, f.originX).putFloat(at + 20, f.originY).putFloat(at + 24, f.originZ).putFloat(at + 28, distance * distance);
				draw.putFloat(at + 32, f.fracX).putFloat(at + 36, f.fracY).putFloat(at + 40, f.fracZ).putFloat(at + 44, 0f);
				draw.putFloat(at + 48, hinge.x).putFloat(at + 52, hinge.y).putFloat(at + 56, hinge.z).putFloat(at + 60, 0f);
			}
		}

		prepared = renderType.prepare();
		RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(108);
	}

	//#if MC >= 26.3
	private void drawFrame(RenderPass pass) {
	//#else
	private void drawFrame() {
	//#endif
		ChestInstancing.Frame f = frame;
		if (f == null || f.total() == 0 || prepared == null) return;
		GpuBuffer instances = instanceBuffers[instanceSlot];
		PreparedRenderType type = prepared;
		//#if MC < 26.3
		GpuDevice device = RenderSystem.getDevice();
		RenderTarget target = type.outputTarget().getRenderTarget();
		GpuTextureView color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
		GpuTextureView depth = target.useDepth
				? (RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView())
				: null;
		//#endif
		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		int draws = 0;
		//#if MC < 26.3
		try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Polarium instanced chests", color, Optional.empty(), depth,
				OptionalDouble.empty())) {
		//#endif
			//#if MC >= 26.3
			pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			//#else
			pass.setPipeline(pipeline);
			//#endif
			if (type.scissorState().enabled()) {
				pass.enableScissor(type.scissorState().x(), type.scissorState().y(), type.scissorState().width(), type.scissorState().height());
			}
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", type.dynamicTransforms());
			pass.setUniform("PolariumChests", instances);
			for (PreparedRenderType.Texture texture : type.textures()) {
				//#if MC >= 26.3
				pass.setUniform(texture.name(), texture.textureView(), texture.sampler());
				//#else
				pass.bindTexture(texture.name(), texture.textureView(), texture.sampler());
				//#endif
			}
			for (int kind = 0; kind < ChestInstancing.KINDS; kind++) {
				if (f.count[kind] == 0) {
					continue;
				}
				int indexCount = meshVertices[kind] / 4 * 6;
				pass.setUniform("PolariumChestDraw", drawBuffers[drawSlot].slice((long) kind * alignment, DRAW_BYTES));
				pass.setVertexBuffer(0, meshes[kind].slice());
				pass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
				pass.drawIndexed(indexCount, f.count[kind], 0, 0, 0);
				draws++;
			}
		//#if MC < 26.3
		}
		//#endif
		// Fenced at the next frame's start: on 26.3 this runs inside the game's render pass, where no fence can be made.
		drewInstances = instanceSlot;
		drewDraws = drawSlot;
		report(f, draws);
	}

	/** Every ten seconds with -Dpolarium.gpuChests.report=true: how many chests took how many draws. */
	private void report(ChestInstancing.Frame f, int draws) {
		long now = System.nanoTime();
		if (now - lastReport > 10_000_000_000L && Boolean.getBoolean("polarium.gpuChests.report")) {
			lastReport = now;
			LOG.info("Polarium: {} chests in {} GPU draws this frame ({} kept, {} sections, {} uploads and {} section bakes since the last line)",
					f.total(), draws, chests.size(), chests.sectionCount(), chests.uploads, chests.bakes);
			chests.resetCounters();
		}
	}

	private static GpuBuffer ensure(GpuDevice device, GpuBuffer buffer, long size, int usage, String label) {
		if (buffer != null && !buffer.isClosed() && buffer.size() >= size) {
			return buffer;
		}
		if (buffer != null) {
			buffer.close();
		}
		return device.createBuffer(() -> label, usage, Math.max(size + size / 2, 16 * 1024));
	}
}
//#endif
