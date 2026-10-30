package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.EntitySections;
import com.arcticlauncher.polarium.legacy.Flags;
import com.arcticlauncher.polarium.legacy.OpenFacesCache;
import com.arcticlauncher.polarium.legacy.Phases;
import com.arcticlauncher.polarium.legacy.RegionBatches;
import com.arcticlauncher.polarium.legacy.TerrainWalk;
import com.google.common.collect.Sets;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BaseFrustum;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.world.AbstractChunkRenderManager;
import net.minecraft.client.render.world.ListedChunkRenderManager;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.DiffuseLighting;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.util.vector.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The terrain walk without garbage. The game builds a record, a set and a queue node for every chunk
 * section it can see, every frame the camera moved or turned; this walks the same sections in the same
 * order (see {@link TerrainWalk}) and hands the game its own record objects, made once per section and
 * used again. Everything around the walk is the game's code, copied as it is.
 *
 * <p>Until the game has made one record of its own (the first frame with sections), its code runs, and
 * the record's class and constructor are taken from that.
 */
@Mixin(WorldRenderer.class)
abstract class WorldRendererTerrainMixin {
	@Shadow
	private MinecraftClient client;
	@Shadow
	private ClientWorld world;
	@Shadow
	private int renderDistance;
	@Shadow
	private BuiltChunkStorage chunks;
	@Shadow
	private double lastCameraChunkUpdateX;
	@Shadow
	private double lastCameraChunkUpdateY;
	@Shadow
	private double lastCameraChunkUpdateZ;
	@Shadow
	private int cameraChunkX;
	@Shadow
	private int cameraChunkY;
	@Shadow
	private int cameraChunkZ;
	@Shadow
	private double lastCameraX;
	@Shadow
	private double lastCameraY;
	@Shadow
	private double lastCameraZ;
	@Shadow
	private double lastCameraPitch;
	@Shadow
	private double lastCameraYaw;
	@Shadow
	private AbstractChunkRenderManager chunkRenderManager;
	@Shadow
	private BaseFrustum capturedFrustum;
	@Shadow
	private boolean field_10813;
	@Shadow
	private boolean needsTerrainUpdate;
	@Shadow
	private List<Object> visibleChunks;
	@Shadow
	private Set<BuiltChunk> chunksToRebuild;
	@Shadow
	private ChunkBuilder chunkBuilder;
	@Shadow
	private double lastTranslucentSortX;
	@Shadow
	private double lastTranslucentSortY;
	@Shadow
	private double lastTranslucentSortZ;

	@Unique
	private final TerrainWalk polarium$walk = new TerrainWalk();
	@Unique
	private Constructor<?> polarium$recordConstructor;
	@Unique
	private boolean polarium$recordsFailed;
	@Unique
	private Object[] polarium$records = new Object[0];
	@Unique
	private BuiltChunkStorage polarium$recordsOf;
	@Unique
	private boolean polarium$ownList;
	@Unique
	private int polarium$verified;
	@Unique
	private final OpenFacesCache polarium$openFaces = new OpenFacesCache();
	@Unique
	private final EntitySections polarium$entitySections = new EntitySections();

	@Shadow
	public abstract void reload();

	@Shadow
	protected abstract Set<Direction> getOpenChunkFaces(BlockPos pos);

	@Shadow
	protected abstract boolean isInChunk(BlockPos pos, BuiltChunk chunk);

	@Shadow
	protected abstract void captureFrustum(double x, double y, double z);

	@Shadow
	protected abstract Vector3f getFacing(Entity entity, double tickDelta);

