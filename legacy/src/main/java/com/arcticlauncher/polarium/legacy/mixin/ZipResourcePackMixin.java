package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.pack.IndexedPackZip;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.resource.AbstractFileResourcePack;
import net.minecraft.resource.ZipResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ZipResourcePack.class)
abstract class ZipResourcePackMixin extends AbstractFileResourcePack {
	@Shadow private ZipFile file;

	@Shadow public abstract void close() throws IOException;

	@Unique private BasicFileAttributes polarium$openedFile;

	protected ZipResourcePackMixin(File base) {
		super(base);
	}

	@WrapOperation(method = "getZipFile", at = @At(value = "NEW",
			target = "(Ljava/io/File;)Ljava/util/zip/ZipFile;", remap = false))
	private ZipFile polarium$openIndexedZip(File file, Operation<ZipFile> original) throws IOException {
		BasicFileAttributes attributes = polarium$readFileAttributes();
		ZipFile zip = IndexedPackZip.ENABLED ? new IndexedPackZip(file, false) : original.call(file);
		polarium$openedFile = attributes;
		return zip;
	}

	@WrapMethod(method = "getZipFile")
	private ZipFile polarium$openOnce(Operation<ZipFile> original) {
		synchronized (this) {
			return original.call();
		}
	}

	@WrapMethod(method = "close")
	private void polarium$closeZip(Operation<Void> original) {
		synchronized (this) {
			original.call();
			polarium$openedFile = null;
		}
	}

	@WrapOperation(method = "getNamespaces", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/resource/ZipResourcePack;getZipFile()Ljava/util/zip/ZipFile;"))
	private ZipFile polarium$openCurrentZip(ZipResourcePack pack, Operation<ZipFile> original) throws IOException {
		synchronized (this) {
			// The legacy loader reuses selected pack objects across resource reloads.
			if (file != null && !polarium$isSameFile(polarium$openedFile, polarium$readFileAttributes())) {
				close();
			}
			return original.call(pack);
		}
	}

	@Unique
	private BasicFileAttributes polarium$readFileAttributes() {
		try {
			return Files.readAttributes(base.toPath(), BasicFileAttributes.class);
		} catch (IOException e) {
			return null;
		}
	}

	@Unique
	private static boolean polarium$isSameFile(BasicFileAttributes oldFile, BasicFileAttributes currentFile) {
		// Some file systems do not supply a key. Compare the available metadata in that case.
		return oldFile != null && currentFile != null
				&& Objects.equals(oldFile.fileKey(), currentFile.fileKey())
				&& oldFile.size() == currentFile.size()
				&& oldFile.creationTime().equals(currentFile.creationTime())
				&& oldFile.lastModifiedTime().equals(currentFile.lastModifiedTime());
	}

	@WrapOperation(method = "getNamespaces", at = @At(value = "INVOKE",
			target = "Ljava/util/zip/ZipFile;entries()Ljava/util/Enumeration;", remap = false))
	private Enumeration<? extends ZipEntry> polarium$namespaceEntries(ZipFile zip,
			Operation<Enumeration<? extends ZipEntry>> original) {
		return zip instanceof IndexedPackZip ? ((IndexedPackZip) zip).namespaces("assets/") : original.call(zip);
	}
}
