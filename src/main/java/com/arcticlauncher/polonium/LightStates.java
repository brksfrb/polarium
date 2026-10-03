//#if MC >= 26.2
package com.arcticlauncher.polonium;

import com.arcticlauncher.polonium.mixin.AvatarRendererLightAccess;
import com.arcticlauncher.polonium.mixin.LivingEntityRendererAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Other players' render states made in full once per tick, and between
 * ticks only brought up to the frame. Almost everything in a player's state
 * (equipment and its models, skin, layers, scale, arm poses, name) only
 * changes when the player ticks or the server says something about it; only
 * where it is within the tick changes from frame to frame: its position and
 * light, turning, the walk and swing animations, its cape, swimming and
 * gliding poses, and its name tag's distance. With dozens of frames a second
 * that's most frames.
 *
 * A player's state is made in full again when it ticked since, when the
 * server sent anything about it (any packet handler looking it up, see
 * ClientLevelTouchedMixin), when its attributes changed, or when it rides,
 * is ridden or leashed. Between, {@link #bringUp} sets exactly what the
 * game's extraction sets per frame, the way it does (the same methods).
 *
 * Other mods may add their own per-frame work to the game's extraction; so
 * with any mod Polonium doesn't know hooking it, this is off (logged). Mods
 * with per-frame work of their own can register a {@code polonium:light_state}
 * entrypoint ({@code BiConsumer<Entity, EntityRenderState>}), called after
 * each bringing up. Off with -Dpolonium.lightStates=false.
 */
public final class LightStates {
	private static final Logger LOG = LoggerFactory.getLogger("Polonium");
	/** Mods whose hooks into the player renderers were checked: tick-level work, or registered for bringing up. */
	private static final Set<String> KNOWN = Set.of("polonium", "arctic", "entityculling", "sodium", "lithium", "immediatelyfast",
			"ferritecore", "modernfix",
			// tr7zw's library (with EntityCulling): notes which entity a state is of, the same for a kept state.
			"transition",
			// Entity Texture Features: notes which entity a state is of (a kept state's stays right), and may make a
			// state full bright for emissive textures in getPackedLightCoords (which bringing up calls too).
			"entity_texture_features",
			// Not Enough Animations (1.12): notes the state's entity and held items in it, and the state on the player.
			"notenoughanimations");
	/** The renderer classes whose extraction a light frame skips. */
	private static final String[] RENDERERS = {"net.minecraft.client.renderer.entity.player.AvatarRenderer",
			"net.minecraft.client.renderer.entity.HumanoidMobRenderer", "net.minecraft.client.renderer.entity.LivingEntityRenderer",
			"net.minecraft.client.renderer.entity.EntityRenderer", "net.minecraft.client.renderer.entity.state.ArmedEntityRenderState"};
	/**
	 * The renderers' methods a light frame skips (it calls the rest of the
	 * per-frame ones itself: name tags, light, body turn, flight, cape).
	 * Another mod's hook into one of these may do per-frame work.
	 */
	private static final Set<String> SKIPPED = Set.of("extractRenderState", "extractHumanoidRenderState", "extractArmedEntityRenderState",
			"getArmPose", "getAttackArm");

	private static volatile Boolean on;
	private static List<BiConsumer<Object, Object>> hooks = List.of();
	/** With -Dpolonium.debugTimeline: why states were made in full or brought up (logged with the timeline). */
	private static final java.util.concurrent.atomic.AtomicLongArray WHY = new java.util.concurrent.atomic.AtomicLongArray(6);
	private static final String[] WHY_NAMES = {"brought up", "ticked", "packet", "attributes", "riding or leashed", "carried over a tick"};

	/** Per thread: whether the state it made last was brought up (not made in full). */
	private static final ThreadLocal<boolean[]> BROUGHT_UP = ThreadLocal.withInitial(() -> new boolean[1]);

	/** Whether the state this thread made last was only brought up to the frame (its tick-level parts as checked before). */
	public static boolean broughtUp() {
		return BROUGHT_UP.get()[0];
	}

	/** Noted by the extraction hook: this thread's state was brought up ({@code light}) or made in full. */
	public static void made(boolean light) {
		BROUGHT_UP.get()[0] = light;
	}

	/** The counts so far (and starts over): for Timeline. */
	public static String counts() {
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < WHY_NAMES.length; i++) {
			line.append(i == 0 ? "" : ", ").append(WHY_NAMES[i]).append(' ').append(WHY.getAndSet(i, 0));
		}
		return line.toString();
	}

	private LightStates() {}

	/** Whether light frames are on (worked out once). */
	public static boolean on() {
		Boolean known = on;
		if (known == null) {
			known = workOut();
			on = known;
		}
		return known;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static synchronized boolean workOut() {
		if (on != null) {
			return on;
		}
		if ("false".equals(System.getProperty("polonium.lightStates"))) {
			return false;
		}
		// Mods whose per-frame work Polonium does for them (see LightStateCompat).
		List<BiConsumer<Object, Object>> builtIn = new ArrayList<>();
		Set<String> handled = new java.util.HashSet<>();
		BiConsumer<Object, Object> playerAnimations = LightStateCompat.playerAnimationLibrary();
		if (playerAnimations != null) {
			builtIn.add(playerAnimations);
			handled.add("player_animation_library");
		}
		Set<String> unknown = new TreeSet<>();
		Set<String> members = new TreeSet<>();
		int ours = 0;
		for (String name : RENDERERS) {
			Set<IMixinInfo> mixins = MixinsOn.of(name);
			for (IMixinInfo mixin : mixins) {
				String mod = MixinsOn.mod(mixin);
				if (handled.contains(mod)) {
					continue;
				}
				if ("polonium".equals(mod)) {
					ours++;
				}
				if (KNOWN.contains(mod) || mod.startsWith("fabric")) {
					continue;
				}
				for (String hooked : MixinsOn.hooks(mixin, SKIPPED)) {
					unknown.add(mod);
					members.add(name.substring(name.lastIndexOf('.') + 1) + "." + hooked + " (" + mixin.getName() + ")");
				}
			}
		}
		if (ours == 0) {
			// Polonium's own hooks there not found: other mods' can't be told either.
			LOG.info("Polonium: other players' render states made in full every frame (can't tell which mods change how they're made)");
			return false;
		}
		if (!unknown.isEmpty()) {
			LOG.info("Polonium: other players' render states made in full every frame ({} change how they're made: {})", unknown, members);
			return false;
		}
		List<BiConsumer<Object, Object>> found = new ArrayList<>(builtIn);
		for (BiConsumer hook : FabricLoader.getInstance().getEntrypoints("polonium:light_state", BiConsumer.class)) {
			found.add(hook);
		}
		hooks = List.copyOf(found);
		List<String> withWork = new ArrayList<>(handled);
		FabricLoader.getInstance().getEntrypointContainers("polonium:light_state", BiConsumer.class)
				.forEach(c -> withWork.add(c.getProvider().getMetadata().getId()));
		LOG.info("Polonium: other players' render states made in full once a tick, brought up to the frame between (mods' own per-frame work: {})",
				withWork);
		return true;
	}

	/**
	 * Whether this state may be brought up to the frame instead of made in
	 * full: a kept player state ({@code kept}), made in full on this tick,
	 * nothing about the player sent since.
	 */
	public static boolean light(EntityRenderer<?, ?> renderer, Entity entity, EntityRenderState state, boolean kept) {
		if (!kept || renderer.getClass() != AvatarRenderer.class || !(entity instanceof Avatar avatar)
				|| !(entity instanceof KeptStates.Holder holder) || !(state instanceof AvatarRenderState) || !on()) {
			return false;
		}
		int why = holder.polonium$touched() ? 2
				: holder.polonium$fullAttributes() != AttributeValues.version(avatar) ? 3
				: entity.isPassenger() || entity.isVehicle() || entity instanceof net.minecraft.world.entity.Leashable leashable
						&& leashable.isLeashed() ? 4
				: holder.polonium$keptTick() == entity.tickCount ? 0
				: carriesOver(avatar, holder) ? 5 : 1;
		if (Timeline.ON) {
			WHY.incrementAndGet(why);
		}
		return why == 0 || why == 5;
	}

	/** The state was just made in full. */
	public static void madeInFull(Entity entity) {
		if (entity instanceof KeptStates.Holder holder && entity instanceof LivingEntity living) {
			holder.polonium$madeInFull(entity.tickCount, AttributeValues.version(living));
			holder.polonium$inputs(CARRY_OVER && entity instanceof Avatar avatar ? TickInputs.of(avatar) : TickInputs.NEVER);
			if (TickInputs.DEBUG && entity instanceof Avatar avatar) {
				TickInputs.debug(avatar, true);
			}
		}
	}

	/** Off with -Dpolonium.carryOver=false: a state is made in full on every tick of its player. */
	private static final boolean CARRY_OVER = !"false".equals(System.getProperty("polonium.carryOver"));

	/**
	 * On a new tick of its player: whether its kept state carries over (see
	 * TickInputs), nothing it's made from but what's brought up every frame
	 * having changed. Made in full anyway every {@link KeptStates#COPY_TICKS}
	 * ticks (as its items' copies are). Notes the tick if so.
	 */
	private static boolean carriesOver(Avatar avatar, KeptStates.Holder holder) {
		long inputs = holder.polonium$inputs();
		if (!CARRY_OVER || inputs == TickInputs.NEVER || avatar.tickCount - holder.polonium$fullTick() >= KeptStates.COPY_TICKS
				|| avatar.tickCount < holder.polonium$fullTick() || TickInputs.of(avatar) != inputs) {
			if (TickInputs.DEBUG && inputs != TickInputs.NEVER) {
				TickInputs.debug(avatar, false);
			}
			return false;
		}
		holder.polonium$keptTick(avatar.tickCount);
		if (CHECK_CARRY) {
			CARRIED.set(true);
		}
		return true;
	}

	/** -Dpolonium.checkCarry=true: each carried-over state checked, field by field, against one made in full (logged every 10 s). */
	public static final boolean CHECK_CARRY = Boolean.getBoolean("polonium.checkCarry");
	private static final ThreadLocal<Boolean> CARRIED = ThreadLocal.withInitial(() -> false);
	private static final java.util.concurrent.ConcurrentHashMap<String, Long> CARRY_DIFFERENT = new java.util.concurrent.ConcurrentHashMap<>();
	private static final java.util.concurrent.atomic.AtomicLong CARRY_CHECKED = new java.util.concurrent.atomic.AtomicLong();
	private static volatile long carryReported = System.nanoTime();

	/** After bringing a state up: if it was carried over a tick (and checking), compared with one made in full. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void checkCarried(EntityRenderer<?, ?> renderer, Entity entity, EntityRenderState kept, float partialTicks) {
		if (!CARRIED.get()) {
			return;
		}
		CARRIED.set(false);
		EntityRenderState fresh = ((EntityRenderer) renderer).createRenderState();
		((EntityRenderer) renderer).extractRenderState(entity, fresh, partialTicks);
		for (Class<?> c = kept.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			for (java.lang.reflect.Field field : c.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getName().contains("$")) {
					continue;
				}
				try {
					field.setAccessible(true);
					if (!sameValue(field.get(kept), field.get(fresh))) {
						CARRY_DIFFERENT.merge(c.getSimpleName() + "." + field.getName(), 1L, Long::sum);
					}
				} catch (ReflectiveOperationException | RuntimeException e) {
					CARRY_DIFFERENT.merge(c.getSimpleName() + "." + field.getName() + " (unreadable)", 1L, Long::sum);
				}
			}
		}
		CARRY_CHECKED.incrementAndGet();
		long now = System.nanoTime();
		if (now - carryReported > 10_000_000_000L) {
			carryReported = now;
			LOG.info("Polonium carry check: {} states, different: {}", CARRY_CHECKED.getAndSet(0), new java.util.TreeMap<>(CARRY_DIFFERENT));
			CARRY_DIFFERENT.clear();
		}
	}

	private static boolean sameValue(Object a, Object b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null) {
			return false;
		}
		if (a instanceof Float x && b instanceof Float y) {
			return Float.floatToIntBits(x) == Float.floatToIntBits(y);
		}
		if (a instanceof net.minecraft.world.item.ItemStack x && b instanceof net.minecraft.world.item.ItemStack y) {
			return net.minecraft.world.item.ItemStack.matches(x, y);
		}
		if (a instanceof net.minecraft.client.renderer.item.ItemStackRenderState x && b instanceof net.minecraft.client.renderer.item.ItemStackRenderState y) {
			return x.isEmpty() == y.isEmpty() && x.isAnimated() == y.isAnimated() && x.usesBlockLight() == y.usesBlockLight();
		}
		if (a instanceof net.minecraft.network.chat.Component x && b instanceof net.minecraft.network.chat.Component y) {
			return x.getString().equals(y.getString());
		}
		if (a instanceof java.util.Collection<?> x && b instanceof java.util.Collection<?> y) {
			return x.size() == y.size();
		}
		return a.equals(b);
	}

	/**
	 * What the game's extraction sets per frame (EntityRenderer,
	 * LivingEntityRenderer, HumanoidMobRenderer, ArmedEntityRenderState,
	 * AvatarRenderer, in that order), set as they do; the rest of the state
	 * is as it was made on this tick.
	 */
	public static void bringUp(EntityRenderer<?, ?> renderer, Avatar entity, AvatarRenderState state, float partialTicks) {
		// EntityRenderer
		state.x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
		state.y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
		state.z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
		state.ageInTicks = entity.tickCount + partialTicks;
		// As on a new state: the game only sets where the tag goes when it shows one.
		state.nameTagAttachment = null;
		((LivingEntityRendererAccess) renderer).polonium$extractLivingNameTags(entity, state, partialTicks);
		boolean appearsGlowing = Minecraft.getInstance().shouldEntityAppearGlowing(entity);
		state.outlineColor = appearsGlowing ? ARGB.opaque(entity.getTeamColor()) : 0;
		@SuppressWarnings({"unchecked", "rawtypes"})
		int light = ((EntityRenderer) renderer).getPackedLightCoords(entity, partialTicks);
		state.lightCoords = light;
		// LivingEntityRenderer
		float headRot = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
		state.bodyRot = LivingEntityRendererAccess.polonium$solveBodyRot(entity, headRot, partialTicks);
		state.yRot = Mth.wrapDegrees(headRot - state.bodyRot);
		state.xRot = entity.getXRot(partialTicks);
		if (state.isUpsideDown) {
			state.xRot *= -1.0F;
			state.yRot *= -1.0F;
		}
		if (entity.isAlive()) {
			state.walkAnimationPos = entity.walkAnimation.position(partialTicks);
			state.walkAnimationSpeed = entity.walkAnimation.speed(partialTicks);
		} else {
			state.walkAnimationPos = 0.0F;
			state.walkAnimationSpeed = 0.0F;
		}
		state.wornHeadAnimationPos = state.walkAnimationPos;
		state.ticksSinceKineticHitFeedback = entity.getTicksSinceLastKineticHitFeedback(partialTicks);
		state.deathTime = entity.deathTime > 0 ? entity.deathTime + partialTicks : 0.0F;
		// HumanoidMobRenderer
		state.swimAmount = entity.getSwimAmount(partialTicks);
		state.ticksUsingItem = entity.getTicksUsingItem(partialTicks);
		state.elytraRotX = entity.elytraAnimationState.getRotX(partialTicks);
		state.elytraRotY = entity.elytraAnimationState.getRotY(partialTicks);
		state.elytraRotZ = entity.elytraAnimationState.getRotZ(partialTicks);
		// ArmedEntityRenderState
		state.attackTime = entity.getAttackAnim(partialTicks);
		// AvatarRenderer
		AvatarRendererLightAccess avatar = (AvatarRendererLightAccess) renderer;
		avatar.polonium$extractFlightData(entity, state, partialTicks);
		avatar.polonium$extractCapeState(entity, state, partialTicks);
		for (BiConsumer<Object, Object> hook : hooks) {
			hook.accept(entity, state);
		}
	}
}
//#endif
