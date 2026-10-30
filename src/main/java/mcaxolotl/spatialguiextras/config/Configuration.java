package mcaxolotl.spatialguiextras.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;

public final class Configuration {
    private static final Map<String, String> DEFAULTS =
            Map.ofEntries(
                    Map.entry("enabled", "true"),
                    Map.entry("overflowItems", "true"),
                    Map.entry("followPanel", "false"),
                    Map.entry("followWhenIdle", "false"),
                    Map.entry("followVertical", "false"),
                    Map.entry("followDragging", "false"),
                    Map.entry("followDelayMs", "600"),
                    Map.entry("followSmoothMs", "700"),
                    Map.entry("shareGui", "true"),
                    Map.entry("shareMaxWidth", "1920"),
                    Map.entry("shareTileSize", "64"),
                    Map.entry("shareMaxTilesPerFrame", "96"),
                    Map.entry("sharePoseIntervalMs", "20"),
                    Map.entry("shareBandwidthKiBps", "6144"),
                    Map.entry("sharePanelScale", "1.0"),
                    Map.entry("shareViewDistance", "32.0"),
                    Map.entry("receiveGui", "true"),
                    Map.entry("shareIndicator", "true"),
                    Map.entry("sharePose", "true"),
                    Map.entry("receiveAvatar", "true"),
                    Map.entry("occludePanels", "false"),
                    Map.entry("linearFiltering", "true"),
                    Map.entry("panelOpacity", "1.0"),
                    Map.entry("captureFps", "50"),
                    Map.entry("keyframeIntervalMs", "2000"),
                    Map.entry("statisticsLogging", "false"),
                    Map.entry("maxVisiblePanels", "256"));

    public static Properties load(Path directory) throws IOException {
        Path target = directory.resolve("spatial-gui-extras.properties");
        if (!Files.exists(target)) {
            Properties values = defaults();
            save(directory, values);
            return values;
        }
        Properties values = new Properties();
        try (var input = Files.newBufferedReader(target)) {
            values.load(input);
        }
        return normalize(values);
    }

    public static Properties defaults() {
        Properties values = new Properties();
        DEFAULTS.forEach(values::setProperty);
        return values;
    }

    public static Properties normalize(Properties input) {
        Properties values = defaults();
        for (String key : DEFAULTS.keySet())
            if (input.containsKey(key)) values.setProperty(key, input.getProperty(key));
        mcaxolotl.spatialguiextras.share.ShareConfig.read(values).write(values);
        InteractionOptions.validate(values);
        values.setProperty(
                "enabled", Boolean.toString(Boolean.parseBoolean(values.getProperty("enabled"))));
        return values;
    }

    public static boolean restartsStream(Properties before, Properties after) {
        for (String key :
                new String[] {
                    "enabled", "shareGui", "shareMaxWidth", "shareTileSize", "shareIndicator"
                })
            if (!java.util.Objects.equals(before.getProperty(key), after.getProperty(key)))
                return true;
        return false;
    }

    public static void save(Path directory, Properties input) throws IOException {
        Properties values = normalize(input);
        Files.createDirectories(directory);
        Path staged = Files.createTempFile(directory, ".spatial-gui-extras-", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(staged)) {
                values.store(writer, null);
            }
            Path target = directory.resolve("spatial-gui-extras.properties");
            try {
                Files.move(
                        staged,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private Configuration() {}
}
