package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.FaceBuckets;
import com.arcticlauncher.polarium.legacy.SectionData;
import net.minecraft.client.render.world.ChunkRenderHelperImpl;
import net.minecraft.client.util.GlAllocationUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A section's face bucket lists go with the section. */
@Mixin(ChunkRenderHelperImpl.class)
abstract class ListedSectionDeleteMixin {
	@Inject(method = "delete", at = @At("RETURN"))
	private void polarium$deleteBuckets(CallbackInfo ci) {
		SectionData data = (SectionData) this;
		if (data.polarium$bucketLists() != 0) {
			GlAllocationUtils.deleteLists(data.polarium$bucketLists(), FaceBuckets.LAYERS * FaceBuckets.BUCKETS);
			data.polarium$setBucketLists(0);
		}
	}
}
