//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
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
	/** Twins by original pipeline (identity: RenderPipeline doesn't override equals), then by vertex shader. */
	private static final Map<RenderPipeline, Map<Identifier, Twin>> TWINS = new ConcurrentHashMap<>();
	/** The same twins by their own pipeline, for drawing with them. */
	private static final Map<RenderPipeline, Twin> BY_PIPELINE = new ConcurrentHashMap<>();
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Polarium");
	private static @Nullable Constructor<RenderPipeline> constructor;

	/** A twin pipeline and its compile, which nothing ever waits on (see {@link AsyncCompile}). */
	private static final class Twin {
		final RenderPipeline pipeline;
		final AsyncCompile<CompiledRenderPipeline> compile;
		/** Other twins it can't draw without (26.3: its transparency stages' twins). */
		volatile List<Twin> alsoNeeds = List.of();

		Twin(RenderPipeline original, RenderPipeline pipeline) {
			this.pipeline = pipeline;
			this.compile = new AsyncCompile<>(new AsyncCompile.Compiler<>() {
				@Override
				public CompletableFuture<Supplier<CompiledRenderPipeline>> start() {
					return InstancedPipelines.start(pipeline);
				}

				@Override
				public boolean isClosed(CompiledRenderPipeline compiled) {
					//#if MC >= 26.3
					return compiled.isClosed();
					//#else
					return false;
					//#endif
				}
			}, e -> LOG.warn("Polarium: the GPU couldn't compile its version of {}; the game draws it its own way from now on",
					original.getLocation(), e));
		}

		/** Compiled and ready, with everything it also needs; on the render thread, moves the compiles along first. */
		boolean ready() {
			boolean renderThread = RenderSystem.isOnRenderThread();
			boolean ready = (renderThread ? compile.advance() : compile.ready()) != null;
			for (Twin other : alsoNeeds) {
				ready &= (renderThread ? other.compile.advance() : other.compile.ready()) != null;
			}
			return ready;
		}
	}

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

	/**
	 * {@code original} with this vertex shader, extra bind group and these vertex buffers (bindings 0, 1, ...),
	 * made now if new. Not necessarily compiled yet: only build batches with it once {@link #ready} said so,
	 * and draw them with {@link #compiled}.
	 */
	static RenderPipeline twin(RenderPipeline original, Identifier vertexShader, BindGroupLayout layout, VertexFormat... formats) {
		return entry(original, vertexShader, layout, formats).pipeline;
	}

	private static Twin entry(RenderPipeline original, Identifier vertexShader, BindGroupLayout layout, VertexFormat... formats) {
		Map<Identifier, Twin> twins = TWINS.computeIfAbsent(original, p -> new ConcurrentHashMap<>());
		Twin twin = twins.get(vertexShader);
		if (twin == null) {
			twin = twins.computeIfAbsent(vertexShader, shader -> {
				// The pipeline keeps one slot per possible binding: fill ours, clear the rest.
				//#if MC >= 26.3
				VertexFormat[] bindings = original.getVertexFormatBindings().toArray(new VertexFormat[0]);
				//#else
				VertexFormat[] bindings = original.getVertexFormatBindings().clone();
				//#endif
				java.util.Arrays.fill(bindings, null);
				System.arraycopy(formats, 0, bindings, 0, formats.length);
				Twin made = new Twin(original, create(original, shader, layout, bindings));
				BY_PIPELINE.put(made.pipeline, made);
				return made;
			});
		}
		return twin;
	}

	/** Moves every twin's compile along (render thread, once a frame): starts new ones, takes finished ones. */
	static void advanceAll() {
		for (Twin twin : BY_PIPELINE.values()) {
			twin.compile.advance();
		}
	}

	/**
	 * Whether {@code original}'s twin with this vertex shader is compiled (with whatever else it
	 * needs): until then the game draws it its own way. Starts its compile if new; never waits.
	 */
	static boolean ready(RenderPipeline original, Identifier vertexShader) {
		return ready(original, vertexShader, ModelMesh.FORMAT, INSTANCES);
	}

	static boolean ready(RenderPipeline original, Identifier vertexShader, VertexFormat format, BindGroupLayout layout) {
		return ready(original, vertexShader, layout, format);
	}

	static boolean ready(RenderPipeline original, Identifier vertexShader, BindGroupLayout layout, VertexFormat... formats) {
		return entry(original, vertexShader, layout, formats).ready();
	}

	/**
	 * Whether this pipeline is one of the game's for {@code gameShader} and its twin with
	 * {@code twinShader} is ready (see {@link #supports}, {@link #ready}).
	 */
	static boolean usable(RenderPipeline pipeline, Identifier gameShader, Identifier twinShader) {
		return supports(pipeline, gameShader) && ready(pipeline, twinShader);
	}

	//#if MC >= 26.3
	/**
	 * {@code original}'s twin also needs its transparency stages' twins to draw: until they compiled
	 * too, {@link #ready} says no for it (and the game draws it); if one never compiles, for good.
	 */
	static void alsoNeedsOit(RenderPipeline original, Identifier vertexShader, net.minecraft.client.renderer.oit.OitPipelineSet stages) {
		Twin twin = entry(original, vertexShader, INSTANCES, ModelMesh.FORMAT);
		if (!twin.alsoNeeds.isEmpty()) {
			return;
		}
		List<Twin> needs = new ArrayList<>();
		for (net.minecraft.client.renderer.oit.OitStage stage : net.minecraft.client.renderer.oit.OitStage.values()) {
			needs.add(entry(stages.getPipeline(stage), vertexShader, INSTANCES, ModelMesh.FORMAT));
		}
		twin.alsoNeeds = List.copyOf(needs);
	}

	/**
	 * The compiled twin to draw with, or null: still compiling, couldn't compile, or freed by a
	 * resource reload (compiling again). Then skip the draw. Render thread; never waits.
	 */
	static @Nullable CompiledRenderPipeline compiled(RenderPipeline twin) {
		Twin entry = BY_PIPELINE.get(twin);
		return entry != null ? entry.compile.advance() : null;
	}

	/**
	 * Starts compiling the twin as the game's pipeline cache would (RenderSystem.getCompiledPipeline),
	 * but without its join(): the shaders are made on the game's background threads, and the
	 * result is finished (on the render thread) and handed to the cache once they're done.
	 */
	private static CompletableFuture<Supplier<CompiledRenderPipeline>> start(RenderPipeline twin) {
		com.mojang.blaze3d.pipeline.PipelineCache cache = activeCache();
		com.mojang.renderpearl.api.pipeline.ShaderSource source;
		try {
			source = (com.mojang.renderpearl.api.pipeline.ShaderSource) shaderSourceField().get(cache);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("can't read the pipeline cache's shaders", e);
		}
		return RenderSystem.getDevice().compilePipeline(twin, source, net.minecraft.util.Util.backgroundExecutor())
				.thenApply(pending -> () -> {
					CompiledRenderPipeline compiled = pending.finishCompile();
					if (compiled != null) {
						// The cache owns it from here: a reload closes it (and the twin compiles again).
						activeCache().insert(twin, compiled);
					}
					return compiled;
				});
	}

	private static java.lang.reflect.@Nullable Field currentCache;
	private static java.lang.reflect.@Nullable Field fallbackCache;
	private static java.lang.reflect.@Nullable Field shaderSource;

	/** The cache RenderSystem.getCompiledPipeline looks in first. */
	private static com.mojang.blaze3d.pipeline.PipelineCache activeCache() {
		try {
			if (currentCache == null || fallbackCache == null) {
				java.lang.reflect.Field current = RenderSystem.class.getDeclaredField("currentPipelineCache");
				java.lang.reflect.Field fallback = RenderSystem.class.getDeclaredField("fallbackPipelineCache");
				current.setAccessible(true);
				fallback.setAccessible(true);
				currentCache = current;
				fallbackCache = fallback;
			}
			Object cache = currentCache.get(null);
			if (cache == null) {
				cache = fallbackCache.get(null);
			}
			if (cache == null) {
				throw new IllegalStateException("no pipeline cache yet");
			}
			return (com.mojang.blaze3d.pipeline.PipelineCache) cache;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("can't find the game's pipeline cache", e);
		}
	}

	private static java.lang.reflect.Field shaderSourceField() throws NoSuchFieldException {
		if (shaderSource == null) {
			java.lang.reflect.Field found = com.mojang.blaze3d.pipeline.PipelineCache.class.getDeclaredField("shaderSource");
			found.setAccessible(true);
			shaderSource = found;
		}
		return shaderSource;
	}
	//#else
	/** 26.2 compiles at once, on this thread (no future to wait on): finished the moment it's started. */
	private static CompletableFuture<Supplier<CompiledRenderPipeline>> start(RenderPipeline twin) {
		CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(twin);
		return CompletableFuture.completedFuture(() -> compiled.isValid() ? compiled : null);
	}
	//#endif

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
