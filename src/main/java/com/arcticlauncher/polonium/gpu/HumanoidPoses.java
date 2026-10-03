//#if MC >= 26.2
package com.arcticlauncher.polonium.gpu;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.SwingAnimationType;

/**
 * A player model's pose worked out directly, as {@code PlayerModel.setupAnim}
 * (and {@code HumanoidModel}'s) does it, for the usual states: standing,
 * walking, crouching, riding, swinging, holding or blocking with an item.
 * The same operations in the same order, so the same floats.
 *
 * Only the six moving parts change (head, body, arms, legs); everything else
 * keeps its initial pose. Per part: x, y, z, xRot, yRot, zRot.
 */
final class HumanoidPoses {
	static final int HEAD = 0;
	static final int BODY = 1;
	static final int RIGHT_ARM = 2;
	static final int LEFT_ARM = 3;
	static final int RIGHT_LEG = 4;
	static final int LEFT_LEG = 5;
	static final int PARTS = 6;
	/** Per part: x, y, z, xRot, yRot, zRot, xScale, yScale, zScale. */
	static final int VALUES = 9;
	private static final int X = 0;
	private static final int Y = 1;
	private static final int Z = 2;
	private static final int X_ROT = 3;
	private static final int Y_ROT = 4;
	private static final int Z_ROT = 5;
	private static final int X_SCALE = 6;
	private static final int Y_SCALE = 7;
	private static final int Z_SCALE = 8;

	private HumanoidPoses() {}

	/** Off with -Dpolonium.skeleton=false: players' parts all posed on the CPU, each part's matrix sent. */
	private static final boolean ENABLED = !"false".equals(System.getProperty("polonium.skeleton"));
	/** What posing a player's model goes through: another mod's hook into any of these may move other parts or move them otherwise. */
	private static final java.util.Map<String, java.util.Set<String>> POSING = java.util.Map.of(
			"net.minecraft.client.model.Model", java.util.Set.of("setupAnim", "resetPose"),
			"net.minecraft.client.model.EntityModel", java.util.Set.of("setupAnim"),
			"net.minecraft.client.model.HumanoidModel", java.util.Set.of("setupAnim", "poseRightArm", "poseLeftArm", "poseBlockingArm",
					"setupAttackAnimation", "translateToHand", "getArm"),
			"net.minecraft.client.model.player.PlayerModel", java.util.Set.of("setupAnim", "translateToHand"),
			"net.minecraft.client.model.AnimationUtils", java.util.Set.of("bobModelPart", "bobArms"),
			"net.minecraft.client.model.geom.ModelPart", java.util.Set.of("resetPose", "translateAndRotate", "loadPose"));
	/**
	 * Mods whose hooks into posing were checked: Sodium's ModelPart.translateAndRotate
	 * is the same math done faster; Arctic's emotes and Player Animation
	 * Library's animations only move (rotate, offset, scale) the six moving
	 * parts, which the skeleton format carries, but need the model's own
	 * setupAnim to run.
	 */
	private static final java.util.Set<String> SAME_MATH = java.util.Set.of("sodium");
	private static final java.util.Set<String> MOVING_PARTS_ONLY = java.util.Set.of("arctic", "player_animation_library");
	/** Not in the skeleton format. */
	static final int OFF = 0;
	/** In the skeleton format, every model posed by its own setupAnim (other mods' hooks run). */
	static final int POSED = 1;
	/** In the skeleton format, usual states worked out directly ({@link #pose}). */
	static final int DIRECT = 2;
	private static volatile Integer mode;

	/** Whether players may be drawn in the skeleton format, and how they're posed (worked out once). */
	static int mode() {
		Integer known = mode;
		if (known == null) {
			known = workOut();
			mode = known;
		}
		return known;
	}

	static boolean allowed() {
		return mode() != OFF;
	}

	private static synchronized int workOut() {
		if (mode != null) {
			return mode;
		}
		if (!ENABLED) {
			return OFF;
		}
		java.util.Set<String> movingOnly = new java.util.TreeSet<>();
		java.util.Set<String> others = new java.util.TreeSet<>();
		for (java.util.Map.Entry<String, java.util.Set<String>> target : POSING.entrySet()) {
			java.util.Set<org.spongepowered.asm.mixin.extensibility.IMixinInfo> mixins = com.arcticlauncher.polonium.MixinsOn.of(target.getKey());
			if (mixins == null) {
				continue;
			}
			for (org.spongepowered.asm.mixin.extensibility.IMixinInfo mixin : mixins) {
				String mod = com.arcticlauncher.polonium.MixinsOn.mod(mixin);
				if ("polonium".equals(mod) || SAME_MATH.contains(mod)) {
					continue;
				}
				for (String hooked : com.arcticlauncher.polonium.MixinsOn.hooks(mixin, target.getValue())) {
					String hook = mod + " (" + target.getKey().substring(target.getKey().lastIndexOf('.') + 1) + "." + hooked + ")";
					(MOVING_PARTS_ONLY.contains(mod) ? movingOnly : others).add(hook);
				}
			}
		}
		org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("Polonium");
		if (!others.isEmpty()) {
			log.info("Polonium: players' part matrices worked out on the CPU (other mods change how player models are posed: {})", others);
			return OFF;
		}
		if (!movingOnly.isEmpty()) {
			log.info("Polonium: players' part matrices worked out on the GPU, posed by the game ({})", movingOnly);
			return POSED;
		}
		log.info("Polonium: players' part matrices worked out on the GPU");
		return DIRECT;
	}

