package mcaxolotl.spatialguiextras.config;

import java.util.Properties;

public final class InteractionOptions {
    public static boolean overflowItems = true,
            followPanel = false,
            followVertical = false,
            followDragging = false,
            followWhenIdle = false;
    public static int followDelayMs = 600, followSmoothMs = 700;

    public static void validate(Properties p) {
        for (String k :
                new String[] {
                    "overflowItems",
                    "followPanel",
                    "followVertical",
                    "followDragging",
                    "followWhenIdle"
                }) p.setProperty(k, Boolean.toString(Boolean.parseBoolean(p.getProperty(k))));
        for (String k : new String[] {"followDelayMs", "followSmoothMs"})
            p.setProperty(
                    k,
                    Integer.toString(
                            Math.max(0, Math.min(3000, Integer.parseInt(p.getProperty(k))))));
    }

    public static void load(Properties p) {
        overflowItems = Boolean.parseBoolean(p.getProperty("overflowItems"));
        followPanel = Boolean.parseBoolean(p.getProperty("followPanel"));
        followWhenIdle = Boolean.parseBoolean(p.getProperty("followWhenIdle"));
        followVertical = Boolean.parseBoolean(p.getProperty("followVertical"));
        followDragging = Boolean.parseBoolean(p.getProperty("followDragging"));
        followDelayMs = Integer.parseInt(p.getProperty("followDelayMs"));
        followSmoothMs = Integer.parseInt(p.getProperty("followSmoothMs"));
    }

    private InteractionOptions() {}
}
