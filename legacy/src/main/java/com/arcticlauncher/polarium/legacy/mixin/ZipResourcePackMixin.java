package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.pack.IndexedPackZip;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.resource.ZipResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ZipResourcePack.class)
abstract class ZipResourcePackMixin {
	@WrapOperation(method = "getZipFile", at = @At(value = "NEW",
			target = "(Ljava/io/File;)Ljava/util/zip/ZipFile;", remap = false))
	private ZipFile polarium$openIndexedZip(File file, Operation<ZipFile> original) throws IOException {
		return IndexedPackZip.ENABLED ? new IndexedPackZip(file, false) : original.call(file);
	}

	@WrapMethod(method = "getZipFile")
	private ZipFile polarium$openOnce(Operation<ZipFile> original) {
		if (!IndexedPackZip.ENABLED) {
			return original.call();
		}
		synchronized (this) {
			return original.call();
		}
	}

	@WrapMethod(method = "close")
	private void polarium$closeZip(Operation<Void> original) {
		if (!IndexedPackZip.ENABLED) {
			original.call();
			return;
		}
		synchronized (this) {
			original.call();
		}
	}

	@WrapOperation(method = "getNamespaces", at = @At(value = "INVOKE",
			target = "Ljava/util/zip/ZipFile;entries()Ljava/util/Enumeration;", remap = false))
	private Enumeration<? extends ZipEntry> polarium$namespaceEntries(ZipFile zip,
			Operation<Enumeration<? extends ZipEntry>> original) {
		return zip instanceof IndexedPackZip ? ((IndexedPackZip) zip).namespaces("assets/") : original.call(zip);
	}
}