	/** Whether {@link #pose} covers this state (else the model's own setupAnim is needed). */
	static boolean covers(AvatarRenderState state) {
		return !state.isFallFlying && !(state.swimAmount > 0.0F) && !state.isUsingItem && simple(state.rightArmPose)
				&& simple(state.leftArmPose) && state.swingAnimationType != SwingAnimationType.STAB;
	}

	/** Arm poses that only move their own arm (so the order the arms are posed in doesn't matter). */
	private static boolean simple(HumanoidModel.ArmPose pose) {
		return pose == HumanoidModel.ArmPose.EMPTY || pose == HumanoidModel.ArmPose.ITEM || pose == HumanoidModel.ArmPose.BLOCK;
	}

	/** The six parts' initial poses ({@link #PARTS} × {@link #VALUES}), as {@code resetPose} puts them back. */
	static float[] rest(HumanoidModel<?> model) {
		float[] rest = new float[PARTS * VALUES];
		ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
		for (int i = 0; i < PARTS; i++) {
			PartPose pose = parts[i].getInitialPose();
			int o = i * VALUES;
			rest[o + X] = pose.x();
			rest[o + Y] = pose.y();
			rest[o + Z] = pose.z();
			rest[o + X_ROT] = pose.xRot();
			rest[o + Y_ROT] = pose.yRot();
			rest[o + Z_ROT] = pose.zRot();
			rest[o + X_SCALE] = pose.xScale();
			rest[o + Y_SCALE] = pose.yScale();
			rest[o + Z_SCALE] = pose.zScale();
		}
		return rest;
	}

