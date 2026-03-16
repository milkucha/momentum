package io.github.milkucha.momentum.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.milkucha.momentum.config.MomentumConfigScreen;

public class MomentumModMenuImpl implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        try {
            // MomentumConfigScreen imports YACL — loading it will throw NoClassDefFoundError
            // if YACL is not installed at runtime. We catch that here so the mod still loads.
            return MomentumConfigScreen::create;
        } catch (NoClassDefFoundError e) {
            // YACL not installed — ModMenu will not show a config button
            return null;
        }
    }
}
