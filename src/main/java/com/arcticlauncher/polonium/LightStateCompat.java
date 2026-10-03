//#if MC >= 26.2
package com.arcticlauncher.polonium;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BiConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Other mods' per-frame work in making a player's render state, done for
 * them when Polonium only brings a kept state up to the frame (see
 * {@link LightStates}). Each is what the mod's own hook does, called the
 * same way (no compile-time dependency: looked up when the mod is there).
 */
final class LightStateCompat {
	private LightStateCompat() {}

	/**
	 * Player Animation Library (Emotecraft's): each frame, the player's
	 * animation manager gets the frame's tick delta, and the state the manager
	 * (as its AvatarRendererMixin does in extractRenderState). Null if the mod
	 * isn't there, or isn't as checked (1.2).
	 */
	static @Nullable BiConsumer<Object, Object> playerAnimationLibrary() {
		if (!FabricLoader.getInstance().isModLoaded("player_animation_library")) {
			return null;
		}
		try {
			ClassLoader loader = LightStateCompat.class.getClassLoader();
			Class<?> animated = Class.forName("com.zigythebird.playeranim.accessors.IAnimatedAvatar", false, loader);
			Class<?> manager = Class.forName("com.zigythebird.playeranim.animation.AvatarAnimManager", false, loader);
			Class<?> animationState = Class.forName("com.zigythebird.playeranim.accessors.IAvatarAnimationState", false, loader);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodHandle getManager = lookup.findVirtual(animated, "playerAnimLib$getAnimManager", MethodType.methodType(manager));
			MethodHandle setTickDelta = lookup.findVirtual(manager, "setTickDelta", MethodType.methodType(void.class, float.class));
			MethodHandle setManager = lookup.findVirtual(animationState, "playerAnimLib$setAnimManager",
					MethodType.methodType(void.class, manager));
			return (entity, state) -> {
				if (!animated.isInstance(entity) || !animationState.isInstance(state)) {
					return;
				}
				try {
					Object animations = getManager.invoke(entity);
					setTickDelta.invoke(animations, partialTick((Entity) entity, (EntityRenderState) state));
					setManager.invoke(state, animations);
				} catch (RuntimeException | Error e) {
					throw e;
				} catch (Throwable e) {
					throw new IllegalStateException(e);
				}
			};
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** The frame's partial tick for this entity, as the state was brought up with ({@code ageInTicks} is tick count plus it). */
	private static float partialTick(Entity entity, EntityRenderState state) {
		return state.ageInTicks - entity.tickCount;
	}
}
//#endif
