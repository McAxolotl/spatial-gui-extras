package mcaxolotl.spatialguiextras.mixin;

import mcaxolotl.spatialguiextras.share.SpatialPoseProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.tastytrash.spatialGUI.render.InventoryRenderer;

@Mixin(targets = "org.tastytrash.spatialGUI.render.InventoryRenderer", remap = false)
public abstract class PanelGeometryMixin {
    @Inject(method = "renderInWorld", at = @At("TAIL"), require = 1, remap = false)
    private void sge$overlayItem(net.minecraft.class_4587 matrices, CallbackInfo ci) {
        mcaxolotl.spatialguiextras.share.OverflowOverlay.renderLocal(matrices);
    }

    @Inject(
            method = "renderInWorld",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lorg/tastytrash/spatialGUI/render/InventoryRenderer;updateScreenBases(Lorg/tastytrash/spatialGUI/render/InventoryRenderer$ScreenPose;Z)V",
                            shift = At.Shift.AFTER),
            require = 1,
            remap = false)
    private void sge$captureDisplayedPanel(CallbackInfo ci) {
        SpatialPoseProvider.capturePanel((InventoryRenderer) (Object) this);
    }
}
