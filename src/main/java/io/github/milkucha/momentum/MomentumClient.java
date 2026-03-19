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

    /** Handbrake (drift) — triggers the active drift profile. Default: Space. */
    public static KeyBinding DRIFT_KEY;

    /** Brake — applies Momentum's own braking. Default: S. */
    public static KeyBinding BRAKE_KEY;

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

        DRIFT_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.momentum.handbrake_drift",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_SPACE,
                "key.categories.momentum"
        ));

        BRAKE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.momentum.brake",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_S,
                "key.categories.momentum"
        ));

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
                MomentumBrakeState.brakeHeld  = isKeyHeld(BRAKE_KEY, win);
                MomentumDriftState.driftKeyHeld = isKeyHeld(DRIFT_KEY, win);
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
                boolean kDriftCamActive = accessor.momentum$isKDriftActive();
                boolean mDriftCamActive = accessor.momentum$isMDriftActive();

                float kTarget = cfg.kDrift.cameraEnabled
                        ? accessor.momentum$getKDriftOffset() * cfg.kDrift.cameraScale : 0f;
                float mTarget = cfg.mDrift.cameraEnabled
                        ? accessor.momentum$getMDriftOffset() * cfg.mDrift.cameraScale : 0f;

                kCameraDriftYawOffset += (kTarget - kCameraDriftYawOffset)
                        * (kDriftCamActive ? cfg.kDrift.cameraLerpIn : cfg.kDrift.cameraLerpOut);
                mCameraDriftYawOffset += (mTarget - mCameraDriftYawOffset)
                        * (mDriftCamActive ? cfg.mDrift.cameraLerpIn : cfg.mDrift.cameraLerpOut);

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
                    MomentumBrakeState.brakeZoomOffset = Math.max(0f,
                        Math.min(cfg.camera.brakeZoomFov, MomentumBrakeState.brakeZoomOffset));
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

                boolean arcadeActive = accessor.momentum$isKDriftActive();
                if (arcadeActive && !prevArcadeDriftActive) {
                    client.getSoundManager().play(new ArcadeDriftSkidSound(auto));
                }
                prevArcadeDriftActive = arcadeActive;

                boolean responsiveActive = accessor.momentum$isMDriftActive();
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
     * Returns true if the given KeyBinding is currently held down.
     * Reads the user-configured key from the binding so remapping works correctly.
     */
    private static boolean isKeyHeld(KeyBinding binding, long windowHandle) {
        InputUtil.Key bound = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        if (bound.getCategory() == InputUtil.Type.KEYSYM) {
            return GLFW.glfwGetKey(windowHandle, bound.getCode()) == GLFW.GLFW_PRESS;
        }
        return false;
    }
}
