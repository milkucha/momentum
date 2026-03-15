package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.util.AUtils;
import io.github.milkucha.momentum.MomentumBrakeState;
import io.github.milkucha.momentum.MomentumDriftState;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
    @Shadow private int driftDir;
    @Shadow private int turboCharge;
    @Shadow public abstract boolean automobileOnGround();
    @Shadow private boolean wasOnGround;
    @Shadow private void setDrifting(boolean drifting) {}
    @Shadow private void consumeTurboCharge() {}
    @Shadow public void createDriftParticles() {}

    // Per-entity previous J-key state for rising/falling edge detection.
    // Instance field (not static) so client and integrated-server entities
    // each track their own edge independently.
    @Unique private boolean momentum$prevDriftKeyHeld = false;

    // ── K-drift state ─────────────────────────────────────────────────────────
    // Per-entity previous K-key state — same pattern as momentum$prevDriftKeyHeld
    // (instance field so client and server entities track edges independently).
    @Unique private boolean momentum$prevKDriftKeyHeld = false;
    @Unique private boolean momentum$kDriftActive = false;
    @Unique private float   momentum$kDriftOffset = 0f;   // current slip angle (degrees)
    @Unique private int     momentum$kDriftTimer  = 0;    // ticks drift has been active
    @Unique private int     momentum$kDriftDir    = 0;    // ±1

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
        if (MomentumBrakeState.brakeHeld) return value;  // brake inject handles decel this tick
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
        if (drifting || momentum$kDriftActive) return target;
        MomentumConfig cfg = MomentumConfig.get();
        float speedCurved = (float) Math.pow(Math.abs(hSpeed), cfg.steeringUndersteerCurve);
        return target / (1f + cfg.steeringUndersteer * speedCurved);
    }

    // ── J-key drift (transplanted from Automobility) ─────────────────────────

    /**
     * Complete replacement of Automobility's driftingTick() using J-key state.
     *
     * This is a direct transplant of the Automobility source logic with
     * holdingDrift/prevHoldDrift replaced by MomentumDriftState.driftKeyHeld
     * and the @Unique per-instance momentum$prevDriftKeyHeld. Automobility's
     * own driftingTick is cancelled so this is the sole drift implementation.
     *
     * By writing directly to the shadowed drifting/driftDir/turboCharge fields,
     * all other Momentum mixins (understeer bypass, steering ramp bypass, brake
     * skip) continue to work correctly without modification.
     *
     * Controller rumble calls (controllerAction) are intentionally omitted —
     * this mod targets keyboard play.
     */
    @Inject(method = "driftingTick", at = @At("HEAD"), cancellable = true)
    private void momentum$jDriftTick(CallbackInfo ci) {
        ci.cancel();

        boolean jHeld  = MomentumDriftState.driftKeyHeld;
        boolean prevJ  = momentum$prevDriftKeyHeld;

        // Log every tick while J is held so we can confirm it's being read
        boolean mcOnGnd = ((net.minecraft.entity.Entity)(Object)this).isOnGround();
        if (jHeld) {
            System.out.println("[Momentum-Drift] J held | steer=" + steering
                + " hSpd=" + hSpeed
                + " autoOnGnd=" + automobileOnGround()
                + " wasOnGnd=" + wasOnGround
                + " mcOnGnd=" + mcOnGnd
                + " drifting=" + drifting + " prevJ=" + prevJ);
        }

        // Rising edge: J just pressed this tick
        if (!prevJ && jHeld) {
            boolean canDrift = steering != 0 && !drifting && hSpeed > 0.4f && mcOnGnd;
            System.out.println("[Momentum-Drift] RISING EDGE | canDrift=" + canDrift
                + " steer=" + steering + " hSpd=" + hSpeed
                + " mcOnGnd=" + mcOnGnd + " drifting=" + drifting);
            if (canDrift) {
                setDrifting(true);
                driftDir = steering > 0 ? 1 : -1;
                engineSpeed -= 0.028f * engineSpeed;
                System.out.println("[Momentum-Drift] DRIFT STARTED dir=" + driftDir);
            }
        }

        if (drifting) {
            if (mcOnGnd) createDriftParticles();

            if (prevJ && !jHeld) {
                // Falling edge: J released → end drift and grant turbo boost
                System.out.println("[Momentum-Drift] FALLING EDGE — consuming turbo charge=" + turboCharge);
                setDrifting(false);
                consumeTurboCharge();
            } else if (hSpeed < 0.33f) {
                // Too slow: drift cancelled, no boost
                System.out.println("[Momentum-Drift] DRIFT CANCELLED (too slow, hSpd=" + hSpeed + ")");
                setDrifting(false);
                turboCharge = 0;
            }

            if (mcOnGnd) {
                turboCharge += ((steeringLeft && driftDir < 0) || (steeringRight && driftDir > 0)) ? 2 : 1;
            }
        }

        momentum$prevDriftKeyHeld = jHeld;
    }

    // ── K-drift ───────────────────────────────────────────────────────────────

    /**
     * K-key arcade drift — state machine, runs at HEAD of movementTick each tick.
     *
     * Independent of Automobility's drifting/holdingDrift/turboCharge pipeline.
     * Reads MomentumDriftState.kDriftKeyHeld (polled from GLFW in START_CLIENT_TICK).
     *
     * Rising edge + conditions → set kDriftActive, snap kDriftOffset to initial slip.
     * While K held → converge offset to kDriftSlipAngle; cancel if speed drops.
     * K released → fade offset to 0; grant boost if drift was sustained.
     *
     * The actual movement direction offset is applied in momentum$applyKDriftSlip (RETURN inject).
     * Understeer is suppressed during K-drift via the @ModifyArg above.
     */
    @Inject(method = "movementTick", at = @At("HEAD"))
    private void momentum$kDriftStateMachine(CallbackInfo ci) {
        boolean kHeld     = MomentumDriftState.kDriftKeyHeld;
        boolean prevKHeld = momentum$prevKDriftKeyHeld;
        MomentumConfig cfg = MomentumConfig.get();

        boolean kMcOnGnd = ((net.minecraft.entity.Entity)(Object)this).isOnGround();

        // Log every tick K is held so we can see the raw state
        if (kHeld) {
            System.out.println("[Momentum-KDrift] K held | kActive=" + momentum$kDriftActive
                + " prevK=" + prevKHeld
                + " steer=" + steering
                + " hSpd=" + hSpeed
                + " onGnd=" + kMcOnGnd
                + " jDrifting=" + drifting);
        }

        if (!momentum$kDriftActive) {
            // Rising edge: start drift
            if (!prevKHeld && kHeld) {
                System.out.println("[Momentum-KDrift] RISING EDGE | jDrifting=" + drifting
                    + " steer=" + steering + " hSpd=" + hSpeed
                    + " onGnd=" + kMcOnGnd);
                if (!drifting && steering != 0 && hSpeed > 0.4f && kMcOnGnd) {
                    momentum$kDriftActive = true;
                    momentum$kDriftDir    = steering > 0 ? 1 : -1;
                    momentum$kDriftTimer  = 0;
                    momentum$kDriftOffset = momentum$kDriftDir * cfg.kDriftSlipAngle;
                    System.out.println("[Momentum-KDrift] DRIFT STARTED dir=" + momentum$kDriftDir
                        + " offset=" + momentum$kDriftOffset);
                } else {
                    System.out.println("[Momentum-KDrift] CONDITIONS NOT MET — "
                        + "drifting=" + drifting
                        + " steer!=0:" + (steering != 0)
                        + " hSpd>0.4:" + (hSpeed > 0.4f)
                        + " onGnd:" + kMcOnGnd);
                }
            }
        } else {
            if (kHeld) {
                // Maintain slip angle while K held
                momentum$kDriftTimer++;
                float target = momentum$kDriftDir * cfg.kDriftSlipAngle;
                momentum$kDriftOffset = AUtils.shift(momentum$kDriftOffset, 4f, target);
                // Cancel drift if speed drops too low
                if (hSpeed < 0.3f) {
                    System.out.println("[Momentum-KDrift] CANCELLED (too slow, hSpd=" + hSpeed + ")");
                    momentum$kDriftActive = false;
                    momentum$kDriftTimer  = 0;
                    momentum$kDriftOffset = 0f;
                }
            } else {
                // K released: fade slip angle back to zero
                momentum$kDriftOffset = AUtils.zero(momentum$kDriftOffset, cfg.kDriftSlipDecay);
                if (Math.abs(momentum$kDriftOffset) < 0.5f) {
                    if (momentum$kDriftTimer >= cfg.kDriftMinTicks) {
                        System.out.println("[Momentum-KDrift] BOOST GRANTED timer=" + momentum$kDriftTimer);
                        engineSpeed += cfg.kDriftBoost;
                    }
                    momentum$kDriftActive = false;
                    momentum$kDriftTimer  = 0;
                    momentum$kDriftOffset = 0f;
                }
            }
        }
        momentum$prevKDriftKeyHeld = kHeld;
    }

    /**
     * Applies the K-drift slip angle by rotating the entity's current velocity vector.
     * Runs at RETURN of movementTick, after setDeltaMovement has been called.
     *
     * Rotation is around the Y axis by kDriftOffset degrees, which makes the car move
     * in a direction diverging from its heading — the visual "rear slides out" effect.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyKDriftSlip(CallbackInfo ci) {
        if (!momentum$kDriftActive || Math.abs(momentum$kDriftOffset) < 0.01f) return;
        Entity self = (Entity)(Object)this;
        Vec3d mov = self.getVelocity();
        double rad = Math.toRadians(momentum$kDriftOffset);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        self.setVelocity(
            mov.x * cos - mov.z * sin,
            mov.y,
            mov.x * sin + mov.z * cos
        );
    }

    // ── Brake ────────────────────────────────────────────────────────────────

    /**
     * Applies linear braking directly to engineSpeed at the end of movementTick,
     * using MomentumBrakeState.brakeHeld as the source of truth instead of
     * Automobility's braking flag.
     *
     * Previous approaches hooked into Automobility's provideClientInput / braking flag
     * pipeline, but that flag can get stuck at true on the server entity indefinitely
     * (a server↔client sync race), meaning braking ran every tick regardless of key
     * state and always drove speed to zero. By reading the volatile static that is
     * written from GLFW at the start of each client tick, both the client entity and
     * the integrated-server entity apply braking for exactly as long as Space is
     * physically held — no stuck-flag problem possible.
     *
     * Linear formula: engineSpeed -= brakeDecay per tick (floored at -0.25).
     * Constant deceleration mirrors real friction braking — no asymptotic tail near zero.
     * Holding Space long enough drives through zero into reverse, capped at Automobility's
     * reverse floor (-0.25).
     *
     * On a dedicated server brakeHeld is always false so this inject is a no-op
     * (the car coasts normally).
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyBrake(CallbackInfo ci) {
        if (!MomentumBrakeState.brakeHeld) return;
        if (drifting) return;  // braking reduces hSpeed which would cancel the drift
        float decay = MomentumConfig.get().brakeDecay;
        engineSpeed = Math.max(engineSpeed - decay, -0.25f);
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
    @Unique @Override public int     momentum$getTurboCharge()      { return turboCharge; }
    @Unique @Override public boolean momentum$isOnGround()          { return ((net.minecraft.entity.Entity)(Object)this).isOnGround(); }
    @Unique @Override public boolean momentum$isKDriftActive()      { return momentum$kDriftActive; }
    @Unique @Override public float   momentum$getKDriftOffset()     { return momentum$kDriftOffset; }
}
