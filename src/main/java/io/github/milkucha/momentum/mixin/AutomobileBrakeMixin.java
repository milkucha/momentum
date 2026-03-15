package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only mixin.
 *
 * H key: diagnostic force-drift.
 * Bypasses ALL conditions (steering, speed, ground, rising-edge).
 * Remove once the real drift trigger is confirmed working.
 */
@Mixin(value = AutomobileEntity.class, remap = false)
public class AutomobileBrakeMixin {

    @Shadow private boolean drifting;
    @Shadow private int driftDir;

    @Inject(method = "driftingTick", at = @At("HEAD"))
    private void momentum$forceDriftDiagnostic(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (client.player.getVehicle() != (Object)this) return;
        long win = client.getWindow().getHandle();
        if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_H) == GLFW.GLFW_PRESS) {
            drifting = true;
            if (driftDir == 0) driftDir = 1;
        }
    }
}
