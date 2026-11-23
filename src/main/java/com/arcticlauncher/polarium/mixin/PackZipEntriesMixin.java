package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.pack.IndexedPackZip;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FilePackResources.class)
abstract class PackZipEntriesMixin {
	//#if MC >= 1.20.2
	@Shadow
	protected abstract String addPrefix(String path);
	//#endif

	@WrapOperation(method = "getNamespaces", at = @At(value = "INVOKE",
			target = "Ljava/util/zip/ZipFile;entries()Ljava/util/Enumeration;", remap = false))
	private Enumeration<? extends ZipEntry> polarium$namespaceEntries(ZipFile zip,
			Operation<Enumeration<? extends ZipEntry>> original, PackType type) {
		if (zip instanceof IndexedPackZip) {
			return ((IndexedPackZip) zip).namespaces(polarium$packPath(type.getDirectory() + "/"));
		}
		return original.call(zip);
	}

	//#if MC >= 1.19.3
	@WrapOperation(method = "listResources", at = @At(value = "INVOKE",
	//#else
	@WrapOperation(method = "getResources", at = @At(value = "INVOKE",
	//#endif
			target = "Ljava/util/zip/ZipFile;entries()Ljava/util/Enumeration;", remap = false))
	private Enumeration<? extends ZipEntry> polarium$resourceEntries(ZipFile zip,
			Operation<Enumeration<? extends ZipEntry>> original, PackType type, String namespace, String path) {
		if (zip instanceof IndexedPackZip) {
			String root = polarium$packPath(type.getDirectory() + "/" + namespace + "/");
			return ((IndexedPackZip) zip).under(root + path + "/");
		}
		return original.call(zip);
	}

	@Unique
	private String polarium$packPath(String path) {
		//#if MC >= 1.20.2
		return addPrefix(path);
		//#else
		return path;
		//#endif
	}
}
