package mcaxolotl.spatialguiextras.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;

import mcaxolotl.spatialguiextras.ExtrasClient;

import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_370;
import net.minecraft.class_437;

import org.slf4j.LoggerFactory;

import java.util.Properties;

public final class ExtrasConfigScreen {
    public static class_437 create(class_437 parent) {
        return build(ExtrasClient.settings()).generateScreen(parent);
    }

    static YetAnotherConfigLib build(Properties draft) {
        Properties defaults = Configuration.defaults();
        var share = toggle("shareGui", draft, defaults);
        var indicator = toggle("shareIndicator", draft, defaults);
        var pose = toggle("sharePose", draft, defaults);
        depend(share, indicator, pose);
        var general =
                ConfigCategory.createBuilder()
                        .name(text("category.general"))
                        .option(toggle("enabled", draft, defaults))
                        .group(
                                OptionGroup.createBuilder()
                                        .name(text("group.sharing"))
                                        .collapsed(false)
                                        .option(share)
                                        .option(indicator)
                                        .option(pose)
                                        .build())
                        .build();

        var follow = toggle("followPanel", draft, defaults);
        var idle = toggle("followWhenIdle", draft, defaults);
        var delay = integer("followDelayMs", 0, 3000, draft, defaults);
        var settling = integer("followSmoothMs", 0, 3000, draft, defaults);
        var vertical = toggle("followVertical", draft, defaults);
        var dragging = toggle("followDragging", draft, defaults);
        depend(follow, idle, delay, settling, vertical, dragging);
        var interaction =
                ConfigCategory.createBuilder()
                        .name(text("category.interaction"))
                        .option(toggle("overflowItems", draft, defaults))
                        .group(
                                OptionGroup.createBuilder()
                                        .name(text("group.follow"))
                                        .collapsed(false)
                                        .option(follow)
                                        .option(idle)
                                        .option(delay)
                                        .option(settling)
                                        .option(vertical)
                                        .option(dragging)
                                        .build())
                        .build();

        var quality =
                ConfigCategory.createBuilder()
                        .name(text("category.quality"))
                        .option(integer("shareMaxWidth", 128, 2048, draft, defaults))
                        .option(integer("shareBandwidthKiBps", 128, 1048576, draft, defaults))
                        .option(integer("captureFps", 1, 120, draft, defaults))
                        .option(distance("shareViewDistance", 4, 48, draft, defaults))
                        .build();

        var receive = toggle("receiveGui", draft, defaults);
        var scale = decimal("sharePanelScale", .05f, 8f, draft, defaults);
        var opacity = decimal("panelOpacity", 0f, 1f, draft, defaults);
        var count = integer("maxVisiblePanels", 1, 256, draft, defaults);
        var avatar = toggle("receiveAvatar", draft, defaults);
        var occlusion = toggle("occludePanels", draft, defaults);
        var filtering = toggle("linearFiltering", draft, defaults);
        depend(receive, scale, opacity, count, avatar, occlusion, filtering);
        var display =
                ConfigCategory.createBuilder()
                        .name(text("category.display"))
                        .option(receive)
                        .option(scale)
                        .option(opacity)
                        .option(count)
                        .option(avatar)
                        .option(occlusion)
                        .option(filtering)
                        .build();
        var advanced =
                ConfigCategory.createBuilder()
                        .name(text("category.advanced"))
                        .group(
                                OptionGroup.createBuilder()
                                        .name(text("group.transfer"))
                                        .collapsed(true)
                                        .option(integer("shareTileSize", 16, 64, draft, defaults))
                                        .option(
                                                integer(
                                                        "shareMaxTilesPerFrame",
                                                        1,
                                                        512,
                                                        draft,
                                                        defaults))
                                        .option(
                                                integer(
                                                        "sharePoseIntervalMs",
                                                        10,
                                                        1000,
                                                        draft,
                                                        defaults))
                                        .option(
                                                integer(
                                                        "keyframeIntervalMs",
                                                        500,
                                                        10000,
                                                        draft,
                                                        defaults))
                                        .option(toggle("statisticsLogging", draft, defaults))
                                        .build())
                        .build();
        return YetAnotherConfigLib.createBuilder()
                .title(text("title"))
                .category(general)
                .category(interaction)
                .category(quality)
                .category(display)
                .category(advanced)
                .save(
                        () -> {
                            try {
                                ExtrasClient.save(draft);
                            } catch (java.io.IOException | IllegalArgumentException e) {
                                LoggerFactory.getLogger("Spatial GUI Extras")
                                        .error("Cannot save configuration", e);
                                var client = class_310.method_1551();
                                class_370.method_1990(
                                        client.method_1566(),
                                        class_370.class_371.field_2220,
                                        text("save_error.title"),
                                        text("save_error.description"));
                            }
                        })
                .build();
    }

    private static void depend(Option<Boolean> parent, Option<?>... children) {
        for (var child : children) child.setAvailable(parent.pendingValue());
        parent.addEventListener(
                (option, event) -> {
                    for (var child : children) child.setAvailable(option.pendingValue());
                });
    }

    private static Option<Boolean> toggle(String key, Properties draft, Properties defaults) {
        return Option.<Boolean>createBuilder()
                .name(optionText(key))
                .description(description(key))
                .binding(
                        Boolean.parseBoolean(defaults.getProperty(key)),
                        () -> Boolean.parseBoolean(draft.getProperty(key)),
                        value -> draft.setProperty(key, value.toString()))
                .controller(option -> BooleanControllerBuilder.create(option).onOffFormatter())
                .build();
    }

    private static Option<Integer> integer(
            String key, int min, int max, Properties draft, Properties defaults) {
        return Option.<Integer>createBuilder()
                .name(optionText(key))
                .description(description(key))
                .binding(
                        Integer.parseInt(defaults.getProperty(key)),
                        () -> Integer.parseInt(draft.getProperty(key)),
                        value -> draft.setProperty(key, value.toString()))
                .controller(option -> IntegerFieldControllerBuilder.create(option).range(min, max))
                .build();
    }

    private static Option<Float> decimal(
            String key, float min, float max, Properties draft, Properties defaults) {
        return Option.<Float>createBuilder()
                .name(optionText(key))
                .description(description(key))
                .binding(
                        Float.parseFloat(defaults.getProperty(key)),
                        () -> Float.parseFloat(draft.getProperty(key)),
                        value -> draft.setProperty(key, value.toString()))
                .controller(option -> FloatFieldControllerBuilder.create(option).range(min, max))
                .build();
    }

    private static Option<Double> distance(
            String key, double min, double max, Properties draft, Properties defaults) {
        return Option.<Double>createBuilder()
                .name(optionText(key))
                .description(description(key))
                .binding(
                        Double.parseDouble(defaults.getProperty(key)),
                        () -> Double.parseDouble(draft.getProperty(key)),
                        value -> draft.setProperty(key, value.toString()))
                .controller(option -> DoubleFieldControllerBuilder.create(option).range(min, max))
                .build();
    }

    private static class_2561 text(String key) {
        return class_2561.method_43471("spatial_gui_extras.config." + key);
    }

    private static class_2561 optionText(String key) {
        return text("option." + key);
    }

    private static OptionDescription description(String key) {
        return OptionDescription.of(text("option." + key + ".description"));
    }

    private ExtrasConfigScreen() {}
}
