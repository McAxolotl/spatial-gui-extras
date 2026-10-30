package mcaxolotl.spatialguiextras;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.*;
import java.nio.*;
import java.util.*;

import javax.imageio.ImageIO;

public class OrientationTests {
    public static void main(String[] args) throws Exception {
        Method uv =
                Class.forName("mcaxolotl.spatialguiextras.share.ShareRenderer")
                        .getDeclaredMethod("curvedTextureU", float.class);
        uv.setAccessible(true);
        float arc = (float) Math.toRadians(30), radius = 4;
        for (int i = 0; i <= 24; i++) {
            float t = i / 24f, angle = (t - .5f) * arc;
            float u = (float) uv.invoke(null, t);
            Vector3f got =
                    new Vector3f(0, 0, -1)
                            .rotate(new Quaternionf().rotateY(angle))
                            .mul(radius)
                            .add(0, 0, radius);
            float originalAngle = (u - .5f) * arc;
            Vector3f expected =
                    new Vector3f(
                            radius * (float) Math.sin(originalAngle),
                            0,
                            radius * (1 - (float) Math.cos(originalAngle)));
            if (got.distance(expected) > 1e-5)
                throw new AssertionError("cylinder UV disagrees with upstream at segment " + i);
        }
        Method paint =
                Class.forName("mcaxolotl.spatialguiextras.share.SharedCursor")
                        .getDeclaredMethod(
                                "paintGlyph",
                                ByteBuffer.class,
                                int.class,
                                int.class,
                                int.class,
                                int.class,
                                int.class,
                                boolean.class);
        paint.setAccessible(true);
        var icons =
                ImageIO.read(
                        OrientationTests.class.getResourceAsStream(
                                "/assets/minecraft/textures/gui/icons.png"));
        int w = 64, h = 64, cx = 24, cy = 24;
        ByteBuffer cross = ByteBuffer.allocate(w * h * 4), arrow = ByteBuffer.allocate(w * h * 4);
        paint.invoke(null, cross, w, h, cx, cy, 1, true);
        paint.invoke(null, arrow, w, h, cx, cy, 1, false);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int sx = x - (cx - 7), sy = y - (cy - 7);
                int expectedAlpha =
                        sx >= 0 && sy >= 0 && sx < 15 && sy < 15 ? icons.getRGB(sx, sy) >>> 24 : 0;
                int actual = cross.get(((h - 1 - y) * w + x) * 4 + 3) & 255;
                if (actual != expectedAlpha)
                    throw new AssertionError(
                            "first-person vanilla icon shape differs at " + x + "," + y);
            }
        ByteBuffer outside = ByteBuffer.allocate(64 * 64 * 4);
        paint.invoke(null, outside, 64, 64, 32, 32, 2, true);
        int overlayVisible = 0;
        for (int pixel = 3; pixel < outside.capacity(); pixel += 4)
            if (outside.get(pixel) != 0) overlayVisible++;
        if (overlayVisible == 0)
            throw new AssertionError("empty-handed outside crosshair is transparent");
        var outsidePose = new mcaxolotl.spatialguiextras.share.OverlayPose(0, 1, 2, 0, 0, .1f);
        var overlay =
                new mcaxolotl.spatialguiextras.share.OverlayVisual(outsidePose, outside.array());
        var received = mcaxolotl.spatialguiextras.share.OverlayVisual.decode(overlay.encode());
        if (!Arrays.equals(received.pixels(), outside.array()))
            throw new AssertionError("outside indicator changed during transmission");
        if (Arrays.equals(cross.array(), arrow.array()))
            throw new AssertionError("perspectives use same cursor");
        // Third-person pointer's hot spot and tail are present; no drawing above/left of hot spot.
        if ((arrow.get(((h - 1 - cy) * w + cx) * 4 + 3) & 255) != 255)
            throw new AssertionError("missing pointer hot spot");
        if ((arrow.get(((h - 1 - (cy + 15)) * w + cx + 6) * 4 + 3) & 255) != 255)
            throw new AssertionError("missing arrow tail");
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if ((x < cx || y < cy) && arrow.get(((h - 1 - y) * w + x) * 4 + 3) != 0)
                    throw new AssertionError("arrow hot spot shifted");
        java.util.Set<Integer> glyphHashes = new java.util.HashSet<>();
        for (int shape = 0x36001; shape <= 0x3600a; shape++) {
            var sprite = mcaxolotl.spatialguiextras.cursor.CursorGlyphs.standard(shape);
            if (sprite.hotX() < 0
                    || sprite.hotX() >= sprite.width()
                    || sprite.hotY() < 0
                    || sprite.hotY() >= sprite.height())
                throw new AssertionError("cursor hot spot");
            glyphHashes.add(Arrays.hashCode(sprite.argb()));
            ByteBuffer projected = ByteBuffer.allocate(w * h * 4);
            mcaxolotl.spatialguiextras.share.SharedCursor.paintSprite(
                    projected, w, h, cx, cy, 1, sprite);
            boolean visible = false;
            for (int i = 3; i < projected.capacity(); i += 4)
                if (projected.get(i) != 0) {
                    visible = true;
                    break;
                }
            if (!visible) throw new AssertionError("missing cursor type " + shape);
        }
        if (glyphHashes.size() != 10)
            throw new AssertionError("cursor types collapsed to the same arrow");
        var translucent =
                new mcaxolotl.spatialguiextras.cursor.CursorGlyphs.Sprite(
                        1, 1, 0, 0, new int[] {0x80ffffff}, false);
        ByteBuffer empty = ByteBuffer.allocate(4);
        mcaxolotl.spatialguiextras.share.SharedCursor.paintSprite(
                empty, 1, 1, 0, 0, 1, translucent);
        if ((empty.get(0) & 255) != 255 || (empty.get(3) & 255) != 128)
            throw new AssertionError("native cursor edges double-premultiplied");
        System.out.println(
                "PASS dynamic cursor types=10, hot spots, visibility and straight-alpha edges");
        System.out.println(
                "PASS 25 curved mesh samples match Spatial's native UV orientation; first-person vanilla crosshair pixels; third-person arrow hot spot/tail");
    }
}
