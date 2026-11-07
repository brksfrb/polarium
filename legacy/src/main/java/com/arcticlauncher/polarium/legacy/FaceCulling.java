package com.arcticlauncher.polarium.legacy;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Which face-direction buckets of a section face away from the camera (see {@link FaceBuckets}).
 *
 * <p>GL drops a triangle as a back face when its corners wind clockwise on screen. For triangles in a plane
 * {@code axis = c}, that winding is the sign of the 3x3 determinant of their clip x, y and w, which is an affine
 * function of {@code c}: positive on one side of the camera and negative on the other. It is worked out here
 * from the matrices GL holds when the layer is drawn (projection times model-view), so it covers whatever the
 * game or a mod set up: third person, view bobbing, nausea, anaglyph, a mirrored matrix.
 *
 * <p>A bucket is skipped only when every plane in it is behind that point by a margin, so GL would have
 * dropped all of its triangles itself. The margin grows with the distance: a face seen almost edge-on far
 * away is a sliver thinner than the rasterizer's sub-pixel grid, and its winding there is down to rounding;
 * those are always drawn and left to GL.
 */
public final class FaceCulling {
	/** Smallest gap between the camera and a plane, in blocks. */
	private static final double MIN_MARGIN = 1.0 / 64.0;

	private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
	private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
	private final double[] m = new double[16];

	/** False when GL culls nothing (or something unusual): nothing is skipped. */
	private boolean usable;
	/** Per axis: the plane through the camera (camera-relative coordinates), and which side a positive face is seen from. */
	private final double[] eye = new double[3];
	private final boolean[] increasing = new boolean[3];
	/** Margin per squared block of distance. */
	private double marginPerDistance2;

	/** Reads the GL state the layer will be drawn with. Call right before drawing, on the render thread. */
	public void begin(int viewportWidth, int viewportHeight) {
		usable = false;
		if (!GL11.glIsEnabled(GL11.GL_CULL_FACE) || GL11.glGetInteger(GL11.GL_CULL_FACE_MODE) != GL11.GL_BACK) {
			return;
		}
		boolean clockwise = GL11.glGetInteger(GL11.GL_FRONT_FACE) == GL11.GL_CW;
		modelView.clear();
		projection.clear();
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
		setup(modelView, projection, clockwise, viewportWidth, viewportHeight);
	}

	/** The work of {@link #begin} on given matrices (column-major, as GL returns them). */
	void setup(FloatBuffer modelView, FloatBuffer projection, boolean clockwise, int viewportWidth, int viewportHeight) {
		usable = false;
		for (int col = 0; col < 4; col++) {
			for (int row = 0; row < 4; row++) {
				double s = 0;
				for (int k = 0; k < 4; k++) {
					s += (double) projection.get(k * 4 + row) * modelView.get(col * 4 + k);
				}
				m[col * 4 + row] = s;
			}
		}
		for (int axis = 0; axis < 3; axis++) {
			double b = winding(axis, 0.0);
			double a = winding(axis, 1.0) - b;
			if (clockwise) {
				a = -a;
				b = -b;
			}
			if (!(Math.abs(a) > 1e-12) || Double.isNaN(b) || Double.isInfinite(b)) {
				return;
			}
			eye[axis] = -b / a;
			increasing[axis] = a > 0;
			if (Double.isNaN(eye[axis]) || Double.isInfinite(eye[axis])) {
				return;
			}
		}
		double focal = Math.min(Math.abs(projection.get(0)) * viewportWidth, Math.abs(projection.get(5)) * viewportHeight) / 2.0;
		if (!(focal > 1.0)) {
			return;
		}
		// a face 1/16 block wide, seen from the margin's distance off its plane, is at least 1/128 pixel thick on screen
		marginPerDistance2 = (2.0 / 256.0) / (focal / 16.0);
		usable = true;
	}

	/**
	 * Determinant of the clip-space x, y and w of a triangle in the plane {@code axis = c} whose corners turn
	 * counter-clockwise seen from the positive side: GL treats the plane's positive-facing triangles as front
	 * faces where this is positive.
	 */
	private double winding(int axis, double c) {
		double[] p0 = new double[4];
		double[] p1 = new double[4];
		double[] p2 = new double[4];
		p0[3] = p1[3] = p2[3] = 1.0;
		int u = (axis + 1) % 3;
		int v = (axis + 2) % 3;
		p0[axis] = p1[axis] = p2[axis] = c;
		p1[u] = 1.0;
		p2[v] = 1.0;
		double x0 = row(0, p0), y0 = row(1, p0), w0 = row(3, p0);
		double x1 = row(0, p1), y1 = row(1, p1), w1 = row(3, p1);
		double x2 = row(0, p2), y2 = row(1, p2), w2 = row(3, p2);
		return x0 * (y1 * w2 - w1 * y2) - x1 * (y0 * w2 - w0 * y2) + x2 * (y0 * w1 - w0 * y1);
	}

	private double row(int r, double[] p) {
		return m[r] * p[0] + m[4 + r] * p[1] + m[8 + r] * p[2] + m[12 + r] * p[3];
	}

	public boolean usable() {
		return usable;
	}

	/**
	 * The buckets of a section to skip, as bits (bit b for bucket b; see {@link FaceBuckets}).
	 *
	 * @param planes per axis bucket the lowest and highest plane, in the coordinates the section was compiled in
	 * @param offset added to those coordinates to get camera-relative ones (the translate it is drawn under)
	 * @param boxMin the section's corner, camera-relative
	 */
	public int hidden(float[] planes, int planesAt, int[] counts, int countsAt,
			double offsetX, double offsetY, double offsetZ, double boxMinX, double boxMinY, double boxMinZ) {
		if (!usable) {
			return 0;
		}
		// the farthest a vertex of the section can be from the camera (one block of slack for models reaching out)
		double fx = Math.max(Math.abs(boxMinX - 1.0 - eye[0]), Math.abs(boxMinX + 17.0 - eye[0]));
		double fy = Math.max(Math.abs(boxMinY - 1.0 - eye[1]), Math.abs(boxMinY + 17.0 - eye[1]));
		double fz = Math.max(Math.abs(boxMinZ - 1.0 - eye[2]), Math.abs(boxMinZ + 17.0 - eye[2]));
		double margin = MIN_MARGIN + marginPerDistance2 * (fx * fx + fy * fy + fz * fz);
		int hidden = 0;
		for (int bucket = 0; bucket < FaceBuckets.AXIS_BUCKETS; bucket++) {
			if (counts[countsAt + bucket] == 0) {
				continue;
			}
			int axis = FaceBuckets.axisOf(bucket);
			boolean positive = (bucket & 1) != 0;
			double offset = axis == 0 ? offsetX : axis == 1 ? offsetY : offsetZ;
			double lo = planes[planesAt + bucket * 2] + offset;
			double hi = planes[planesAt + bucket * 2 + 1] + offset;
			double c0 = eye[axis];
			// front faces of a positive bucket are where the winding is positive; of a negative bucket where it is negative
			boolean frontAbove = positive == increasing[axis];
			boolean allBack = frontAbove ? hi < c0 - margin : lo > c0 + margin;
			if (allBack) {
				hidden |= 1 << bucket;
			}
		}
		return hidden;
	}
}
