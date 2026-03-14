package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.util.AUtils;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * AutomobileEntityMixin — corrected movement feel for Automobility.
 *
 * 1. COASTING FIX
 *    Automobility's movementTick() contains:
 *        this.engineSpeed = AUtils.zero(this.engineSpeed, 0.025f);
 *    which stops the car in ~2 ticks from full speed.
 *
 *    We use @Redirect to replace that specific AUtils.zero call with one using
 *    coastDecay from config. This intercepts it *before* hSpeed is computed from
 *    engineSpeed, so the slower decay takes effect on the current tick's movement.
 *
 *    There are two AUtils.zero calls in movementTick — ordinal 0 is for boostSpeed,
 *    ordinal 1 is the engineSpeed coast decay we want to replace.
 *
 * 2. ACCELERATION SCALE
 *    @ModifyArg on the calculateAcceleration() call site scales the speed input,
 *    which indirectly scales the acceleration output since speed is in the denominator.
 *
 * 3. STEERING RAMP RATE
 *    Automobility's steeringTick() uses a hardcoded 0.42f constant for both
 *    ramping toward full lock and returning to centre. This reaches full lock in
 *    ~2.4 ticks — instant and horse-like.
 *    @ModifyConstant replaces every occurrence of 0.42f in steeringTick with the
 *    configurable steeringRampRate. Skipped during a drift so drift steering stays snappy.
 *
 * 4. SPEED-BASED UNDERSTEER
 *    postMovementTick() drives angularSpeed via:
 *        newAngularSpeed = AUtils.shift(newAngularSpeed, 6*traction, steering_target)
 *        this.angularSpeed = grip*newAngularSpeed + (1-grip)*angularSpeed
 *        yawInc = angularSpeed; setYRot(yaw + yawInc);   ← yaw applied here
 *    Scaling angularSpeed at TAIL (after the yaw update) has almost no real effect:
 *    the grip lerp quickly restores it from target, giving a hard floor at grip×target.
 *    Instead, @ModifyArg scales the `to` argument of AUtils.shift (ordinal 1, index 2)
 *    in postMovementTick, so angularSpeed genuinely converges to target/scale, no grip floor.
 *    Skipped during a drift so the drift arc is unaffected.
 */
@Mixin(value = AutomobileEntity.class, remap = false)
public abstract class AutomobileEntityMixin implements SteeringDebugAccessor {

    @Shadow private boolean drifting;
    @Shadow private boolean holdingDrift;
    @Shadow private boolean accelerating;
    @Shadow private boolean braking;
    @Shadow private boolean steeringLeft;
    @Shadow private boolean steeringRight;
    @Shadow private float engineSpeed;
    @Shadow private float hSpeed;
    @Shadow private float steering;
    @Shadow private float angularSpeed;

    // ── Coasting fix ─────────────────────────────────────────────────────────

    @Redirect(
        method = "movementTick",
        at = @At(
            value = "INVOKE",
            target = "Lio/github/foundationgames/automobility/util/AUtils;zero(FF)F",
            ordinal = 1
        )
    )
    private float momentum$replaceCoastDecay(float value, float rate) {
        if (braking) return value;  // braking has its own deceleration; don't double-apply coast
        return AUtils.zero(value, MomentumConfig.get().coastDecay);
    }

    // ── Acceleration scale ────────────────────────────────────────────────────

    @ModifyArg(
        method = "movementTick",
        at = @At(
            value = "INVOKE",
            target = "Lio/github/foundationgames/automobility/entity/AutomobileEntity;calculateAcceleration(FLio/github/foundationgames/automobility/automobile/AutomobileStats;)F"
        ),
        index = 0
    )
    private float momentum$scaleAcceleration(float speed) {
        float scale = MomentumConfig.get().accelerationScale;
        return speed / scale;
    }

    // ── Steering ramp rate ────────────────────────────────────────────────────

