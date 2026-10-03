//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import java.util.List;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public interface PreparedFrameAccess {
	@Accessor("allSubmits")
	List<SubmitNode> polarium$allSubmits();
}
//#endif
