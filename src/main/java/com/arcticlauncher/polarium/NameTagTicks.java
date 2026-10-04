//#if MC >= 26.2
package com.arcticlauncher.polarium;

import com.arcticlauncher.polarium.mixin.EntityRendererNameTagsAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A player's name tags brought up to the frame (see LightStates#bringUp) as
 * the game's extractNameTags makes them, but working out once per tick what
 * only changes on a tick: whether the tag is shown at all (team rules,
 * invisibility, whose camera), its text and the score below it. Every
 * frame where it hangs and the distance to the camera is measured again,
 * against the same limits (the attributes' distances, and 32 blocks for a
 * sneaking player). Also worked out again when the HUD is shown or hidden,
 * or the camera's entity or the one under the crosshair changes. -Dpolarium.checkTags=true
 * compares every frame with the game's own (logged every 10 s).
 */
public final class NameTagTicks {
	private NameTagTicks() {}

	/** What a player's tags are made from on one tick. */
	private static final class Kept {
		int tick = -1;
		boolean hudHidden;
		@Nullable Entity camera;
		@Nullable Entity picked;
		boolean shown;
		@Nullable Component text;
		@Nullable Component score;
	}

	/** The name tags for this frame, into {@code state} (distances: the player's name tag attributes). */
	public static void extract(EntityRenderer<?, ?> renderer, Avatar entity, AvatarRenderState state, float partialTicks, double nameTagDistance,
			double belowNameDistance) {
		Minecraft mc = Minecraft.getInstance();
		EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
		if (dispatcher.camera == null) {
			return;
		}
		EntityRendererNameTagsAccess access = (EntityRendererNameTagsAccess) renderer;
		KeptStates.Holder holder = (KeptStates.Holder) entity;
		Kept kept = holder.polarium$tagTick() instanceof Kept k ? k : new Kept();
		boolean hudHidden = mc.gui.hud.isHidden();
		Entity camera = mc.getCameraEntity();
		Entity picked = dispatcher.crosshairPickEntity;
		if (kept.tick != entity.tickCount || kept.hudHidden != hudHidden || kept.camera != camera || kept.picked != picked) {
			kept.tick = entity.tickCount;
			kept.hudHidden = hudHidden;
			kept.camera = camera;
			kept.picked = picked;
			// At no distance: the only distance rule in it is the sneaking one, checked below every frame.
			kept.shown = access.polarium$shouldShowName(entity, 0.0);
			kept.text = access.polarium$getNameTag(entity);
			kept.score = entity.belowNameDisplay();
			holder.polarium$tagTick(kept);
		}
		double distanceSq = dispatcher.distanceToSqr(entity);
		state.distanceToCameraSq = distanceSq;
		boolean shown = distanceSq < nameTagDistance * nameTagDistance && kept.shown && !(entity.isDiscrete() && distanceSq >= 1024.0);
		if (shown) {
			state.nameTag = kept.text;
			// Every frame: it turns with the player, and its dimensions can change within a tick.
			state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTicks));
		} else {
			state.nameTag = null;
		}
		state.scoreText = distanceSq < belowNameDistance * belowNameDistance ? kept.score : null;
		if (CHECK) {
			check(renderer, entity, state, partialTicks);
		}
	}

	private static final boolean CHECK = Boolean.getBoolean("polarium.checkTags");
	private static final java.util.concurrent.atomic.AtomicLong CHECKED = new java.util.concurrent.atomic.AtomicLong();
	private static final java.util.concurrent.ConcurrentHashMap<String, Long> DIFFERENT = new java.util.concurrent.ConcurrentHashMap<>();
	private static volatile long reported = System.nanoTime();

	/** Debug: the same tags made the game's way, compared. */
	private static void check(EntityRenderer<?, ?> renderer, Avatar entity, AvatarRenderState state, float partialTicks) {
		Component nameTag = state.nameTag;
		Vec3 attachment = state.nameTagAttachment;
		Component score = state.scoreText;
		double distance = state.distanceToCameraSq;
		state.nameTagAttachment = null;
		((com.arcticlauncher.polarium.mixin.LivingEntityRendererAccess) renderer).polarium$extractLivingNameTags(entity, state, partialTicks);
		CHECKED.incrementAndGet();
		if (nameTag != state.nameTag) {
			DIFFERENT.merge(nameTag == null || state.nameTag == null ? "shown" : "text", 1L, Long::sum);
		}
		if (state.nameTag != null && !java.util.Objects.equals(attachment, state.nameTagAttachment)) {
			DIFFERENT.merge("attachment", 1L, Long::sum);
		}
		if (score != state.scoreText && !java.util.Objects.equals(score, state.scoreText)) {
			DIFFERENT.merge("score", 1L, Long::sum);
		}
		if (Double.doubleToLongBits(distance) != Double.doubleToLongBits(state.distanceToCameraSq)) {
			DIFFERENT.merge("distance", 1L, Long::sum);
		}
		long now = System.nanoTime();
		if (now - reported > 10_000_000_000L) {
			reported = now;
			org.slf4j.LoggerFactory.getLogger("Polarium").info("Polarium tag check: {} frames, different: {}", CHECKED.getAndSet(0),
					new java.util.TreeMap<>(DIFFERENT));
			DIFFERENT.clear();
		}
	}
}
//#endif
