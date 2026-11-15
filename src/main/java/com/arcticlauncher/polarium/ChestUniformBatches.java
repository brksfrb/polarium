package com.arcticlauncher.polarium;

import java.nio.ByteBuffer;

/** Uniform buffers on 1.21.11/26.1 have a portable 16 KiB limit. Each batch fits in 8.5 KiB. */
public final class ChestUniformBatches {
    public static final int CAPACITY = 256;
    public static final int BYTES = (ChestInstancing.SPRITES + CAPACITY * 2) * 16;
    private ChestUniformBatches() {}
    public static int count(ChestInstancing.Frame frame) {
        int count = 0;
        for (int n : frame.count) count += (n + CAPACITY - 1) / CAPACITY;
        return count;
    }
    /** Every batch starts with the same atlas table, then its model's instances, in shader order. */
    public static void pack(ByteBuffer target, ChestInstancing.Frame frame, int stride) {
        if (stride < BYTES || stride % 16 != 0) throw new IllegalArgumentException("unaligned chest batch");
        int slot = 0;
        for (int kind = 0; kind < ChestInstancing.KINDS; kind++) {
            for (int offset = 0; offset < frame.count[kind]; offset += CAPACITY) {
                int count = Math.min(CAPACITY, frame.count[kind] - offset);
                target.position(slot++ * stride);
                target.asFloatBuffer().put(frame.data, 0, ChestInstancing.SPRITES * 4)
                    .put(frame.data, frame.first[kind] * 4 + offset * ChestInstancing.FLOATS, count * ChestInstancing.FLOATS);
            }
        }
        target.clear();
    }
}
