package mcaxolotl.spatialguiextras.share;

import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.class_6367;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

final class ShareCapture {
    private static class_6367 target;
    private static ByteBuffer buffer;

    private static final class Slot {
        int id, bytes;
        long fence;
    }

    private static final Slot[] slots = {new Slot(), new Slot(), new Slot()};
    private static final java.util.ArrayDeque<Slot> pending = new java.util.ArrayDeque<>();
    private static int asyncWidth, asyncHeight;

    static ByteBuffer captureAsync(
            int sourceFbo, int sourceWidth, int sourceHeight, int width, int height) {
        if (width != asyncWidth || height != asyncHeight) {
            discardAsync();
            asyncWidth = width;
            asyncHeight = height;
        }
        ByteBuffer completed = null;
        Slot oldest = pending.peek();
        if (oldest != null) {
            int status = GL32.glClientWaitSync(oldest.fence, 0, 0);
            if (status == GL32.GL_WAIT_FAILED)
                throw new IllegalStateException("[Spatial GUI Extras share] GPU fence failed");
            if (status == GL32.GL_ALREADY_SIGNALED || status == GL32.GL_CONDITION_SATISFIED) {
                int bytes = width * height * 4;
                if (buffer == null || buffer.capacity() < bytes) {
                    if (buffer != null) MemoryUtil.memFree(buffer);
                    buffer = MemoryUtil.memAlloc(bytes);
                }
                buffer.clear();
                try (PixelTransferState state = new PixelTransferState(true)) {
                    GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, oldest.id);
                    ByteBuffer mapped =
                            GL30.glMapBufferRange(
                                    GL21.GL_PIXEL_PACK_BUFFER, 0, bytes, GL30.GL_MAP_READ_BIT);
                    if (mapped == null)
                        throw new IllegalStateException(
                                "[Spatial GUI Extras share] GPU readback mapping failed");
                    try {
                        MemoryUtil.memCopy(
                                MemoryUtil.memAddress(mapped),
                                MemoryUtil.memAddress(buffer),
                                bytes);
                    } finally {
                        GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
                    }
                }
                GL32.glDeleteSync(oldest.fence);
                oldest.fence = 0;
                pending.remove();
                buffer.position(0);
                buffer.limit(bytes);
                completed = buffer;
            }
        }
        for (Slot slot : slots)
            if (slot.fence == 0) {
                try (PixelTransferState state = new PixelTransferState(true)) {
                    if (slot.id == 0) slot.id = GL15.glGenBuffers();
                    GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, slot.id);
                    if (slot.bytes != width * height * 4) {
                        slot.bytes = width * height * 4;
                        GL15.glBufferData(
                                GL21.GL_PIXEL_PACK_BUFFER, slot.bytes, GL15.GL_STREAM_READ);
                    }
                }
                transfer(sourceFbo, sourceWidth, sourceHeight, width, height, slot.id);
                slot.fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
                pending.add(slot);
                break;
            }
        return completed;
    }

    static void invalidate() {
        discardAsync();
        asyncWidth = asyncHeight = 0;
    }

    private static void discardAsync() {
        pending.clear();
        for (Slot slot : slots) {
            if (slot.fence != 0) GL32.glDeleteSync(slot.fence);
            if (slot.id != 0) GL15.glDeleteBuffers(slot.id);
            slot.fence = 0;
            slot.id = 0;
            slot.bytes = 0;
        }
    }

    private static void transfer(
            int sourceFbo, int sourceWidth, int sourceHeight, int width, int height, int pbo) {
        if (sourceFbo <= 0 || sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0)
            return;
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try (PixelTransferState state = new PixelTransferState(true)) {
            boolean direct = width == sourceWidth && height == sourceHeight;
            if (!direct) {
                try (PixelTransferState allocation = new PixelTransferState(false)) {
                    if (target == null) {
                        target = new class_6367(width, height, false, false);
                    } else if (target.field_1480 != width || target.field_1477 != height) {
                        target.method_1234(width, height, false);
                    }
                }
            }
            GlStateManager._disableScissorTest();
            if (direct) {
                // Capturing at native size: read straight out of the Spatial target.
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
            } else {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFbo);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.field_1476);
                GL30.glBlitFramebuffer(
                        0,
                        0,
                        sourceWidth,
                        sourceHeight,
                        0,
                        0,
                        width,
                        height,
                        GL11.GL_COLOR_BUFFER_BIT,
                        GL11.GL_LINEAR);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.field_1476);
            }
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GlStateManager._bindTexture(previousTexture);
            if (scissor) GlStateManager._enableScissorTest();
            else GlStateManager._disableScissorTest();
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    static void close() {
        discardAsync();
        asyncWidth = asyncHeight = 0;
        if (target != null) {
            target.method_1238();
            target = null;
        }
        if (buffer != null) MemoryUtil.memFree(buffer);
        buffer = null;
    }

    private ShareCapture() {}
}
