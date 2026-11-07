package com.arcticlauncher.polarium.legacy;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.world.BuiltChunk;
import org.lwjgl.opengl.GL11;

/**
 * A section's opaque layers (solid, cutout mipped, cutout) are split by face direction when they are built:
 * six buckets for faces lying in a plane across one axis, by which way they face, and a seventh for the rest.
 * When the section is drawn, a bucket whose faces all point away from the camera is skipped (see
 * {@link FaceCulling}); GL would have culled every triangle in it anyway, after doing the vertex work.
 *
 * <p>Which way a face points is its winding, the same thing GL looks at. A quad goes in an axis bucket only when
 * its four corners share that coordinate exactly and both ways of cutting it into triangles wind the same way;
 * anything else (water surfaces, plants, slanted or broken quads) is in the last bucket and always drawn.
 * Inside a bucket the quads keep the order they were built in, so faces in the same plane (a grass side and its
 * overlay) still draw in the same order. Translucent geometry is not split: its order is the depth sort.
 */
public final class FaceBuckets {
	/**
	 * Buckets 0 to 5: down, up, north, south, west, east (the order the game builds a block's faces in, so faces
	 * of one block keep their order); even ones face the negative way of their axis, odd ones the positive way.
	 */
	public static final int AXIS_BUCKETS = 6;
	/** The axis (0 x, 1 y, 2 z) of each axis bucket. */
	private static final int[] AXIS = {1, 1, 2, 2, 0, 0};
	/** The first bucket (negative facing) of each axis. */
	private static final int[] FIRST_BUCKET = {4, 0, 2};
	public static final int BUCKETS = 7;
	/** Layers split this way: the first three (solid, cutout mipped, cutout). */
	public static final int LAYERS = 3;

	private static final int INTS_PER_VERTEX = 7;
	private static final int INTS_PER_QUAD = INTS_PER_VERTEX * 4;

	private static final ThreadLocal<int[][]> SCRATCH = new ThreadLocal<int[][]>() {
		@Override
		protected int[][] initialValue() {
			return new int[2][0];
		}
	};

	private FaceBuckets() {
	}

	/** Builder threads: are these vertices something this can split? */
	public static boolean splittable(BufferBuilder buffer) {
		return buffer.getDrawMode() == GL11.GL_QUADS && buffer.getFormat() == VertexFormats.BLOCK && (buffer.getVertexCount() & 3) == 0;
	}

	/**
	 * Builder threads: reorders the quads of a finished layer bucket by bucket (stable) and fills in the vertex
	 * count of each bucket and the lowest and highest plane of each axis bucket.
	 */
	public static void split(BufferBuilder buffer, int[] counts, float[] planes) {
		int quads = buffer.getVertexCount() >> 2;
		int ints = quads * INTS_PER_QUAD;
		int[][] scratch = SCRATCH.get();
		if (scratch[0].length < ints) {
			scratch[0] = new int[ints];
		}
		if (scratch[1].length < quads) {
			scratch[1] = new int[Math.max(quads, 256)];
		}
		int[] data = scratch[0];
		int[] bucketOf = scratch[1];
		ByteBuffer bytes = buffer.getByteBuffer();
		IntBuffer view = ((ByteBuffer) bytes.duplicate().order(bytes.order()).clear()).asIntBuffer();
		view.get(data, 0, ints);
		for (int b = 0; b < BUCKETS; b++) {
			counts[b] = 0;
		}
		for (int b = 0; b < AXIS_BUCKETS; b++) {
			planes[b * 2] = Float.POSITIVE_INFINITY;
			planes[b * 2 + 1] = Float.NEGATIVE_INFINITY;
		}
		for (int q = 0; q < quads; q++) {
			int at = q * INTS_PER_QUAD;
			int bucket = classify(data, at);
			bucketOf[q] = bucket;
			counts[bucket] += 4;
			if (bucket < AXIS_BUCKETS) {
				float c = Float.intBitsToFloat(data[at + AXIS[bucket]]);
				if (c < planes[bucket * 2]) {
					planes[bucket * 2] = c;
				}
				if (c > planes[bucket * 2 + 1]) {
					planes[bucket * 2 + 1] = c;
				}
			}
		}
		view.clear();
		for (int b = 0; b < BUCKETS; b++) {
			if (counts[b] == 0) {
				continue;
			}
			for (int q = 0; q < quads; q++) {
				if (bucketOf[q] == b) {
					view.put(data, q * INTS_PER_QUAD, INTS_PER_QUAD);
				}
			}
		}
	}

	/** The axis (0 x, 1 y, 2 z) of an axis bucket. */
	public static int axisOf(int bucket) {
		return AXIS[bucket];
	}

