package com.arcticlauncher.polarium.legacy;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.render.BufferBuilder;

/**
 * Builder threads: after a block of the cutout-mipped layer is built, its quads that sample only fully opaque
 * sprites (see {@link OpaqueSprites}) with fully opaque vertex colours move to the solid layer. Such a quad passes
 * the 0.5 alpha test on every pixel, so it draws the same pixels; in the solid layer it is drawn earlier, with no
 * alpha test (which on a software rasterizer also means its hidden pixels are rejected before shading).
 *
 * <p>Order: inside the block, moved quads were already drawn before the quads that stay (the solid layer is drawn
 * first), except where a quad that stays came earlier. A quad therefore only moves when no earlier quad of the
 * block that stays lies in the same plane facing the same way (where the order decides which one shows at equal
 * depth, like a grass side and its overlay); quads that are not axis-aligned never move past one that stays.
 */
public final class CutoutRouting {
	private static final int INTS_PER_VERTEX = 7;
	private static final int INTS_PER_QUAD = INTS_PER_VERTEX * 4;

	private static final ThreadLocal<int[]> SCRATCH = new ThreadLocal<int[]>() {
		@Override
		protected int[] initialValue() {
			return new int[INTS_PER_QUAD * 64];
		}
	};

	private CutoutRouting() {
	}

	/**
	 * Splits the block's quads (vertices {@code from} to the end of {@code cutout}) into those that move and those
	 * that stay. The ones that stay are packed back in order; the ones that move are returned in order as raw
	 * vertex data (length is the int count), or null when none move.
	 */
	public static int[] split(BufferBuilder cutout, int from, OpaqueSprites sprites, int[] lengthOut) {
		int to = cutout.getVertexCount();
		int quads = (to - from) >> 2;
		if (quads <= 0 || ((to - from) & 3) != 0) {
			return null;
		}
		int ints = quads * INTS_PER_QUAD;
		int[] data = SCRATCH.get();
		if (data.length < ints) {
			data = new int[Math.max(ints, data.length * 2)];
			SCRATCH.set(data);
		}
		ByteBuffer bytes = cutout.getByteBuffer();
		IntBuffer view = ((ByteBuffer) bytes.duplicate().order(bytes.order()).clear()).asIntBuffer();
		view.position(from * INTS_PER_VERTEX);
		view.get(data, 0, ints);

		// planes of the quads that stay, so far: bucket and coordinate bits; an unaligned stayer blocks everything after it
		int[] keptBucket = new int[quads];
		int[] keptPlane = new int[quads];
		int kept = 0;
		boolean unalignedKept = false;
		boolean[] move = new boolean[quads];
		int moving = 0;
		for (int q = 0; q < quads; q++) {
			int at = q * INTS_PER_QUAD;
			int bucket = FaceBuckets.classify(data, at);
			int plane = bucket < FaceBuckets.AXIS_BUCKETS ? data[at + FaceBuckets.axisOf(bucket)] : 0;
			boolean ok = !unalignedKept && opaque(data, at, sprites);
			if (ok) {
				for (int k = 0; k < kept; k++) {
					if (bucket == FaceBuckets.AXIS_BUCKETS || keptBucket[k] == bucket && keptPlane[k] == plane) {
						ok = false;
						break;
					}
				}
			}
			if (ok) {
				move[q] = true;
				moving++;
			} else {
				if (bucket == FaceBuckets.AXIS_BUCKETS) {
					unalignedKept = true;
				}
				keptBucket[kept] = bucket;
				keptPlane[kept] = plane;
				kept++;
			}
		}
		if (moving == 0) {
			return null;
		}
		int[] moved = new int[moving * INTS_PER_QUAD];
		int m = 0;
		view.position(from * INTS_PER_VERTEX);
		for (int q = 0; q < quads; q++) {
			if (move[q]) {
				System.arraycopy(data, q * INTS_PER_QUAD, moved, m, INTS_PER_QUAD);
				m += INTS_PER_QUAD;
			} else {
				view.put(data, q * INTS_PER_QUAD, INTS_PER_QUAD);
			}
		}
		lengthOut[0] = m;
		return moved;
	}

	private static boolean opaque(int[] data, int at, OpaqueSprites sprites) {
		float minU = Float.POSITIVE_INFINITY;
		float minV = Float.POSITIVE_INFINITY;
		float maxU = Float.NEGATIVE_INFINITY;
		float maxV = Float.NEGATIVE_INFINITY;
		for (int v = 0; v < 4; v++) {
			int base = at + v * INTS_PER_VERTEX;
			if (OpaqueSprites.alpha(data[base + 3]) != 0xFF) {
				return false;
			}
			float u = Float.intBitsToFloat(data[base + 4]);
			float t = Float.intBitsToFloat(data[base + 5]);
			minU = Math.min(minU, u);
			maxU = Math.max(maxU, u);
			minV = Math.min(minV, t);
			maxV = Math.max(maxV, t);
		}
		return sprites.opaque(minU, minV, maxU, maxV);
	}
}
