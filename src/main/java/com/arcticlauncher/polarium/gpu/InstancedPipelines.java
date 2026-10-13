//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.shaders.UniformType;
//#if MC >= 26.3
import com.mojang.blaze3d.shaders.ShaderType;
//#endif
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * For each of Minecraft's entity pipelines, a twin that differs only in its
 * vertex shader and vertex data: same fragment shader and defines, blending,
 * depth, culling and textures, so pixels come out the same.
 */
final class InstancedPipelines {
	/** Instanced meshes: where each draw's entities start, and every entity's data. */
	static final BindGroupLayout INSTANCES = BindGroupLayout.builder()
			.withUniform("PolariumDraw", UniformType.UNIFORM_BUFFER)
			.withUniform("PolariumInstances", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
			.build();
	/** Streams whose vertices name their own entity: only the entities' data. */
	static final BindGroupLayout ENTITY_DATA = BindGroupLayout.builder()
			.withUniform("PolariumInstances", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
			.build();
	/** Twins by original pipeline, then by vertex shader. */
	private static final Map<RenderPipeline, Map<Identifier, RenderPipeline>> TWINS = new IdentityHashMap<>();
	private static @Nullable Constructor<RenderPipeline> constructor;

	private InstancedPipelines() {}

	/** Whether this pipeline is one of the game's pipelines for {@code vertexShader} (whose instanced twin Polarium has). */
	static boolean supports(RenderPipeline pipeline, Identifier vertexShader) {
		return vertexShader.equals(vertexShader(pipeline))
				&& pipeline.getPrimitiveTopology() == PrimitiveTopology.QUADS
				&& DefaultVertexFormat.ENTITY.equals(pipeline.getVertexFormatBinding(0));
	}

	static RenderPipeline twin(RenderPipeline original, Identifier vertexShader) {
		return twin(original, vertexShader, ModelMesh.FORMAT, INSTANCES);
	}

	/** {@code original} with this vertex shader, vertex format and extra bind group. */
	static RenderPipeline twin(RenderPipeline original, Identifier vertexShader, VertexFormat format, BindGroupLayout layout) {
		return twin(original, vertexShader, layout, format);
	}

	/** {@code original} with this vertex shader, extra bind group and these vertex buffers (bindings 0, 1, ...). */
	static RenderPipeline twin(RenderPipeline original, Identifier vertexShader, BindGroupLayout layout, VertexFormat... formats) {
		Map<Identifier, RenderPipeline> twins = TWINS.computeIfAbsent(original, p -> new java.util.HashMap<>());
		RenderPipeline twin = twins.get(vertexShader);
		if (twin == null) {
			// The pipeline keeps one slot per possible binding: fill ours, clear the rest.
			//#if MC >= 26.3
			VertexFormat[] bindings = original.getVertexFormatBindings().toArray(new VertexFormat[0]);
			//#else
			VertexFormat[] bindings = original.getVertexFormatBindings().clone();
			//#endif
			java.util.Arrays.fill(bindings, null);
			System.arraycopy(formats, 0, bindings, 0, formats.length);
			twin = create(original, vertexShader, layout, bindings);
			// Compiled now: a shader that doesn't compile would otherwise draw nothing, silently.
			if (!compiles(twin)) {
				throw new IllegalStateException("the GPU couldn't compile Polarium's version of " + original.getLocation());
			}
			twins.put(vertexShader, twin);
		}
		return twin;
	}

	private static RenderPipeline create(RenderPipeline original, Identifier vertexShader, BindGroupLayout layout, VertexFormat[] bindings) {
		List<BindGroupLayout> layouts = new ArrayList<>(original.getBindGroupLayouts());
		layouts.add(layout);
		Identifier location = Identifier.fromNamespaceAndPath("polarium",
				"instanced/" + original.getLocation().getNamespace() + "/" + original.getLocation().getPath() + "/" + vertexShader.getPath());
		try {
			//#if MC >= 26.3
			java.util.Map<ShaderType, Identifier> shaders = new java.util.EnumMap<>(ShaderType.class);
			shaders.putAll(original.getShaders());
			shaders.put(ShaderType.VERTEX, vertexShader);
			return constructor().newInstance(location, shaders, original.getShaderDefines(), List.copyOf(layouts),
					original.getColorTargetStates().toArray(new ColorTargetState[0]), original.getDepthStencilState(),
					original.getPolygonMode(), original.isCull(), bindings, original.getPrimitiveTopology(),
					original.pushConstantSize(), original.getSortKey());
			//#else
			return constructor().newInstance(location, vertexShader, original.getFragmentShader(),
					original.getShaderDefines(), List.copyOf(layouts), original.getColorTargetStates().clone(),
					original.getDepthStencilState(), original.getPolygonMode(), original.isCull(), bindings,
					original.getPrimitiveTopology(), original.getSortKey());
			//#endif
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("can't make an instanced twin of " + original, e);
		}
	}

	/** A pipeline's vertex shader (26.3 keeps the shaders in one map by stage). */
	static @Nullable Identifier vertexShader(RenderPipeline pipeline) {
		//#if MC >= 26.3
		return pipeline.getShaders().get(ShaderType.VERTEX);
		//#else
		return pipeline.getVertexShader();
		//#endif
	}

	/** Compiles now (a shader that doesn't would draw nothing, silently). */
	static boolean compiles(RenderPipeline pipeline) {
		//#if MC >= 26.3
		return RenderSystem.getCompiledPipelineNullable(pipeline) != null;
		//#else
		return RenderSystem.getDevice().precompilePipeline(pipeline).isValid();
		//#endif
	}

	/** RenderPipeline's own constructor (the builder can't copy an existing pipeline's settings). */
	@SuppressWarnings("unchecked")
	private static Constructor<RenderPipeline> constructor() throws NoSuchMethodException {
		if (constructor == null) {
			//#if MC >= 26.3
			Constructor<RenderPipeline> found = RenderPipeline.class.getDeclaredConstructor(Identifier.class, java.util.Map.class,
					ShaderDefines.class, java.util.Collection.class, ColorTargetState[].class, DepthStencilState.class,
					PolygonMode.class, boolean.class, VertexFormat[].class, PrimitiveTopology.class, int.class, int.class);
			//#else
			Constructor<RenderPipeline> found = RenderPipeline.class.getDeclaredConstructor(Identifier.class, Identifier.class,
					Identifier.class, ShaderDefines.class, List.class, ColorTargetState[].class, DepthStencilState.class,
					PolygonMode.class, boolean.class, VertexFormat[].class, PrimitiveTopology.class, int.class);
			//#endif
			found.setAccessible(true);
			constructor = found;
		}
		return constructor;
	}
}
//#endif
