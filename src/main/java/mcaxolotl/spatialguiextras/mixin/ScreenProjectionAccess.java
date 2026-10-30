package mcaxolotl.spatialguiextras.mixin;

import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.tastytrash.spatialGUI.util.RenderUtil;

@Mixin(targets = "org.tastytrash.spatialGUI.util.RenderUtil", remap = false)
public interface ScreenProjectionAccess {
    @Invoker("buildCameraRayDirection")
    static float[] sge$ray(double x, double y) {
        throw new AssertionError();
    }

    @Invoker("getQuadUv")
    static Vector2f sge$quadUv(double x, double y, RenderUtil.QuadBasis basis) {
        throw new AssertionError();
    }

    @Invoker("getCylinderUv")
    static Vector2f sge$cylinderUv(double x, double y, RenderUtil.CylinderBasis basis) {
        throw new AssertionError();
    }
}
