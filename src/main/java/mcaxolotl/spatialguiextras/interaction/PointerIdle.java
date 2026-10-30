package mcaxolotl.spatialguiextras.interaction;

import mcaxolotl.spatialguiextras.share.AvatarPose;

public final class PointerIdle {
    private boolean initialized;
    private double x, y;
    private float yaw, pitch;
    private long movedAt;

    public void reset() {
        initialized = false;
    }

    public boolean update(
            long now, double nextX, double nextY, float nextYaw, float nextPitch, int delayMs) {
        if (!Double.isFinite(nextX)
                || !Double.isFinite(nextY)
                || !Float.isFinite(nextYaw)
                || !Float.isFinite(nextPitch)) {
            reset();
            return false;
        }
        if (!initialized
                || Math.hypot(nextX - x, nextY - y) >= 1
                || Math.abs(AvatarPose.wrap(nextYaw - yaw)) >= .15f
                || Math.abs(nextPitch - pitch) >= .15f) {
            initialized = true;
            x = nextX;
            y = nextY;
            yaw = nextYaw;
            pitch = nextPitch;
            movedAt = now;
        }
        return now - movedAt >= delayMs * 1_000_000L;
    }
}
