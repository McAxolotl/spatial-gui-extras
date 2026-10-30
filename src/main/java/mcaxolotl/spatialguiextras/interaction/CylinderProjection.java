package mcaxolotl.spatialguiextras.interaction;

import org.joml.Vector2f;
import org.joml.Vector3f;
import org.tastytrash.spatialGUI.util.RenderUtil;

public final class CylinderProjection {
    public static boolean inside(Vector2f uv) {
        return uv != null && uv.x >= 0 && uv.x <= 1 && uv.y >= 0 && uv.y <= 1;
    }

    public static Vector2f visibleHit(float[] ray, RenderUtil.CylinderBasis basis) {
        if (ray == null
                || ray.length != 3
                || basis.radius() <= 0
                || basis.halfHeight() <= 0
                || basis.arcRadians() <= 0) return null;
        Vector3f d = new Vector3f(ray[0], ray[1], ray[2]),
                up = basis.axisDir(),
                origin = new Vector3f(basis.axisPos()).negate();
        Vector3f dp = new Vector3f(d).fma(-d.dot(up), up),
                op = new Vector3f(origin).fma(-origin.dot(up), up);
        double a = dp.lengthSquared(),
                b = 2 * op.dot(dp),
                c = op.lengthSquared() - basis.radius() * basis.radius(),
                disc = b * b - 4 * a * c;
        if (a < 1e-10 || disc < 0 || !Double.isFinite(disc)) return null;
        for (double t :
                new double[] {(-b - Math.sqrt(disc)) / (2 * a), (-b + Math.sqrt(disc)) / (2 * a)}) {
            if (t <= 0 || !Double.isFinite(t)) continue;
            var hit = new Vector3f(d).mul((float) t);
            var radial = new Vector3f(hit).sub(basis.axisPos());
            radial.fma(-radial.dot(up), up);
            if (radial.lengthSquared() < 1e-12) continue;
            radial.normalize();
            float cosine = basis.refDir().dot(radial),
                    sine = new Vector3f(up).cross(radial).dot(basis.refDir());
            float u = .5f + (float) Math.atan2(sine, cosine) / basis.arcRadians();
            float v =
                    .5f + new Vector3f(hit).sub(basis.center()).dot(up) / (2 * basis.halfHeight());
            var uv = new Vector2f(u, v);
            if (inside(uv)) return uv;
        }
        return null;
    }

    private CylinderProjection() {}
}
