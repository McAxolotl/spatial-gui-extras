package mcaxolotl.spatialguiextras.share;

final class FirstPersonPose {
    static AvatarPose capture(float entityBody, float viewYaw, float viewPitch) {
        if (!Float.isFinite(entityBody) || !Float.isFinite(viewYaw) || !Float.isFinite(viewPitch))
            return null;
        return AvatarPose.displayed(entityBody, AvatarPose.wrap(viewYaw - entityBody), viewPitch);
    }

    private FirstPersonPose() {}
}
