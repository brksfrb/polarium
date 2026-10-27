package com.arcticlauncher.polarium.legacy;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.GlAllocationUtils;
import org.lwjgl.opengl.GL11;

/**
 * The fancy clouds as one display list. The game builds all their geometry again every frame (about 3000
 * vertices, twice); it only depends on the cloud cell the camera is over, the cloud colour and whether the
 * camera is under or over them. The smooth part, the shift inside a cell and the height, is a translate
 * applied when the list is drawn.
 */
public final class CloudCache {
	private static final float TEXEL = 0.00390625F;
	private static final float EDGE = 9.765625E-4F;

	private int list = -1;
	private int cellU = -1;
	private int cellV = -1;
	private int colour = -1;
	private boolean bottom;
	private boolean top;
	private float r;
	private float g;
	private float b;

	/** The list for these inputs, built now when the last one was made for others. */
	public int list(int cellU, int cellV, float red, float green, float blue, boolean bottom, boolean top) {
		if (list < 0) {
			list = GlAllocationUtils.genLists(1);
		} else if (cellU == this.cellU && cellV == this.cellV && red == r && green == g && blue == b
				&& bottom == this.bottom && top == this.top) {
			return list;
		}
		this.cellU = cellU;
		this.cellV = cellV;
		this.r = red;
		this.g = green;
		this.b = blue;
		this.bottom = bottom;
		this.top = top;
		build();
		return list;
	}

	private void build() {
		float m = r;
		float n = g;
		float o = b;
		float p = m * 0.9F;
		float q = n * 0.9F;
		float rr = o * 0.9F;
		float s = m * 0.7F;
		float t = n * 0.7F;
		float u = o * 0.7F;
		float v = m * 0.8F;
		float w = n * 0.8F;
		float x = o * 0.8F;
		float z = cellU * TEXEL;
		float aa = cellV * TEXEL;
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder bb = tessellator.getBuffer();
		GL11.glNewList(list, GL11.GL_COMPILE);
		bb.begin(7, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
		for (int ah = -3; ah <= 4; ah++) {
			for (int ai = -3; ai <= 4; ai++) {
				float aj = ah * 8;
				float ak = ai * 8;
				float al = aj;
				float am = ak;
				if (bottom) {
					face(bb, al, 0.0F, am + 8.0F, al + 8.0F, 0.0F, am + 8.0F, al + 8.0F, 0.0F, am, al, 0.0F, am,
							(aj + 0.0F) * TEXEL + z, (ak + 8.0F) * TEXEL + aa, (aj + 8.0F) * TEXEL + z, (ak + 8.0F) * TEXEL + aa,
							(aj + 8.0F) * TEXEL + z, (ak + 0.0F) * TEXEL + aa, (aj + 0.0F) * TEXEL + z, (ak + 0.0F) * TEXEL + aa,
							s, t, u, 0.0F, -1.0F, 0.0F);
				}
				if (top) {
					float y = 4.0F - EDGE;
					face(bb, al, y, am + 8.0F, al + 8.0F, y, am + 8.0F, al + 8.0F, y, am, al, y, am,
							(aj + 0.0F) * TEXEL + z, (ak + 8.0F) * TEXEL + aa, (aj + 8.0F) * TEXEL + z, (ak + 8.0F) * TEXEL + aa,
							(aj + 8.0F) * TEXEL + z, (ak + 0.0F) * TEXEL + aa, (aj + 0.0F) * TEXEL + z, (ak + 0.0F) * TEXEL + aa,
							m, n, o, 0.0F, 1.0F, 0.0F);
				}
				if (ah > -1) {
					for (int an = 0; an < 8; an++) {
						float xx = al + an + 0.0F;
						float uu = (aj + an + 0.5F) * TEXEL + z;
						face(bb, xx, 0.0F, am + 8.0F, xx, 4.0F, am + 8.0F, xx, 4.0F, am, xx, 0.0F, am,
								uu, (ak + 8.0F) * TEXEL + aa, uu, (ak + 8.0F) * TEXEL + aa, uu, (ak + 0.0F) * TEXEL + aa, uu, (ak + 0.0F) * TEXEL + aa,
								p, q, rr, -1.0F, 0.0F, 0.0F);
					}
				}
				if (ah <= 1) {
					for (int an = 0; an < 8; an++) {
						float xx = al + an + 1.0F - EDGE;
						float uu = (aj + an + 0.5F) * TEXEL + z;
						face(bb, xx, 0.0F, am + 8.0F, xx, 4.0F, am + 8.0F, xx, 4.0F, am, xx, 0.0F, am,
								uu, (ak + 8.0F) * TEXEL + aa, uu, (ak + 8.0F) * TEXEL + aa, uu, (ak + 0.0F) * TEXEL + aa, uu, (ak + 0.0F) * TEXEL + aa,
								p, q, rr, 1.0F, 0.0F, 0.0F);
					}
				}
				if (ai > -1) {
					for (int an = 0; an < 8; an++) {
						float zz = am + an + 0.0F;
						float vv = (ak + an + 0.5F) * TEXEL + aa;
						face(bb, al, 4.0F, zz, al + 8.0F, 4.0F, zz, al + 8.0F, 0.0F, zz, al, 0.0F, zz,
								(aj + 0.0F) * TEXEL + z, vv, (aj + 8.0F) * TEXEL + z, vv, (aj + 8.0F) * TEXEL + z, vv, (aj + 0.0F) * TEXEL + z, vv,
								v, w, x, 0.0F, 0.0F, -1.0F);
					}
				}
				if (ai <= 1) {
					for (int an = 0; an < 8; an++) {
						float zz = am + an + 1.0F - EDGE;
						float vv = (ak + an + 0.5F) * TEXEL + aa;
						face(bb, al, 4.0F, zz, al + 8.0F, 4.0F, zz, al + 8.0F, 0.0F, zz, al, 0.0F, zz,
								(aj + 0.0F) * TEXEL + z, vv, (aj + 8.0F) * TEXEL + z, vv, (aj + 8.0F) * TEXEL + z, vv, (aj + 0.0F) * TEXEL + z, vv,
								v, w, x, 0.0F, 0.0F, 1.0F);
					}
				}
			}
		}
		tessellator.draw();
		GL11.glEndList();
	}

	/** One quad: four corners (x, y, z), their texture coordinates, one colour and one normal. */
	private static void face(BufferBuilder bb,
			float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
			float u0, float v0, float u1, float v1, float u2, float v2, float u3, float v3,
			float r, float g, float b, float nx, float ny, float nz) {
		bb.vertex(x0, y0, z0).texture(u0, v0).color(r, g, b, 0.8F).normal(nx, ny, nz).next();
		bb.vertex(x1, y1, z1).texture(u1, v1).color(r, g, b, 0.8F).normal(nx, ny, nz).next();
		bb.vertex(x2, y2, z2).texture(u2, v2).color(r, g, b, 0.8F).normal(nx, ny, nz).next();
		bb.vertex(x3, y3, z3).texture(u3, v3).color(r, g, b, 0.8F).normal(nx, ny, nz).next();
	}
}
