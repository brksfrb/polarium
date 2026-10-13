//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.SwingAnimationType;

/**
 * A player's arm swing, read the same way on every version. 26.3 describes a swing as one
 * {@code currentSwing} (hand, animation, length) plus its progress; earlier versions kept the
 * attacking arm, the animation type and the progress as separate fields.
 */
public final class Swings {
	private Swings() {}

	/** How far the swing is, 0 to 1 (0: not swinging). */
	public static float progress(ArmedEntityRenderState state) {
		//#if MC >= 26.3
		return state.currentSwing == null ? 0.0F : state.swingAnimation;
		//#else
		return state.attackTime;
		//#endif
	}

	/** The arm swinging. */
	public static HumanoidArm arm(ArmedEntityRenderState state) {
		//#if MC >= 26.3
		return state.currentSwing == null ? state.mainArm : state.currentSwing.hand().asArm(state.mainArm);
		//#else
		return state.attackArm;
		//#endif
	}

	/** The swing's animation (NONE when not swinging). */
	public static SwingAnimationType type(ArmedEntityRenderState state) {
		//#if MC >= 26.3
		return state.currentSwing == null ? SwingAnimationType.NONE : state.currentSwing.animation().type();
		//#else
		return state.swingAnimationType;
		//#endif
	}

	/** Fill a light render state's swing the way the game's renderer does. */
	public static void extract(LivingEntity entity, ArmedEntityRenderState state, float partialTicks) {
		//#if MC >= 26.3
		state.currentSwing = entity.getCurrentSwing();
		state.swingAnimation = entity.getSwingAnimation(partialTicks);
		//#else
		state.attackTime = entity.getAttackAnim(partialTicks);
		//#endif
	}

	/** The swinging hand, for change detection (-1: none). */
	public static int handKey(LivingEntity entity) {
		//#if MC >= 26.3
		LivingEntity.SwingDescription swing = entity.getCurrentSwing();
		return swing == null ? -1 : swing.hand().ordinal();
		//#else
		return entity.swingingArm == null ? -1 : entity.swingingArm.ordinal();
		//#endif
	}
}
//#endif
