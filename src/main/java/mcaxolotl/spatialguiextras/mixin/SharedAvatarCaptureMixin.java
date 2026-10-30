package mcaxolotl.spatialguiextras.mixin;

import mcaxolotl.spatialguiextras.share.AvatarDisplay;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.class_1007", priority = 900, remap = false)
public abstract class SharedAvatarCaptureMixin {
    @Inject(
            method = "spatialGUI$computePose(FFF)[F",
            at = @At("RETURN"),
            require = 0,
            remap = false)
    private static void sge$computedAvatar(
            float body, float headOffset, float pitch, CallbackInfoReturnable<float[]> result) {
        AvatarDisplay.capture(result.getReturnValue());
    }
}
