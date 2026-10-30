package mcaxolotl.spatialguiextras.interaction;

import mcaxolotl.spatialguiextras.share.AvatarPose;

import net.minecraft.class_1297;

public final class FirstPersonLook {
    public static double yawDelta(float current, float target) {
        return AvatarPose.wrap(target - current) / (double) .15f;
    }

    static void apply(class_1297 player, float yaw, float pitch) {
        if (!Float.isFinite(yaw)
                || !Float.isFinite(pitch)
                || !Float.isFinite(player.method_36454())
                || !Float.isFinite(player.method_36455())) return;
        float clampedPitch = Math.max(-90, Math.min(90, pitch));
        player.method_5872(
                yawDelta(player.method_36454(), yaw),
                (clampedPitch - player.method_36455()) / (double) .15f);
    }

    private FirstPersonLook() {}
}
