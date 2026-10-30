package mcaxolotl.spatialguiextras.cursor;

import org.lwjgl.glfw.GLFW;

public final class CursorState {
    public static int shape() {
        return mcaxolotl.spatialguiextras.IntegrationHooks.cursorShape();
    }

    public static boolean visible(long window) {
        return GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_NORMAL;
    }

    public static CursorGlyphs.Sprite image(long window) {
        if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE) {
            CursorGlyphs.Sprite nativeCursor = NativeCursor.capture();
            if (nativeCursor != null) return nativeCursor;
        }
        return CursorGlyphs.standard(shape());
    }

    private CursorState() {}
}
