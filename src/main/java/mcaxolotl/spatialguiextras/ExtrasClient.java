package mcaxolotl.spatialguiextras;

import mcaxolotl.spatialguiextras.config.Configuration;
import mcaxolotl.spatialguiextras.config.InteractionOptions;
import mcaxolotl.spatialguiextras.share.ShareClient;
import mcaxolotl.spatialguiextras.share.ShareConfig;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.util.Properties;

public final class ExtrasClient implements ClientModInitializer {
    private static volatile boolean enabled = true;
    private static Properties current = Configuration.defaults();

    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("spatial_gui"))
            throw new IllegalStateException(
                    "Spatial GUI Extras requires Spatial GUI 1.9.1 on clients");
        try {
            Properties properties = Configuration.load(FabricLoader.getInstance().getConfigDir());
            current = Configuration.normalize(properties);
            enabled = Boolean.parseBoolean(current.getProperty("enabled"));
            ShareConfig.INSTANCE.load(current);
            InteractionOptions.load(current);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot read Spatial GUI Extras configuration", e);
        }
        new ShareClient().onInitializeClient();
    }

    public static Properties settings() {
        Properties copy = new Properties();
        copy.putAll(current);
        return copy;
    }

    public static void save(Properties input) throws IOException {
        Properties values = Configuration.normalize(input);
        Configuration.save(FabricLoader.getInstance().getConfigDir(), values);
        long before = ShareClient.streamGeneration();
        boolean reset = Configuration.restartsStream(current, values);
        if (reset) ShareClient.configurationChanged();
        ShareConfig.INSTANCE.load(values);
        enabled = Boolean.parseBoolean(values.getProperty("enabled"));
        InteractionOptions.load(values);
        current = values;
        ShareClient.configurationApplied();
        if (ShareConfig.INSTANCE.statisticsLogging)
            System.out.println(
                    "[Spatial GUI Extras settings] outgoingReset="
                            + reset
                            + " generation="
                            + before
                            + "->"
                            + ShareClient.streamGeneration());
    }

    public static boolean enabled() {
        return enabled;
    }
}
