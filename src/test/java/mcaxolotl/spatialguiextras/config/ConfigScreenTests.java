package mcaxolotl.spatialguiextras.config;

import dev.isxander.yacl3.api.Option;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;

public final class ConfigScreenTests {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var draft = Configuration.defaults();
        var model = ExtrasConfigScreen.build(draft);
        var options = new ArrayList<Option<?>>();
        for (var category : model.categories())
            for (var group : category.groups()) options.addAll(group.options());
        if (model.categories().size() != 5 || options.size() != 27)
            throw new AssertionError("missing YACL controls");
        Option<Boolean> share =
                (Option<Boolean>)
                        options.stream()
                                .filter(option -> option.name().getString().endsWith(".shareGui"))
                                .findFirst()
                                .orElseThrow();
        Option<?> indicator =
                options.stream()
                        .filter(option -> option.name().getString().endsWith(".shareIndicator"))
                        .findFirst()
                        .orElseThrow();
        Option<Boolean> follow =
                (Option<Boolean>)
                        options.stream()
                                .filter(
                                        option ->
                                                option.name().getString().endsWith(".followPanel"))
                                .findFirst()
                                .orElseThrow();
        Option<?> idle =
                options.stream()
                        .filter(option -> option.name().getString().endsWith(".followWhenIdle"))
                        .findFirst()
                        .orElseThrow();
        if (idle.available())
            throw new AssertionError("disabled following left dependent controls active");
        follow.requestSet(true);
        if (!idle.available())
            throw new AssertionError("pending follow enable did not activate dependent controls");
        follow.forgetPendingValue();
        if (idle.available())
            throw new AssertionError("cancel did not restore control availability");
        share.requestSet(false);
        if (!draft.getProperty("shareGui").equals("true") || !share.changed())
            throw new AssertionError("pending edit applied early");
        if (indicator.available())
            throw new AssertionError("pending share disable left indicator control active");
        share.forgetPendingValue();
        if (!indicator.available())
            throw new AssertionError("cancel did not restore share controls");
        if (!share.pendingValue() || share.changed())
            throw new AssertionError("cancel lost original value");
        share.requestSet(false);
        share.applyValue();
        if (!draft.getProperty("shareGui").equals("false"))
            throw new AssertionError("apply did not bind settings");
        share.requestSetDefault();
        if (!share.pendingValue() || !share.isPendingValueDefault())
            throw new AssertionError("reset does not use factory defaults");
        var expanded = Configuration.defaults();
        var expandedModel = ExtrasConfigScreen.build(expanded);
        for (var category : expandedModel.categories())
            for (var group : category.groups())
                for (Option<?> raw : group.options()) {
                    Option<Object> option = (Option<Object>) raw;
                    String key =
                            option.name()
                                    .getString()
                                    .substring("spatial_gui_extras.config.option.".length());
                    Object old = option.pendingValue();
                    Object changed =
                            old instanceof Boolean value
                                    ? !value
                                    : old instanceof Integer value
                                            ? value - 1
                                            : old instanceof Float value
                                                    ? value - .1f
                                                    : ((Double) old) - 1;
                    String before = expanded.getProperty(key);
                    option.requestSet(changed);
                    if (!before.equals(expanded.getProperty(key)))
                        throw new AssertionError("new control bypassed pending state: " + key);
                    option.applyValue();
                    if (before.equals(expanded.getProperty(key)))
                        throw new AssertionError("new control not bound: " + key);
                    option.requestSetDefault();
                    option.applyValue();
                    if (!Configuration.defaults()
                            .getProperty(key)
                            .equals(expanded.getProperty(key)))
                        throw new AssertionError("new control reset failed: " + key);
                }
        var dir = Files.createTempDirectory("yacl-settings-");
        try {
            Configuration.save(dir, draft);
            if (!Configuration.load(dir).getProperty("shareGui").equals("false"))
                throw new AssertionError("applied settings not persistent");
        } finally {
            try (var files = Files.walk(dir)) {
                for (var path : files.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
        System.out.println(
                "PASS actual YACL model: five categories, 27 controls, pending/apply/cancel/reset and persistence");
    }
}
