package io.github.milkucha.momentum;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.hud.BarHud;
import io.github.milkucha.momentum.network.KeyStatePacket;
import io.github.milkucha.momentum.sound.BrakingSkidSound;
import io.github.milkucha.momentum.sound.ArcadeDriftSkidSound;
import io.github.milkucha.momentum.sound.ResponsiveDriftSkidSound;
import io.github.milkucha.momentum.sound.VanillaDriftSkidSound;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class MomentumClient implements ClientModInitializer {

    // ── Registered KeyBindings ────────────────────────────────────────────────
    // NOTE: Brake and Drift keys are NOT registered as Minecraft KeyBindings.
    // They are stored in MomentumConfig as GLFW key codes (brakeKey / driftKey).
    // This avoids Fabric's key conflict suppression in multiplayer — registering
    // them on Space/S would interfere with vanilla Jump and Move Backwards.

    // ── Tick state ────────────────────────────────────────────────────────────

    private boolean prevBrakeHeld        = false;
    private boolean prevVanillaDriftActive = false;
    private boolean prevArcadeDriftActive  = false;
    private boolean prevResponsiveDriftActive = false;
    private float   kCameraDriftYawOffset  = 0f;
    private float   mCameraDriftYawOffset  = 0f;
    private float   brakeZoomVelocity      = 0f;
    private float   prevHSpeed             = 0f;

    // Last key-state snapshot sent to the server; used to send only on changes.
    private boolean pktBrake = false;
    private boolean pktDrift = false;

    @Override
    public void onInitializeClient() {
        MomentumConfig.get();

        // ── KeyBinding registrations ──────────────────────────────────────────
        // These appear in Minecraft's vanilla Controls screen under "Momentum".

        KeyBinding reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.momentum.reload_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "key.categories.momentum"
        ));

        KeyBinding openOptionsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.momentum.open_options",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PERIOD,
                "key.categories.momentum"
        ));

        // ── HUD rendering ─────────────────────────────────────────────────────

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MomentumConfig cfg = MomentumConfig.get();
            if (!cfg.enabled) return;
            if (!cfg.barHud.enabled) return;
            BarHud.render(drawContext, tickDelta);
            BarHud.renderDebug(drawContext, tickDelta);
        });

        // ── Key polling ───────────────────────────────────────────────────────
        // Poll the KeyBinding held state before entity ticks so both client and
        // server entities read the correct values this frame.

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player != null && client.player.getVehicle() instanceof AutomobileEntity) {
                long win = client.getWindow().getHandle();
                MomentumConfig mc = MomentumConfig.get();
                MomentumBrakeState.brakeHeld    = isKeyHeld(mc.brakeKey, win);
                MomentumDriftState.driftKeyHeld = isKeyHeld(mc.driftKey, win);
            } else {
                MomentumBrakeState.brakeHeld    = false;
                MomentumDriftState.driftKeyHeld = false;
            }

            // Send key state to server whenever any value changes.
            boolean nb = MomentumBrakeState.brakeHeld;
            boolean nd = MomentumDriftState.driftKeyHeld;
            if (client.getNetworkHandler() != null
                    && (nb != pktBrake || nd != pktDrift)) {
                pktBrake = nb; pktDrift = nd;
                PacketByteBuf buf = PacketByteBufs.create();
                new KeyStatePacket(nb, nd).write(buf);
                ClientPlayNetworking.send(KeyStatePacket.ID, buf);
            }
        });

        // ── Per-tick effects ──────────────────────────────────────────────────

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (reloadKey.wasPressed() && client.player != null) {
                MomentumConfig.reload();
                client.player.sendMessage(Text.literal("[Momentum] Config reloaded"), true);
            }
            if (openOptionsKey.wasPressed()) {
                client.execute(() -> client.setScreen(
                    io.github.milkucha.momentum.config.MomentumConfigScreen.create(null)));
            }

            if (client.player != null && client.player.getVehicle() instanceof AutomobileEntity auto) {
                MomentumConfig cfg = MomentumConfig.get();
                SteeringDebugAccessor accessor = (SteeringDebugAccessor) auto;

                if (!cfg.enabled) {
                    resetTickState();
                    return;
                }

                // Independent camera lerp for Arcade and Responsive drift.
                boolean arcadeDriftCamActive    = accessor.momentum$isArcadeDriftActive();
                boolean responsiveDriftCamActive = accessor.momentum$isResponsiveDriftActive();

                float kTarget = cfg.arcadeDrift.cameraEnabled
                        ? accessor.momentum$getArcadeDriftOffset() * cfg.arcadeDrift.cameraScale : 0f;
                float mTarget = cfg.responsiveDrift.cameraEnabled
                        ? accessor.momentum$getResponsiveDriftOffset() * cfg.responsiveDrift.cameraScale : 0f;

                kCameraDriftYawOffset += (kTarget - kCameraDriftYawOffset)
                        * (arcadeDriftCamActive ? cfg.arcadeDrift.cameraLerpIn : cfg.arcadeDrift.cameraLerpOut);
                mCameraDriftYawOffset += (mTarget - mCameraDriftYawOffset)
                        * (responsiveDriftCamActive ? cfg.responsiveDrift.cameraLerpIn : cfg.responsiveDrift.cameraLerpOut);

                if (cfg.camera.enabled && cfg.camera.lock) {
                    client.player.setYaw(auto.getYaw() + kCameraDriftYawOffset + mCameraDriftYawOffset);
                    client.player.setPitch(cfg.camera.pitch);
                }

                // Brake skid sound
                boolean brakeHeld = MomentumBrakeState.brakeHeld;
                if (brakeHeld && !prevBrakeHeld) {
                    client.getSoundManager().play(new BrakingSkidSound(auto));
                }
                prevBrakeHeld = brakeHeld;

                // Brake zoom — spring-damper driven by deceleration.
                float hSpd = accessor.momentum$getHSpeed();
                float decel = Math.max(0f, prevHSpeed - hSpd);
                prevHSpeed = hSpd;
                if (cfg.camera.enabled) {
                    float inputForce = decel * cfg.camera.brakeZoomInputScale;
                    brakeZoomVelocity = brakeZoomVelocity * cfg.camera.brakeZoomDamping
                        + inputForce
                        - cfg.camera.brakeZoomSpring * MomentumBrakeState.brakeZoomOffset;
                    MomentumBrakeState.brakeZoomOffset += brakeZoomVelocity;
                    if (MomentumBrakeState.brakeZoomOffset > cfg.camera.brakeZoomFov) {
                        MomentumBrakeState.brakeZoomOffset = cfg.camera.brakeZoomFov;
                        brakeZoomVelocity = Math.min(brakeZoomVelocity, 0f);
                    } else if (MomentumBrakeState.brakeZoomOffset < 0f) {
                        MomentumBrakeState.brakeZoomOffset = 0f;
                        brakeZoomVelocity = Math.max(brakeZoomVelocity, 0f);
                    }
                } else {
                    brakeZoomVelocity = 0f;
                    MomentumBrakeState.brakeZoomOffset = 0f;
                }

                // Drift skid sounds — one per drift type, triggered on rising edge of active state
                boolean vanillaActive = accessor.momentum$isDrifting();
                if (vanillaActive && !prevVanillaDriftActive) {
                    client.getSoundManager().play(new VanillaDriftSkidSound(auto));
                }
                prevVanillaDriftActive = vanillaActive;

                boolean arcadeActive = accessor.momentum$isArcadeDriftActive();
                if (arcadeActive && !prevArcadeDriftActive) {
                    client.getSoundManager().play(new ArcadeDriftSkidSound(auto));
                }
                prevArcadeDriftActive = arcadeActive;

                boolean responsiveActive = accessor.momentum$isResponsiveDriftActive();
                if (responsiveActive && !prevResponsiveDriftActive) {
                    client.getSoundManager().play(new ResponsiveDriftSkidSound(auto));
                }
                prevResponsiveDriftActive = responsiveActive;

            } else {
                resetTickState();
            }
        });
    }

    private void resetTickState() {
        prevBrakeHeld             = false;
        prevVanillaDriftActive    = false;
        prevArcadeDriftActive     = false;
        prevResponsiveDriftActive = false;
        kCameraDriftYawOffset              = 0f;
        mCameraDriftYawOffset              = 0f;
        brakeZoomVelocity                  = 0f;
        prevHSpeed                         = 0f;
        MomentumBrakeState.brakeZoomOffset = 0f;
    }

    /**
     * Returns true if the given GLFW key code is currently held down.
     * Reads raw GLFW state — does not consume key events or interfere with
     * vanilla key bindings (Jump, Move Backwards, etc.).
     */
    private static boolean isKeyHeld(int glfwKeyCode, long windowHandle) {
        return glfwKeyCode != GLFW.GLFW_KEY_UNKNOWN
                && GLFW.glfwGetKey(windowHandle, glfwKeyCode) == GLFW.GLFW_PRESS;
    }
}
