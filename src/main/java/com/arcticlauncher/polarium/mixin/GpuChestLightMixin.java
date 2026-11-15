package com.arcticlauncher.polarium.mixin;

//#if MC >= 1.21.11
import com.arcticlauncher.polarium.gpu.GpuChests;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Light changed in a section: its chests' light is read again (they keep it while nothing changes). */
@Mixin(ClientChunkCache.class)
abstract class GpuChestLightMixin {
	@Inject(method = "onLightUpdate", at = @At("HEAD"))
	private void polarium$chestLight(LightLayer layer, SectionPos pos, CallbackInfo ci) {
		GpuChests.lightChanged(pos.x(), pos.y(), pos.z());
	}
}
//#endif
