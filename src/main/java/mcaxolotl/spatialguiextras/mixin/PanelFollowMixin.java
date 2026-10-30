package mcaxolotl.spatialguiextras.mixin;

import mcaxolotl.spatialguiextras.interaction.PanelFollow;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.tastytrash.spatialGUI.render.SpatialGUIRenderer;

@Mixin(targets = "org.tastytrash.spatialGUI.render.InventoryRenderer", remap = false)
public abstract class PanelFollowMixin {
    @Redirect(
            method = {"computeScreenPose", "beginClosing"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lorg/tastytrash/spatialGUI/render/SpatialGUIRenderer;anchoredYaw()F"),
            require = 1,
            remap = false)
    private float sge$followYaw(SpatialGUIRenderer renderer) {
        return PanelFollow.angle(renderer.anchoredYaw(), false);
    }

    @Redirect(
            method = {"computeScreenPose", "beginClosing"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lorg/tastytrash/spatialGUI/render/SpatialGUIRenderer;anchoredPitch()F"),
            require = 1,
            remap = false)
    private float sge$followPitch(SpatialGUIRenderer renderer) {
        return PanelFollow.angle(renderer.anchoredPitch(), true);
    }
}
