package mcaxolotl.spatialguiextras.share;

import mcaxolotl.spatialguiextras.ExtrasClient;
import mcaxolotl.spatialguiextras.IntegrationHooks;

import net.minecraft.class_742;

public final class AvatarDisplay {
    private static AvatarPose local, firstPerson;

    public static void beginFrame() {
        local = firstPerson = null;
    }

    public static void captureFirstPerson(class_742 player, float worldYaw, float worldPitch) {
        if (!ExtrasClient.enabled() || !IntegrationHooks.worldView()) return;
        var renderer = org.tastytrash.spatialGUI.client.SpatialGUIClient.renderer();
        if (renderer == null) return;
        boolean mode =
                renderer.isClosing()
                        ? renderer.isClosingFirstPerson()
                        : org.tastytrash.spatialGUI.client.SpatialGUIClient
                                .getEffectiveFirstPersonMode();
        if (!mode) return;
        firstPerson = FirstPersonPose.capture(player.field_6283, worldYaw, worldPitch);
    }

    public static AvatarPose outgoing(boolean firstPersonMode) {
        return ShareConfig.INSTANCE.sharePose ? (firstPersonMode ? firstPerson : local) : null;
    }

    public static void capture(float[] values) {
        if (values == null || values.length != 3 || !ExtrasClient.enabled()) return;
        if (!IntegrationHooks.worldView()) return;
        for (float value : values) if (!Float.isFinite(value)) return;
        local = AvatarPose.displayed(values[0], values[1], values[2]);
    }

    public static final class Scope implements AutoCloseable {
        private final class_742 player;
        private final float body, oldBody, head, oldHead, pitch, oldPitch;

        public Scope(class_742 player, AvatarPose pose, float delta, float weight) {
            this.player = player;
            body = player.field_6283;
            oldBody = player.field_6220;
            head = player.field_6241;
            oldHead = player.field_6259;
            pitch = player.method_36455();
            oldPitch = player.field_6004;
            AvatarPose vanilla =
                    AvatarPose.entity(oldBody, body, oldHead, head, oldPitch, pitch, delta);
            AvatarPose shown = weight >= 1 ? pose : vanilla.blend(pose, weight);
            player.field_6283 = player.field_6220 = shown.body();
            player.field_6241 = player.field_6259 = shown.body() + shown.head();
            player.method_36457(shown.pitch());
            player.field_6004 = shown.pitch();
        }

        public void close() {
            player.field_6283 = body;
            player.field_6220 = oldBody;
            player.field_6241 = head;
            player.field_6259 = oldHead;
            player.method_36457(pitch);
            player.field_6004 = oldPitch;
        }
    }

    private AvatarDisplay() {}
}
