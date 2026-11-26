package com.arcticlauncher.polarium.legacy;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Which resource pack addresses a server may send on 1.8.9. The game takes any address: a
 * {@code level://} one is opened as a file under {@code saves/} with {@code ..} allowed, and any other
 * scheme (such as {@code file:}) is fetched by the downloader. Either way the server learns from the
 * answer whether a file exists on the player's computer, and the file is loaded as a pack.
 *
 * <p>Allowed, as Minecraft itself does from 1.12 on: http and https, and {@code level://<world>/resources.zip}
 * (the pack of a singleplayer world) when it stays inside {@code saves/}.
 */
public final class ServerPackUrls {
	private static final String LEVEL = "level://";
	private static final String WORLD_PACK = "/resources.zip";

	private ServerPackUrls() {
	}

	public static boolean allowed(String url, File saves) {
		if (url == null) {
			return false;
		}
		String lower = url.toLowerCase(Locale.ROOT);
		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			return true;
		}
		if (!url.startsWith(LEVEL)) {
			return false;
		}
		String path = url.substring(LEVEL.length());
		if (!path.endsWith(WORLD_PACK) || path.startsWith("/") || path.indexOf('\\') >= 0
				|| path.indexOf(':') >= 0 || path.indexOf('\0') >= 0) {
			return false;
		}
		String world = path.substring(0, path.length() - WORLD_PACK.length());
		if (world.isEmpty() || world.indexOf('/') >= 0 || world.equals(".") || world.equals("..")) {
			return false;
		}
		try {
			File root = saves.getCanonicalFile();
			File pack = new File(saves, path).getCanonicalFile();
			File parent = pack.getParentFile();
			return parent != null && root.equals(parent.getParentFile());
		} catch (IOException e) {
			return false;
		}
	}
}
