package mcaxolotl.spatialguiextras.share;

import mcaxolotl.spatialguiextras.interaction.FirstPersonLook;

public final class WorldPoseTests {
    private static int checks;

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        var pose = FirstPersonPose.capture(0, 35, -20);
        check(
                pose.body() == 0 && pose.head() == 35 && pose.pitch() == -20,
                "head movement forced the body into the view direction");
        for (int yaw = -720; yaw <= 720; yaw++) {
            float body = AvatarPose.wrap(yaw * .2f);
            pose = FirstPersonPose.capture(body, yaw, 10);
            check(pose.body() == body, "display body diverged from real body");
            check(
                    Math.abs(AvatarPose.wrap(pose.body() + pose.head() - yaw)) < .001,
                    "head lost the 360-degree world direction");
            var normal = AvatarPose.displayed(body, 0, 0);
            for (float weight : new float[] {0, .25f, .5f, .75f, 1})
                check(
                        Math.abs(AvatarPose.wrap(normal.blend(pose, weight).body() - body)) < .001,
                        "GUI exit introduced a body rotation");
        }
        check(
                FirstPersonPose.capture(20, 25, 0).body() == 20,
                "new GUI did not use real body orientation");
        check(FirstPersonPose.capture(Float.NaN, 0, 0) == null, "invalid pose transmitted");
        for (int yaw = -720; yaw <= 720; yaw++) {
            float current = AvatarPose.wrap(yaw - 1);
            double turn = FirstPersonLook.yawDelta(current, yaw);
            check(
                    Math.abs(turn * .15f - 1) < .001,
                    "world yaw crossing caused a large native turn");
            check(
                    FirstPersonLook.yawDelta(yaw, yaw) == 0,
                    "stationary world camera kept turning the player");
        }
        var firstField = AvatarDisplay.class.getDeclaredField("firstPerson");
        firstField.setAccessible(true);
        var localField = AvatarDisplay.class.getDeclaredField("local");
        localField.setAccessible(true);
        var first = AvatarPose.displayed(0, 35, -20);
        var third = AvatarPose.displayed(10, 15, 5);
        AvatarDisplay.beginFrame();
        firstField.set(null, first);
        localField.set(null, third);
        ShareConfig.INSTANCE.sharePose = true;
        check(
                AvatarDisplay.outgoing(true).equals(first)
                        && AvatarDisplay.outgoing(false).equals(third),
                "combined pose control lost a camera mode");
        ShareConfig.INSTANCE.sharePose = false;
        check(
                AvatarDisplay.outgoing(true) == null && AvatarDisplay.outgoing(false) == null,
                "combined pose opt-out left a mode sharing");
        AvatarDisplay.beginFrame();
        ShareConfig.INSTANCE.sharePose = true;
        check(AvatarDisplay.outgoing(true) == null, "old frame's pose survived");
        for (boolean active : new boolean[] {false, true})
            for (boolean free : new boolean[] {false, true})
                check(
                        SharedCursor.crosshair(active, free) == (active && !free),
                        "indicator does not follow Spatial/editor mode");
        var random = new java.util.Random(619);
        for (int i = 0; i < 1000; i++) {
            pose =
                    FirstPersonPose.capture(
                            20, random.nextFloat() * 720 - 360, random.nextFloat() * 180 - 90);
            check(
                    pose.equals(AvatarPose.decode(AvatarPose.encode(pose))),
                    "pose failed existing wire validation");
        }
        System.out.println(
                "PASS pose checks="
                        + checks
                        + "; 360-degree head/body separation, native look deltas, real body orientation and stationary exit, unified opt-out, frame isolation and v3 wire");
    }
}
