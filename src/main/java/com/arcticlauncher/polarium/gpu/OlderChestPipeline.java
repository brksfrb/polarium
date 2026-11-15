//#if MC < 26.2
package com.arcticlauncher.polarium.gpu;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

/** Copy every pipeline field, changing only the shader, vertex format and uniform list. */
final class OlderChestPipeline {
    static RenderPipeline twin(RenderPipeline original, Identifier shader, VertexFormat format) throws ReflectiveOperationException {
        var constructor = RenderPipeline.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        // Constructor order is checked against the mapped game sources on both older targets.
        var uniforms = new java.util.ArrayList<>(original.getUniforms());
        uniforms.add(new RenderPipeline.UniformDescription("PolariumChestDraw", UniformType.UNIFORM_BUFFER));
        uniforms.add(new RenderPipeline.UniformDescription("PolariumChests", UniformType.UNIFORM_BUFFER));
        //#if MC >= 26.1
        return (RenderPipeline)constructor.newInstance(shader, shader, original.getFragmentShader(), original.getShaderDefines(),
            original.getSamplers(), uniforms, original.getColorTargetState(), original.getDepthStencilState(),
            original.getPolygonMode(), original.isCull(), format, original.getVertexFormatMode(), original.getSortKey());
        //#else
        return (RenderPipeline)constructor.newInstance(shader, shader, original.getFragmentShader(), original.getShaderDefines(),
            original.getSamplers(), uniforms, original.getBlendFunction(), original.getDepthTestFunction(),
            original.getPolygonMode(), original.isCull(), original.isWriteColor(), original.isWriteAlpha(), original.isWriteDepth(),
            original.getColorLogic(), format, original.getVertexFormatMode(), original.getDepthBiasScaleFactor(),
            original.getDepthBiasConstant(), original.getSortKey());
        //#endif
    }
}
//#endif
