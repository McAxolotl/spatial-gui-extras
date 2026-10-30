package mcaxolotl.spatialguiextras.share;

import java.util.Arrays;
import java.util.zip.Deflater;

public record OverlayVisual(OverlayPose pose, byte[] pixels) {
    public static final int PIXEL_BYTES = 64 * 64 * 4, MAX_BYTES = OverlayPose.BYTES + PIXEL_BYTES;

    public static OverlayVisual decode(byte[] data) {
        if (data.length == 0) return null;
        if (data.length <= OverlayPose.BYTES || data.length > MAX_BYTES)
            throw new IllegalArgumentException("overlay visual size");
        var pose = OverlayPose.decode(Arrays.copyOf(data, OverlayPose.BYTES));
        byte[] pixels =
                ShareProtocol.inflate(
                        Arrays.copyOfRange(data, OverlayPose.BYTES, data.length), PIXEL_BYTES);
        if (pixels == null) throw new IllegalArgumentException("overlay visual encoding");
        return new OverlayVisual(pose, pixels);
    }

    public byte[] encode() {
        if (pixels.length != PIXEL_BYTES) throw new IllegalArgumentException("overlay pixel size");
        var deflater = new Deflater(1);
        byte[] compressed = new byte[PIXEL_BYTES];
        int size;
        try {
            deflater.setInput(pixels);
            deflater.finish();
            size = deflater.deflate(compressed);
            if (!deflater.finished() || size >= PIXEL_BYTES) {
                compressed = pixels;
                size = pixels.length;
            }
        } finally {
            deflater.end();
        }
        byte[] output = Arrays.copyOf(pose.encode(), OverlayPose.BYTES + size);
        System.arraycopy(compressed, 0, output, OverlayPose.BYTES, size);
        return output;
    }
}
