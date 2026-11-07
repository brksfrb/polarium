//#if MC >= 26.2 || MC < 26.1
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.SidebarCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
//#if MC >= 26.1
import net.minecraft.client.gui.Hud;
//#endif
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The scoreboard sidebar's lines, kept between frames while they're the same (see SidebarCache). */
//#if MC >= 26.1
@Mixin(Hud.class)
//#else
// Before 26.1 the HUD is drawn by Gui (lines made in its lambda with only an intermediary name).
@Mixin(net.minecraft.client.gui.Gui.class)
//#endif
abstract class HudSidebarCacheMixin {
	//#if MC >= 26.1
	private static final String LINE = "lambda$displayScoreboardSidebar$1";
	//#else
	private static final String LINE = "method_55439";
	//#endif

	@WrapOperation(method = LINE, require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"))
	private MutableComponent polarium$keptName(Team team, Component name, Operation<MutableComponent> format) {
		return SidebarCache.name(team, name, () -> format.call(team, name));
	}

	@WrapOperation(method = LINE, require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/scores/PlayerScoreEntry;formatValue(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/MutableComponent;"))
	private MutableComponent polarium$keptValue(PlayerScoreEntry entry, NumberFormat fallback, Operation<MutableComponent> format) {
		return SidebarCache.value(entry.value(), entry.numberFormatOverride(), fallback, () -> format.call(entry, fallback));
	}

	@WrapOperation(method = {LINE, "displayScoreboardSidebar"}, require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"))
	private int polarium$keptWidth(Font font, FormattedText text, Operation<Integer> width) {
		return SidebarCache.width(text, () -> width.call(font, text));
	}
}
//#endif
