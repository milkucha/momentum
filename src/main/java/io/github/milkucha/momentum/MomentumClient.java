package io.github.milkucha.momentum;

import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.hud.MomentumHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class MomentumClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MomentumConfig.get();

        HudRenderCallback.EVENT.register((drawContext, tickDelta) ->
                MomentumHud.render(drawContext, tickDelta));

        KeyBinding reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.momentum.reload_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.momentum"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (reloadKey.wasPressed() && client.player != null) {
                MomentumConfig.reload();
                client.player.sendMessage(Text.literal("[Momentum] Config reloaded"), true);
            }
        });
    }
}
