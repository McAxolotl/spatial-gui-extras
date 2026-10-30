package mcaxolotl.spatialguiextras.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.minecraft.class_437;

public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<class_437> getModConfigScreenFactory() {
        return ExtrasConfigScreen::create;
    }
}
