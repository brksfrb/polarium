package com.arcticlauncher.polarium;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChestUniformBatchesTest {
    @Test void batchesPreserveAtlasAndEveryModelAcrossThe256InstanceBoundary() {
        var core = new ChestInstancing();
        var world = new ChestInstancingTest.TestWorld();
        core.beginFrame(0, 64, 0, 128);
        for (int kind = 0; kind < 3; kind++) {
            for (int i = 0; i < 257; i++) {
                var c = new ChestInstancingTest.Chest(i % 16, 64 + i / 16, kind);
                c.kind = kind; c.sprite = kind * 3;
                c.entry = core.add(c, c.x, c.y, c.z, false);
                core.see(c.entry, false);
            }
        }
        core.sprite(0, .125f, .25f, .5f, .75f);
        var f = core.endFrame(world, 0);
        assertEquals(6, ChestUniformBatches.count(f));
        assertTrue(ChestUniformBatches.BYTES <= 16384);
        int stride = (ChestUniformBatches.BYTES + 255) / 256 * 256;
        var bytes = ByteBuffer.allocate(stride * 6).order(ByteOrder.nativeOrder());
        ChestUniformBatches.pack(bytes, f, stride);
        int slot = 0;
        for (int kind = 0; kind < 3; kind++) {
            for (int offset = 0; offset < 257; offset += 256) {
                int at = slot++ * stride;
                for (int i = 0; i < ChestInstancing.SPRITES * 4; i++) assertEquals(f.data[i], bytes.getFloat(at + i * 4));
                int n = Math.min(256, 257 - offset) * ChestInstancing.FLOATS;
                for (int i = 0; i < n; i++) assertEquals(f.data[f.first[kind] * 4 + offset * ChestInstancing.FLOATS + i],
                    bytes.getFloat(at + ChestInstancing.SPRITES * 16 + i * 4));
            }
        }
    }
}
