//#if MC >= 26.2
package com.arcticlauncher.polarium.gpu;

import com.arcticlauncher.polarium.BlockEntityOptions;
import com.arcticlauncher.polarium.SpriteInstancing;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** Atlas-backed vanilla block entity models; text, cracks and layered patterns stay vanilla. */
public final class GpuSpriteModels {
    private static final String OPTION = System.getProperty("polarium.gpuSpriteModels", "auto");
    private static final boolean ON = !"false".equals(OPTION);
    private static Boolean deviceEnabled;
    private static final List<ModelFeatureRenderer.Submit<?>> captured = new ArrayList<>();
    private static final Class<?>[] SHULKER_MODELS = net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer.class.getDeclaredClasses();
    private static boolean recovering;
    private static boolean worldFrame;
    private static final boolean FAIL_DRAW = Boolean.getBoolean("polarium.gpuSpriteModels.failDraw");
    private static final int FAIL_MIN = Integer.getInteger("polarium.gpuSpriteModels.failMin", 1);
    private static final boolean REPORT = Boolean.getBoolean("polarium.gpuSpriteModels.report");
    private static final java.util.Set<String> reported = new java.util.HashSet<>();

    private GpuSpriteModels() {}

    static TextureAtlasSprite sprite(ModelFeatureRenderer.Submit<?> submit) {
        //#if MC >= 26.3
        return submit.uvMapping() instanceof TextureAtlasSprite sprite ? sprite : null;
        //#else
        return submit.sprite();
        //#endif
    }

    static boolean supported(ModelFeatureRenderer.Submit<?> submit) {
        if (!ON || !worldFrame || recovering || !BlockEntityOptions.get().spriteModelInstancing || !BlockEntityOptions.compatible()) return false;
        if (deviceEnabled == null) {
            var info = RenderSystem.getDevice().getDeviceInfo();
            deviceEnabled = "true".equals(OPTION) || com.arcticlauncher.polarium.SpriteInstancing.hardware(info.name(),
                    info.type() == com.mojang.blaze3d.systems.DeviceType.CPU);
        }
        if (!deviceEnabled) return false;
        TextureAtlasSprite sprite = sprite(submit);
        if (sprite == null) return false;
        Class<?> model = submit.model().getClass();
        boolean shulker = false;
        for (Class<?> type : SHULKER_MODELS) if (model == type && Model.class.isAssignableFrom(type)) shulker = true;
        var name = sprite.contents().name();
        boolean supported = SpriteInstancing.supported(name.getNamespace(), name.getPath(), shulker,
                model == net.minecraft.client.model.object.banner.BannerModel.class,
                submit.renderType().hasBlending(), submit.sheetedDecalPose() != null);
        if (REPORT) {
            String key = name + " " + model.getSimpleName();
            if (reported.add(key)) org.slf4j.LoggerFactory.getLogger("Polarium").info("Polarium sprite model {}: {}", key, supported ? "instanced" : "vanilla");
        }
        return supported;
    }

    public static void beginWorldFrame() {
        captured.clear();
        worldFrame = true;
    }

    public static void endWorldFrame() {
        worldFrame = false;
        captured.clear();
    }

    static void captured(ModelFeatureRenderer.Submit<?> submit) { captured.add(submit); }

    static void checkDraw() {
        if (!captured.isEmpty() && FAIL_DRAW && captured.size() >= FAIL_MIN) {
            throw new IllegalStateException("Polarium sprite model injected draw failure (" + captured.size() + " retained)");
        }
    }

    static void finishFrame() {
        if (!GpuBatches.disabled()) captured.clear();
    }

    /** Failed batches retain original submits, including their poses and immutable animation inputs. */
    public static void recover(FeatureRenderDispatcher features, net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        if (captured.isEmpty()) return;
        recovering = true;
        var view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.mul(camera.viewRotationMatrix);
        try {
            var storage = new SubmitNodeStorage();
            for (var submit : captured) storage.order(0).solid.submit(submit);
            var backup = features.prepareFrame(storage);
            try {
                //#if MC >= 26.3
                var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Polarium vanilla sprite model recovery", target.getColorTextureView(), Optional.empty(),
                        target.getDepthTextureView(), OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    backup.executeSolid(pass);
                }
                //#else
                backup.executeSolid();
                //#endif
            } finally { backup.close(); }
            org.slf4j.LoggerFactory.getLogger("Polarium").info("Polarium: recovered {} sprite models in the failed frame", captured.size());
        } finally {
            captured.clear();
            view.popMatrix();
            recovering = false;
        }
    }
}
//#endif
