package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Client-only mixin for brake key remapping side-effects.
 *
 * The actual brake deceleration is now handled entirely in AutomobileEntityMixin
 * via a @Inject at RETURN of movementTick, reading MomentumBrakeState.brakeHeld
 * (a volatile static written from GLFW in START_CLIENT_TICK). That approach
 * bypasses Automobility's braking flag and is immune to server-side stuck-flag issues.
 *
 * This mixin only handles one thing: suppressing holdingDrift when Space is used
 * as the brake key. Without this, Automobility's LocalPlayerMixin passes
 * input.jumping (Space) as the `space` param → holdingDrift=true, which fires
 * drift physics on top of braking.
 */
@Mixin(value = AutomobileEntity.class, remap = false)
public class AutomobileBrakeMixin {

    @ModifyVariable(
        method = "provideClientInput",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 4   // 5th boolean param: space → holdingDrift
    )
    private boolean momentum$suppressDriftOnBrake(boolean space) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return space;
        if (client.player.getVehicle() != (Object)this) return space;
        return false;
    }
}
