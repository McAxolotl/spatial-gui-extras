package mcaxolotl.spatialguiextras.share;

import java.nio.ByteBuffer;

public record OverlayPose(float x, float y, float z, float yaw, float pitch, float size) {
    public OverlayPose scaledAround(float centerX, float centerY, float centerZ, float scale) {
        return new OverlayPose(
                centerX + (x - centerX) * scale,
                centerY + (y - centerY) * scale,
                centerZ + (z - centerZ) * scale,
                yaw,
                pitch,
                size * scale);
    }

    public static final int BYTES = 24;

    public byte[] encode() {
        return ByteBuffer.allocate(BYTES)
                .putFloat(x)
                .putFloat(y)
                .putFloat(z)
                .putFloat(yaw)
                .putFloat(pitch)
                .putFloat(size)
                .array();
    }

    public static OverlayPose decode(byte[] bytes) {
        if (bytes.length != BYTES) throw new IllegalArgumentException("overlay pose size");
        var b = ByteBuffer.wrap(bytes);
        float x = b.getFloat(),
                y = b.getFloat(),
                z = b.getFloat(),
                yaw = b.getFloat(),
                pitch = b.getFloat(),
                size = b.getFloat();
        if (!Float.isFinite(x)
                || !Float.isFinite(y)
                || !Float.isFinite(z)
                || Math.abs(x) > 1024
                || Math.abs(y) > 1024
                || Math.abs(z) > 1024
                || !Float.isFinite(yaw)
                || !Float.isFinite(pitch)
                || Math.abs(yaw) > 180
                || Math.abs(pitch) > 90
                || !Float.isFinite(size)
                || size <= 0
                || size > 2) throw new IllegalArgumentException("overlay pose values");
        return new OverlayPose(x, y, z, yaw, pitch, size);
    }
}
