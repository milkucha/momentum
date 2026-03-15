package io.github.milkucha.momentum.mixin;

import io.github.milkucha.momentum.MomentumBrakeState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Subtracts brakeZoomOffset from the returned FOV each frame.
 * brakeZoomOffset is written by MomentumClient each tick and is 0 when
 * the player is not in a car or the brake is not held.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void momentum$applyBrakeZoom(Camera camera, float tickDelta, boolean changingFov,
                                         CallbackInfoReturnable<Double> cir) {
        float offset = MomentumBrakeState.brakeZoomOffset;
        if (offset == 0f) return;
        cir.setReturnValue(cir.getReturnValue() - (double) offset);
    }
}