	/** The pose for this state ({@link #covers} it): {@code out} gets the six parts' values, from {@code rest}. */
	static void pose(AvatarRenderState state, float[] rest, float[] out) {
		System.arraycopy(rest, 0, out, 0, PARTS * VALUES);
		int head = HEAD * VALUES;
		int body = BODY * VALUES;
		int rightArm = RIGHT_ARM * VALUES;
		int leftArm = LEFT_ARM * VALUES;
		int rightLeg = RIGHT_LEG * VALUES;
		int leftLeg = LEFT_LEG * VALUES;
		out[head + X_ROT] = state.xRot * (float) (Math.PI / 180.0);
		out[head + Y_ROT] = state.yRot * (float) (Math.PI / 180.0);
		float animationPos = state.walkAnimationPos;
		float animationSpeed = state.walkAnimationSpeed;
		out[rightArm + X_ROT] = Mth.cos(animationPos * 0.6662F + (float) Math.PI) * 2.0F * animationSpeed * 0.5F / state.speedValue;
		out[leftArm + X_ROT] = Mth.cos(animationPos * 0.6662F) * 2.0F * animationSpeed * 0.5F / state.speedValue;
		out[rightLeg + X_ROT] = Mth.cos(animationPos * 0.6662F) * 1.4F * animationSpeed / state.speedValue;
		out[leftLeg + X_ROT] = Mth.cos(animationPos * 0.6662F + (float) Math.PI) * 1.4F * animationSpeed / state.speedValue;
		out[rightLeg + Y_ROT] = 0.005F;
		out[leftLeg + Y_ROT] = -0.005F;
		out[rightLeg + Z_ROT] = 0.005F;
		out[leftLeg + Z_ROT] = -0.005F;
		if (state.isPassenger) {
			out[rightArm + X_ROT] += (float) (-Math.PI / 5);
			out[leftArm + X_ROT] += (float) (-Math.PI / 5);
			out[rightLeg + X_ROT] = -1.4137167F;
			out[rightLeg + Y_ROT] = (float) (Math.PI / 10);
			out[rightLeg + Z_ROT] = 0.07853982F;
			out[leftLeg + X_ROT] = -1.4137167F;
			out[leftLeg + Y_ROT] = (float) (-Math.PI / 10);
			out[leftLeg + Z_ROT] = -0.07853982F;
		}
		poseArm(state.rightArmPose, out, rightArm, head, true);
		poseArm(state.leftArmPose, out, leftArm, head, false);
		float attackTime = state.attackTime;
		if (!(attackTime <= 0.0F)) {
			out[body + Y_ROT] = Mth.sin(Mth.sqrt(attackTime) * (float) (Math.PI * 2)) * 0.2F;
			if (state.attackArm == HumanoidArm.LEFT) {
				out[body + Y_ROT] *= -1.0F;
			}
			float bodyYRot = out[body + Y_ROT];
			float ageScale = state.ageScale;
			out[rightArm + Z] = Mth.sin(bodyYRot) * 5.0F * ageScale;
			out[rightArm + X] = -Mth.cos(bodyYRot) * 5.0F * ageScale;
			out[leftArm + Z] = -Mth.sin(bodyYRot) * 5.0F * ageScale;
			out[leftArm + X] = Mth.cos(bodyYRot) * 5.0F * ageScale;
			out[rightArm + Y_ROT] = out[rightArm + Y_ROT] + bodyYRot;
			out[leftArm + Y_ROT] = out[leftArm + Y_ROT] + bodyYRot;
			out[leftArm + X_ROT] = out[leftArm + X_ROT] + bodyYRot;
			if (state.swingAnimationType == SwingAnimationType.WHACK) {
				float swing = Ease.outQuart(attackTime);
				float aa = Mth.sin(swing * (float) Math.PI);
				float bb = Mth.sin(attackTime * (float) Math.PI) * -(out[head + X_ROT] - 0.7F) * 0.75F;
				int attackArm = state.attackArm == HumanoidArm.LEFT ? leftArm : rightArm;
				out[attackArm + X_ROT] -= aa * 1.2F + bb;
				out[attackArm + Y_ROT] = out[attackArm + Y_ROT] + bodyYRot * 2.0F;
				out[attackArm + Z_ROT] = out[attackArm + Z_ROT] + Mth.sin(attackTime * (float) Math.PI) * -0.4F;
			}
		}
		if (state.isCrouching) {
			out[body + X_ROT] = 0.5F;
			out[rightArm + X_ROT] += 0.4F;
			out[leftArm + X_ROT] += 0.4F;
			out[rightLeg + Z] += 4.0F;
			out[leftLeg + Z] += 4.0F;
			out[head + Y] += 4.2F;
			out[body + Y] += 3.2F;
			out[leftArm + Y] += 3.2F;
			out[rightArm + Y] += 3.2F;
		}
		// AnimationUtils.bobModelPart (the arm poses here are never SPYGLASS).
		out[rightArm + Z_ROT] = out[rightArm + Z_ROT] + 1.0F * (Mth.cos(state.ageInTicks * 0.09F) * 0.05F + 0.05F);
		out[rightArm + X_ROT] = out[rightArm + X_ROT] + 1.0F * (Mth.sin(state.ageInTicks * 0.067F) * 0.05F);
		out[leftArm + Z_ROT] = out[leftArm + Z_ROT] + -1.0F * (Mth.cos(state.ageInTicks * 0.09F) * 0.05F + 0.05F);
		out[leftArm + X_ROT] = out[leftArm + X_ROT] + -1.0F * (Mth.sin(state.ageInTicks * 0.067F) * 0.05F);
	}

	/** {@code poseRightArm} / {@code poseLeftArm} for the simple poses. */
	private static void poseArm(HumanoidModel.ArmPose pose, float[] out, int arm, int head, boolean right) {
		switch (pose) {
			case EMPTY -> out[arm + Y_ROT] = 0.0F;
			case ITEM -> {
				out[arm + X_ROT] = out[arm + X_ROT] * 0.5F - (float) (Math.PI / 10);
				out[arm + Y_ROT] = 0.0F;
			}
			case BLOCK -> {
				out[arm + X_ROT] = out[arm + X_ROT] * 0.5F - 0.9424779F
						+ Mth.clamp(out[head + X_ROT], (float) (-Math.PI * 4.0 / 9.0), 0.43633232F);
				out[arm + Y_ROT] = (right ? -30.0F : 30.0F) * (float) (Math.PI / 180.0)
						+ Mth.clamp(out[head + Y_ROT], (float) (-Math.PI / 6), (float) (Math.PI / 6));
			}
			default -> throw new IllegalArgumentException("not a simple arm pose: " + pose);
		}
	}

