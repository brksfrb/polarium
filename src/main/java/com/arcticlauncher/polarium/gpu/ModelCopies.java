//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.jspecify.annotations.Nullable;
import sun.misc.Unsafe;

/**
 * Private copies of a model for the helper threads. The game poses a model
 * ({@code setupAnim}) by writing its parts, and one model object serves every
 * entity, so posing could only happen on the render thread, one entity at a
 * time. A copy has its own parts (same tree, same starting poses) and shares
 * everything else, so each thread can pose entities with the game's own code
 * at the same time.
 *
 * Only models made of parts, lists and maps of parts, plain values and
 * functions are copied (players, armor, most mobs' simple models); anything
 * holding other objects might keep posing state in them, so it stays on the
 * render thread.
 */
final class ModelCopies {
	/** One model's copy, with its parts in the mesh's order. */
	static final class Copy {
		final Model<?> model;
		final ModelPart[] parts;

		private Copy(Model<?> model, ModelPart[] parts) {
			this.model = model;
			this.parts = parts;
		}
	}

	private static final Map<Class<?>, Boolean> COPYABLE = new ConcurrentHashMap<>();
	private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();
	private static final @Nullable Unsafe UNSAFE = unsafe();
	/** Each thread's copies, by original model. */
	private static final ThreadLocal<Mine> COPIES = ThreadLocal.withInitial(Mine::new);

	private ModelCopies() {}

	/** Models checked so far (render thread): whether each can be copied. */
	private static final Map<Model<?>, Boolean> CHECKED = new java.util.WeakHashMap<>();

	/**
	 * Whether this model can be copied: its kind holds only copyable fields,
	 * and lists and maps whose element types aren't declared (Fabric API adds
	 * a raw name-to-part map, for one) hold only parts in this model.
	 * Render thread.
	 */
	static boolean copyable(Model<?> model) {
		if (UNSAFE == null || !COPYABLE.computeIfAbsent(model.getClass(), ModelCopies::check)) {
			return false;
		}
		return CHECKED.computeIfAbsent(model, ModelCopies::holdsOnlyParts);
	}

	private static boolean holdsOnlyParts(Model<?> model) {
		try {
			for (Field field : fields(model.getClass())) {
				Object value = field.get(model);
				Collection<?> contents = value instanceof List<?> list ? list : value instanceof Map<?, ?> map ? map.values() : null;
				if (contents != null) {
					for (Object o : contents) {
						if (o != null && !(o instanceof ModelPart)) {
							org.slf4j.LoggerFactory.getLogger("Polarium").info("Polarium: {} is posed on the render thread ({} holds a {})",
									model.getClass().getSimpleName(), field.getName(), o.getClass().getSimpleName());
							return false;
						}
					}
				}
			}
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** This thread's copy of {@code model} ({@code meshParts}: the original's parts in mesh order). */
	static Copy forThisThread(Model<?> model, ModelPart[] meshParts) {
		return COPIES.get().copy(model, meshParts);
	}

	/** This thread's copies: fetched once by a caller that looks up many (a thread-local lookup per model is costly). */
	static Mine mine() {
		return COPIES.get();
	}

	/**
	 * One thread's copies. The few models a crowd is posed with are found in
	 * a short list of the last ones used; the rest in a map held weakly
	 * (models are replaced on resource reloads).
	 */
	static final class Mine {
		private static final int RECENT = 16;
		private final Map<Model<?>, Copy> all = new java.util.WeakHashMap<>();
		private final Model<?>[] recentModels = new Model<?>[RECENT];
		private final Copy[] recentCopies = new Copy[RECENT];
		private int next;

		Copy copy(Model<?> model, ModelPart[] meshParts) {
			for (int i = 0; i < RECENT; i++) {
				if (recentModels[i] == model) {
					return recentCopies[i];
				}
			}
			Copy copy = all.get(model);
			if (copy == null) {
				copy = ModelCopies.copy(model, meshParts);
				all.put(model, copy);
			}
			recentModels[next] = model;
			recentCopies[next] = copy;
			next = (next + 1) % RECENT;
			return copy;
		}
	}

	private static Copy copy(Model<?> original, ModelPart[] meshParts) {
		try {
			Map<ModelPart, ModelPart> parts = new IdentityHashMap<>();
			Model<?> copy = (Model<?>) UNSAFE.allocateInstance(original.getClass());
			for (Field field : fields(original.getClass())) {
				field.set(copy, mapped(field.get(original), parts));
			}
			ModelPart[] ordered = new ModelPart[meshParts.length];
			for (int i = 0; i < meshParts.length; i++) {
				ordered[i] = part(meshParts[i], parts);
			}
			return new Copy(copy, ordered);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("can't copy " + original.getClass().getName(), e);
		}
	}

	/** A field's value in the copy: parts (and lists and maps of them) copied, the rest shared. */
	@SuppressWarnings("unchecked")
	private static Object mapped(Object value, Map<ModelPart, ModelPart> parts) throws ReflectiveOperationException {
		if (value instanceof ModelPart part) {
			return part(part, parts);
		}
		if (value instanceof List<?> list) {
			List<Object> out = new ArrayList<>(list.size());
			for (Object o : list) {
				out.add(mapped(o, parts));
			}
			return out;
		}
		if (value instanceof Map<?, ?> map) {
			Map<Object, Object> out = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : map.entrySet()) {
				out.put(e.getKey(), mapped(e.getValue(), parts));
			}
			return out;
		}
		if (value != null && !(value instanceof Function<?, ?>) && plainData(value.getClass(), 0)) {
			return copyData(value);
		}
		return value;
	}

