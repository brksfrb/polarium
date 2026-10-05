package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.hello.HelloPayload;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A server that lists {@code polarium:hello} in its channels gets Polarium's
 * hello, once per connection (see {@link HelloPayload}). The channel list is
 * dropped quietly afterwards, as the game does with messages it doesn't know.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class HelloHandlerMixin {
	@Shadow
	@Final
	protected Connection connection;

	/** The connection Polarium already said hello on. */
	private static Connection polarium$greeted;

	@Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V",
			at = @At("HEAD"), cancellable = true)
	private void polarium$hello(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
		if (!(packet.payload() instanceof HelloPayload payload)) {
			return;
		}
		ci.cancel();
		if (connection != polarium$greeted && payload.channels().contains(HelloPayload.HELLO.toString())) {
			polarium$greeted = connection;
			connection.send(new ServerboundCustomPayloadPacket(HelloPayload.hello()));
		}
	}
}
