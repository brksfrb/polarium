package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.pack.IndexedPackZip;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//#if MC >= 1.20.2
@Mixin(targets = "net.minecraft.server.packs.FilePackResources$SharedZipFileAccess")
//#else
@Mixin(net.minecraft.server.packs.FilePackResources.class)
//#endif
abstract class PackZipOwnerMixin {
	@WrapOperation(method = "getOrCreateZipFile", at = @At(value = "NEW",
			target = "(Ljava/io/File;)Ljava/util/zip/ZipFile;", remap = false))
	private ZipFile polarium$openIndexedZip(File file, Operation<ZipFile> original) throws IOException {
		return IndexedPackZip.ENABLED ? new IndexedPackZip(file, true) : original.call(file);
	}

	@WrapMethod(method = "getOrCreateZipFile")
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
}
