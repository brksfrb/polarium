//#if MC >= 26.2
package com.arcticlauncher.polarium;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.EmptyModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;

/**
 * What a player's render state is made from that only changes on a tick (or
 * when the server says so, which LightStates watches separately), as one
 * number: its pose and flags, hurt and death, freezing, water, item use,
 * arrows and stingers, arms, equipment, skin and shown skin parts, size,
 * team and name, vehicle. While it stays the same from one tick to the
 * next, so does the state the game would make, but for what's brought up to
 * every frame anyway; so the kept state carries on (see LightStates).
 *
 * A held or worn item whose model can depend on time or its holder (any
 * model choosing between others: compass, clock, a resource pack's range
 * dispatch) makes it {@link #NEVER}: made in full every tick.
 */
public final class TickInputs {
	/** Never the same as anything (not even itself). */
	public static final long NEVER = Long.MIN_VALUE;
	private static final Map<ItemModel, Boolean> FIXED = new ConcurrentHashMap<>();
	private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();
	private static final PlayerModelPart[] MODEL_PARTS = PlayerModelPart.values();

	private TickInputs() {}

	/** The inputs as one number, or {@link #NEVER}. Safe on any thread while states are made. */
	public static long of(Avatar entity) {
		long h = 0x6A09E667F3BCC908L;
		h = mix(h, entity.getPose().ordinal());
		int flags = (entity.isOnFire() ? 1 : 0) | (entity.displayFireAnimation() ? 2 : 0) | (entity.isShiftKeyDown() ? 4 : 0)
				| (entity.isSprinting() ? 8 : 0) | (entity.isSwimming() ? 16 : 0) | (entity.isVisuallySwimming() ? 32 : 0)
				| (entity.isInvisible() ? 64 : 0) | (entity.isCurrentlyGlowing() ? 128 : 0) | (entity.isFallFlying() ? 256 : 0)
				| (entity.isInWater() ? 512 : 0) | (entity.isUsingItem() ? 1024 : 0) | (entity.isAutoSpinAttack() ? 2048 : 0)
				| (entity.isFullyFrozen() ? 4096 : 0) | (entity.isBaby() ? 8192 : 0) | (entity.isAlive() ? 16384 : 0)
				| (entity.hurtTime > 0 ? 32768 : 0) | (entity.deathTime > 0 ? 65536 : 0) | (entity.isDiscrete() ? 131072 : 0)
				| (entity.isPassenger() ? 262144 : 0) | (entity.isVehicle() ? 524288 : 0);
		if (entity.isUsingItem() || entity.isFallFlying() || entity.isAutoSpinAttack() || entity.deathTime > 0) {
			// Changing every tick (use time, flight, spin, death).
			return NEVER;
		}
		h = mix(h, flags);
		h = mix(h, entity.getArrowCount());
		h = mix(h, entity.getStingerCount());
		h = mix(h, entity.getMainArm().ordinal());
		h = mix(h, entity.swingingArm == null ? -1 : entity.swingingArm.ordinal());
		h = mix(h, Float.floatToRawIntBits(entity.getBbWidth()));
		h = mix(h, Float.floatToRawIntBits(entity.getBbHeight()));
		h = mix(h, Float.floatToRawIntBits(entity.getEyeHeight()));
		h = mix(h, Float.floatToRawIntBits(entity.getScale()));
		h = mix(h, Float.floatToRawIntBits(entity.getAgeScale()));
		h = mix(h, System.identityHashCode(entity.getBedOrientation()));
		h = mix(h, System.identityHashCode(entity.getVehicle()));
		h = mix(h, System.identityHashCode(entity.getTeam()));
		h = mix(h, System.identityHashCode(entity.getCustomName()));
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = entity.getItemBySlot(slot);
			if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND || slot == EquipmentSlot.HEAD) && !fixedModel(stack)) {
				return NEVER;
			}
			h = mix(h, System.identityHashCode(stack));
			h = mix(h, stack.getCount());
		}
		if (entity instanceof net.minecraft.client.entity.ClientAvatarEntity avatar) {
			h = mix(h, System.identityHashCode(avatar.getSkin()));
		}
		if (entity instanceof Player player) {
			int parts = 0;
			for (PlayerModelPart part : MODEL_PARTS) {
				parts = parts << 1 | (player.isModelPartShown(part) ? 1 : 0);
			}
			h = mix(h, parts);
			h = mix(h, player.isSpectator() ? 1 : 0);
		}
		return h == NEVER ? NEVER + 1 : h;
	}

	private static long mix(long h, long v) {
		if (DEBUG) {
			PARTS.get().add(v);
		}
		h ^= v + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
		return h * 0xBF58476D1CE4E5B9L;
	}

	/** -Dpolarium.debugInputs=true: which input changed, when a state can't carry over (logged every 10 s). */
	public static final boolean DEBUG = Boolean.getBoolean("polarium.debugInputs");
	private static final ThreadLocal<java.util.List<Long>> PARTS = ThreadLocal.withInitial(java.util.ArrayList::new);
	private static final Map<Object, java.util.List<Long>> LAST = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
	private static final Map<Integer, Long> CHANGED = new ConcurrentHashMap<>();
	private static volatile long reported = System.nanoTime();

	/** Debug: the inputs' parts noted for this entity (made in full), or compared with the noted ones (log which differ). */
	public static void debug(Avatar entity, boolean note) {
		PARTS.get().clear();
		of(entity);
		java.util.List<Long> parts = new java.util.ArrayList<>(PARTS.get());
		if (note) {
			LAST.put(entity, parts);
			return;
		}
		java.util.List<Long> last = LAST.get(entity);
		if (last == null) {
			return;
		}
		for (int i = 0; i < Math.max(last.size(), parts.size()); i++) {
			if (i >= last.size() || i >= parts.size() || !last.get(i).equals(parts.get(i))) {
				CHANGED.merge(i, 1L, Long::sum);
			}
		}
		long now = System.nanoTime();
		if (now - reported > 10_000_000_000L) {
			reported = now;
			org.slf4j.LoggerFactory.getLogger("Polarium").info("Polarium inputs changed (part number: times): {}", new java.util.TreeMap<>(CHANGED));
			CHANGED.clear();
		}
	}

	/** Whether the item's model is the same whatever the time or holder (no model choosing between others). */
	private static boolean fixedModel(ItemStack stack) {
		if (stack.isEmpty()) {
			return true;
		}
		Identifier id = stack.get(DataComponents.ITEM_MODEL);
		if (id == null) {
			return true;
		}
		ItemModel model = Minecraft.getInstance().getModelManager().getItemModel(id);
		return FIXED.computeIfAbsent(model, TickInputs::fixed);
	}

	private static boolean fixed(ItemModel model) {
		boolean fixed = fixedModel(model);
		if (!fixed && DEBUG) {
			org.slf4j.LoggerFactory.getLogger("Polarium").info("Polarium: players holding items with a {} are made in full every tick",
					model.getClass().getSimpleName());
		}
		return fixed;
	}

	private static boolean fixedModel(ItemModel model) {
		if (model instanceof CompositeModel composite) {
			for (ItemModel part : ((com.arcticlauncher.polarium.mixin.CompositeModelAccess) composite).polarium$models()) {
				if (!fixedModel(part)) {
					return false;
				}
			}
			return true;
		}
		if (model instanceof net.minecraft.client.renderer.item.SelectItemModel<?> select) {
			// Choosing by the item, its holder, where it's drawn, the dimension: the same while those are (the
			// inputs cover them). Only the local time changes by itself. (The models it chooses between are
			// taken to be fixed: they can't be looked into.)
			return !(((com.arcticlauncher.polarium.mixin.SelectItemModelAccess) select).polarium$property()
					instanceof net.minecraft.client.renderer.item.properties.select.LocalTime);
		}
		return model instanceof CuboidItemModelWrapper || model instanceof SpecialModelWrapper<?> || model instanceof EmptyModel
				|| model instanceof MissingItemModel;
	}
}
//#endif
