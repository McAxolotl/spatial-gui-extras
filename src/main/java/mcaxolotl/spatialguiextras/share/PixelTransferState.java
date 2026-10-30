package mcaxolotl.spatialguiextras.share;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

public final class PixelTransferState implements AutoCloseable {
    private final int[] names;
    private final int[] values;
    private final int target;
    private final int buffer;

    public PixelTransferState(boolean pack) {
        names =
                pack
                        ? new int[] {
                            GL11.GL_PACK_ALIGNMENT,
                            GL11.GL_PACK_ROW_LENGTH,
                            GL11.GL_PACK_SKIP_ROWS,
                            GL11.GL_PACK_SKIP_PIXELS,
                            GL12.GL_PACK_IMAGE_HEIGHT,
                            GL12.GL_PACK_SKIP_IMAGES
                        }
                        : new int[] {
                            GL11.GL_UNPACK_ALIGNMENT,
                            GL11.GL_UNPACK_ROW_LENGTH,
                            GL11.GL_UNPACK_SKIP_ROWS,
                            GL11.GL_UNPACK_SKIP_PIXELS,
                            GL12.GL_UNPACK_IMAGE_HEIGHT,
                            GL12.GL_UNPACK_SKIP_IMAGES
                        };
        values = new int[names.length];
        target = pack ? GL21.GL_PIXEL_PACK_BUFFER : GL21.GL_PIXEL_UNPACK_BUFFER;
        buffer =
                GL11.glGetInteger(
                        pack
                                ? GL21.GL_PIXEL_PACK_BUFFER_BINDING
                                : GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        for (int i = 0; i < names.length; i++) {
            values[i] = GL11.glGetInteger(names[i]);
            GL11.glPixelStorei(names[i], i == 0 ? 1 : 0);
        }
        GL15.glBindBuffer(target, 0);
    }

    public void close() {
        GL15.glBindBuffer(target, buffer);
        for (int i = 0; i < names.length; i++) GL11.glPixelStorei(names[i], values[i]);
    }
}
