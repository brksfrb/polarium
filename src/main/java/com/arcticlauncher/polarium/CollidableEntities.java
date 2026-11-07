package com.arcticlauncher.polarium;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Primed TNT looks for entities it could bump into on every move (boats,
 * shulkers and the like), which means going through every entity near it:
 * with thousands of TNT in a pile that is most of the client's tick, and it
 * finds nothing. Only entities of classes that override
 * {@code canBeCollidedWith} can ever answer yes, so when the level has none of
 * those (checked once a tick, see {@link #update}), the search is skipped and
 * the answer is the same empty list.
 */
public final class CollidableEntities {
	//#if MC >= 26.1
	private static final String CAN_BE_COLLIDED_WITH = "canBeCollidedWith";
	//#else
	// Obfuscated: the name the game runs with.
	private static final String CAN_BE_COLLIDED_WITH = Intermediary.method("net.minecraft.class_1297", "method_30948", "(Lnet/minecraft/class_1297;)Z");
	//#endif
	private static final ClassValue<Boolean> OVERRIDES = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			try {
				return type.getMethod(CAN_BE_COLLIDED_WITH, Entity.class).getDeclaringClass() != Entity.class;
			} catch (ReflectiveOperationException | LinkageError e) {
				// Can't tell: assume it may.
				return true;
			}
		}
	};
	/** The client level has no entity that can be bumped into (as of the start of this tick). */
	private static volatile boolean none;

	private CollidableEntities() {}

	/** The level is about to tick its entities. */
	public static void update(Iterable<Entity> entities) {
		for (Entity entity : entities) {
			if (OVERRIDES.get(entity.getClass())) {
				none = false;
				return;
			}
		}
		none = true;
	}

	/** Whether the search for things {@code entity} could bump into can be skipped: it would find nothing. */
	public static boolean skips(Level level, Entity entity) {
		return none && level.isClientSide() && entity.getClass() == net.minecraft.world.entity.item.PrimedTnt.class;
	}
}
