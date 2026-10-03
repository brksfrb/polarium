package com.arcticlauncher.polonium;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

/**
 * The mixins every mod has on a class. Mixin's own record of applied mixins
 * is only kept in some setups, so this reads the set each class keeps of the
 * mixins targeting it (Mixin's internals: if they change, null, and callers
 * assume the worst).
 */
public final class MixinsOn {
	private static final @Nullable Field MIXINS = field();

	private MixinsOn() {}

	/** The mixins on this class ({@code a.b.C}), or null if that can't be told. */
	@SuppressWarnings("unchecked")
	public static @Nullable Set<IMixinInfo> of(String className) {
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
	public static String mod(IMixinInfo mixin) {
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

	/** Annotations of hooks into a method (their {@code method} names it). */
	private static final Set<String> HOOKS = Set.of("Lorg/spongepowered/asm/mixin/injection/Inject;",
			"Lorg/spongepowered/asm/mixin/injection/Redirect;", "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyArgs;", "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
			"Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
			"Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
			"Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;", "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
			"Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;", "Lcom/llamalad7/mixinextras/injector/ModifyReceiver;",
			"Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");

	/** Which of {@code methods} this mixin hooks or replaces (by name); a target it can't make out counts as one. */
	public static List<String> hooks(IMixinInfo mixin, Set<String> methods) {
		List<String> out = new ArrayList<>();
		ClassNode node;
		try {
			node = mixin.getClassNode(0);
		} catch (RuntimeException e) {
			out.add("(can't read " + mixin.getClassName() + ")");
			return out;
		}
		for (MethodNode method : node.methods) {
			List<AnnotationNode> annotations = new ArrayList<>();
			if (method.visibleAnnotations != null) {
				annotations.addAll(method.visibleAnnotations);
			}
			if (method.invisibleAnnotations != null) {
				annotations.addAll(method.invisibleAnnotations);
			}
			for (AnnotationNode annotation : annotations) {
				if ("Lorg/spongepowered/asm/mixin/Overwrite;".equals(annotation.desc) && methods.contains(method.name)) {
					out.add(method.name);
				}
				if (!HOOKS.contains(annotation.desc) || annotation.values == null) {
					continue;
				}
				for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
					if ("method".equals(annotation.values.get(i)) && annotation.values.get(i + 1) instanceof List<?> targets) {
						for (Object target : targets) {
							String name = targetName(String.valueOf(target));
							if (name == null || methods.contains(name)) {
								out.add(name == null ? String.valueOf(target) : name);
							}
						}
					}
				}
			}
		}
		return out;
	}

	/** A hook's target's method name (from {@code Lowner;name(desc)}, {@code name(desc)} or {@code name}); null for a pattern. */
	private static @org.jspecify.annotations.Nullable String targetName(String target) {
		String name = target;
		if (name.startsWith("<init>") || name.startsWith("<clinit>")) {
			// Constructors: not per frame.
			return "<init>";
		}
		int args = name.indexOf('(');
		int owner = name.indexOf(';');
		if (name.startsWith("L") && owner > 0 && (args < 0 || owner < args)) {
			name = name.substring(owner + 1);
			args = name.indexOf('(');
		}
		if (args >= 0) {
			name = name.substring(0, args);
		}
		return name.isEmpty() || !name.chars().allMatch(c -> Character.isJavaIdentifierPart(c) || c == '<' || c == '>') ? null : name;
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
