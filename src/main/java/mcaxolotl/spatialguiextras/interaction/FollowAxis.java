package mcaxolotl.spatialguiextras.interaction;

import mcaxolotl.spatialguiextras.share.AvatarPose;

final class FollowAxis {
    private double velocity;

    void reset() {
        velocity = 0;
    }

    float step(float position, float target, double dt, int durationMs, boolean angular) {
        if (durationMs <= 0) {
            reset();
            return angular ? AvatarPose.wrap(target) : target;
        }
        if (dt <= 0) return position;
        double damping = .8,
                omega = 4.6 / (damping * durationMs / 1000.0),
                frequency = omega * Math.sqrt(1 - damping * damping);
        double error = angular ? AvatarPose.wrap(position - target) : position - target;
        double goal = position - error;
        double b = (velocity + damping * omega * error) / frequency;
        double e = Math.exp(-damping * omega * dt),
                cos = Math.cos(frequency * dt),
                sin = Math.sin(frequency * dt);
        double offset = e * (error * cos + b * sin);
        velocity =
                e
                        * ((-damping * omega * error + frequency * b) * cos
                                + (-damping * omega * b - frequency * error) * sin);
        float result = (float) (goal + offset);
        return angular ? AvatarPose.wrap(result) : result;
    }

    boolean settled() {
        return Math.abs(velocity) < .01;
    }
}
