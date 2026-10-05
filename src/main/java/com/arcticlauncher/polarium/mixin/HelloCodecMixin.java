package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.hello.HelloPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The server's channel list and Polarium's hello are read and written as raw bytes (see {@link HelloPayload}). */
@Mixin(targets = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$1")
abstract class HelloCodecMixin {
	@Inject(method = "findCodec", at = @At("HEAD"), cancellable = true)
	private void polarium$helloCodec(Identifier id, CallbackInfoReturnable<StreamCodec<?, ?>> cir) {
		if (HelloPayload.handles(id)) {
			cir.setReturnValue(HelloPayload.codec(id));
		}
	}
}
