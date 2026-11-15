package com.arcticlauncher.polarium;

/** Conservative eligibility for the atlas-backed cube-model path. */
public final class SpriteInstancing {
    private SpriteInstancing() {}

    public static boolean supported(String namespace, String path, boolean shulkerModel,
            boolean bannerModel, boolean blending, boolean decal) {
        if (blending || decal || !"minecraft".equals(namespace)) return false;
        if (shulkerModel) return path.startsWith("entity/shulker/");
        if (bannerModel) return path.equals("entity/banner/banner_base");
        // On 26.2+, sign and bed bodies are terrain models, not these submits.
        return false;
    }

    /** Software devices regressed in paired scenes; leave their cube models on vanilla by default. */
    public static boolean hardware(String name, boolean cpu) {
        if (cpu || name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return !lower.contains("llvmpipe") && !lower.contains("softpipe") && !lower.contains("lavapipe");
    }

    /** Same affine mapping as TextureAtlasSprite.getU/getV; no atlas copies or resampling. */
    public static void placement(float u0, float v0, float u1, float v1, float[] out) {
        out[0] = u0;
        out[1] = v0;
        out[2] = u1 - u0;
        out[3] = v1 - v0;
    }
}
