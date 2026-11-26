package com.arcticlauncher.polarium.legacy.mixin;

import com.arcticlauncher.polarium.legacy.Flags;
import com.arcticlauncher.polarium.legacy.ServerPackUrls;
import java.io.File;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.play.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.play.ResourcePackSendS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A server's resource pack address that could reach files outside a world's pack is refused (see ServerPackUrls). */
@Mixin(ClientPlayNetworkHandler.class)
abstract class ServerPackUrlMixin {
	@Shadow @Final private ClientConnection connection;

	@Inject(method = "onResourcePackSend", at = @At("HEAD"), cancellable = true)
	private void polarium$refusePathEscape(ResourcePackSendS2CPacket packet, CallbackInfo ci) {
		if (!Flags.PACK_URLS) {
			return;
		}
		String url = packet.getURL();
		File saves = new File(MinecraftClient.getInstance().runDirectory, "saves");
		if (!ServerPackUrls.allowed(url, saves)) {
			System.out.println("[Polarium] refused a server resource pack address that could reach other files");
			connection.send(new ResourcePackStatusC2SPacket(packet.getHash(), ResourcePackStatusC2SPacket.Status.FAILED_DOWNLOAD));
			ci.cancel();
		}
	}
}