	/** A part's copy (its children too), made once per part. */
	private static ModelPart part(ModelPart original, Map<ModelPart, ModelPart> parts) throws ReflectiveOperationException {
		ModelPart copy = parts.get(original);
		if (copy != null) {
			return copy;
		}
		copy = (ModelPart) UNSAFE.allocateInstance(ModelPart.class);
		parts.put(original, copy);
		for (Field field : fields(ModelPart.class)) {
			Object value = field.get(original);
			field.set(copy, value instanceof Map<?, ?> ? mapped(value, parts) : value);
		}
		return copy;
	}

	/** Every instance field of the class and its superclasses, made writable. */
	private static List<Field> fields(Class<?> type) {
		return FIELDS.computeIfAbsent(type, t -> {
			List<Field> out = new ArrayList<>();
			for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
				for (Field field : c.getDeclaredFields()) {
					if (!Modifier.isStatic(field.getModifiers())) {
						field.setAccessible(true);
						out.add(field);
					}
				}
			}
			return out;
		});
	}

	private static final Map<Class<?>, Boolean> PLAIN = new ConcurrentHashMap<>();
	private static final int PLAIN_DEPTH = 4;

	/** Values shared as they are: they can't change. */
	private static boolean immutable(Class<?> type) {
		return type.isPrimitive() || type == String.class || type.isEnum() || type == Float.class || type == Double.class
				|| type == Integer.class || type == Long.class || type == Short.class || type == Byte.class || type == Boolean.class
				|| type == Character.class;
	}

	/**
	 * A small value object: only immutable values, primitive arrays and other
	 * such objects (other mods add some to models, as scratch space while
	 * posing: Player Animation Library's bones, say, a name and three
	 * vectors). Each copy of the model gets its own.
	 */
	private static boolean plainData(Class<?> type, int depth) {
		if (immutable(type)) {
			return true;
		}
		if (type.isArray()) {
			return type.getComponentType().isPrimitive();
		}
		if (depth > PLAIN_DEPTH || type.isInterface() || Modifier.isAbstract(type.getModifiers()) || type == Object.class
				|| ModelPart.class.isAssignableFrom(type) || Model.class.isAssignableFrom(type)) {
			return false;
		}
		Boolean known = PLAIN.get(type);
		if (known != null) {
			return known;
		}
		boolean plain = true;
		for (Field field : fields(type)) {
			if (!plainData(field.getType(), depth + 1)) {
				plain = false;
				break;
			}
		}
		PLAIN.put(type, plain);
		return plain;
	}

	/** A value object's own copy (see {@link #plainData}). */
	private static Object copyData(Object value) throws ReflectiveOperationException {
		Class<?> type = value.getClass();
		if (immutable(type)) {
			return value;
		}
		if (type.isArray()) {
			int length = java.lang.reflect.Array.getLength(value);
			Object out = java.lang.reflect.Array.newInstance(type.getComponentType(), length);
			System.arraycopy(value, 0, out, 0, length);
			return out;
		}
		if (!plainData(type, 0)) {
			throw new IllegalStateException(type.getName() + " isn't a plain value object");
		}
		Object copy = UNSAFE.allocateInstance(type);
		for (Field field : fields(type)) {
			Object v = field.get(value);
			field.set(copy, v == null ? null : copyData(v));
		}
		return copy;
	}

	/** Only parts, lists of parts, maps to parts, plain values and value objects, strings, enums and functions. */
	private static boolean check(Class<?> type) {
		try {
			for (Field field : fields(type)) {
				Class<?> t = field.getType();
				boolean plain = t.isPrimitive() || t == String.class || t.isEnum() || t == ModelPart.class || t == Function.class || plainData(t, 0)
						|| (t == List.class && partsOrUndeclared(typeArgument(field, 0)))
						|| (t == Map.class && partsOrUndeclared(typeArgument(field, 1)));
				if (!plain) {
					org.slf4j.LoggerFactory.getLogger("Polarium").info("Polarium: {} is posed on the render thread ({} {} can't be copied)",
							type.getSimpleName(), field.getGenericType().getTypeName(), field.getName());
					return false;
				}
			}
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Declared as parts, or not declared (raw): the model's own contents are checked then. */
	private static boolean partsOrUndeclared(java.lang.reflect.@Nullable Type type) {
		return type == null || type == ModelPart.class;
	}

	private static java.lang.reflect.@Nullable Type typeArgument(Field field, int index) {
		if (field.getGenericType() instanceof java.lang.reflect.ParameterizedType p && p.getActualTypeArguments().length > index) {
			return p.getActualTypeArguments()[index];
		}
		return null;
	}

	private static @Nullable Unsafe unsafe() {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			return (Unsafe) f.get(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
//#endif
