package mcaxolotl.spatialguiextras.cursor;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public final class CursorGlyphs {
    public record Sprite(
            int width, int height, int hotX, int hotY, int[] argb, boolean nativeImage) {}

    private static final Map<Integer, Sprite> STANDARD = new HashMap<>();

    static {
        STANDARD.put(
                GLFW.GLFW_ARROW_CURSOR,
                ascii(
                        new String[] {
                            "X...........", "XX..........", "XOX.........", "XOOX........",
                                    "XOOOX.......", "XOOOOX......",
                            "XOOOOOX.....", "XOOOOOOX....", "XOOOOOOOX...", "XOOOOOOOOX..",
                                    "XOOOOXXXXX..", "XOOXOOX.....",
                            "XOX.XOOX....", "XX..XOOX....", "X....XOOX...", ".....XOOX...",
                                    "......XX...."
                        },
                        0,
                        0));
        STANDARD.put(
                GLFW.GLFW_HAND_CURSOR,
                ascii(
                        new String[] {
                            ".....XX.........",
                            "....XOOX........",
                            "....XOOX........",
                            "....XOOX........",
                            "....XOOXXXX.....",
                            "....XOOXOOXXX...",
                            "....XOOOOOXOOX..",
                            "XX..XOOOOOOOOOX.",
                            "XOX.XOOOOOOOOOX.",
                            "XOOXXOOOOOOOOOX.",
                            ".XOOOOOOOOOOOOX.",
                            "..XOOOOOOOOOOOX.",
                            "...XOOOOOOOOOX..",
                            "....XOOOOOOOOX..",
                            "....XOOOOOOOX...",
                            ".....XXXXXXXX..."
                        },
                        5,
                        1));
        for (int shape :
                new int[] {
                    GLFW.GLFW_IBEAM_CURSOR,
                    GLFW.GLFW_CROSSHAIR_CURSOR,
                    GLFW.GLFW_HRESIZE_CURSOR,
                    GLFW.GLFW_VRESIZE_CURSOR,
                    0x36007,
                    0x36008,
                    0x36009,
                    0x3600a
                }) STANDARD.put(shape, procedural(shape));
    }

    public static Sprite standard(int shape) {
        return STANDARD.getOrDefault(shape, STANDARD.get(GLFW.GLFW_ARROW_CURSOR));
    }

    private static Sprite ascii(String[] rows, int hx, int hy) {
        int width = java.util.Arrays.stream(rows).mapToInt(String::length).max().orElseThrow();
        int[] pixels = new int[width * rows.length];
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < rows[y].length(); x++)
                pixels[y * width + x] =
                        rows[y].charAt(x) == 'X'
                                ? 0xff000000
                                : rows[y].charAt(x) == 'O' ? 0xffffffff : 0;
        return new Sprite(width, rows.length, hx, hy, pixels, false);
    }

    private static Sprite procedural(int shape) {
        boolean[] mask = new boolean[19 * 19];
        if (shape == GLFW.GLFW_IBEAM_CURSOR) {
            line(mask, 9, 2, 9, 16);
            line(mask, 6, 2, 12, 2);
            line(mask, 6, 16, 12, 16);
        } else if (shape == GLFW.GLFW_CROSSHAIR_CURSOR) {
            line(mask, 2, 9, 16, 9);
            line(mask, 9, 2, 9, 16);
        } else if (shape == GLFW.GLFW_HRESIZE_CURSOR || shape == 0x36009) horizontal(mask);
        else if (shape == GLFW.GLFW_VRESIZE_CURSOR) {
            boolean[] temp = new boolean[mask.length];
            horizontal(temp);
            for (int y = 0; y < 19; y++)
                for (int x = 0; x < 19; x++) mask[y * 19 + x] = temp[x * 19 + y];
        } else if (shape == 0x3600a) {
            for (int y = 1; y < 18; y++)
                for (int x = 1; x < 18; x++) {
                    double radius = Math.hypot(x - 9, y - 9);
                    if (radius >= 5 && radius <= 6.3) mask[y * 19 + x] = true;
                }
            line(mask, 5, 5, 13, 13);
        } else {
            line(mask, 3, 3, 15, 15);
            line(mask, 3, 3, 9, 3);
            line(mask, 3, 3, 3, 9);
            line(mask, 15, 15, 9, 15);
            line(mask, 15, 15, 15, 9);
            if (shape == 0x36008) {
                boolean[] temp = mask.clone();
                for (int y = 0; y < 19; y++)
                    for (int x = 0; x < 19; x++) mask[y * 19 + x] = temp[y * 19 + 18 - x];
            }
        }
        if (shape == 0x36009) {
            boolean[] temp = mask.clone();
            for (int y = 0; y < 19; y++)
                for (int x = 0; x < 19; x++) mask[y * 19 + x] |= temp[x * 19 + y];
        }
        int[] pixels = new int[mask.length];
        for (int y = 0; y < 19; y++)
            for (int x = 0; x < 19; x++) {
                if (mask[y * 19 + x]) pixels[y * 19 + x] = 0xffffffff;
                else
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dx = -1; dx <= 1; dx++)
                            if (x + dx >= 0
                                    && x + dx < 19
                                    && y + dy >= 0
                                    && y + dy < 19
                                    && mask[(y + dy) * 19 + x + dx])
                                pixels[y * 19 + x] = 0xff000000;
            }
        return new Sprite(19, 19, 9, 9, pixels, false);
    }

    private static void horizontal(boolean[] pixels) {
        line(pixels, 2, 9, 16, 9);
        line(pixels, 2, 9, 6, 5);
        line(pixels, 2, 9, 6, 13);
        line(pixels, 16, 9, 12, 5);
        line(pixels, 16, 9, 12, 13);
    }

    private static void line(boolean[] pixels, int x, int y, int endX, int endY) {
        int dx = Math.abs(endX - x),
                sx = x < endX ? 1 : -1,
                dy = -Math.abs(endY - y),
                sy = y < endY ? 1 : -1,
                error = dx + dy;
        while (true) {
            pixels[y * 19 + x] = true;
            if (x == endX && y == endY) break;
            int twice = 2 * error;
            if (twice >= dy) {
                error += dy;
                x += sx;
            }
            if (twice <= dx) {
                error += dx;
                y += sy;
            }
        }
    }

    private CursorGlyphs() {}
}
