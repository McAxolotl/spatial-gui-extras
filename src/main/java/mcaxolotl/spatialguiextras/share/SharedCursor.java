package mcaxolotl.spatialguiextras.share;

import mcaxolotl.spatialguiextras.cursor.CursorGlyphs;
import mcaxolotl.spatialguiextras.cursor.CursorState;

import net.minecraft.class_310;

import org.lwjgl.glfw.GLFW;
import org.tastytrash.spatialGUI.client.SpatialGUIClient;
import org.tastytrash.spatialGUI.editor.EditorMode;
import org.tastytrash.spatialGUI.render.SpatialGUIRenderer;
import org.tastytrash.spatialGUI.util.MouseHandlerUtil;
import org.tastytrash.spatialGUI.util.RenderUtil;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

public final class SharedCursor {
    private static BufferedImage icons;
    private static boolean loaded;

    static void paint(ByteBuffer pixels, int width, int height) {
        if (!ShareConfig.INSTANCE.shareIndicator) return;
        boolean crosshair =
                crosshair(SpatialGUIRenderer.isCrosshairModeActive(), EditorMode.mouseFree());
        var inventory = SpatialGUIClient.renderer().getInventoryRenderer();
        var quad = inventory.getQuadBasis();
        if (quad == null) return;
        var cylinder = inventory.getCylinderBasis();
        double x = MouseHandlerUtil.getSourceX(), y = MouseHandlerUtil.getSourceY();
        if (!(cylinder == null
                ? RenderUtil.isMouseOverQuad(x, y, quad)
                : RenderUtil.isMouseOverCylinder(x, y, cylinder))) return;
        var point =
                cylinder == null
                        ? RenderUtil.getQuadScreenPosition(x, y, quad)
                        : RenderUtil.getCylinderScreenPosition(x, y, cylinder);
        if (point == null) return;
        var window = class_310.method_1551().method_22683();
        int cx = (int) Math.round(point.x * width / window.method_4489()),
                cy = (int) Math.round(point.y * height / window.method_4506());
        int scale = Math.max(1, width / Math.max(1, window.method_4486()));
        if (crosshair) paintGlyph(pixels, width, height, cx, cy, scale, true);
        else if (CursorState.visible(window.method_4490())) {
            var image = CursorState.image(window.method_4490());
            paintSprite(
                    pixels,
                    width,
                    height,
                    cx,
                    cy,
                    image.nativeImage() ? width / (float) window.method_4489() : scale,
                    image);
        }
    }

    static boolean overflowVisible() {
        var window = class_310.method_1551().method_22683();
        return ShareConfig.INSTANCE.shareIndicator
                && (crosshair(SpatialGUIRenderer.isCrosshairModeActive(), EditorMode.mouseFree())
                        || CursorState.visible(window.method_4490()));
    }

    static void paintOverflow(byte[] pixels) {
        if (!overflowVisible()) return;
        var window = class_310.method_1551().method_22683();
        var buffer = ByteBuffer.wrap(pixels);
        if (crosshair(SpatialGUIRenderer.isCrosshairModeActive(), EditorMode.mouseFree()))
            paintGlyph(buffer, 64, 64, 32, 32, 2, true);
        else {
            var cursor = CursorState.image(window.method_4490());
            float scale =
                    cursor.nativeImage() ? 2f * window.method_4486() / window.method_4489() : 2;
            float maxScale =
                    Math.min(
                            31f / Math.max(1, cursor.width() - cursor.hotX()),
                            31f / Math.max(1, cursor.height() - cursor.hotY()));
            paintSprite(buffer, 64, 64, 32, 32, Math.min(scale, maxScale), cursor);
        }
    }

    static boolean crosshair(boolean upstreamCrosshairActive, boolean editorMouseFree) {
        return upstreamCrosshairActive && !editorMouseFree;
    }

    private static void paintGlyph(
            ByteBuffer pixels,
            int width,
            int height,
            int cx,
            int cy,
            int scale,
            boolean firstPerson) {
        if (!firstPerson) {
            paintSprite(
                    pixels,
                    width,
                    height,
                    cx,
                    cy,
                    scale,
                    CursorGlyphs.standard(GLFW.GLFW_ARROW_CURSOR));
            return;
        }
        if (!loaded) {
            loaded = true;
            try (var input =
                    class_310.class.getResourceAsStream(
                            "/assets/minecraft/textures/gui/icons.png")) {
                if (input != null) icons = ImageIO.read(input);
            } catch (IOException e) {
                System.err.println("[Spatial GUI Extras cursor] Cannot load crosshair: " + e);
            }
        }
        if (icons == null) return;
        for (int y = 0; y < 15; y++)
            for (int x = 0; x < 15; x++)
                for (int sy = 0; sy < scale; sy++)
                    for (int sx = 0; sx < scale; sx++)
                        put(
                                pixels,
                                width,
                                height,
                                cx + (x - 7) * scale + sx,
                                cy + (y - 7) * scale + sy,
                                icons.getRGB(x, y));
    }

    public static void paintSprite(
            ByteBuffer pixels,
            int width,
            int height,
            float cx,
            float cy,
            float scale,
            CursorGlyphs.Sprite image) {
        if (!(scale > 0) || !Float.isFinite(scale)) return;
        float left = cx - image.hotX() * scale, top = cy - image.hotY() * scale;
        for (int y = Math.max(0, (int) Math.floor(top));
                y < Math.min(height, (int) Math.ceil(top + image.height() * scale));
                y++)
            for (int x = Math.max(0, (int) Math.floor(left));
                    x < Math.min(width, (int) Math.ceil(left + image.width() * scale));
                    x++) {
                int sx = (int) ((x - left) / scale), sy = (int) ((y - top) / scale);
                if (sx >= 0 && sy >= 0 && sx < image.width() && sy < image.height())
                    put(pixels, width, height, x, y, image.argb()[sy * image.width() + sx]);
            }
    }

    private static void put(ByteBuffer pixels, int width, int height, int x, int y, int argb) {
        int alpha = argb >>> 24;
        if (alpha == 0 || x < 0 || y < 0 || x >= width || y >= height) return;
        int offset = ((height - 1 - y) * width + x) * 4, oldAlpha = pixels.get(offset + 3) & 255;
        int weight = alpha * 255 + oldAlpha * (255 - alpha);
        for (int i = 0; i < 3; i++) {
            int source = argb >>> (16 - i * 8) & 255, old = pixels.get(offset + i) & 255;
            pixels.put(
                    offset + i,
                    (byte)
                            ((source * alpha * 255 + old * oldAlpha * (255 - alpha) + weight / 2)
                                    / weight));
        }
        pixels.put(offset + 3, (byte) ((weight + 127) / 255));
    }

    private SharedCursor() {}
}
