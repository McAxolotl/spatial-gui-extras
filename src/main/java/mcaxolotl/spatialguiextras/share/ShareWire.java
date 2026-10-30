package mcaxolotl.spatialguiextras.share;

import net.minecraft.class_2960;

public final class ShareWire {
    public static final class_2960 CHANNEL = new class_2960("spatial_gui_extras", "share_v3");
    public static final byte TILE = 0;
    public static final byte POSE = 1;
    public static final byte CLEAR = 2;
    public static final byte ACK = 3;
    public static final byte AVATAR = 4;
    public static final byte OVERLAY = 5;

    public static final double RELAY_RADIUS = 48.0D;

    public static final int MAX_DIMENSION = 2048;
    public static final int MAX_TILE_SIDE = 64;
    public static final int MAX_TILE_PIXELS = MAX_TILE_SIDE * MAX_TILE_SIDE;
    public static final int MAX_PAYLOAD_BYTES = 32767;

    private ShareWire() {}
}
