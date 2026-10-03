package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.AttributeValues;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** An entity's kept scale and name tag distances (see {@link AttributeValues}). */
@Mixin(LivingEntity.class)
abstract class LivingEntityAttributeValuesMixin implements AttributeValues.Holder2 {
	@Unique
	private long polarium$version = -1;
	@Unique
	private double polarium$nameDistance;
	@Unique
	private double polarium$belowNameDistance;
	@Unique
	private long polarium$scaleVersion = -1;
	@Unique
	private float polarium$scale;

	@Override
	public long polarium$version() {
		return polarium$version;
	}

	@Override
	public void polarium$version(long version) {
		polarium$version = version;
	}

	@Override
	public double polarium$nameDistance() {
		return polarium$nameDistance;
	}

	@Override
	public double polarium$belowNameDistance() {
		return polarium$belowNameDistance;
	}

	@Override
	public void polarium$nameDistances(double name, double belowName) {
		polarium$nameDistance = name;
		polarium$belowNameDistance = belowName;
	}

	@Override
	public long polarium$scaleVersion() {
		return polarium$scaleVersion;
	}

	@Override
	public float polarium$scale() {
		return polarium$scale;
	}

	@Override
	public void polarium$scale(float scale, long version) {
		polarium$scale = scale;
		polarium$scaleVersion = version;
	}

	@WrapMethod(method = "getScale")
	private float polarium$keptScale(Operation<Float> scale) {
		long version = AttributeValues.version((LivingEntity) (Object) this);
		if (polarium$scaleVersion == version) {
			return polarium$scale;
		}
		float value = scale.call();
		polarium$scale(value, version);
		return value;
	}
}
