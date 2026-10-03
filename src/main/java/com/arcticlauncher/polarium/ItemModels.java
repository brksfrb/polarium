//#if MC >= 26.1
package com.arcticlauncher.polarium;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.CompositeModel;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.EmptyModel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Whether an item's model is fixed: the same whatever the time or its
 * holder, and worked out without shared state. Models that choose by
 * something that changes by itself aren't: a compass's needle, a clock's
 * face, a cooldown, a bow being drawn, the local time. Some of those also
 * share state while they're worked out (a spinning compass's one random
 * source), so they're worked out one at a time on any thread ({@link #LOCK}),
 * and an entity holding or wearing one is made in full every tick (see
 * TickInputs).
 *
 * A model choosing by something fixed for the item and its holder (where
 * it's drawn, its components, its trim, the holder's main hand) is fixed if
 * every model it chooses between is.
 */
public final class ItemModels {
	private static final Map<ItemModel, Boolean> FIXED = new ConcurrentHashMap<>();
	private static final EquipmentSlot[] HELD = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD};
	private static final int DEPTH = 8;

	private ItemModels() {}

	/** Whether every item the entity holds or wears on its head (the ones drawn as item models) has a fixed model. */
	public static boolean fixedItems(LivingEntity entity) {
		for (EquipmentSlot slot : HELD) {
			if (!fixed(entity.getItemBySlot(slot))) {
				return false;
			}
		}
		return true;
	}

	/** Whether the stack's model is fixed (see the class). Safe on any thread. */
	public static boolean fixed(ItemStack stack) {
		if (stack.isEmpty()) {
			return true;
		}
		Identifier id = stack.get(DataComponents.ITEM_MODEL);
		if (id == null) {
			return true;
		}
		return fixed(Minecraft.getInstance().getModelManager().getItemModel(id));
	}

	/** Whether this model is fixed (see the class; worked out once per model). Safe on any thread. */
	public static boolean fixed(ItemModel model) {
		Boolean known = FIXED.get(model);
		if (known == null) {
			known = fixed(model, 0);
			FIXED.put(model, known);
		}
		return known;
	}

	/**
	 * Held while a model that isn't fixed is worked out, on any thread (see
	 * ItemModelResolverLockMixin): some share state while they are (a
	 * spinning compass's random source), and the HUD works out its own while
	 * entities' states are made on the helpers.
	 */
	public static final Object LOCK = new Object();

	private static boolean fixed(ItemModel model, int depth) {
		if (depth > DEPTH) {
			return false;
		}
		if (model instanceof CuboidItemModelWrapper || model instanceof SpecialModelWrapper<?> || model instanceof EmptyModel
				|| model instanceof MissingItemModel) {
			return true;
		}
		try {
			if (model instanceof CompositeModel) {
				return all(field(CompositeModel.class, "models").get(model), depth);
			}
			if (model instanceof SelectItemModel<?>) {
				Object property = field(SelectItemModel.class, "property").get(model);
				if (property instanceof net.minecraft.client.renderer.item.properties.select.LocalTime) {
					return false;
				}
				// The models it chooses between, wherever the selector keeps them.
				Object selector = field(SelectItemModel.class, "models").get(model);
				boolean any = false;
				for (Class<?> c = selector.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
					for (Field f : c.getDeclaredFields()) {
						if (Modifier.isStatic(f.getModifiers())) {
							continue;
						}
						f.setAccessible(true);
						Object value = f.get(selector);
						if (value instanceof ItemModel || value instanceof Map<?, ?> || value instanceof Collection<?>) {
							any = true;
							if (!all(value, depth)) {
								return false;
							}
						}
					}
				}
				return any;
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
		// Conditions and ranges (compass, clock, cooldown, use, damage...), and anything a mod adds.
		return false;
	}

	/** Whether every model in it (a model, or a map's values, or a collection) is fixed. */
	private static boolean all(Object value, int depth) {
		if (value instanceof ItemModel model) {
			return fixed(model, depth + 1);
		}
		Collection<?> models = value instanceof Map<?, ?> map ? map.values() : value instanceof Collection<?> c ? c : null;
		if (models == null) {
			return false;
		}
		for (Object o : models) {
			if (o instanceof ItemModel model) {
				if (!fixed(model, depth + 1)) {
					return false;
				}
			} else if (o != null) {
				return false;
			}
		}
		return true;
	}

	private static Field field(Class<?> type, String name) throws NoSuchFieldException {
		Field f = type.getDeclaredField(name);
		f.setAccessible(true);
		return f;
	}
}
//#endif
