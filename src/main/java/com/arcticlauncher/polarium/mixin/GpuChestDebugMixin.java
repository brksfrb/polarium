package com.arcticlauncher.polarium.mixin;

//#if MC >= 26.2
import com.arcticlauncher.polarium.gpu.GpuChests;
import net.minecraft.client.gui.components.debug.DebugEntryFps;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Under F3's fps line (shown in every F3 profile): whether Polarium draws the chests, and why not. */
@Mixin(DebugEntryFps.class)
abstract class GpuChestDebugMixin {
	@Inject(method = "display", at = @At("TAIL"))
	private void polarium$chestLine(DebugScreenDisplayer displayer, Level serverOrClientLevel, LevelChunk clientChunk, LevelChunk serverChunk,
			CallbackInfo ci) {
		displayer.addPriorityLine(GpuChests.debugLine());
	}
}
//#endif
