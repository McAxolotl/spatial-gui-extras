package mcaxolotl.spatialguiextras.interaction;

import mcaxolotl.spatialguiextras.share.AvatarPose;

public final class FollowMotion {
    private float yaw, pitch, endYaw, endPitch;
    private final FollowAxis yawAxis = new FollowAxis(), pitchAxis = new FollowAxis();
    private long outsideSince, previous;
    private boolean following;

    public void reset() {
        yawAxis.reset();
        pitchAxis.reset();
        yaw = pitch = 0;
        outsideSince = previous = 0;
        following = false;
    }

    public void pause(long now) {
        following = false;
        outsideSince = 0;
        previous = now;
        yawAxis.reset();
        pitchAxis.reset();
    }

    public boolean following() {
        return following;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public void update(
            long now,
            boolean outside,
            float targetYaw,
            float targetPitch,
            boolean vertical,
            int delayMs,
            int smoothMs) {
        if (!Float.isFinite(targetYaw) || !Float.isFinite(targetPitch)) return;
        targetPitch = Math.max(-90, Math.min(90, targetPitch));
        double dt = previous == 0 ? 0 : Math.max(0, Math.min(.1, (now - previous) / 1e9));
        previous = now;
        if (!following) {
            if (!outside) {
                outsideSince = 0;
                return;
            }
            if (Math.abs(AvatarPose.wrap(targetYaw - yaw)) < .05
                    && (!vertical || Math.abs(targetPitch - pitch) < .05)) {
                outsideSince = 0;
                return;
            }
            if (outsideSince == 0) outsideSince = now;
            if (now - outsideSince < delayMs * 1_000_000L) return;
            following = true;
            endYaw = targetYaw;
            endPitch = Math.max(-90, Math.min(90, targetPitch));
        }
        outsideSince = 0;
        yaw = yawAxis.step(yaw, endYaw, dt, smoothMs, true);
        if (vertical) pitch = pitchAxis.step(pitch, endPitch, dt, smoothMs, false);
        if (Math.abs(AvatarPose.wrap(yaw - endYaw)) < .01
                && (!vertical || Math.abs(pitch - endPitch) < .01)
                && yawAxis.settled()
                && (!vertical || pitchAxis.settled())) {
            yaw = AvatarPose.wrap(endYaw);
            if (vertical) pitch = endPitch;
            following = false;
            yawAxis.reset();
            pitchAxis.reset();
        }
    }
}
