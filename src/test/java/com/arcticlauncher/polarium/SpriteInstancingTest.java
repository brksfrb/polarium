package com.arcticlauncher.polarium;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpriteInstancingTest {
    @Test void onlyKnownVanillaModelsAndOpaquePasses() {
        assertTrue(SpriteInstancing.supported("minecraft", "entity/shulker/shulker_red", true, false, false, false));
        assertTrue(SpriteInstancing.supported("minecraft", "entity/banner/banner_base", false, true, false, false));
        assertFalse(SpriteInstancing.supported("minecraft", "entity/signs/oak", false, false, false, false));
        assertFalse(SpriteInstancing.supported("minecraft", "entity/signs/hanging/oak", false, false, false, false));
        assertFalse(SpriteInstancing.supported("minecraft", "entity/banner/stripe", false, true, false, false));
        assertFalse(SpriteInstancing.supported("minecraft", "entity/shulker/shulker", true, false, true, false));
        assertFalse(SpriteInstancing.supported("minecraft", "entity/shulker/shulker", true, false, false, true));
        assertFalse(SpriteInstancing.supported("mod", "entity/shulker/shulker", true, false, false, false));
        assertFalse(SpriteInstancing.supported("minecraft", "entity/shulker/shulker", false, false, false, false));
        assertFalse(SpriteInstancing.supported("minecraft", "entity/bed/red", false, false, false, false));
    }
    @Test void softwareDevicesKeepTheMeasuredFasterVanillaPath() {
        assertFalse(SpriteInstancing.hardware("llvmpipe (LLVM 19)", false));
        assertFalse(SpriteInstancing.hardware("llvmpipe", true));
        assertFalse(SpriteInstancing.hardware("lavapipe", false));
        assertFalse(SpriteInstancing.hardware("SOFTPIPE", false));
        assertFalse(SpriteInstancing.hardware(null, false));
        assertTrue(SpriteInstancing.hardware("NVIDIA GeForce RTX 3080 Ti", false));
        assertTrue(SpriteInstancing.hardware("AMD Radeon", false));
    }
    @Test void uvMappingPreservesAtlasEndpointsAndInterior() {
        float[] uv = new float[4];
        SpriteInstancing.placement(.125f, .25f, .375f, .75f, uv);
        assertArrayEquals(new float[] {.125f, .25f, .25f, .5f}, uv);
        for (float u : new float[] {0f, .25f, .5f, 1f}) {
            assertEquals(.125f + (.375f - .125f) * u, uv[0] + u * uv[2]);
            assertEquals(.25f + (.75f - .25f) * u, uv[1] + u * uv[3]);
        }
    }
}
