package com.arcticlauncher.polonium;

import net.minecraft.world.entity.LivingEntity;

/**
 * A few attributes read for every entity every frame (its scale, how far
 * its name tag shows), kept per entity as values. Looking them up means a
 * map and an attribute object per entity, a frame; with thousands of players
 * that's a noticeable share of making their render states. They hardly ever
 * change: kept values are read again only after the entity's own
 * attributes changed (see {@link #version}).
 */
public final class AttributeValues {
	/** Kept values on an entity (added to LivingEntity by a mixin). */
	public interface Holder2 {
		long polonium$version();

		void polonium$version(long version);

		double polonium$nameDistance();

		double polonium$belowNameDistance();

		void polonium$nameDistances(double name, double belowName);

		long polonium$scaleVersion();

		float polonium$scale();

		void polonium$scale(float scale, long version);
	}

	/** Counts an attribute map's changes (added to AttributeMap by a mixin). */
	public interface Versioned {
		long polonium$version();
	}

	private AttributeValues() {}

	/**
	 * How many times this entity's attributes changed: values kept from
	 * another version are stale. Every change to a value goes through its
	 * map (AttributeInstance.setDirty), so one entity's changes don't make
	 * every other entity's kept values stale.
	 */
	public static long version(LivingEntity entity) {
		return ((Versioned) entity.getAttributes()).polonium$version();
	}
}
