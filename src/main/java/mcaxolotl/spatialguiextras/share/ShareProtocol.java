package mcaxolotl.spatialguiextras.share;

import net.minecraft.class_2540;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class ShareProtocol {
    public static final int POSE_BYTES = 101;
    private static final ThreadLocal<Inflater> INFLATERS = ThreadLocal.withInitial(Inflater::new);

    public record Message(
            byte type,
            long session,
            long frame,
            int width,
            int height,
            int x,
            int y,
            int tw,
            int th,
            byte[] data,
            UUID owner,
            int bytes) {}

    public static Message read(class_2540 input) {
        if (input.readableBytes() < 9 || input.readableBytes() > ShareWire.MAX_PAYLOAD_BYTES)
            throw new IllegalArgumentException("message size");
        byte type = input.readByte();
        long session = input.readLong();
        if (session <= 0) throw new IllegalArgumentException("session");
        Message result;
        if (type == ShareWire.CLEAR)
            result = new Message(type, session, 0, 0, 0, 0, 0, 0, 0, null, null, 0);
        else if (type == ShareWire.ACK) {
            UUID owner = input.method_10790();
            int bytes = input.readInt();
            if (bytes <= 0 || bytes > FlowWindow.LIMIT)
                throw new IllegalArgumentException("ACK credits");
            result = new Message(type, session, 0, 0, 0, 0, 0, 0, 0, null, owner, bytes);
        } else if (type == ShareWire.POSE) {
            byte[] data = input.method_10803(POSE_BYTES);
            validatePose(data);
            result = new Message(type, session, 0, 0, 0, 0, 0, 0, 0, data, null, 0);
        } else if (type == ShareWire.AVATAR) {
            byte[] data = input.method_10803(AvatarPose.BYTES);
            AvatarPose.decode(data);
            result = new Message(type, session, 0, 0, 0, 0, 0, 0, 0, data, null, 0);
        } else if (type == ShareWire.OVERLAY) {
            byte[] data = input.method_10803(OverlayVisual.MAX_BYTES);
            OverlayVisual.decode(data);
            result = new Message(type, session, 0, 0, 0, 0, 0, 0, 0, data, null, 0);
        } else if (type == ShareWire.TILE) {
            long frame = input.readLong();
            int w = input.readInt(),
                    h = input.readInt(),
                    x = input.readInt(),
                    y = input.readInt(),
                    tw = input.readInt(),
                    th = input.readInt();
            if (frame <= 0
                    || w <= 0
                    || h <= 0
                    || w > ShareWire.MAX_DIMENSION
                    || h > ShareWire.MAX_DIMENSION
                    || tw <= 0
                    || th <= 0
                    || tw > ShareWire.MAX_TILE_SIDE
                    || th > ShareWire.MAX_TILE_SIDE
                    || x < 0
                    || y < 0
                    || x > w - tw
                    || y > h - th) throw new IllegalArgumentException("tile bounds");
            byte[] data = input.method_10803(ShareWire.MAX_TILE_PIXELS * 4);
            if (inflate(data, tw * th * 4) == null)
                throw new IllegalArgumentException("tile encoding");
            result = new Message(type, session, frame, w, h, x, y, tw, th, data, null, 0);
        } else throw new IllegalArgumentException("message type");
        if (input.isReadable()) throw new IllegalArgumentException("trailing bytes");
        return result;
    }

    public static void validatePose(byte[] data) {
        if (data.length != POSE_BYTES) throw new IllegalArgumentException("pose size");
        ByteBuffer b = ByteBuffer.wrap(data);
        if ((b.get() & ~3) != 0) throw new IllegalArgumentException("pose flags");
        float[] v = new float[24];
        for (int i = 0; i < v.length; i++) {
            v[i] = b.getFloat();
            if (!Float.isFinite(v[i])) throw new IllegalArgumentException("nonfinite pose");
        }
        for (int i : new int[] {0, 1, 2, 14, 15, 16})
            if (Math.abs(v[i]) > 1024) throw new IllegalArgumentException("pose offset");
        for (int i : new int[] {3, 6, 17, 20}) {
            float norm = v[i] * v[i] + v[i + 1] * v[i + 1] + v[i + 2] * v[i + 2];
            if (norm < 0.25f || norm > 2.25f) throw new IllegalArgumentException("pose basis");
        }
        if (v[9] < 0
                || v[9] > 256
                || v[10] < 0
                || v[10] > 256
                || v[11] < 0
                || v[11] > 6.284f
                || v[12] < 0
                || v[12] > 8192
                || v[13] < 0
                || v[13] > 256
                || v[23] < 0
                || v[23] > 1) throw new IllegalArgumentException("pose dimensions");
        int fade = b.getInt();
        if (fade < 0 || fade > 10000) throw new IllegalArgumentException("fade duration");
    }

    public static byte[] inflate(byte[] input, int expected) {
        if (input.length == expected) return input;
        if (input.length == 0 || expected <= 0 || expected > ShareWire.MAX_TILE_PIXELS * 4)
            return null;
        Inflater inflater = INFLATERS.get();
        inflater.reset();
        try {
            inflater.setInput(input);
            byte[] output = new byte[expected];
            int offset = 0;
            while (offset < expected && !inflater.finished()) {
                int n = inflater.inflate(output, offset, expected - offset);
                if (n == 0) return null;
                offset += n;
            }
            return offset == expected && inflater.finished() && inflater.getRemaining() == 0
                    ? output
                    : null;
        } catch (DataFormatException e) {
            return null;
        }
    }

    private ShareProtocol() {}
}
