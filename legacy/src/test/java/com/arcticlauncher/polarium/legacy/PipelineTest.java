package com.arcticlauncher.polarium.legacy;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PipelineTest {
    @Test void hardwareAndUnknownDriversNeverEnableSoftwareSubmission() {
        assertFalse(Pipeline.isSoftware(null));
        assertFalse(Pipeline.isSoftware("NVIDIA GeForce RTX 3080 Ti"));
        assertFalse(Pipeline.isSoftware("AMD Radeon RX 7900"));
        assertFalse(Pipeline.isSoftware("Intel Arc"));
        assertTrue(Pipeline.isSoftware("llvmpipe (LLVM 19.1.7, 256 bits)"));
        assertTrue(Pipeline.isSoftware("SOFTPIPE"));
    }
    @Test void budgetCountsVerticesAcrossDrawCallsWithoutEarlyFlushes() throws Exception {
        Field pending = Pipeline.class.getDeclaredField("pending");
        pending.setAccessible(true);
        pending.setInt(null, 0);
        try {
            assertFalse(Pipeline.due(Pipeline.BUDGET - 1));
            Pipeline.submitted(Pipeline.BUDGET / 2);
            assertFalse(Pipeline.due(Pipeline.BUDGET / 2 - 1));
            assertTrue(Pipeline.due(Pipeline.BUDGET / 2));
            assertTrue(Pipeline.due(Pipeline.BUDGET));
        } finally { pending.setInt(null, 0); }
    }
}
