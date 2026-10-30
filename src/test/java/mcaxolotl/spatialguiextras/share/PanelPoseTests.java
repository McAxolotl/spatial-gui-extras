package mcaxolotl.spatialguiextras.share;

import org.joml.Vector3f;
import org.tastytrash.spatialGUI.util.RenderUtil;

public final class PanelPoseTests {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static RenderUtil.QuadBasis quad() {
        return new RenderUtil.QuadBasis(
                new Vector3f(0, 1, -3),
                new Vector3f(1, 0, 0),
                new Vector3f(0, 1, 0),
                new Vector3f(0, 0, 1),
                2,
                1);
    }

    public static void main(String[] args) {
        var quad = quad();
        var cylinder =
                new RenderUtil.CylinderBasis(
                        new Vector3f(0, 0, 2),
                        new Vector3f(0, 1, 0),
                        new Vector3f(0, 0, -1),
                        new Vector3f(),
                        4,
                        .5f,
                        1);
        for (boolean curved : new boolean[] {false, true}) {
            for (int i = 0; i < 100; i++) {
                SpatialPoseProvider.beginFrame();
                check(
                        SpatialPoseProvider.snapshot() == null,
                        "previous frame leaked into a new frame");
                SpatialPoseProvider.captureGeometry(
                        quad, curved ? cylinder : null, .8f, 300, false);
                check(
                        SpatialPoseProvider.snapshot() == null,
                        "panel published without a captured world origin");
                SpatialPoseProvider.captureOrigin(
                        29_999_999.25, 66, -29_999_999.5, 29_999_998, 64, -29_999_998);
                SpatialPoseProvider.captureGeometry(
                        quad, curved ? cylinder : null, .8f, 300, false);
                var displayed = SpatialPoseProvider.snapshot();
                check(
                        displayed.centerX() == 1.25f
                                && displayed.centerY() == 3
                                && displayed.centerZ() == -4.5f,
                        "panel no longer follows the captured player world origin");
                check(
                        displayed.curved() == curved
                                && displayed.rightX() == 1
                                && displayed.upY() == 1,
                        "actual displayed orientation changed");
                if (curved)
                    check(
                            displayed.axisX() == 1.25f
                                    && displayed.axisY() == 2
                                    && displayed.axisZ() == .5f,
                            "cylinder axis used a different origin");
                // A later monitor pass can move/rotate its camera and mutate shared JOML vectors.
                SpatialPoseProvider.captureOrigin(i * 100, -i * 30, i * 50, 0, 0, 0);
                quad.right().rotateY(.7f);
                quad.centerOffset().add(20, 30, 40);
                cylinder.axisPos().add(50, 60, 70);
                cylinder.refDir().rotateY(.5f);
                check(
                        SpatialPoseProvider.snapshot().equals(displayed),
                        "late monitor state moved the outgoing panel");
                quad = quad();
                cylinder =
                        new RenderUtil.CylinderBasis(
                                new Vector3f(0, 0, 2),
                                new Vector3f(0, 1, 0),
                                new Vector3f(0, 0, -1),
                                new Vector3f(),
                                4,
                                .5f,
                                1);
            }
        }
        SpatialPoseProvider.beginFrame();
        SpatialPoseProvider.captureOrigin(0, 0, 0, 0, 0, 0);
        SpatialPoseProvider.captureGeometry(quad, null, .35f, 400, true);
        var closing = SpatialPoseProvider.snapshot();
        check(
                closing.closing() && closing.opacity() == .35f && closing.fadeMs() == 400,
                "closing animation metadata lost");
        System.out.println(
                "PASS panel pose checks="
                        + checks
                        + " (flat/curved origins, late monitor changes, mutable bases, world-border precision, frame reset, fade)");
    }
}
