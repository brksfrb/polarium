//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.arcticlauncher.polarium.gpu.ShaderSources;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The game looks shaders up here when compiling a pipeline; Polarium's are made from the game's own (ShaderSources). */
//#if MC >= 26.3
// 26.3: pipelines compile from the loaded shader configs (a ShaderSource).
@Mixin(net.minecraft.client.renderer.ShaderManager.Configs.class)
abstract class ShaderSourceMixin {
	@Shadow
	public abstract String getShader(Identifier id, ShaderType type);

	/** Polarium's vertex shaders, made from the game's (loaded in these same configs). */
	@Inject(method = "getShader", at = @At("HEAD"), cancellable = true)
	private void polarium$ownShaders(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
		if (type == ShaderType.VERTEX && ShaderSources.ours(id)) {
			cir.setReturnValue(ShaderSources.vertex(id, game -> getShader(game, ShaderType.VERTEX)));
		}
	}
}
//#else
@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$CompilationCache")
abstract class ShaderSourceMixin {
	@Shadow
	public abstract @org.jspecify.annotations.Nullable String getShaderSource(Identifier id, ShaderType type);

	/** Polarium's vertex shaders, made from the game's (loaded in this same cache). */
	@Inject(method = "getShaderSource", at = @At("HEAD"), cancellable = true)
	private void polarium$ownShaders(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
		if (type == ShaderType.VERTEX && ShaderSources.ours(id)) {
			cir.setReturnValue(ShaderSources.vertex(id, game -> getShaderSource(game, ShaderType.VERTEX)));
		}
	}
}
//#endif
//#endif
