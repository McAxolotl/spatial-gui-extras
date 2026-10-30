package mcaxolotl.spatialguiextras.share;

import java.nio.ByteBuffer;

public record AvatarPose(float body, float head, float pitch) {
    public static final int BYTES = 13;

    public static float wrap(float value) {
        float angle = value % 360;
        return angle >= 180 ? angle - 360 : angle < -180 ? angle + 360 : angle;
    }

    public static AvatarPose displayed(float body, float headOffset, float pitch) {
        return new AvatarPose(wrap(body), wrap(headOffset), Math.max(-90, Math.min(90, pitch)));
    }

    public static AvatarPose entity(
            float oldBody,
            float body,
            float oldHead,
            float head,
            float oldPitch,
            float pitch,
            float delta) {
        float t = Math.max(0, Math.min(1, delta));
        float shownBody = wrap(oldBody + wrap(body - oldBody) * t);
        float shownHead = wrap(oldHead + wrap(head - oldHead) * t);
        return displayed(shownBody, wrap(shownHead - shownBody), oldPitch + (pitch - oldPitch) * t);
    }

    public static byte[] encode(AvatarPose pose) {
        ByteBuffer bytes = ByteBuffer.allocate(BYTES);
        bytes.put((byte) (pose == null ? 0 : 1));
        if (pose != null) bytes.putFloat(pose.body).putFloat(pose.head).putFloat(pose.pitch);
        return bytes.array();
    }

    public static AvatarPose decode(byte[] data) {
        if (data.length != BYTES) throw new IllegalArgumentException("avatar size");
        ByteBuffer bytes = ByteBuffer.wrap(data);
        int active = bytes.get();
        float body = bytes.getFloat(), head = bytes.getFloat(), pitch = bytes.getFloat();
        if (active < 0
                || active > 1
                || !Float.isFinite(body)
                || !Float.isFinite(head)
                || !Float.isFinite(pitch)
                || Math.abs(body) > 180
                || Math.abs(head) > 180
                || Math.abs(pitch) > 90) throw new IllegalArgumentException("avatar orientation");
        return active == 0 ? null : new AvatarPose(body, head, pitch);
    }

    public AvatarPose blend(AvatarPose target, float delta) {
        float t = Math.max(0, Math.min(1, delta));
        return new AvatarPose(
                wrap(body + wrap(target.body - body) * t),
                wrap(head + wrap(target.head - head) * t),
                pitch + (target.pitch - pitch) * t);
    }
}
