package mcaxolotl.spatialguiextras;

import com.mojang.blaze3d.systems.RenderSystem;

import mcaxolotl.spatialguiextras.share.*;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public final class GpuTests {
    private static int fbo(int texture) {
        int f = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, f);
        GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        return f;
    }

    public static void main(String[] args) throws Exception {
        CoreTests.check(GLFW.glfwInit(), "GLFW");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(32, 32, "Shared GUI texture checks", 0, 0);
        CoreTests.check(window != 0, "context");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        RenderSystem.initRenderThread();
        try {
            RemoteGui tombstone = new RemoteGui(java.util.UUID.randomUUID());
            CoreTests.check(tombstone.session(1), "new shared session");
            tombstone.clear();
            CoreTests.check(
                    !tombstone.session(1) && tombstone.session(2),
                    "late pose/tile resurrected a closed GUI");
            tombstone.texture().close();
            RemoteGuiTexture gui = new RemoteGuiTexture(), child = new RemoteGuiTexture();
            gui.resize(64, 64);
            child.resize(64, 64);
            int outer = fbo(gui.glId()), inner = fbo(child.glId());
            Method async =
                    Class.forName("mcaxolotl.spatialguiextras.share.ShareCapture")
                            .getDeclaredMethod(
                                    "captureAsync",
                                    int.class,
                                    int.class,
                                    int.class,
                                    int.class,
                                    int.class);
            Method dispose =
                    Class.forName("mcaxolotl.spatialguiextras.share.ShareCapture")
                            .getDeclaredMethod("close");
            async.setAccessible(true);
            dispose.setAccessible(true);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, inner);
            GL11.glClearColor(1, 0, 0, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            CoreTests.check(
                    async.invoke(null, inner, 64, 64, 64, 64) == null,
                    "async readback unexpectedly blocked/returned stale frame");
            GL11.glFinish(); // Tests wait; production only polls fences with a zero timeout.
            ByteBuffer completed = (ByteBuffer) async.invoke(null, inner, 64, 64, 64, 64);
            CoreTests.check(
                    completed != null
                            && (completed.get(0) & 255) == 255
                            && (completed.get(3) & 255) == 255,
                    "async readback pixels");
            GL11.glFinish();
            Method newSession = ShareClient.class.getDeclaredMethod("newSession");
            newSession.setAccessible(true);
            newSession.invoke(null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, inner);
            GL11.glClearColor(0, 0, 1, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            CoreTests.check(
                    async.invoke(null, inner, 64, 64, 64, 64) == null,
                    "new session reused old PBO image");
            GL11.glFinish();
            ByteBuffer blue = (ByteBuffer) async.invoke(null, inner, 64, 64, 64, 64);
            CoreTests.check(
                    blue != null && blue.get(0) == 0 && (blue.get(2) & 255) == 255,
                    "new session did not return new source");
            dispose.invoke(null);
            int pbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo);
            GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, 65536, GL15.GL_STATIC_DRAW);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 512);
            byte[] tile = new byte[16 * 16 * 4];
            for (int i = 0; i < tile.length; i++) tile[i] = (byte) (i * 37);
            child.uploadTile(0, 0, 16, 16, tile);
            CoreTests.check(
                    GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING) == pbo
                            && GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH) == 512,
                    "pixel transfer leaked state");
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL15.glDeleteBuffers(pbo);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, gui.glId());
            child.filter(false);
            CoreTests.check(
                    GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) == gui.glId(),
                    "filter switch leaked binding");
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, child.glId());
            CoreTests.check(
                    GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER)
                            == GL11.GL_NEAREST,
                    "pixel filtering not applied");
            child.filter(true);
            CoreTests.check(
                    GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER)
                            == GL11.GL_LINEAR,
                    "smooth filtering not restored");
            var remotesField = ShareClient.class.getDeclaredField("REMOTES");
            remotesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var remotes = (java.util.Map<java.util.UUID, RemoteGui>) remotesField.get(null);
            RemoteGui received = new RemoteGui(java.util.UUID.randomUUID());
            received.texture().resize(16, 16);
            remotes.put(received.sender(), received);
            ShareConfig.INSTANCE.receiveGui = false;
            ShareClient.configurationApplied();
            CoreTests.check(
                    remotes.isEmpty() && received.texture().width() == 0,
                    "disabled reception retained texture resources");
            ShareConfig.INSTANCE.receiveGui = true;
            var generationField = ShareClient.class.getDeclaredField("generation");
            generationField.setAccessible(true);
            long beforeApply = generationField.getLong(null);
            ShareConfig.INSTANCE.panelOpacity = .5f;
            ShareClient.configurationApplied();
            CoreTests.check(
                    generationField.getLong(null) == beforeApply,
                    "receiver-only apply changed the outgoing session");
            ShareConfig.INSTANCE.panelOpacity = 1;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glDeleteFramebuffers(outer);
            GL30.glDeleteFramebuffers(inner);
            gui.close();
            child.close();
            var actor = new RemoteGui(java.util.UUID.randomUUID());
            actor.session(1);
            long started = System.nanoTime();
            var shown = AvatarPose.displayed(120, 60, 40);
            actor.avatar(shown, started);
            actor.clear();
            long closed = actor.closedNanos();
            CoreTests.check(
                    actor.avatar(closed) != null && actor.avatarWeight(closed) > .99f,
                    "GUI close dropped the pose immediately");
            CoreTests.check(
                    actor.avatarWeight(closed + 125_000_000L) > .45f
                            && actor.avatarWeight(closed + 125_000_000L) < .55f,
                    "pose return lacks a smooth midpoint");
            var normal = AvatarPose.displayed(0, 0, 0);
            var middle =
                    normal.blend(
                            actor.avatar(closed + 125_000_000L),
                            actor.avatarWeight(closed + 125_000_000L));
            CoreTests.check(
                    Math.abs(middle.body() - 60) < .1 && Math.abs(middle.head() - 30) < .1,
                    "pose does not blend toward normal orientation");
            CoreTests.check(
                    actor.avatar(closed + 260_000_000L) == null,
                    "finished pose transition still overrides normal rendering");
            actor.session(2);
            actor.avatar(shown, started + 1_000_000_000L);
            actor.avatar(null, started + 1_020_000_000L);
            CoreTests.check(
                    actor.avatar(started + 1_021_000_000L) != null,
                    "pose opt-out snapped rather than returning");
            actor.texture().close();
            var overlayPose = new mcaxolotl.spatialguiextras.share.OverlayPose(0, 1, 2, 0, 0, .1f);
            byte[] iconPixels =
                    new byte[mcaxolotl.spatialguiextras.share.OverlayVisual.PIXEL_BYTES];
            new java.util.Random(14).nextBytes(iconPixels);
            var visual =
                    mcaxolotl.spatialguiextras.share.OverlayVisual.decode(
                            new mcaxolotl.spatialguiextras.share.OverlayVisual(
                                            overlayPose, iconPixels)
                                    .encode());
            actor.overlayVisual(visual, System.nanoTime());
            int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try (var pack = new mcaxolotl.spatialguiextras.share.PixelTransferState(true)) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, actor.overlayTexture().glId());
                var actual = org.lwjgl.BufferUtils.createByteBuffer(iconPixels.length);
                GL11.glGetTexImage(
                        GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, actual);
                for (int pixel = 0; pixel < iconPixels.length; pixel++)
                    CoreTests.check(
                            actual.get(pixel) == iconPixels[pixel],
                            "remote overlay sprite differs from sender pixels");
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldTexture);
            }
            actor.dispose();
            CoreTests.check(GL11.glGetError() == 0, "GL error");
            System.out.println(
                    "PASS shared texture lifecycle, async readback session isolation, dirty UNPACK/PBO restoration");
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
