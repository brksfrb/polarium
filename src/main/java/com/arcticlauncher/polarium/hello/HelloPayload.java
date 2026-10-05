package com.arcticlauncher.polarium.hello;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Lets a server that wants to know learn that Polarium is here, so it can
 * recommend it (or not warn about slow crowds). The server lists
 * {@code polarium:hello} among the channels in its {@code minecraft:register};
 * Polarium then answers once with {@code {"version":"<its version>"}} as plain
 * UTF-8 JSON. Nothing about the player or the computer, and servers that
 * don't list the channel never hear anything.
 *
 * <p>Both messages are read and written as raw bytes, so this needs no Fabric
 * API. With Fabric API installed it owns channel registration and Polarium
 * stays quiet. When Arctic Client is installed and says it greets for Polarium
 * (it does from its 1.20.5+ builds), it sends this hello instead, because only
 * one mod can read the server's channel list.
 */
public record HelloPayload(CustomPacketPayload.Type<HelloPayload> type, byte[] data) implements CustomPacketPayload {
	public static final Identifier HELLO = Identifier.fromNamespaceAndPath("polarium", "hello");
	public static final Identifier REGISTER = Identifier.withDefaultNamespace("register");
	/** Anything bigger isn't a channel list. */
	private static final int MAX = 4096;

	/** Whether Polarium reads and writes the channel list and its hello itself. */
	private static final boolean ACTIVE = !FabricLoader.getInstance().isModLoaded("fabric-networking-api-v1") && !arcticGreets();

	/** Arctic Client declares {@code "arctic:greets": ["polarium"]} when it says hello for Polarium. */
	private static boolean arcticGreets() {
		return FabricLoader.getInstance().getModContainer("arctic").map(arctic -> {
			CustomValue greets = arctic.getMetadata().getCustomValue("arctic:greets");
			if (greets == null || greets.getType() != CustomValue.CvType.ARRAY) {
				return false;
			}
			for (CustomValue name : greets.getAsArray()) {
				if (name.getType() == CustomValue.CvType.STRING && "polarium".equals(name.getAsString())) {
					return true;
				}
			}
			return false;
		}).orElse(false);
	}

	/** Whether Polarium reads and writes this payload itself. */
	public static boolean handles(Identifier id) {
		return ACTIVE && (HELLO.equals(id) || REGISTER.equals(id));
	}

	public static StreamCodec<FriendlyByteBuf, HelloPayload> codec(Identifier id) {
		final CustomPacketPayload.Type<HelloPayload> type = new CustomPacketPayload.Type<>(id);
		return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.data()), buf -> {
			int n = buf.readableBytes();
			if (n > MAX) {
				buf.skipBytes(n);
				return new HelloPayload(type, new byte[0]);
			}
			byte[] data = new byte[n];
			buf.readBytes(data);
			return new HelloPayload(type, data);
		});
	}

	/** The channels a server announced in its {@code minecraft:register} (empty for any other payload). */
	public List<String> channels() {
		if (!REGISTER.equals(type().id())) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		for (String name : new String(data, StandardCharsets.UTF_8).split("\u0000")) {
			if (!name.isEmpty()) {
				out.add(name);
			}
		}
		return out;
	}

	/** {@code {"version":"<Polarium's version>"}} and nothing else. */
	public static HelloPayload hello() {
		String version = FabricLoader.getInstance().getModContainer("polarium")
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
		JsonObject json = new JsonObject();
		json.addProperty("version", version);
		return new HelloPayload(new CustomPacketPayload.Type<>(HELLO), json.toString().getBytes(StandardCharsets.UTF_8));
	}
}
