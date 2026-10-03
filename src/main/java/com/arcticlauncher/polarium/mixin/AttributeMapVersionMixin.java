package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.AttributeValues;
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
	private long polarium$version;

	@Override
	public long polarium$version() {
		return polarium$version;
	}

	@Inject(method = "onAttributeModified", at = @At("HEAD"))
	private void polarium$changed(AttributeInstance instance, CallbackInfo ci) {
		polarium$version++;
	}
}
