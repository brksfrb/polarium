//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.SidebarCache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The scoreboard sidebar's lines, kept between frames while they're the same (see SidebarCache). */
@Mixin(Hud.class)
abstract class HudSidebarCacheMixin {
	@WrapOperation(method = "lambda$displayScoreboardSidebar$1", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"))
	private MutableComponent polarium$keptName(Team team, Component name, Operation<MutableComponent> format) {
		return SidebarCache.name(team, name, () -> format.call(team, name));
	}

	@WrapOperation(method = "lambda$displayScoreboardSidebar$1", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/scores/PlayerScoreEntry;formatValue(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/MutableComponent;"))
	private MutableComponent polarium$keptValue(PlayerScoreEntry entry, NumberFormat fallback, Operation<MutableComponent> format) {
		return SidebarCache.value(entry.value(), entry.numberFormatOverride(), fallback, () -> format.call(entry, fallback));
	}

	@WrapOperation(method = {"lambda$displayScoreboardSidebar$1", "displayScoreboardSidebar"}, require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"))
	private int polarium$keptWidth(Font font, FormattedText text, Operation<Integer> width) {
		return SidebarCache.width(text, () -> width.call(font, text));
	}
}
//#endif
