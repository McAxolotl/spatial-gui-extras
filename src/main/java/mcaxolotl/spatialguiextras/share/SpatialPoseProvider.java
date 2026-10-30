package mcaxolotl.spatialguiextras.share;

import net.minecraft.class_310;

import org.joml.Vector3f;
import org.tastytrash.spatialGUI.client.SpatialGUIClient;
import org.tastytrash.spatialGUI.render.InventoryRenderer;
import org.tastytrash.spatialGUI.util.RenderUtil;

public final class SpatialPoseProvider {
    record Snapshot(
            float centerX,
            float centerY,
            float centerZ,
            float rightX,
            float rightY,
            float rightZ,
            float upX,
            float upY,
            float upZ,
            float halfWidth,
            float halfHeight,
            boolean curved,
            float arc,
            float radius,
            float cylinderHalfHeight,
            float axisX,
            float axisY,
            float axisZ,
            float axisDirX,
            float axisDirY,
            float axisDirZ,
            float refDirX,
            float refDirY,
            float refDirZ,
            float opacity,
            int fadeMs,
            boolean closing) {}

    static boolean available() {
        var renderer = SpatialGUIClient.renderer();
        return SpatialGUIClient.isEnabled()
                        && renderer != null
                        && renderer.getHookedScreen() != null
                        && renderer.getHookedScreen() == class_310.method_1551().field_1755
                        && renderer.shouldCapture()
                || SpatialGUIClient.isEnabled() && renderer != null && renderer.isClosing();
    }

    private record Origin(double x, double y, double z) {}

    private static Origin origin;
    private static Snapshot displayed;

    public static void beginFrame() {
        origin = null;
        displayed = null;
    }

    public static void capturePerspective() {
        if (!mcaxolotl.spatialguiextras.IntegrationHooks.worldView()) return;
        var client = class_310.method_1551();
        if (client.field_1724 == null) return;
        var worldCamera = client.field_1773.method_19418();
        mcaxolotl.spatialguiextras.interaction.ViewInteraction.capture(
                client.field_1724, worldCamera.method_19330(), worldCamera.method_19329());
        AvatarDisplay.captureFirstPerson(
                client.field_1724, worldCamera.method_19330(), worldCamera.method_19329());
        var camera = worldCamera.method_19326();
        var player = client.field_1724.method_19538();
        captureOrigin(
                camera.field_1352,
                camera.field_1351,
                camera.field_1350,
                player.field_1352,
                player.field_1351,
                player.field_1350);
    }

    static void captureOrigin(
            double cameraX,
            double cameraY,
            double cameraZ,
            double playerX,
            double playerY,
            double playerZ) {
        origin = new Origin(cameraX - playerX, cameraY - playerY, cameraZ - playerZ);
    }

    public static void capturePanel(InventoryRenderer inventory) {
        if (!mcaxolotl.spatialguiextras.IntegrationHooks.worldView()) return;
        var renderer = SpatialGUIClient.renderer();
        if (renderer == null || inventory != renderer.getInventoryRenderer()) return;
        var access = (mcaxolotl.spatialguiextras.mixin.ShareInventoryAccess) inventory;
        var quad = access.sge$getQuadBasis();
        if (quad == null || origin == null) return;
        var config = org.tastytrash.spatialGUI.config.ConfigManager.config();
        float opacity =
                ((mcaxolotl.spatialguiextras.mixin.ShareInventoryAccess) inventory)
                                .sge$getFadeAlpha()
                        * config.screenAlpha
                        / 255f;
        int fadeMs =
                Math.max(
                        0,
                        Math.min(10000, config.enableCloseAnimation ? config.fadeDurationMs : 0));
        captureGeometry(quad, access.sge$getCylinderBasis(), opacity, fadeMs, renderer.isClosing());
    }

    static Snapshot snapshot() {
        return displayed;
    }

    static void captureGeometry(
            RenderUtil.QuadBasis quad,
            RenderUtil.CylinderBasis cylinder,
            float opacity,
            int fadeMs,
            boolean closing) {
        if (origin == null || quad == null) return;
        double ox = origin.x(), oy = origin.y(), oz = origin.z();
        Vector3f offset = quad.centerOffset();
        float centerX = (float) (ox + offset.x);
        float centerY = (float) (oy + offset.y);
        float centerZ = (float) (oz + offset.z);

        if (cylinder != null) {
            Vector3f axisPos = cylinder.axisPos();
            Vector3f axisDir = cylinder.axisDir();
            Vector3f refDir = cylinder.refDir();
            displayed =
                    new Snapshot(
                            centerX,
                            centerY,
                            centerZ,
                            quad.right().x,
                            quad.right().y,
                            quad.right().z,
                            quad.up().x,
                            quad.up().y,
                            quad.up().z,
                            quad.halfWidth(),
                            quad.halfHeight(),
                            true,
                            cylinder.arcRadians(),
                            cylinder.radius(),
                            cylinder.halfHeight(),
                            (float) (ox + axisPos.x),
                            (float) (oy + axisPos.y),
                            (float) (oz + axisPos.z),
                            axisDir.x,
                            axisDir.y,
                            axisDir.z,
                            refDir.x,
                            refDir.y,
                            refDir.z,
                            opacity,
                            fadeMs,
                            closing);
            return;
        }
        displayed =
                new Snapshot(
                        centerX,
                        centerY,
                        centerZ,
                        quad.right().x,
                        quad.right().y,
                        quad.right().z,
                        quad.up().x,
                        quad.up().y,
                        quad.up().z,
                        quad.halfWidth(),
                        quad.halfHeight(),
                        false,
                        0f,
                        0f,
                        0f,
                        centerX,
                        centerY,
                        centerZ,
                        0f,
                        1f,
                        0f,
                        1f,
                        0f,
                        0f,
                        opacity,
                        fadeMs,
                        closing);
    }

    private SpatialPoseProvider() {}
}