    /**
     * Replaces the 0.42f constant in steeringTick() with steeringRampRate from config.
     * This constant controls both how fast steering builds toward full lock and how
     * fast it returns to centre. Skipped during a drift to keep drift steering responsive.
     */
    @ModifyConstant(
        method = "steeringTick",
        constant = @Constant(floatValue = 0.42f)
    )
    private float momentum$steeringRampRate(float original) {
        if (drifting) return original;
        return MomentumConfig.get().steeringRampRate;
    }

    // ── Speed-based understeer ────────────────────────────────────────────────

    /**
     * Scales the TARGET argument of the AUtils.shift() call that drives angularSpeed
     * in postMovementTick (ordinal 1 — the normal driving branch, not burnout or stopped).
     *
     * Automobility computes:
     *   newAngularSpeed = AUtils.shift(newAngularSpeed, 6 * traction, steering_target)
     * then blends with grip and applies the result to yaw — all before any TAIL inject
     * could act. Scaling the stored angularSpeed at TAIL has almost no effect because
     * the lerp (grip ≈ 0.6) resets it mostly from scratch each tick; no matter how
     * large the understeer value, the yaw rotation can never drop below grip × target.
     *
     * By scaling the target here instead, angularSpeed genuinely converges to
     *   target / (1 + steeringUndersteer * |hSpeed|^steeringUndersteerCurve)
     * giving true proportional understeer with no grip floor.
     * Skipped during a drift so the drift arc is unaffected.
     */
    @ModifyArg(
        method = "postMovementTick",
        at = @At(
            value = "INVOKE",
            target = "Lio/github/foundationgames/automobility/util/AUtils;shift(FFF)F",
            ordinal = 1
        ),
        index = 2
    )
    private float momentum$applyUndersteer(float target) {
        if (drifting) return target;
        MomentumConfig cfg = MomentumConfig.get();
        float speedCurved = (float) Math.pow(Math.abs(hSpeed), cfg.steeringUndersteerCurve);
        return target / (1f + cfg.steeringUndersteer * speedCurved);
    }

    // ── Brake decay ───────────────────────────────────────────────────────────

    /**
     * Replaces the braking deceleration in movementTick() with a multiplicative decay.
     * Automobility's braking line:
     *   this.engineSpeed = Math.max(this.engineSpeed - 0.15f, -0.25f);
     *
     * Math.max(FF)F ordinals in movementTick:
     *   0 — Math.max(getHSpeed(), 0.1f)   gel bounce  (line ~762)
     *   1 — Math.max(getVSpeed(), 0.9f)   gel bounce  (line ~762)
     *   2 — Math.max(engineSpeed, 0)      acceleration (line ~782)
     *   3 — Math.max(engineSpeed-0.15f, -0.25f)  BRAKING  (line ~799)  ← us
     *
     * Multiplicative formula: engineSpeed * (1 - brakeDecay) per tick.
     * This makes deceleration proportional to current speed — large at high speeds,
     * tapering naturally at low speeds — so a brief press only reduces speed by a
     * fraction rather than driving it abruptly to zero.
     * Floor is 0 (Space does not push into reverse; let the car roll back naturally).
     */
    @Redirect(
        method = "movementTick",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Math;max(FF)F",
            ordinal = 3
        )
    )
    private float momentum$brakeDecay(float a, float b) {
        return Math.max(engineSpeed * (1f - MomentumConfig.get().brakeDecay), 0f);
    }

    // ── Debug accessors (SteeringDebugAccessor) ───────────────────────────────

    @Unique @Override public float momentum$getSteering()           { return steering; }
    @Unique @Override public float momentum$getHSpeed()             { return hSpeed; }
    @Unique @Override public float momentum$getAngularSpeed()       { return angularSpeed; }
    @Unique @Override public boolean momentum$isDrifting()          { return drifting; }
    @Unique @Override public boolean momentum$isHoldingDrift()      { return holdingDrift; }
    @Unique @Override public boolean momentum$isAccelerating()      { return accelerating; }
    @Unique @Override public boolean momentum$isBraking()           { return braking; }
    @Unique @Override public boolean momentum$isSteeringLeft()      { return steeringLeft; }
    @Unique @Override public boolean momentum$isSteeringRight()     { return steeringRight; }
}