	/** The first record the game makes tells which class and constructor to use. */
	@Inject(method = "setupTerrain", at = @At("RETURN"))
	private void polarium$learnRecord(CallbackInfo ci) {
		if (polarium$recordConstructor != null || polarium$recordsFailed || visibleChunks.isEmpty()) {
			return;
		}
		try {
			Class<?> type = visibleChunks.get(0).getClass();
			for (Constructor<?> c : type.getDeclaredConstructors()) {
				if (!c.isSynthetic() && c.getParameterTypes().length == 4) {
					c.setAccessible(true);
					polarium$recordConstructor = c;
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		if (polarium$recordConstructor == null) {
			polarium$recordsFailed = true;
			System.out.println("[Polarium] terrain walk off: the game's section record has an unexpected shape");
		}
	}

	@Inject(method = "setupTerrain", at = @At("HEAD"), cancellable = true)
	private void polarium$setupTerrain(Entity entity, double tickDelta, CameraView cameraView, int frame, boolean spectator, CallbackInfo ci) {
		if (!Flags.TERRAIN || polarium$recordConstructor == null) {
			return;
		}
		ci.cancel();
		if (client.options.viewDistance != renderDistance) {
			reload();
		}

		world.profiler.push("camera");
		double d = entity.x - lastCameraChunkUpdateX;
		double e = entity.y - lastCameraChunkUpdateY;
		double f = entity.z - lastCameraChunkUpdateZ;
		if (cameraChunkX != entity.chunkX || cameraChunkY != entity.chunkY || cameraChunkZ != entity.chunkZ || d * d + e * e + f * f > 16.0) {
			lastCameraChunkUpdateX = entity.x;
			lastCameraChunkUpdateY = entity.y;
			lastCameraChunkUpdateZ = entity.z;
			cameraChunkX = entity.chunkX;
			cameraChunkY = entity.chunkY;
			cameraChunkZ = entity.chunkZ;
			chunks.updateCameraPosition(entity.x, entity.z);
		}

		world.profiler.swap("renderlistcamera");
		double g = entity.prevTickX + (entity.x - entity.prevTickX) * tickDelta;
		double h = entity.prevTickY + (entity.y - entity.prevTickY) * tickDelta;
		double i = entity.prevTickZ + (entity.z - entity.prevTickZ) * tickDelta;
		chunkRenderManager.setViewPos(g, h, i);
		world.profiler.swap("cull");

		client.profiler.swap("culling");
		BlockPos blockPos = new BlockPos(g, h + entity.getEyeHeight(), i);
		BuiltChunk builtChunk = polarium$chunkAt(blockPos);
		BlockPos blockPos2 = new BlockPos(MathHelper.floor(g / 16.0) * 16, MathHelper.floor(h / 16.0) * 16, MathHelper.floor(i / 16.0) * 16);
		needsTerrainUpdate = needsTerrainUpdate
				|| !chunksToRebuild.isEmpty()
				|| entity.x != lastCameraX
				|| entity.y != lastCameraY
				|| entity.z != lastCameraZ
				|| entity.pitch != lastCameraPitch
				|| entity.yaw != lastCameraYaw;
		lastCameraX = entity.x;
		lastCameraY = entity.y;
		lastCameraZ = entity.z;
		lastCameraPitch = entity.pitch;
		lastCameraYaw = entity.yaw;
		boolean captured = capturedFrustum != null;
		if (chunks != polarium$recordsOf || !polarium$ownList) {
			polarium$recordsOf = chunks;
			polarium$records = new Object[chunks.chunks.length];
			needsTerrainUpdate = true;
		}
		if (!captured && needsTerrainUpdate) {
			needsTerrainUpdate = false;
			polarium$walk(entity, tickDelta, cameraView, frame, spectator, blockPos, blockPos2, builtChunk);
		}

		if (field_10813) {
			captureFrustum(g, h, i);
			field_10813 = false;
		}

		chunkBuilder.clear();
		Set<BuiltChunk> pending = chunksToRebuild;
		chunksToRebuild = Sets.newLinkedHashSet();
		TerrainWalk walk = polarium$walk;
		for (int n = 0; n < walk.visibleCount; n++) {
			BuiltChunk chunk = walk.visible[n];
			if (chunk.method_10173() || pending.contains(chunk)) {
				needsTerrainUpdate = true;
				if (isInChunk(blockPos2, chunk)) {
					client.profiler.push("build near");
					chunkBuilder.upload(chunk);
					chunk.method_10162(false);
					client.profiler.pop();
				} else {
					chunksToRebuild.add(chunk);
				}
			}
		}

		chunksToRebuild.addAll(pending);
		client.profiler.pop();
	}

	/** Any block, light or chunk-data change the game tells the renderer about: the camera section's open faces may differ. */
	@Inject(method = "updateBlock", at = @At("HEAD"))
	private void polarium$blocksChanged(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
		polarium$openFaces.blocksChanged();
	}

	@Unique
	private Set<Direction> polarium$openFaces(BlockPos blockPos) {
		if (!Flags.OPEN_FACES) {
			return getOpenChunkFaces(blockPos);
		}
		Object chunk = world.getChunk(blockPos);
		Set<Direction> cached = polarium$openFaces.get(blockPos, chunk);
		if (cached != null) {
			return cached;
		}
		Set<Direction> fresh = getOpenChunkFaces(blockPos);
		polarium$openFaces.put(blockPos, chunk, fresh);
		return fresh;
	}

	@Unique
	private final RegionBatches polarium$layerBatches = new RegionBatches();

	/**
	 * One layer of terrain: the game filters the visible sections once per layer and hands the survivors on, and
	 * the render manager then reads each one again. Here the visible array is walked once and drawn region by region.
	 */
	@Inject(method = "renderLayer(Lnet/minecraft/client/render/RenderLayer;DILnet/minecraft/entity/Entity;)I", at = @At("HEAD"), cancellable = true)
	private void polarium$renderLayer(RenderLayer layer, double tickDelta, int anaglyph, Entity entity, CallbackInfoReturnable<Integer> cir) {
		if (!Flags.REGIONS || !polarium$ownList || !(chunkRenderManager instanceof ListedChunkRenderManager)) {
			return;
		}
		if (Phases.ON) {
			Phases.begin(Phases.TERRAIN_DRAW);
		}
		TerrainWalk walk = polarium$walk;
		int total = walk.visibleCount;
		DiffuseLighting.disable();
		if (layer == RenderLayer.TRANSLUCENT) {
			double dx = entity.x - lastTranslucentSortX;
			double dy = entity.y - lastTranslucentSortY;
			double dz = entity.z - lastTranslucentSortZ;
			if (dx * dx + dy * dy + dz * dz > 1.0) {
				lastTranslucentSortX = entity.x;
				lastTranslucentSortY = entity.y;
				lastTranslucentSortZ = entity.z;
				int queued = 0;
				for (int n = 0; n < total; n++) {
					BuiltChunk section = walk.visible[n];
					if (section.method_10170().isUnused(layer) && queued++ < 15) {
						chunkBuilder.method_10133(section);
					}
				}
			}
		}
		int found = 0;
		client.gameRenderer.enableLightmap();
		AbstractManagerAccess access = (AbstractManagerAccess) chunkRenderManager;
		if (access.polarium$ready()) {
			found = polarium$layerBatches.renderVisible(walk.visible, total, layer, access.polarium$viewX(), access.polarium$viewY(), access.polarium$viewZ());
			GlStateManager.clearColor();
		}
		client.gameRenderer.disableLightmap();
		if (Phases.ON) {
			Phases.end(Phases.TERRAIN_DRAW);
		}
		cir.setReturnValue(found);
	}

	/** The first pass over the visible sections (entities): only the ones that hold an entity, in the same order. */
	@Redirect(method = "renderEntities", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 0))
	private Iterator<?> polarium$entitySections(List<?> visible) {
		if (!Flags.ENTITIES || !polarium$ownList) {
			return visible.iterator();
		}
		List<Object> sections = polarium$entitySections.sectionsWithEntities(world.getLoadedEntities(), polarium$walk, visibleChunks);
		return sections == null ? visible.iterator() : sections.iterator();
	}

	@Unique
	private BuiltChunk polarium$chunkAt(BlockPos pos) {
		StorageAccess access = (StorageAccess) chunks;
		int sizeY = access.polarium$sizeY();
		int j = MathHelper.floorDiv(pos.getY(), 16);
		if (j < 0 || j >= sizeY) {
			return null;
		}
		int sizeX = access.polarium$sizeX();
		int sizeZ = access.polarium$sizeZ();
		int x = MathHelper.floorDiv(pos.getX(), 16) % sizeX;
		if (x < 0) {
			x += sizeX;
		}
		int z = MathHelper.floorDiv(pos.getZ(), 16) % sizeZ;
		if (z < 0) {
			z += sizeZ;
		}
		return chunks.chunks[(z * sizeY + j) * sizeX + x];
	}

	@Unique
	private void polarium$walk(Entity entity, double tickDelta, CameraView cameraView, int frame, boolean spectator, BlockPos blockPos,
			BlockPos blockPos2, BuiltChunk builtChunk) {
		TerrainWalk walk = polarium$walk;
		walk.begin(chunks, cameraView, frame, client.chunkCullingEnabled, blockPos2, renderDistance);
		Object reuseKey = null;
		if (builtChunk != null) {
			boolean enclosed = false;
			if (Phases.ON) {
				Phases.begin(Phases.OPEN_FACES);
			}
			Set<Direction> open = polarium$openFaces(blockPos);
			if (Phases.ON) {
				Phases.end(Phases.OPEN_FACES);
			}
			if (open.size() == 1) {
				Vector3f vector3f = getFacing(entity, tickDelta);
				Direction direction = Direction.getFacing(vector3f.x, vector3f.y, vector3f.z).getOpposite();
				open.remove(direction);
			}

			if (open.isEmpty()) {
				enclosed = true;
			}

			if (enclosed && !spectator) {
				walk.addVisible(builtChunk);
			} else {
				if (spectator && world.getBlockState(blockPos).getBlock().hasTransparency()) {
					walk.setCull(false);
				}

				walk.seed(builtChunk, walk.slotAt(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
				reuseKey = spectator ? null : builtChunk;
			}
		} else {
			int j = blockPos.getY() > 0 ? 248 : 8;
			for (int k = -renderDistance; k <= renderDistance; k++) {
				for (int l = -renderDistance; l <= renderDistance; l++) {
					int x = (k << 4) + 8;
					int z = (l << 4) + 8;
					BuiltChunk seed = polarium$chunkAt(new BlockPos(x, j, z));
					if (seed != null && walk.inFrustum(seed.field_11071)) {
						walk.seed(seed, walk.slotAt(x, j, z));
					}
				}
			}
		}

		if (Phases.ON) {
			Phases.begin(Phases.WALK);
		}
		walk.runCached(reuseKey);
		if (Phases.ON) {
			Phases.end(Phases.WALK);
		}
		if (Flags.TERRAIN_VERIFY) {
			String diff = walk.verify(-1 - frame, chunks, blockPos2, renderDistance);
			if (diff != null) {
				System.out.println("[Polarium] TERRAIN WALK MISMATCH: " + diff);
			} else if (++polarium$verified % 500 == 0) {
				System.out.println("[Polarium] terrain walk verified " + polarium$verified + " times, " + walk.extra + " extra and " + walk.missing + " missing sections so far");
			}
		}
		polarium$fillList();
	}

	/** The game's list of visible sections, filled with the records made once per section. */
	@Unique
	private void polarium$fillList() {
		TerrainWalk walk = polarium$walk;
		List<Object> list = visibleChunks;
		list.clear();
		polarium$entitySections.begin(polarium$records.length);
		try {
			for (int n = 0; n < walk.visibleCount; n++) {
				BuiltChunk chunk = walk.visible[n];
				int index = TerrainWalk.indexOf(chunk);
				polarium$entitySections.record(index, n);
				Object record = polarium$records[index];
				if (record == null) {
					record = polarium$recordConstructor.newInstance(this, chunk, null, 0);
					polarium$records[index] = record;
				}
				list.add(record);
			}
			polarium$ownList = true;
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("Polarium: cannot make a section record", ex);
		}
	}
}
