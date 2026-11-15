package com.arcticlauncher.polarium.mixin;

//#if MC >= 1.21.11
import com.arcticlauncher.polarium.gpu.GpuChests;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A section and its neighbours are to be drawn afresh (light sent by the server, say): their chests' light is read again. */
@Mixin(ClientLevel.class)
abstract class GpuChestDirtyMixin {
	@Inject(method = "setSectionDirtyWithNeighbors", at = @At("HEAD"))
	private void polarium$chestsDirty(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
		GpuChests.lightChanged(sectionX, sectionY, sectionZ);
	}
}
//#endif
