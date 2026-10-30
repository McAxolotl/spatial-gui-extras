package mcaxolotl.spatialguiextras.share;

import java.util.Properties;

public final class ShareConfig {
    public static final ShareConfig INSTANCE = new ShareConfig();
    public volatile boolean enabled = true;
    public volatile int maxWidth = 1920;
    public volatile int tileSize = 64;
    public volatile int maxTilesPerFrame = 96;
    public volatile int poseIntervalMs = 20;
    public volatile int bandwidthBudgetKiBps = 6144;
    public volatile float panelScale = 1.0f;
    public volatile double viewDistance = 32.0;
    public volatile boolean receiveGui = true;
    public volatile boolean shareIndicator = true;
    public volatile boolean sharePose = true;
    public volatile boolean receiveAvatar = true;
    public volatile boolean occludePanels = false;
    public volatile boolean linearFiltering = true;
    public volatile float panelOpacity = 1.0f;
    public volatile int captureFps = 50;
    public volatile int keyframeIntervalMs = 2000;
    public volatile boolean statisticsLogging = false;
    public volatile int maxVisiblePanels = 256;

    public static ShareConfig read(Properties properties) {
        ShareConfig result = new ShareConfig();
        result.load(properties);
        return result;
    }

    public void load(Properties properties) {
        boolean next_enabled =
                Boolean.parseBoolean(
                        properties.getProperty("shareGui", Boolean.toString(enabled)).trim());
        int next_maxWidth =
                clamp(
                        Integer.parseInt(
                                properties
                                        .getProperty("shareMaxWidth", Integer.toString(maxWidth))
                                        .trim()),
                        128,
                        2048);
        int next_tileSize =
                clamp(
                        Integer.parseInt(
                                properties
                                        .getProperty("shareTileSize", Integer.toString(tileSize))
                                        .trim()),
                        16,
                        64);
        int next_maxTilesPerFrame =
                clamp(
                        Integer.parseInt(
                                properties
                                        .getProperty(
                                                "shareMaxTilesPerFrame",
                                                Integer.toString(maxTilesPerFrame))
                                        .trim()),
                        1,
                        512);
        int next_poseIntervalMs =
                clamp(
                        Integer.parseInt(
                                properties
                                        .getProperty(
                                                "sharePoseIntervalMs",
                                                Integer.toString(poseIntervalMs))
                                        .trim()),
                        10,
                        1000);
        int next_bandwidthBudgetKiBps =
                clamp(
                        Integer.parseInt(
                                properties
                                        .getProperty(
                                                "shareBandwidthKiBps",
                                                Integer.toString(bandwidthBudgetKiBps))
                                        .trim()),
                        128,
                        1048576);
        float next_panelScale =
                finite(
                        Float.parseFloat(
                                properties
                                        .getProperty("sharePanelScale", Float.toString(panelScale))
                                        .trim()),
                        0.05f,
                        8f);
        double next_viewDistance =
                finite(
                        Double.parseDouble(
                                properties
                                        .getProperty(
                                                "shareViewDistance", Double.toString(viewDistance))
                                        .trim()),
                        4,
                        48);
        boolean next_receiveGui =
                Boolean.parseBoolean(
                        properties.getProperty("receiveGui", Boolean.toString(receiveGui)).trim());
        boolean next_shareIndicator =
                Boolean.parseBoolean(
                        properties
                                .getProperty("shareIndicator", Boolean.toString(shareIndicator))
                                .trim());
        boolean next_sharePose =
                Boolean.parseBoolean(
                        properties.getProperty("sharePose", Boolean.toString(sharePose)).trim());
        boolean next_receiveAvatar =
                Boolean.parseBoolean(
                        properties
                                .getProperty("receiveAvatar", Boolean.toString(receiveAvatar))
                                .trim());
        boolean next_occludePanels =
                Boolean.parseBoolean(
                        properties
                                .getProperty("occludePanels", Boolean.toString(occludePanels))
                                .trim());
        boolean next_linearFiltering =
                Boolean.parseBoolean(
                        properties
                                .getProperty("linearFiltering", Boolean.toString(linearFiltering))
                                .trim());
        float next_panelOpacity =
                finite(
                        Float.parseFloat(
                                properties
                                        .getProperty("panelOpacity", Float.toString(panelOpacity))
                                        .trim()),
                        0f,
                        1f);
        int next_captureFps =
                clamp(
                        Integer.parseInt(
                                properties
                                        .getProperty("captureFps", Integer.toString(captureFps))
                                        .trim()),
                        1,
                        120);
        int next_keyframeIntervalMs =
                clamp(
                        Integer.parseInt(
                                properties
                                        .getProperty(
                                                "keyframeIntervalMs",
                                                Integer.toString(keyframeIntervalMs))
                                        .trim()),
                        500,
                        10000);
        boolean next_statisticsLogging =
                Boolean.parseBoolean(
                        properties
                                .getProperty(
                                        "statisticsLogging", Boolean.toString(statisticsLogging))
                                .trim());
        int next_maxVisiblePanels =
                clamp(
                        Integer.parseInt(
                                properties
                                        .getProperty(
                                                "maxVisiblePanels",
                                                Integer.toString(maxVisiblePanels))
                                        .trim()),
                        1,
                        256);
        enabled = next_enabled;
        maxWidth = next_maxWidth;
        tileSize = next_tileSize;
        maxTilesPerFrame = next_maxTilesPerFrame;
        poseIntervalMs = next_poseIntervalMs;
        bandwidthBudgetKiBps = next_bandwidthBudgetKiBps;
        panelScale = next_panelScale;
        viewDistance = next_viewDistance;
        receiveGui = next_receiveGui;
        shareIndicator = next_shareIndicator;
        sharePose = next_sharePose;
        receiveAvatar = next_receiveAvatar;
        occludePanels = next_occludePanels;
        linearFiltering = next_linearFiltering;
        panelOpacity = next_panelOpacity;
        captureFps = next_captureFps;
        keyframeIntervalMs = next_keyframeIntervalMs;
        statisticsLogging = next_statisticsLogging;
        maxVisiblePanels = next_maxVisiblePanels;
    }

