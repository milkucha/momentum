package io.github.milkucha.momentum;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.hud.MomentumHud;
import io.github.milkucha.momentum.sound.BrakingSkidSound;
import io.github.milkucha.momentum.sound.JDriftSkidSound;
import io.github.milkucha.momentum.sound.KDriftSkidSound;
import io.github.milkucha.momentum.sound.MDriftSkidSound;
import io.github.milkucha.momentum.sound.NDriftSkidSound;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class MomentumClient implements ClientModInitializer {

    private boolean prevBrakeHeld      = false;
    private boolean prevJDriftActive   = false;
    private boolean prevKDriftActive   = false;
    private boolean prevNDriftKeyHeld  = false;
    private boolean prevMDriftActive   = false;
    private float   cameraDriftYawOffset = 0f;

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
                MomentumDriftState.nDriftKeyHeld =
                    GLFW.glfwGetKey(win, GLFW.GLFW_KEY_N) == GLFW.GLFW_PRESS;
                MomentumDriftState.mKeyHeld =
                    GLFW.glfwGetKey(win, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            } else {
                MomentumBrakeState.brakeHeld = false;
                MomentumDriftState.driftKeyHeld = false;
                MomentumDriftState.kDriftKeyHeld = false;
                MomentumDriftState.nDriftKeyHeld = false;
                MomentumDriftState.mKeyHeld      = false;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (reloadKey.wasPressed() && client.player != null) {
                MomentumConfig.reload();
                client.player.sendMessage(Text.literal("[Momentum] Config reloaded"), true);
            }

            if (client.player != null && client.player.getVehicle() instanceof AutomobileEntity auto) {
                MomentumConfig cfg = MomentumConfig.get();
                SteeringDebugAccessor accessor = (SteeringDebugAccessor) auto;

                // Lerp camera yaw offset toward (kDriftOffset + mDriftOffset) * scale.
                // Use a slow lerp while either drift is active (dramatic swing in) and a fast
                // lerp on exit (snappy return to car heading).
                float combinedDriftOffset = accessor.momentum$getKDriftOffset()
                                          + accessor.momentum$getMDriftOffset();
                float targetCameraOffset = combinedDriftOffset * cfg.camera.driftScale;
                boolean kActive = accessor.momentum$isKDriftActive() || accessor.momentum$isMDriftActive();
                float cameraLerp = kActive ? cfg.camera.driftLerpIn : cfg.camera.driftLerpOut;
                cameraDriftYawOffset += (targetCameraOffset - cameraDriftYawOffset) * cameraLerp;

                if (cfg.camera.lock) {
                    client.player.setYaw(auto.getYaw() + cameraDriftYawOffset);
                    client.player.setPitch(cfg.camera.pitch);
                }

                boolean brakeHeld = MomentumBrakeState.brakeHeld;
                if (brakeHeld && !prevBrakeHeld) {
                    client.getSoundManager().play(new BrakingSkidSound(auto));
                }
                prevBrakeHeld = brakeHeld;

                // Brake zoom: lerp FOV offset toward brakeZoomFov whenever any key is braking.
                // N always brakes while held; M brakes while held and not in a slip-angle drift.
                boolean anyBraking = brakeHeld
                    || MomentumDriftState.nDriftKeyHeld
                    || (MomentumDriftState.mKeyHeld && !accessor.momentum$isMDriftActive());
                float brakeZoomTarget = anyBraking ? cfg.camera.brakeZoomFov : 0f;
                MomentumBrakeState.brakeZoomOffset +=
                    (brakeZoomTarget - MomentumBrakeState.brakeZoomOffset) * cfg.camera.brakeZoomLerp;

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

                boolean nDriftKeyHeld = MomentumDriftState.nDriftKeyHeld;
                if (nDriftKeyHeld && !prevNDriftKeyHeld) {
                    client.getSoundManager().play(new NDriftSkidSound(auto));
                }
                prevNDriftKeyHeld = nDriftKeyHeld;

                boolean mKeyHeld = MomentumDriftState.mKeyHeld;
                if (mKeyHeld && !prevMDriftActive) {
                    client.getSoundManager().play(new MDriftSkidSound(auto));
                }
                prevMDriftActive = mKeyHeld;
            } else {
                prevBrakeHeld        = false;
                prevJDriftActive     = false;
                prevKDriftActive     = false;
                prevNDriftKeyHeld    = false;
                prevMDriftActive     = false;
                cameraDriftYawOffset        = 0f;
                MomentumBrakeState.brakeZoomOffset = 0f;
            }
        });
    }
}
