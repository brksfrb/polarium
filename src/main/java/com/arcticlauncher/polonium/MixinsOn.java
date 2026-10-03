package com.arcticlauncher.polonium;

import java.lang.reflect.Field;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

/**
 * The mixins every mod has on a class. Mixin's own record of applied mixins
 * is only kept in some setups, so this reads the set each class keeps of the
 * mixins targeting it (Mixin's internals: if they change, null, and callers
 * assume the worst).
 */
final class MixinsOn {
	private static final @Nullable Field MIXINS = field();

	private MixinsOn() {}

	/** The mixins on this class ({@code a.b.C}), or null if that can't be told. */
	@SuppressWarnings("unchecked")
	static @Nullable Set<IMixinInfo> of(String className) {
		if (MIXINS == null) {
			return null;
		}
		try {
			ClassInfo info = ClassInfo.forName(className.replace('.', '/'));
			return info == null ? null : (Set<IMixinInfo>) MIXINS.get(info);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** The id of the mod a mixin comes from (as Fabric notes it on the mixin's config); else its config's name. */
	static String mod(IMixinInfo mixin) {
		try {
			Object id = mixin.getConfig().getDecoration("fabric-modId");
			if (id != null) {
				return id.toString();
			}
			String source = mixin.getConfig().getCleanSourceId();
			return source != null ? source : mixin.getConfig().getName();
		} catch (RuntimeException e) {
			return mixin.getClassName();
		}
	}

	private static @Nullable Field field() {
		try {
			Field field = ClassInfo.class.getDeclaredField("mixins");
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
