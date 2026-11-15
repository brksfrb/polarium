package com.arcticlauncher.polarium.mixin;

//#if MC >= 1.21.11
import com.arcticlauncher.polarium.gpu.GpuChests;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where a chest drawn on the GPU keeps its place in the instance data (see {@link GpuChests}), and
 * where that place is given up: when the block entity is removed or its block changes.
 */
@Mixin(BlockEntity.class)
abstract class GpuChestSlotMixin implements GpuChests.Slot {
	@Unique
	private Object polarium$gpu;
	@Unique
	private boolean polarium$lidOpen;

	@Override
	public Object polarium$gpu() {
		return polarium$gpu;
	}

	@Override
	public void polarium$gpu(Object value) {
		polarium$gpu = value;
	}

	@Override
	public boolean polarium$lidOpen() {
		return polarium$lidOpen;
	}

	@Override
	public void polarium$lidOpen(boolean open) {
		polarium$lidOpen = open;
	}

	@Inject(method = "setRemoved", at = @At("HEAD"))
	private void polarium$gpuRemoved(CallbackInfo ci) {
		if (polarium$gpu != null) {
			GpuChests.gone((BlockEntity) (Object) this);
		}
	}

	@Inject(method = "setBlockState", at = @At("HEAD"))
	private void polarium$gpuBlockChanged(net.minecraft.world.level.block.state.BlockState state, CallbackInfo ci) {
		if (polarium$gpu != null) {
			GpuChests.gone((BlockEntity) (Object) this);
		}
	}
}
//#endif
