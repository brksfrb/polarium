package com.arcticlauncher.polarium;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** Same JVM-property options as Polarium's entity path. Read once at startup. */
public final class BlockEntityOptions {
    public boolean blockEntityCulling = !"false".equals(System.getProperty("polarium.blockEntityCulling"));
    public boolean chestInstancing = !"false".equals(System.getProperty("polarium.gpuChests"));
    public int blockEntityDistance = 64;
    private static final BlockEntityOptions INSTANCE = new BlockEntityOptions();
    private static final class CompatibilityState { static final boolean VALUE = check(); }
    public static BlockEntityOptions get() { return INSTANCE; }
    private static volatile boolean rendererReplaced;
    public static boolean compatible() { return !rendererReplaced && CompatibilityState.VALUE; }
    public static boolean rendererSupported(Object renderer) {
        if (renderer == null) return false;
        if (!renderer.getClass().getName().startsWith("net.minecraft.client.renderer.blockentity.")) {
            if (!rendererReplaced) LoggerFactory.getLogger("Polarium").info(
                    "Polarium: {} replaces a block entity renderer; block entity path disabled", renderer.getClass().getName());
            rendererReplaced = true;
            return false;
        }
        return true;
    }
    private static boolean check() {
        for (String id : new String[] {"betterblockentities", "enhancedblockentities", "fastchest", "iris", "oculus",
                "optifabric", "immersive_portals", "imm_ptl_core", "entity_model_features", "entity_texture_features", "canvas", "vulkanmod"}) {
            if (FabricLoader.getInstance().isModLoaded(id)) {
                LoggerFactory.getLogger("Polarium").info("Polarium: {} loaded; block entities stay on vanilla rendering", id);
                return false;
            }
        }
        for (Class<?> target : new Class<?>[] {
                net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher.class,
                net.minecraft.client.renderer.blockentity.ChestRenderer.class,
                //#if MC < 26.2
                net.minecraft.client.renderer.blockentity.BedRenderer.class,
                //#endif
                net.minecraft.client.renderer.blockentity.SkullBlockRenderer.class,
                net.minecraft.client.renderer.blockentity.LecternRenderer.class,
                net.minecraft.client.renderer.blockentity.CampfireRenderer.class,
                //#if MC >= 26.1
                net.minecraft.client.renderer.blockentity.StandingSignRenderer.class,
                //#else
                net.minecraft.client.renderer.blockentity.SignRenderer.class,
                //#endif
                net.minecraft.client.renderer.blockentity.HangingSignRenderer.class,
                net.minecraft.client.renderer.blockentity.AbstractSignRenderer.class,
                net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer.class,
                net.minecraft.client.renderer.blockentity.BannerRenderer.class,
                net.minecraft.client.renderer.blockentity.BellRenderer.class,
                net.minecraft.client.renderer.blockentity.DecoratedPotRenderer.class}) {
            var mixins = MixinsOn.of(target.getName());
            if (mixins == null) {
                LoggerFactory.getLogger("Polarium").warn("Polarium: cannot inspect {}; block entity path disabled", target.getName());
                return false;
            }
            for (var mixin : mixins) {
                String name = mixin.getClassName();
                if (!name.startsWith("com.arcticlauncher.polarium.") && !MixinsOn.mod(mixin).equals("sodium")
                        // Fabric registers reload listeners here; it does not replace rendering.
                        && !MixinsOn.mod(mixin).equals("fabric-resource-loader-v1")) {
                    LoggerFactory.getLogger("Polarium").info("Polarium: {} changes {}; block entity path disabled",
                            MixinsOn.mod(mixin), target.getName());
                    return false;
                }
            }
        }
        return true;
    }
}
