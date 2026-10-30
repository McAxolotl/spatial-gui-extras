package mcaxolotl.spatialguiextras.interaction;

import io.netty.buffer.Unpooled;

import mcaxolotl.spatialguiextras.config.Configuration;
import mcaxolotl.spatialguiextras.share.*;

import net.minecraft.class_2540;

import java.nio.ByteBuffer;

public final class InteractionTests {
    private static int checks;

    private static void check(boolean b, String message) {
        checks++;
        if (!b) throw new AssertionError(message);
    }

    private static void reject(byte[] payload) {
        try {
            OverlayPose.decode(payload);
            throw new AssertionError("invalid overlay item accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    public static void main(String[] args) {
        var motion = new FollowMotion();
        long t = 1_000_000_000L;
        motion.update(t, true, 90, 40, true, 300, 250);
        motion.update(t + 299_000_000L, true, 90, 40, true, 300, 250);
        check(motion.yaw() == 0, "follow bypassed dwell delay");
        motion.update(t + 310_000_000L, true, 90, 40, true, 300, 250);
        check(motion.yaw() > 0 && motion.pitch() > 0, "followAxis did not follow view");
        float paused = motion.yaw();
        motion.pause(t + 320_000_000L);
        motion.update(t + 330_000_000L, false, -90, 0, true, 300, 250);
        check(motion.yaw() == paused && !motion.following(), "pause did not hold panel position");
        motion.reset();
        check(motion.yaw() == 0 && motion.pitch() == 0, "new screen retained follow offset");
        motion.update(t, true, 90, 0, false, 0, 250);
        motion.update(t + 50_000_000L, true, 90, 0, false, 0, 250);
        float released = motion.yaw();
        motion.update(t + 60_000_000L, false, 0, 0, false, 0, 250);
        check(motion.yaw() > released, "followAxis stopped abruptly on reentry");
        double lastFollow = 0;
        for (int fps : new int[] {30, 60, 144}) {
            var followAxis = new FollowAxis();
            float value = 0;
            boolean crossed = false;
            for (int i = 0; i < fps; i++) {
                value = followAxis.step(value, 90, 1.0 / fps, 250, false);
                if (value > 90) crossed = true;
            }
            check(crossed && Math.abs(value - 90) < .001, "followAxis did not float and settle");
            if (lastFollow != 0)
                check(Math.abs(value - lastFollow) < .001, "followAxis changed with FPS");
            lastFollow = value;
            followAxis.reset();
            value = followAxis.step(179, -179, .01, 250, true);
            check(Math.abs(AvatarPose.wrap(value - 179)) < 2, "followAxis used long angular path");
        }
        motion.reset();
        motion.update(t, true, 90, 0, false, 0, 700);
        motion.update(t + 50_000_000L, true, -90, 0, false, 0, 700);
        check(motion.yaw() > 0, "active response retargeted before settling");
        for (int i = 1; i <= 600; i++)
            motion.update(t + 50_000_000L + i * 10_000_000L, false, -90, 0, false, 600, 700);
        check(
                !motion.following() && Math.abs(motion.yaw() - 90) < .01,
                "inside panel started a new response");
        long again = t + 7_000_000_000L;
        motion.update(again, true, -90, 0, false, 600, 700);
        motion.update(again + 599_000_000L, true, -90, 0, false, 600, 700);
        check(
                !motion.following() && Math.abs(motion.yaw() - 90) < .01,
                "next response bypassed delay");
        motion.update(again + 601_000_000L, true, -90, 0, false, 600, 700);
        check(motion.following(), "next delayed response never started");
        var idle = new PointerIdle();
        check(!idle.update(t, 100, 100, 0, 0, 600), "new pointer starts idle");
        check(!idle.update(t + 599_000_000L, 100, 100, 0, 0, 600), "idle bypassed delay");
        check(
                idle.update(t + 600_000_000L, 100, 100, 0, 0, 600),
                "stationary pointer never became eligible");
        check(
                !idle.update(t + 610_000_000L, 100, 100, 2, 0, 600),
                "centered crosshair missed world look movement");
        check(
                !idle.update(t + 1_000_000_000L, 110, 100, 2, 0, 600),
                "physical pointer movement did not restart wait");
        float nativeElastic = org.tastytrash.spatialGUI.util.AnimationUtil.easeOutElastic(.5f);
        float retainedMotion = new FollowAxis().step(0, 1, .35, 700, false);
        check(
                Math.abs(nativeElastic - retainedMotion) > .05,
                "upstream animation comparison no longer describes this pinned version");
        System.out.println(
                "Pinned Spatial easing at half progress: elastic="
                        + nativeElastic
                        + ", floating="
                        + retainedMotion);
        var wide =
                new org.tastytrash.spatialGUI.util.RenderUtil.CylinderBasis(
                        new org.joml.Vector3f(0, 0, .9f),
                        new org.joml.Vector3f(0, 1, 0),
                        new org.joml.Vector3f(0, 0, 1),
                        new org.joml.Vector3f(0, 0, 1.5f),
                        .6f,
                        (float) Math.toRadians(150),
                        .9f);
        var centerHit = CylinderProjection.visibleHit(new float[] {0, 0, 1}, wide);
        check(
                centerHit != null
                        && Math.abs(centerHit.x - .5) < .001
                        && Math.abs(centerHit.y - .5) < .001,
                "near hidden cylinder wall blocked center input");
        check(
                CylinderProjection.visibleHit(new float[] {0, 0, -1}, wide) == null,
                "backwards ray selected an invisible hit");
        for (int i = -20; i <= 20; i++) {
            var direction = new org.joml.Vector3f(i / 100f, 0, 1).normalize();
            var hit =
                    CylinderProjection.visibleHit(
                            new float[] {direction.x, direction.y, direction.z}, wide);
            check(
                    hit != null && CylinderProjection.inside(hit),
                    "visible curved panel was not interactive");
        }
        var item = new OverlayPose(1, 2, 3, -179, 45, .1f);
        var scaled = item.scaledAround(0, 1, 2, 2);
        check(
                scaled.equals(new OverlayPose(2, 3, 4, -179, 45, .2f)),
                "remote overlay scale moved its reference point");
        check(
                item.equals(OverlayPose.decode(item.encode())),
                "overlay item lost visual traits or position");
        reject(new byte[1]);
        byte[] invalid = item.encode();
        ByteBuffer.wrap(invalid).putInt(-1);
        reject(invalid);
        invalid = item.encode();
        ByteBuffer.wrap(invalid).putFloat(0, Float.NaN);
        reject(invalid);
        invalid = item.encode();
        ByteBuffer.wrap(invalid).putFloat(20, 3);
        reject(invalid);
        var buf = new class_2540(Unpooled.buffer());
        try {
            buf.writeByte(ShareWire.OVERLAY).writeLong(1);
            buf.method_10813(new OverlayVisual(item, new byte[OverlayVisual.PIXEL_BYTES]).encode());
            var decoded = ShareProtocol.read(buf);
            check(
                    OverlayVisual.decode(decoded.data()).pose().equals(item),
                    "relay did not validate new visual data");
        } finally {
            buf.release();
        }
        byte[] pixels = new byte[OverlayVisual.PIXEL_BYTES];
        new java.util.Random(5).nextBytes(pixels);
        for (boolean noise : new boolean[] {true, false}) {
            if (!noise) java.util.Arrays.fill(pixels, (byte) 42);
            var visual = new OverlayVisual(item, pixels);
            var decoded = OverlayVisual.decode(visual.encode());
            check(
                    decoded.pose().equals(item)
                            && java.util.Arrays.equals(decoded.pixels(), pixels),
                    "shared sprite pixels changed");
        }
        check(OverlayVisual.decode(new byte[0]) == null, "sprite clear retained pixels");
        var config = Configuration.defaults();
        check(
                config.getProperty("followSmoothMs").equals("700"),
                "default settling time did not change");
        check(
                config.getProperty("followDelayMs").equals("600"),
                "default dwell delay did not change");
        check(
                config.getProperty("followPanel").equals("false"),
                "follow unexpectedly enabled by default");
        config.setProperty("followDelayMs", "-1");
        config.setProperty("followSmoothMs", "9999");
        config = Configuration.normalize(config);
        check(
                config.getProperty("followDelayMs").equals("0")
                        && config.getProperty("followSmoothMs").equals("3000"),
                "follow settings not bounded");
        System.out.println(
                "PASS interaction checks="
                        + checks
                        + ": dwell, easing, FPS independence, angular wrap, stop/reset, overlay visual protocol and invalid data");
    }
}
