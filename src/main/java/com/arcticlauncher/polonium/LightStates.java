//#if MC >= 26.2
package com.arcticlauncher.polonium;

import com.arcticlauncher.polonium.mixin.AvatarRendererLightAccess;
import com.arcticlauncher.polonium.mixin.LivingEntityRendererAccess;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
			// Bridges MixinExtras generates for other hooks (themselves checked by their own names).
			"bridge");
	/** The renderer classes whose extraction a light frame skips. */
	private static final String[] RENDERERS = {"net.minecraft.client.renderer.entity.player.AvatarRenderer",
			"net.minecraft.client.renderer.entity.HumanoidMobRenderer", "net.minecraft.client.renderer.entity.LivingEntityRenderer",
			"net.minecraft.client.renderer.entity.EntityRenderer", "net.minecraft.client.renderer.entity.state.ArmedEntityRenderState"};
	/** A mixin's merged member: handler$zdg000$entityculling$name, md2f7439$polonium$name, … */
	private static final Pattern MERGED = Pattern.compile("^(?:[a-zA-Z]+\\$[a-z]{3}\\d{3}|md[0-9a-f]{6,}|mixinextras)\\$([a-z0-9_.\\-]+)\\$");
	private static volatile Boolean on;
	private static List<BiConsumer<Object, Object>> hooks = List.of();
	/** With -Dpolonium.debugTimeline: why states were made in full or brought up (logged with the timeline). */
	private static final java.util.concurrent.atomic.AtomicLongArray WHY = new java.util.concurrent.atomic.AtomicLongArray(5);
	private static final String[] WHY_NAMES = {"brought up", "ticked", "packet", "attributes", "riding or leashed"};

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
		Set<String> unknown = new TreeSet<>();
		Set<String> members = new TreeSet<>();
		for (String name : RENDERERS) {
			try {
				for (Method method : Class.forName(name, false, LightStates.class.getClassLoader()).getDeclaredMethods()) {
					Matcher m = MERGED.matcher(method.getName());
					if (m.find() && !KNOWN.contains(m.group(1)) && !m.group(1).startsWith("fabric")) {
						unknown.add(m.group(1));
						members.add(method.getDeclaringClass().getSimpleName() + "." + method.getName());
					}
				}
			} catch (ClassNotFoundException | LinkageError e) {
				unknown.add("(can't look: " + e + ")");
			}
		}
		if (!unknown.isEmpty()) {
			LOG.info("Polonium: other players' render states made in full every frame ({} change how they're made: {})", unknown, members);
			return false;
		}
		List<BiConsumer<Object, Object>> found = new ArrayList<>();
		for (BiConsumer hook : FabricLoader.getInstance().getEntrypoints("polonium:light_state", BiConsumer.class)) {
			found.add(hook);
		}
		hooks = List.copyOf(found);
		LOG.info("Polonium: other players' render states made in full once a tick, brought up to the frame between ({} mods' own per-frame work: {})",
				hooks.size(), FabricLoader.getInstance().getEntrypointContainers("polonium:light_state", BiConsumer.class).stream()
						.map(c -> c.getProvider().getMetadata().getId()).toList());
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
		int why = holder.polonium$fullTick() != entity.tickCount ? 1 : holder.polonium$touched() ? 2
				: holder.polonium$fullAttributes() != AttributeValues.version(avatar) ? 3
				: entity.isPassenger() || entity.isVehicle() || entity instanceof net.minecraft.world.entity.Leashable leashable
						&& leashable.isLeashed() ? 4 : 0;
		if (Timeline.ON) {
			WHY.incrementAndGet(why);
		}
		return why == 0;
	}

	/** The state was just made in full. */
	public static void madeInFull(Entity entity) {
		if (entity instanceof KeptStates.Holder holder && entity instanceof LivingEntity living) {
			holder.polonium$madeInFull(entity.tickCount, AttributeValues.version(living));
		}
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
