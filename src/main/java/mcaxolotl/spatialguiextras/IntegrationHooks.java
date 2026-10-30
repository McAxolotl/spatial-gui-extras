package mcaxolotl.spatialguiextras;

import net.fabricmc.loader.api.FabricLoader;

import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class IntegrationHooks {
    private static final class Providers {
        static final List<BooleanSupplier> VIEW =
                FabricLoader.getInstance()
                        .getEntrypoints("spatial_gui_extras:world_view", BooleanSupplier.class);
        static final List<IntSupplier> CURSOR =
                FabricLoader.getInstance()
                        .getEntrypoints("spatial_gui_extras:cursor_shape", IntSupplier.class);
    }

    public static boolean worldView() {
        for (var filter : Providers.VIEW) if (!filter.getAsBoolean()) return false;
        return true;
    }

    public static int cursorShape() {
        for (var source : Providers.CURSOR) {
            int shape = source.getAsInt();
            if (shape >= GLFW.GLFW_ARROW_CURSOR
                    && shape <= GLFW.GLFW_NOT_ALLOWED_CURSOR
                    && shape != GLFW.GLFW_ARROW_CURSOR) return shape;
        }
        return GLFW.GLFW_ARROW_CURSOR;
    }

    private IntegrationHooks() {}
}
