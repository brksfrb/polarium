//#if MC >= 26.2
package com.arcticlauncher.polarium.mixin;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.List;
import net.minecraft.client.renderer.StagedVertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StagedVertexBuffer.Draw.class)
public interface DrawAccess {
	@Accessor("vertexOffset")
	int polarium$vertexOffset();

	@Accessor("indexOffset")
	int polarium$indexOffset();

	@Accessor("quadSorting")
	VertexSorting polarium$quadSorting();

	@Accessor("vertexBufferSlices")
	List<ByteBufferBuilder.Result> polarium$slices();

	@Invoker("indexType")
	IndexType polarium$indexType();

	@Invoker("freeVertexData")
	void polarium$freeVertexData();
}
//#endif
