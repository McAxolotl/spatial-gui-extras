package mcaxolotl.spatialguiextras.mixin;

import mcaxolotl.spatialguiextras.share.AvatarDisplay;
import mcaxolotl.spatialguiextras.share.ShareClient;
import mcaxolotl.spatialguiextras.share.SpatialPoseProvider;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_757", priority = 800, remap = false)
public abstract class FrameMixin {
    @Inject(method = "method_3192", at = @At("HEAD"), require = 1, remap = false)
    private void sge$beginFrame(CallbackInfo ci) {
        mcaxolotl.spatialguiextras.interaction.PanelFollow.beginFrame();
        mcaxolotl.spatialguiextras.share.OverflowOverlay.beginFrame();
        AvatarDisplay.beginFrame();
        SpatialPoseProvider.beginFrame();
    }

    @Inject(method = "method_3192", at = @At("TAIL"), require = 1, remap = false)
    private void sge$shareFrame(CallbackInfo ci) {
        ShareClient.afterFrame();
    }
}
