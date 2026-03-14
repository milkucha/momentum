package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Maps the brake input to the jump key (Space) instead of S.
 *
 * Replaces the vanilla 'back' argument in provideClientInput (input.down / S)
 * with the raw GLFW Space key state, before the change-detection comparison runs.
 * This sends the correct braking value to both the client and server entities.
 *
 * NOTE: No movementTick inject here. A previous attempt injected at HEAD of
 * movementTick to directly set this.braking from GLFW. That caused a race:
 * when AutomobileEntity ticks before LocalPlayer, the inject set braking=false
 * on the client entity first, so provideClientInput saw braking=false AND back=false
 * — no change — and never sent the reset packet to the server. The server entity
 * stayed at braking=true indefinitely. Relying solely on @ModifyVariable avoids
 * this: the client entity's braking field is set exactly once per tick by
 * provideClientInput, so the change-detection always fires correctly on release.
 *
 * Client-only (in momentum.mixins.json "client" list) because GLFW is client-only.
 */
@Mixin(value = AutomobileEntity.class, remap = false)
public class AutomobileBrakeMixin {

    @ModifyVariable(
        method = "provideClientInput",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 1   // 2nd boolean param: (fwd, back, left, right, space)
    )
    private boolean momentum$mapBrakeToSpace(boolean back) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return back;
        if (client.player.getVehicle() != (Object)this) return back;
        long win = client.getWindow().getHandle();
        return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
    }
}
