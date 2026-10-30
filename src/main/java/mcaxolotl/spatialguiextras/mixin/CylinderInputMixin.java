package mcaxolotl.spatialguiextras.mixin;

import mcaxolotl.spatialguiextras.ExtrasClient;
import mcaxolotl.spatialguiextras.interaction.CylinderProjection;

import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.tastytrash.spatialGUI.util.RenderUtil;

@Mixin(targets = "org.tastytrash.spatialGUI.util.RenderUtil", remap = false)
public abstract class CylinderInputMixin {
    @Inject(
            method = "getCylinderUv",
            at = @At("RETURN"),
            cancellable = true,
            require = 1,
            remap = false)
    private static void sge$visibleCylinder(
            double x,
            double y,
            RenderUtil.CylinderBasis basis,
            CallbackInfoReturnable<Vector2f> cir) {
        if (!ExtrasClient.enabled() || CylinderProjection.inside(cir.getReturnValue())) return;
        var hit = CylinderProjection.visibleHit(ScreenProjectionAccess.sge$ray(x, y), basis);
        if (hit != null) cir.setReturnValue(hit);
    }
}