	/** The bucket of the quad whose first int is at {@code at}. */
	public static int classify(int[] data, int at) {
		for (int axis = 0; axis < 3; axis++) {
			int c = data[at + axis];
			if (c != data[at + INTS_PER_VERTEX + axis] || c != data[at + 2 * INTS_PER_VERTEX + axis] || c != data[at + 3 * INTS_PER_VERTEX + axis]) {
				continue;
			}
			// all four in the plane axis = c (same float bits; -0 and 0 are not mixed by the game's own math)
			int u = (axis + 1) % 3;
			int v = (axis + 2) % 3;
			double u0 = f(data, at, 0, u), v0 = f(data, at, 0, v);
			double u1 = f(data, at, 1, u), v1 = f(data, at, 1, v);
			double u2 = f(data, at, 2, u), v2 = f(data, at, 2, v);
			double u3 = f(data, at, 3, u), v3 = f(data, at, 3, v);
			// both ways GL may cut the quad: (0,1,2)+(0,2,3) and (0,1,3)+(1,2,3); every triangle must turn the same way
			int s012 = turn(u0, v0, u1, v1, u2, v2);
			int s023 = turn(u0, v0, u2, v2, u3, v3);
			int s013 = turn(u0, v0, u1, v1, u3, v3);
			int s123 = turn(u1, v1, u2, v2, u3, v3);
			int sign = 0;
			int[] all = {s012, s023, s013, s123};
			for (int s : all) {
				if (s == 0) {
					continue;
				}
				if (sign == 0) {
					sign = s;
				} else if (s != sign) {
					return AXIS_BUCKETS;
				}
			}
			if (sign == 0) {
				return AXIS_BUCKETS;
			}
			return FIRST_BUCKET[axis] + (sign > 0 ? 1 : 0);
		}
		return AXIS_BUCKETS;
	}

	private static double f(int[] data, int at, int vertex, int component) {
		return Float.intBitsToFloat(data[at + vertex * INTS_PER_VERTEX + component]);
	}

	/**
	 * Sign of the turn a to b to c in the (u, v) plane, where (u, v, axis) is a right-handed cyclic order: positive
	 * means the triangle's normal points along the positive axis. Exact for float inputs (the products of float
	 * differences fit a double).
	 */
	private static int turn(double au, double av, double bu, double bv, double cu, double cv) {
		double d = (bu - au) * (cv - av) - (bv - av) * (cu - au);
		return d > 0 ? 1 : d < 0 ? -1 : 0;
	}

	/**
	 * Render thread: compiles one display list per non-empty bucket from a split layer (each with the section's
	 * matrix, like the game's list) and the game's own list as calls to them, so anything drawing that list still
	 * draws the whole layer.
	 */
	public static void compile(BufferBuilder buffer, int gameList, int firstBucketList, int[] counts, int countsAt, BuiltChunk chunk) {
		if (buffer.getVertexCount() > 0) {
			VertexFormat format = buffer.getFormat();
			int stride = format.getVertexSize();
			ByteBuffer bytes = buffer.getByteBuffer();
			List<VertexFormatElement> elements = format.getElements();
			for (int j = 0; j < elements.size(); j++) {
				VertexFormatElement element = elements.get(j);
				int type = element.getFormat().getGlId();
				bytes.position(format.getIndex(j));
				switch (element.getType()) {
					case POSITION:
						GL11.glVertexPointer(element.getCount(), type, stride, bytes);
						GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
						break;
					case UV:
						GLX.gl13ClientActiveTexture(GLX.textureUnit + element.getIndex());
						GL11.glTexCoordPointer(element.getCount(), type, stride, bytes);
						GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
						GLX.gl13ClientActiveTexture(GLX.textureUnit);
						break;
					case COLOR:
						GL11.glColorPointer(element.getCount(), type, stride, bytes);
						GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
						break;
					case NORMAL:
						GL11.glNormalPointer(type, stride, bytes);
						GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
						break;
					default:
						break;
				}
			}
			int first = 0;
			for (int b = 0; b < BUCKETS; b++) {
				int count = counts[countsAt + b];
				if (count == 0) {
					continue;
				}
				GL11.glNewList(firstBucketList + b, GL11.GL_COMPILE);
				GlStateManager.pushMatrix();
				chunk.method_10169();
				GL11.glDrawArrays(GL11.GL_QUADS, first, count);
				GlStateManager.popMatrix();
				GL11.glEndList();
				first += count;
			}
			for (VertexFormatElement element : elements) {
				switch (element.getType()) {
					case POSITION:
						GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
						break;
					case UV:
						GLX.gl13ClientActiveTexture(GLX.textureUnit + element.getIndex());
						GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
						GLX.gl13ClientActiveTexture(GLX.textureUnit);
						break;
					case COLOR:
						GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
						GlStateManager.clearColor();
						break;
					case NORMAL:
						GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
						break;
					default:
						break;
				}
			}
		}
		GL11.glNewList(gameList, GL11.GL_COMPILE);
		for (int b = 0; b < BUCKETS; b++) {
			if (counts[countsAt + b] != 0) {
				GL11.glCallList(firstBucketList + b);
			}
		}
		GL11.glEndList();
		buffer.reset();
	}
}
