//#if MC < 26.1
package com.arcticlauncher.polarium;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;

/**
 * Minecraft's names as they are when the game runs, for code that looks members up by name
 * (reflection, other mods' mixins). Before 26.1 the game is obfuscated: a player's game runs
 * with intermediary names ({@code net.minecraft.class_1297}, {@code method_30948}), a
 * development run with Mojang's. The names here are intermediary (Fabric's, stable across
 * runs); Fabric Loader maps them to whichever the game runs with.
 */
public final class Intermediary {
	private static final String NAMESPACE = "intermediary";

	private Intermediary() {}

	private static MappingResolver resolver() {
		return FabricLoader.getInstance().getMappingResolver();
	}

	/** A class's name at run time ({@code a.b.C}), from its intermediary name. */
	public static String cls(String intermediary) {
		return resolver().mapClassName(NAMESPACE, intermediary);
	}

	/** A method's name at run time (owner and descriptor in intermediary names). */
	public static String method(String owner, String name, String descriptor) {
		return resolver().mapMethodName(NAMESPACE, owner, name, descriptor);
	}

	/** A field's name at run time (owner and descriptor in intermediary names). */
	public static String field(String owner, String name, String descriptor) {
		return resolver().mapFieldName(NAMESPACE, owner, name, descriptor);
	}
}
//#endif
