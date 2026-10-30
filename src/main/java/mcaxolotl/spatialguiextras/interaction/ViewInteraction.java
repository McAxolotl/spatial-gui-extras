package mcaxolotl.spatialguiextras.interaction;

import mcaxolotl.spatialguiextras.ExtrasClient;
import mcaxolotl.spatialguiextras.IntegrationHooks;

import net.minecraft.class_310;
import net.minecraft.class_742;

import org.tastytrash.spatialGUI.client.SpatialGUIClient;

public final class ViewInteraction {
    public static void capture(class_742 player, float yaw, float pitch) {
        if (!ExtrasClient.enabled() || !IntegrationHooks.worldView()) return;
        var renderer = SpatialGUIClient.renderer();
        var client = class_310.method_1551();
        if (renderer != null
                && !renderer.isClosing()
                && SpatialGUIClient.getEffectiveFirstPersonMode()
                && renderer.shouldCapture()
                && renderer.getHookedScreen() == client.field_1755
                && client.field_1773.method_19418().method_19331() == player)
            FirstPersonLook.apply(player, yaw, pitch);
    }

    private ViewInteraction() {}
}
