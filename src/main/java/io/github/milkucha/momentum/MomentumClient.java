package io.github.milkucha.momentum;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.hud.MomentumHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.entity.Entity;
import org.lwjgl.glfw.GLFW;

public class MomentumClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MomentumConfig.get();

        HudRenderCallback.EVENT.register((drawContext, tickDelta) ->
                MomentumHud.render(drawContext, tickDelta));

        // Vanilla suppresses input.jumping while riding, so Automobility's LocalPlayerMixin
        // always passes space=false to provideClientInput even when Space is held. This means
        // holdingDrift is never set true on the server and drift never triggers.
        // We call provideClientInput again after Automobility's call with the raw jump key
        // state, which sends the corrected value to the server.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            Entity vehicle = client.player.getVehicle();
            if (!(vehicle instanceof AutomobileEntity auto)) return;

            // KeyBinding.isPressed() is a consumed check that returns false once the press
            // counter is exhausted. Use GLFW directly for raw held-state.
            // Movement inputs are read from the entity (already correctly set by Automobility).
            long win = client.getWindow().getHandle();
            if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_SPACE) != GLFW.GLFW_PRESS) return;

            SteeringDebugAccessor dbg = (SteeringDebugAccessor) auto;
            auto.provideClientInput(
                dbg.momentum$isAccelerating(),
                dbg.momentum$isBraking(),
                dbg.momentum$isSteeringLeft(),
                dbg.momentum$isSteeringRight(),
                true
            );
        });
    }
}