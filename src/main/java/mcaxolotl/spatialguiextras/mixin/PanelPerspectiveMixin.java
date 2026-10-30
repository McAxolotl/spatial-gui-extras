package mcaxolotl.spatialguiextras.mixin;

import mcaxolotl.spatialguiextras.share.SpatialPoseProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.tastytrash.spatialGUI.render.SpatialGUIRenderer", remap = false)
public abstract class PanelPerspectiveMixin {
    @Inject(method = "capturePerspectiveState", at = @At("RETURN"), require = 1, remap = false)
    private void sge$captureWorldOrigin(CallbackInfo ci) {
        SpatialPoseProvider.capturePerspective();
    }
}
