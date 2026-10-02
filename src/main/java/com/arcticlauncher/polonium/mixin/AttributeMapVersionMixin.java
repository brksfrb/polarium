package com.arcticlauncher.polonium.mixin;

import com.arcticlauncher.polonium.AttributeValues;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Each attribute map counts its changes (see AttributeValues#version). */
@Mixin(AttributeMap.class)
abstract class AttributeMapVersionMixin implements AttributeValues.Versioned {
	@Unique
	private long polonium$version;

	@Override
	public long polonium$version() {
		return polonium$version;
	}

	@Inject(method = "onAttributeModified", at = @At("HEAD"))
	private void polonium$changed(AttributeInstance instance, CallbackInfo ci) {
		polonium$version++;
	}
}
