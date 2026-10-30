package mcaxolotl.spatialguiextras;

import mcaxolotl.spatialguiextras.config.Configuration;

import java.nio.file.Files;
import java.util.Comparator;

public final class ConfigurationTests {
    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("extras-settings-");
        try {
            var first = Configuration.load(dir);
            var target = dir.resolve("spatial-gui-extras.properties");
            if (!Files.exists(target) || !first.equals(Configuration.defaults()))
                throw new AssertionError("initial settings differ from defaults");
            first.setProperty("shareGui", "false");
            first.setProperty("shareMaxWidth", "1536");
            Configuration.save(dir, first);
            var loaded = Configuration.load(dir);
            if (!loaded.getProperty("shareGui").equals("false")
                    || !loaded.getProperty("shareMaxWidth").equals("1536"))
                throw new AssertionError("saved options lost");
            var unknown = new java.util.Properties();
            unknown.setProperty("unknown-option", "true");
            if (Configuration.normalize(unknown).containsKey("unknown-option"))
                throw new AssertionError("unknown option retained");
            var invalid = Configuration.defaults();
            invalid.setProperty("sharePanelScale", "NaN");
            String good = Files.readString(target);
            try {
                Configuration.save(dir, invalid);
                throw new AssertionError("invalid config saved");
            } catch (IllegalArgumentException expected) {
            }
            if (!Files.readString(target).equals(good))
                throw new AssertionError("invalid write changed config");
            var live = mcaxolotl.spatialguiextras.share.ShareConfig.INSTANCE;
            int width = live.maxWidth;
            invalid.setProperty("shareMaxWidth", "128");
            try {
                live.load(invalid);
                throw new AssertionError("invalid options applied");
            } catch (IllegalArgumentException expected) {
            }
            if (live.maxWidth != width) throw new AssertionError("partial config change");
            Files.delete(target);
            Files.createDirectory(target);
            try {
                Configuration.save(dir, first);
                throw new AssertionError("failed save reported success");
            } catch (java.io.IOException expected) {
            }
            try (var entries = Files.list(dir)) {
                if (entries.anyMatch(p -> p.toString().endsWith(".tmp")))
                    throw new AssertionError("temporary file leaked");
            }
            System.out.println(
                    "PASS current settings schema, defaults, save/reload, bounds, atomic failure cleanup and validation rollback");
        } finally {
            try (var paths = Files.walk(dir)) {
                for (var p : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(p);
            }
        }
    }
}