    public void write(Properties properties) {
        properties.setProperty("shareGui", Boolean.toString(enabled));
        properties.setProperty("shareMaxWidth", Integer.toString(maxWidth));
        properties.setProperty("shareTileSize", Integer.toString(tileSize));
        properties.setProperty("shareMaxTilesPerFrame", Integer.toString(maxTilesPerFrame));
        properties.setProperty("sharePoseIntervalMs", Integer.toString(poseIntervalMs));
        properties.setProperty("shareBandwidthKiBps", Integer.toString(bandwidthBudgetKiBps));
        properties.setProperty("sharePanelScale", Float.toString(panelScale));
        properties.setProperty("shareViewDistance", Double.toString(viewDistance));
        properties.setProperty("receiveGui", Boolean.toString(receiveGui));
        properties.setProperty("shareIndicator", Boolean.toString(shareIndicator));
        properties.setProperty("sharePose", Boolean.toString(sharePose));
        properties.setProperty("receiveAvatar", Boolean.toString(receiveAvatar));
        properties.setProperty("occludePanels", Boolean.toString(occludePanels));
        properties.setProperty("linearFiltering", Boolean.toString(linearFiltering));
        properties.setProperty("panelOpacity", Float.toString(panelOpacity));
        properties.setProperty("captureFps", Integer.toString(captureFps));
        properties.setProperty("keyframeIntervalMs", Integer.toString(keyframeIntervalMs));
        properties.setProperty("statisticsLogging", Boolean.toString(statisticsLogging));
        properties.setProperty("maxVisiblePanels", Integer.toString(maxVisiblePanels));
    }

    public long captureIntervalNanos() {
        return 1_000_000_000L / captureFps;
    }

    private static float finite(float value, float min, float max) {
        if (!Float.isFinite(value))
            throw new IllegalArgumentException("Nonfinite shared GUI option");
        return Math.max(min, Math.min(max, value));
    }

    private static double finite(double value, double min, double max) {
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("Nonfinite shared GUI option");
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private ShareConfig() {}
}
