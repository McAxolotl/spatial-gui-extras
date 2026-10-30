package mcaxolotl.spatialguiextras.interaction;

import mcaxolotl.spatialguiextras.ExtrasClient;
import mcaxolotl.spatialguiextras.IntegrationHooks;
import mcaxolotl.spatialguiextras.config.InteractionOptions;

import net.minecraft.class_310;

import org.tastytrash.spatialGUI.client.SpatialGUIClient;
import org.tastytrash.spatialGUI.util.MouseHandlerUtil;
import org.tastytrash.spatialGUI.util.RenderUtil;

public final class PanelFollow {
    private static final FollowMotion motion = new FollowMotion();
    private static final PointerIdle idle = new PointerIdle();
    private static Object screen;
    private static long frame;

    public static void beginFrame() {
        frame++;
        var renderer = SpatialGUIClient.renderer();
        if (renderer == null || (!renderer.shouldCapture() && !renderer.isClosing())) reset();
    }

    private static long updated = -1;

    public static float angle(float original, boolean vertical) {
        if (!ExtrasClient.enabled()
                || !InteractionOptions.followPanel
                || !IntegrationHooks.worldView()) {
            reset();
            return original;
        }
        var renderer = SpatialGUIClient.renderer();
        var client = class_310.method_1551();
        if (renderer == null
                || client.field_1724 == null
                || !SpatialGUIClient.getEffectiveFirstPersonMode()) {
            reset();
            return original;
        }
        if (renderer.isClosing()) return original + (vertical ? motion.pitch() : motion.yaw());
        if (renderer.getHookedScreen() != screen) {
            reset();
            screen = renderer.getHookedScreen();
        }
        if (updated != frame) {
            updated = frame;
            var inv = renderer.getInventoryRenderer();
            var quad = inv.getQuadBasis();
            var cylinder = inv.getCylinderBasis();
            double x = MouseHandlerUtil.getSourceX(), y = MouseHandlerUtil.getSourceY();
            boolean outside =
                    quad != null
                            && !(cylinder == null
                                    ? RenderUtil.isMouseOverQuad(x, y, quad)
                                    : RenderUtil.isMouseOverCylinder(x, y, cylinder));
            boolean dragging = !client.field_1724.field_7512.method_34255().method_7960();
            var camera = client.field_1773.method_19418();
            long now = System.nanoTime();
            boolean stationary =
                    idle.update(
                            now,
                            x,
                            y,
                            camera.method_19330(),
                            camera.method_19329(),
                            InteractionOptions.followDelayMs);
            boolean eligible = outside && (!InteractionOptions.followWhenIdle || stationary);
            if (dragging && !InteractionOptions.followDragging) {
                idle.reset();
                motion.pause(now);
            } else
                motion.update(
                        now,
                        eligible,
                        camera.method_19330() - renderer.anchoredYaw(),
                        camera.method_19329() - renderer.anchoredPitch(),
                        InteractionOptions.followVertical,
                        InteractionOptions.followWhenIdle ? 0 : InteractionOptions.followDelayMs,
                        InteractionOptions.followSmoothMs);
        }
        return original + (vertical ? motion.pitch() : motion.yaw());
    }

    private static void reset() {
        motion.reset();
        idle.reset();
        screen = null;
        updated = -1;
    }

    private PanelFollow() {}
}
