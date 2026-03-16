package io.github.milkucha.momentum;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.hud.BarHud;
import io.github.milkucha.momentum.hud.MomentumHud;
import io.github.milkucha.momentum.network.KeyStatePacket;
import io.github.milkucha.momentum.sound.BrakingSkidSound;
import io.github.milkucha.momentum.sound.JDriftSkidSound;
import io.github.milkucha.momentum.sound.KDriftSkidSound;
import io.github.milkucha.momentum.sound.MDriftSkidSound;
import io.github.milkucha.momentum.sound.NDriftSkidSound;
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

    private boolean prevBrakeHeld      = false;
    private boolean prevJDriftActive   = false;
    private boolean prevKDriftActive   = false;
    private boolean prevNDriftKeyHeld  = false;
    private boolean prevMDriftActive   = false;
    private float   kCameraDriftYawOffset = 0f;
    private float   mCameraDriftYawOffset = 0f;
    private float   brakeZoomVelocity    = 0f;
    private float   prevHSpeed           = 0f;

    // Last key-state snapshot sent to the server; used to send only on changes.
    private boolean pktBrake = false;
    private boolean pktJ     = false;
    private boolean pktK     = false;
    private boolean pktN     = false;
    private boolean pktM     = false;
    private boolean pktO     = false;

    @Override
    public void onInitializeClient() {
        MomentumConfig.get();

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (MomentumConfig.get().hud.useBarHud) {
                BarHud.render(drawContext, tickDelta);
            } else {
                MomentumHud.render(drawContext, tickDelta);
            }
            MomentumHud.renderDebug(drawContext, tickDelta);
        });

        KeyBinding reloadKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.momentum.reload_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.momentum"
        ));
        KeyBinding openOptionsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.momentum.open_options",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PERIOD,
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
                MomentumDriftState.oKeyHeld =
                    GLFW.glfwGetKey(win, GLFW.GLFW_KEY_O) == GLFW.GLFW_PRESS;
            } else {
                MomentumBrakeState.brakeHeld = false;
                MomentumDriftState.driftKeyHeld = false;
                MomentumDriftState.kDriftKeyHeld = false;
                MomentumDriftState.nDriftKeyHeld = false;
                MomentumDriftState.mKeyHeld      = false;
                MomentumDriftState.oKeyHeld      = false;
            }

            // Send key state to server whenever any value changes.
            // Guards: network handler must exist (player is connected).
            boolean nb = MomentumBrakeState.brakeHeld;
            boolean nj = MomentumDriftState.driftKeyHeld;
            boolean nk = MomentumDriftState.kDriftKeyHeld;
            boolean nn = MomentumDriftState.nDriftKeyHeld;
            boolean nm = MomentumDriftState.mKeyHeld;
            boolean no = MomentumDriftState.oKeyHeld;
            if (client.getNetworkHandler() != null
                    && (nb != pktBrake || nj != pktJ || nk != pktK || nn != pktN || nm != pktM || no != pktO)) {
                pktBrake = nb; pktJ = nj; pktK = nk; pktN = nn; pktM = nm; pktO = no;
                PacketByteBuf buf = PacketByteBufs.create();
                new KeyStatePacket(nb, nj, nk, nn, nm, no).write(buf);
                ClientPlayNetworking.send(KeyStatePacket.ID, buf);
            }
        });

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

                // Independent camera lerp for K-drift and M-drift.
                // Each drift's contribution is tracked separately so scale/lerp settings don't interfere.
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

                if (cfg.camera.lock) {
                    client.player.setYaw(auto.getYaw() + kCameraDriftYawOffset + mCameraDriftYawOffset);
                    client.player.setPitch(cfg.camera.pitch);
                }

                boolean brakeHeld = MomentumBrakeState.brakeHeld;
                if (brakeHeld && !prevBrakeHeld) {
                    client.getSoundManager().play(new BrakingSkidSound(auto));
                }
                prevBrakeHeld = brakeHeld;

                // Brake zoom — spring-damper driven by deceleration.
                // Deceleration (drop in hSpeed this tick) acts as a force on the zoom "mass".
                // The spring returns zoom to zero; damping prevents runaway.
                // When the vehicle stops, accumulated velocity carries the zoom forward briefly
                // before the spring pulls it back — the inertia-body feel.
                float hSpd = accessor.momentum$getHSpeed();
                float decel = Math.max(0f, prevHSpeed - hSpd); // positive only while slowing
                prevHSpeed = hSpd;
                float inputForce = decel * cfg.camera.brakeZoomInputScale;
                brakeZoomVelocity = brakeZoomVelocity * cfg.camera.brakeZoomDamping
                    + inputForce
                    - cfg.camera.brakeZoomSpring * MomentumBrakeState.brakeZoomOffset;
                MomentumBrakeState.brakeZoomOffset += brakeZoomVelocity;
                // Clamp to [0, brakeZoomFov] — no negative zoom-out and no runaway
                MomentumBrakeState.brakeZoomOffset = Math.max(0f,
                    Math.min(cfg.camera.brakeZoomFov, MomentumBrakeState.brakeZoomOffset));

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

                boolean mDriftNowActive = accessor.momentum$isMDriftActive();
                if (mDriftNowActive && !prevMDriftActive) {
                    client.getSoundManager().play(new MDriftSkidSound(auto));
                }
                prevMDriftActive = mDriftNowActive;
            } else {
                prevBrakeHeld        = false;
                prevJDriftActive     = false;
                prevKDriftActive     = false;
                prevNDriftKeyHeld    = false;
                prevMDriftActive     = false;
                kCameraDriftYawOffset              = 0f;
                mCameraDriftYawOffset              = 0f;
                brakeZoomVelocity                  = 0f;
                prevHSpeed                         = 0f;
                MomentumBrakeState.brakeZoomOffset = 0f;
            }
        });
    }
}
