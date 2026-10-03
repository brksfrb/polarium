package com.arcticlauncher.polarium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What the crowd path needs to place an entity's model the way {@link LivingEntityRenderer#submit} does. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccess {
	@Invoker("setupRotations")
	void polarium$setupRotations(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float entityScale);

	@Invoker("scale")
	void polarium$scale(LivingEntityRenderState state, PoseStack poseStack);

	@Accessor("layers")
	List<RenderLayer<?, ?>> polarium$layers();

	//#if MC >= 26.2
	@Invoker("solveBodyRot")
	static float polarium$solveBodyRot(net.minecraft.world.entity.LivingEntity entity, float headRot, float partialTicks) {
		throw new AssertionError();
	}

	@Invoker("extractNameTags")
	void polarium$extractLivingNameTags(net.minecraft.world.entity.LivingEntity entity, LivingEntityRenderState state, float partialTicks);
	//#endif
}
