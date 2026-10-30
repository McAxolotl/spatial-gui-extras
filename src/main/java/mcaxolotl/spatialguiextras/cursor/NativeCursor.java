package mcaxolotl.spatialguiextras.cursor;

import com.sun.jna.*;
import com.sun.jna.ptr.IntByReference;

import org.lwjgl.glfw.GLFWNativeX11;

public final class NativeCursor {
    @Structure.FieldOrder({"x", "y", "width", "height", "xhot", "yhot", "serial", "pixels"})
    public static class Image extends Structure {
        public short x, y, width, height, xhot, yhot;
        public NativeLong serial;
        public Pointer pixels;

        public Image(Pointer p) {
            super(p);
            read();
        }
    }

    private static boolean unavailable, ready;
    private static Pointer display;
    private static Function readImage, free;
    private static long serial = -1;
    private static CursorGlyphs.Sprite cached;

    public static CursorGlyphs.Sprite capture() {
        if (unavailable) return null;
        Pointer pointer = null;
        try {
            if (!ready) {
                if (!System.getProperty("os.name", "")
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains("linux")) {
                    unavailable = true;
                    return null;
                }
                long address = GLFWNativeX11.glfwGetX11Display();
                if (address == 0) {
                    unavailable = true;
                    return null;
                }
                display = new Pointer(address);
                NativeLibrary fixes = NativeLibrary.getInstance("Xfixes");
                if (fixes.getFunction("XFixesQueryExtension")
                                .invokeInt(
                                        new Object[] {
                                            display, new IntByReference(), new IntByReference()
                                        })
                        == 0) {
                    unavailable = true;
                    return null;
                }
                readImage = fixes.getFunction("XFixesGetCursorImage");
                free = NativeLibrary.getInstance("X11").getFunction("XFree");
                ready = true;
            }
            pointer = readImage.invokePointer(new Object[] {display});
            if (pointer == null) return null;
            Image image = new Image(pointer);
            int width = Short.toUnsignedInt(image.width),
                    height = Short.toUnsignedInt(image.height);
            int hx = Short.toUnsignedInt(image.xhot), hy = Short.toUnsignedInt(image.yhot);
            if (width == 0
                    || height == 0
                    || width > 256
                    || height > 256
                    || hx >= width
                    || hy >= height
                    || image.pixels == null) return null;
            if (cached != null && image.serial.longValue() == serial) return cached;
            int[] pixels = new int[width * height];
            if (Native.LONG_SIZE == 8) {
                long[] packed = image.pixels.getLongArray(0, pixels.length);
                for (int i = 0; i < pixels.length; i++) pixels[i] = (int) packed[i];
            } else pixels = image.pixels.getIntArray(0, pixels.length);
            for (int i = 0; i < pixels.length; i++) {
                int value = pixels[i], a = value >>> 24;
                if (a == 0) {
                    pixels[i] = 0;
                    continue;
                }
                // XRender cursor pixels use premultiplied ARGB; our rasterizer expects straight
                // RGBA.
                int r = Math.min(255, ((value >>> 16 & 255) * 255 + a / 2) / a),
                        g = Math.min(255, ((value >>> 8 & 255) * 255 + a / 2) / a),
                        b = Math.min(255, ((value & 255) * 255 + a / 2) / a);
                pixels[i] = a << 24 | r << 16 | g << 8 | b;
            }
            serial = image.serial.longValue();
            cached = new CursorGlyphs.Sprite(width, height, hx, hy, pixels, true);
            return cached;
        } catch (RuntimeException | LinkageError e) {
            unavailable = true;
            System.out.println(
                    "[Spatial GUI Extras cursor] Native cursor unavailable; standard-shape fallback: "
                            + e.getClass().getSimpleName());
            return null;
        } finally {
            if (pointer != null && free != null) free.invokeInt(new Object[] {pointer});
        }
    }

    private NativeCursor() {}
}
