package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.screen.AutomobileHud;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AutomobileHudMixin — suppresses Automobility's built-in speedometer so
 * Momentum's own HUD panel renders in its place without overlap.
 *
 * We only cancel renderSpeedometer(). The renderControlHints() call is
 * intentionally also cancelled to declutter the screen.
 *
 * Note: AutomobileHud in 1.19.2 uses MatrixStack (not DrawContext, which
 * was introduced in MC 1.20). remap=false so we target Automobility names directly.
 */
@Mixin(value = AutomobileHud.class, remap = false)
public abstract class AutomobileHudMixin {

    @Inject(
        method = "renderSpeedometer",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void momentum$suppressOriginalSpeedometer(
            MatrixStack matrices,
            AutomobileEntity auto,
            CallbackInfo ci) {
        MomentumConfig cfg = MomentumConfig.get();
        if (!cfg.enabled || !cfg.barHud.enabled) return;
        ci.cancel();
    }

    @Inject(
        method = "renderControlHints",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void momentum$suppressControlHints(
            MatrixStack matrices,
            float alpha,
            CallbackInfo ci) {
        if (!MomentumConfig.get().enabled) return;
        ci.cancel();
    }
}