	/** The six parts as the model has them now (after its own setupAnim): to check {@link #pose} against. */
	static void read(HumanoidModel<?> model, float[] out) {
		ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
		for (int i = 0; i < PARTS; i++) {
			ModelPart part = parts[i];
			int o = i * VALUES;
			out[o + X] = part.x;
			out[o + Y] = part.y;
			out[o + Z] = part.z;
			out[o + X_ROT] = part.xRot;
			out[o + Y_ROT] = part.yRot;
			out[o + Z_ROT] = part.zRot;
			out[o + X_SCALE] = part.xScale;
			out[o + Y_SCALE] = part.yScale;
			out[o + Z_SCALE] = part.zScale;
		}
	}

	/** What decides each part's visibility in {@code PlayerModel.setupAnim} (see {@link #flags}): none (always shown). */
	static final byte ALWAYS = 0;
	private static final byte SHOW_BODY = 1;
	private static final byte HAT = 2;
	private static final byte JACKET = 3;
	private static final byte LEFT_PANTS = 4;
	private static final byte RIGHT_PANTS = 5;
	private static final byte LEFT_SLEEVE = 6;
	private static final byte RIGHT_SLEEVE = 7;

	/** Per part ({@code parts}, the model's in mesh order): what decides whether it's visible. */
	static byte[] flags(net.minecraft.client.model.player.PlayerModel model, ModelPart[] parts) {
		byte[] flags = new byte[parts.length];
		for (int i = 0; i < parts.length; i++) {
			ModelPart part = parts[i];
			flags[i] = part == model.body || part == model.rightArm || part == model.leftArm || part == model.rightLeg || part == model.leftLeg
					? SHOW_BODY : part == model.hat ? HAT : part == model.jacket ? JACKET : part == model.leftPants ? LEFT_PANTS
					: part == model.rightPants ? RIGHT_PANTS : part == model.leftSleeve ? LEFT_SLEEVE : part == model.rightSleeve ? RIGHT_SLEEVE
					: ALWAYS;
		}
		return flags;
	}

	/**
	 * Which parts are drawn (a bit per part, in mesh order), with the
	 * visibility {@code PlayerModel.setupAnim} sets from this state: a part
	 * is drawn if it and every part above it are visible.
	 */
	static int drawn(ModelMesh mesh, AvatarRenderState state) {
		int bits = 0;
		for (int p = 0; p < mesh.parts.length; p++) {
			boolean visible = switch (mesh.flag[p]) {
				case SHOW_BODY -> !state.isSpectator;
				case HAT -> state.showHat;
				case JACKET -> state.showJacket;
				case LEFT_PANTS -> state.showLeftPants;
				case RIGHT_PANTS -> state.showRightPants;
				case LEFT_SLEEVE -> state.showLeftSleeve;
				case RIGHT_SLEEVE -> state.showRightSleeve;
				default -> true;
			};
			int parent = mesh.parent[p];
			if (visible && (parent < 0 || (bits & (1 << parent)) != 0)) {
				bits |= 1 << p;
			}
		}
		return bits;
	}

	/** As {@link #drawn(ModelMesh, AvatarRenderState)}, from a posed model's parts (mesh order); a part skipping its own cubes isn't drawn. */
	static int drawn(ModelMesh mesh, ModelPart[] parts) {
		int shown = 0;
		int bits = 0;
		for (int p = 0; p < parts.length; p++) {
			int parent = mesh.parent[p];
			if (parts[p].visible && (parent < 0 || (shown & (1 << parent)) != 0)) {
				shown |= 1 << p;
				if (!parts[p].skipDraw) {
					bits |= 1 << p;
				}
			}
		}
		return bits;
	}

	/**
	 * Where a held item hangs: {@code root}, then the model's root part (no
	 * pose) and the arm, as {@code PlayerModel.translateToHand} places it (a
	 * slim model's arm half a pixel further out).
	 */
	static void hand(org.joml.Matrix4f root, float[] values, HumanoidArm arm, boolean slim, org.joml.Matrix4f out) {
		int v = (arm == HumanoidArm.LEFT ? LEFT_ARM : RIGHT_ARM) * VALUES;
		float x = values[v + X];
		if (slim) {
			x += 0.5F * (arm == HumanoidArm.RIGHT ? 1 : -1);
		}
		out.set(root).translate(x / 16.0F, values[v + Y] / 16.0F, values[v + Z] / 16.0F);
		float xRot = values[v + X_ROT];
		float yRot = values[v + Y_ROT];
		float zRot = values[v + Z_ROT];
		if (xRot != 0.0F || yRot != 0.0F || zRot != 0.0F) {
			out.rotate(new org.joml.Quaternionf().rotationZYX(zRot, yRot, xRot));
		}
		float xScale = values[v + X_SCALE];
		float yScale = values[v + Y_SCALE];
		float zScale = values[v + Z_SCALE];
		if (xScale != 1.0F || yScale != 1.0F || zScale != 1.0F) {
			out.scale(xScale, yScale, zScale);
		}
	}
}
//#endif
