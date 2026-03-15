package io.github.milkucha.momentum;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.hud.MomentumHud;
import io.github.milkucha.momentum.sound.BrakingSkidSound;
import io.github.milkucha.momentum.sound.JDriftSkidSound;
import io.github.milkucha.momentum.sound.KDriftSkidSound;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class MomentumClient implements ClientModInitializer {

    private boolean prevBrakeHeld    = false;
    private boolean prevJDriftActive = false;
    private boolean prevKDriftActive = false;

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

        // Update brake and drift key states from GLFW before entity ticks so both
        // client and server entities read the correct values this frame.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player != null && client.player.getVehicle() instanceof AutomobileEntity) {
                long win = client.getWindow().getHandle();
                MomentumBrakeState.brakeHeld =
                    GLFW.glfwGetKey(win, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
                MomentumDriftState.driftKeyHeld =
                    GLFW.glfwGetKey(win, GLFW.GLFW_KEY_J) == GLFW.GLFW_PRESS;
                MomentumDriftState.kDriftKeyHeld =
                    GLFW.glfwGetKey(win, GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;
            } else {
                MomentumBrakeState.brakeHeld = false;
                MomentumDriftState.driftKeyHeld = false;
                MomentumDriftState.kDriftKeyHeld = false;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (reloadKey.wasPressed() && client.player != null) {
                MomentumConfig.reload();
                client.player.sendMessage(Text.literal("[Momentum] Config reloaded"), true);
            }

            if (client.player != null && client.player.getVehicle() instanceof AutomobileEntity auto) {
                MomentumConfig cfg = MomentumConfig.get();
                if (cfg.lockCamera) {
                    client.player.setYaw(auto.getYaw());
                    client.player.setPitch(cfg.lockCameraPitch);
                }

                SteeringDebugAccessor accessor = (SteeringDebugAccessor) auto;

                boolean brakeHeld = MomentumBrakeState.brakeHeld;
                if (brakeHeld && !prevBrakeHeld) {
                    client.getSoundManager().play(new BrakingSkidSound(auto));
                }
                prevBrakeHeld = brakeHeld;

                boolean jDriftActive = accessor.momentum$isDrifting();
                if (jDriftActive && !prevJDriftActive) {
                    client.getSoundManager().play(new JDriftSkidSound(auto));
                }
                prevJDriftActive = jDriftActive;

                boolean kDriftActive = accessor.momentum$isKDriftActive();
                if (kDriftActive && !prevKDriftActive) {
                    client.getSoundManager().play(new KDriftSkidSound(auto));
                }
                prevKDriftActive = kDriftActive;
            } else {
                prevBrakeHeld    = false;
                prevJDriftActive = false;
                prevKDriftActive = false;
            }
        });
    }
}
