package mcaxolotl.spatialguiextras.mixin;

import mcaxolotl.spatialguiextras.share.OverflowOverlay;

import net.minecraft.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.class_465", remap = false)
public abstract class HeldItemMixin {
    @Inject(
            method = "method_2382",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false)
    private void sge$outsideItem(
            class_332 graphics, class_1799 stack, int x, int y, String text, CallbackInfo ci) {
        if (OverflowOverlay.active()) ci.cancel();
    }
}
