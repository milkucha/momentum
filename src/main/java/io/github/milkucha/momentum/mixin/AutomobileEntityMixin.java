package io.github.milkucha.momentum.mixin;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.foundationgames.automobility.util.AUtils;
import io.github.milkucha.momentum.MomentumBrakeState;
import io.github.milkucha.momentum.MomentumDriftState;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.network.ServerKeyState;
import net.minecraft.entity.Entity;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
    @Shadow public void boost(float power, int time) {}

    // Per-entity previous drift key state for rising/falling edge detection.
    // Instance field (not static) so client and integrated-server entities
    // each track their own edge independently.
    @Unique private boolean momentum$prevDriftKeyHeld = false;

    // ── Arcade (K-drift) state ─────────────────────────────────────────────
    @Unique private boolean momentum$prevArcadeDriftKeyHeld = false;
    @Unique private boolean momentum$kDriftActive = false;
    @Unique private float   momentum$kDriftOffset = 0f;   // current slip angle (degrees)
    @Unique private int     momentum$kDriftTimer  = 0;    // ticks drift has been active
    @Unique private int     momentum$kDriftDir    = 0;    // ±1
    @Unique private int     momentum$kHeldTimer   = 0;    // ticks drift key held without drift active

    // ── Responsive (M-drift) state ─────────────────────────────────────────
    @Unique private boolean momentum$prevResponsiveDriftKeyHeld = false;
    @Unique private boolean momentum$mDriftActive   = false;
    @Unique private float   momentum$mDriftOffset   = 0f;
    @Unique private int     momentum$mDriftTimer    = 0;   // ticks drift has been active
    @Unique private int     momentum$mDriftDir      = 0;
    @Unique private int     momentum$mHeldTimer     = 0;   // ticks drift key held without drift active
    @Unique private float   momentum$mSteerAccum    = 0f;  // 0..1 steering time accumulator

    // ── Key-state helpers (client vs. dedicated server) ───────────────────────
    //
    // On the client logical side we read the volatile statics set by KeyBinding polling.
    // On a dedicated server those statics are always false; instead we look up the
    // ServerKeyState map populated by the C2S KeyStatePacket.

    @Unique
    private UUID momentum$getRiderUuid() {
        Entity rider = ((Entity)(Object)this).getFirstPassenger();
        return rider != null ? rider.getUuid() : null;
    }

    @Unique private boolean momentum$brake() {
        if (((Entity)(Object)this).getWorld().isClient) return MomentumBrakeState.brakeHeld;
        UUID id = momentum$getRiderUuid(); return id != null && ServerKeyState.getBrake(id);
    }

    /** Raw drift key state — true if the Handbrake (drift) key is currently held. */
    @Unique private boolean momentum$driftKey() {
        if (((Entity)(Object)this).getWorld().isClient) return MomentumDriftState.driftKeyHeld;
        UUID id = momentum$getRiderUuid(); return id != null && ServerKeyState.getDrift(id);
    }

    /** Drift key held AND active profile is Vanilla Drift. */
    @Unique private boolean momentum$vanillaDriftKey() {
        return momentum$driftKey() && MomentumConfig.get().oDrift.profile == MomentumConfig.ODrift.Profile.VANILLA;
    }

    /** Drift key held AND active profile is Arcade Drift. */
    @Unique private boolean momentum$arcadeDriftKey() {
        return momentum$driftKey() && MomentumConfig.get().oDrift.profile == MomentumConfig.ODrift.Profile.ARCADE;
    }

    /** Drift key held AND active profile is Responsive Drift. */
    @Unique private boolean momentum$responsiveDriftKey() {
        return momentum$driftKey() && MomentumConfig.get().oDrift.profile == MomentumConfig.ODrift.Profile.RESPONSIVE;
    }

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
        if (!MomentumConfig.get().enabled) return AUtils.zero(value, 0.025f);
        if (!MomentumConfig.get().movement.enabled) return AUtils.zero(value, 0.025f);
        if (momentum$brake()) return value;  // brake inject handles decel this tick
        return AUtils.zero(value, MomentumConfig.get().movement.coastDecay);
    }

    // ── Acceleration scale ────────────────────────────────────────────────────

    /**
     * Bypasses Automobility's steering acceleration gate.
     *
     * movementTick() contains a ternary that suppresses normal acceleration when:
     *   (!drifting && steering != 0 && hSpeed > 0.5)
     * capping engineSpeed increment to 0.001 while cornering above ~36 km/h.
     *
     * Replacing the 0.5f threshold with Float.MAX_VALUE makes hSpeed > Float.MAX_VALUE
     * permanently false, so calculateAcceleration() is always called while steering.
     * The drift branch of the same ternary (drifting && haveSameSign) uses no float
     * constant and is unaffected.
     *
     * Momentum's understeer system already handles speed-based corner resistance,
     * so this gate is redundant and undesirable.
     */
    @ModifyConstant(
        method = "movementTick",
        constant = @Constant(doubleValue = 0.5)
    )
    private double momentum$removeSteeringAccelGate(double original) {
        if (!MomentumConfig.get().enabled) return original;
        if (!MomentumConfig.get().movement.enabled) return original;
        return Double.MAX_VALUE;
    }

    @ModifyArg(
        method = "movementTick",
        at = @At(
            value = "INVOKE",
            target = "Lio/github/foundationgames/automobility/entity/AutomobileEntity;calculateAcceleration(FLio/github/foundationgames/automobility/automobile/AutomobileStats;)F"
        ),
        index = 0
    )
    private float momentum$scaleAcceleration(float speed) {
        if (!MomentumConfig.get().enabled) return speed;
        if (!MomentumConfig.get().movement.enabled) return speed;
        return speed / MomentumConfig.get().movement.accelerationScale;
    }

    // ── Steering ramp rate ────────────────────────────────────────────────────

    /**
     * Replaces the 0.42f constant in steeringTick() with steering.rampRate from config.
     * This constant controls both how fast steering builds toward full lock and how
     * fast it returns to centre. Skipped during a drift to keep drift steering responsive.
     */
    @ModifyConstant(
        method = "steeringTick",
        constant = @Constant(floatValue = 0.42f)
    )
    private float momentum$steeringRampRate(float original) {
        if (!MomentumConfig.get().enabled) return original;
        if (!MomentumConfig.get().steering.enabled) return original;
        if (drifting) return original;
        if (steeringLeft || steeringRight) return MomentumConfig.get().steering.rampRate;
        return MomentumConfig.get().steering.centerRate;
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
        if (!MomentumConfig.get().enabled) return target;
        if (!MomentumConfig.get().steering.enabled) return target;
        if (drifting || momentum$kDriftActive || momentum$mDriftActive) return target;
        MomentumConfig.Steering s = MomentumConfig.get().steering;
        float speedCurved = (float) Math.pow(Math.abs(hSpeed), s.understeerCurve);
        return target / (1f + s.understeer * speedCurved);
    }

    // ── Vanilla Drift (transplanted from Automobility) ────────────────────────

    /**
     * Complete replacement of Automobility's driftingTick() using the Drift key
     * when profile == VANILLA.
     *
     * This is a direct transplant of the Automobility source logic with
     * holdingDrift/prevHoldDrift replaced by the Drift keybinding state.
     * Automobility's own driftingTick is cancelled so this is the sole
     * drift implementation.
     *
     * By writing directly to the shadowed drifting/driftDir/turboCharge fields,
     * all other Momentum mixins (understeer bypass, steering ramp bypass, brake
     * skip) continue to work correctly without modification.
     */
    @Inject(method = "driftingTick", at = @At("HEAD"), cancellable = true)
    private void momentum$vanillaDriftTick(CallbackInfo ci) {
        if (!MomentumConfig.get().enabled) return;
        ci.cancel();

        boolean driftHeld = momentum$vanillaDriftKey();
        boolean prevDrift = momentum$prevDriftKeyHeld;

        boolean mcOnGnd = ((net.minecraft.entity.Entity)(Object)this).isOnGround();

        if (drifting) {
            System.out.println("[Momentum-VanillaDrift] held | steer=" + steering
                + " hSpd=" + hSpeed
                + " autoOnGnd=" + automobileOnGround()
                + " wasOnGnd=" + wasOnGround
                + " mcOnGnd=" + mcOnGnd
                + " drifting=" + drifting + " prevDrift=" + prevDrift);
        }

        // Rising edge: drift key just pressed this tick
        if (!prevDrift && driftHeld) {
            boolean canDrift = steering != 0 && !drifting && hSpeed > 0.4f && mcOnGnd;
            System.out.println("[Momentum-VanillaDrift] RISING EDGE | canDrift=" + canDrift
                + " steer=" + steering + " hSpd=" + hSpeed
                + " mcOnGnd=" + mcOnGnd + " drifting=" + drifting);
            if (canDrift) {
                setDrifting(true);
                driftDir = steering > 0 ? 1 : -1;
                engineSpeed -= 0.028f * engineSpeed;
                System.out.println("[Momentum-VanillaDrift] DRIFT STARTED dir=" + driftDir);
            }
        }

        if (drifting) {
            if (mcOnGnd) createDriftParticles();

            if (prevDrift && !driftHeld) {
                // Falling edge: drift key released → end drift and grant turbo boost
                System.out.println("[Momentum-VanillaDrift] FALLING EDGE — consuming turbo charge=" + turboCharge);
                setDrifting(false);
                consumeTurboCharge();
            } else if (hSpeed < 0.33f) {
                // Too slow: drift cancelled, no boost
                System.out.println("[Momentum-VanillaDrift] DRIFT CANCELLED (too slow, hSpd=" + hSpeed + ")");
                setDrifting(false);
                turboCharge = 0;
            }

            if (mcOnGnd) {
                turboCharge += ((steeringLeft && driftDir < 0) || (steeringRight && driftDir > 0)) ? 2 : 1;
            }
        }

        momentum$prevDriftKeyHeld = driftHeld;
    }

    // ── Arcade Drift ──────────────────────────────────────────────────────────

    /**
     * Arcade Drift state machine — runs at HEAD of movementTick each tick.
     * Active when profile == ARCADE.
     *
     * Independent of Automobility's drifting/holdingDrift/turboCharge pipeline.
     * Reads the Drift keybinding state (polled from GLFW/KeyBinding in START_CLIENT_TICK).
     *
     * Rising edge + conditions → set kDriftActive, snap kDriftOffset to initial slip.
     * While drift held → converge offset to slipAngle; cancel if speed drops.
     * Drift released → fade offset to 0; grant boost if drift was sustained.
     *
     * The actual movement direction offset is applied in momentum$applyArcadeDriftSlip (RETURN inject).
     * Understeer is suppressed during Arcade Drift via the @ModifyArg above.
     */
    @Inject(method = "movementTick", at = @At("HEAD"))
    private void momentum$arcadeDriftStateMachine(CallbackInfo ci) {
        if (!MomentumConfig.get().enabled) return;
        boolean kHeld     = momentum$arcadeDriftKey();
        boolean prevKHeld = momentum$prevArcadeDriftKeyHeld;
        MomentumConfig.KDrift cfg = MomentumConfig.get().kDrift;

        boolean kMcOnGnd = ((net.minecraft.entity.Entity)(Object)this).isOnGround();

        if (kHeld) {
            System.out.println("[Momentum-ArcadeDrift] held | kActive=" + momentum$kDriftActive
                + " prevK=" + prevKHeld
                + " steer=" + steering
                + " hSpd=" + hSpeed
                + " onGnd=" + kMcOnGnd
                + " vanillaDrifting=" + drifting);
        }

        if (!momentum$kDriftActive) {
            if (kHeld) {
                momentum$kHeldTimer++;
                // Steering-based trigger: steering exceeds threshold, hold long enough, speed OK
                if (!drifting && Math.abs(steering) > cfg.steerThreshold
                        && momentum$kHeldTimer >= cfg.minHoldTicks
                        && hSpeed > cfg.minSpeedKmh / 72f && kMcOnGnd) {
                    momentum$kDriftActive = true;
                    momentum$kDriftDir    = steering > 0 ? 1 : -1;
                    momentum$kDriftTimer  = 0;
                    momentum$kDriftOffset = momentum$kDriftDir * cfg.slipAngle;
                    System.out.println("[Momentum-ArcadeDrift] DRIFT STARTED (steer) dir=" + momentum$kDriftDir
                        + " offset=" + momentum$kDriftOffset);
                }
                // Auto-trigger: random direction after holding long enough without drift starting
                else if (cfg.autoTriggerTicks > 0 && momentum$kHeldTimer >= cfg.autoTriggerTicks
                        && !drifting && hSpeed > cfg.minSpeedKmh / 72f && kMcOnGnd) {
                    momentum$kDriftActive = true;
                    momentum$kDriftDir    = (Math.random() < 0.5) ? 1 : -1;
                    momentum$kDriftTimer  = 0;
                    momentum$kDriftOffset = momentum$kDriftDir * cfg.slipAngle;
                    System.out.println("[Momentum-ArcadeDrift] DRIFT STARTED (auto) dir=" + momentum$kDriftDir);
                }
            } else {
                momentum$kHeldTimer = 0;
            }
        } else {
            // Drift is active — emit smoke particles every tick
            if (kMcOnGnd) createDriftParticles();

            if (kHeld) {
                // Maintain slip angle while drift held.
                // Use current steering direction so slip angle can be redirected mid-drift.
                momentum$kDriftTimer++;
                if (cfg.boostEnabled) {
                    int t = momentum$kDriftTimer, min = cfg.minTicks;
                    if      (t >= min + 40) turboCharge = AutomobileEntity.LARGE_TURBO_TIME + 1;
                    else if (t >= min + 20) turboCharge = AutomobileEntity.MEDIUM_TURBO_TIME + 1;
                    else if (t >= min)      turboCharge = AutomobileEntity.SMALL_TURBO_TIME + 1;
                    else                    turboCharge = 0;
                }
                int currentDir = steering > 0 ? 1 : (steering < 0 ? -1 : momentum$kDriftDir);
                float target = currentDir * cfg.slipAngle;
                momentum$kDriftOffset = AUtils.shift(momentum$kDriftOffset, cfg.slipConvergeRate, target);
                // Cancel drift if speed drops too low
                if (hSpeed < 0.3f) {
                    System.out.println("[Momentum-ArcadeDrift] CANCELLED (too slow, hSpd=" + hSpeed + ")");
                    momentum$kDriftActive = false;
                    momentum$kDriftTimer  = 0;
                    momentum$kDriftOffset = 0f;
                    momentum$kHeldTimer   = 0;
                    turboCharge = 0;
                }
            } else {
                // Drift released: fade slip angle back to zero (speed-adjusted)
                MomentumConfig.KDrift kCfg = MomentumConfig.get().kDrift;
                float kDecay = kCfg.slipDecaySpeedRef > 0
                    ? kCfg.slipDecay * kCfg.slipDecaySpeedRef / Math.max(0.1f, Math.abs(hSpeed))
                    : kCfg.slipDecay;
                momentum$kDriftOffset = AUtils.zero(momentum$kDriftOffset, kDecay);
                if (Math.abs(momentum$kDriftOffset) < 0.5f) {
                    if (kCfg.boostEnabled && momentum$kDriftTimer >= kCfg.minTicks) {
                        System.out.println("[Momentum-ArcadeDrift] BOOST GRANTED timer=" + momentum$kDriftTimer);
                        engineSpeed += kCfg.boost;
                        boost(0.23f, kCfg.boostDuration);
                    }
                    turboCharge = 0;
                    momentum$kDriftActive = false;
                    momentum$kDriftTimer  = 0;
                    momentum$kDriftOffset = 0f;
                }
            }
        }
        momentum$prevArcadeDriftKeyHeld = kHeld;
    }

    /**
     * Applies the Arcade Drift slip angle by rotating the entity's current velocity vector.
     * Runs at RETURN of movementTick, after setDeltaMovement has been called.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyArcadeDriftSlip(CallbackInfo ci) {
        if (!MomentumConfig.get().enabled) return;
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

    // ── Responsive Drift ──────────────────────────────────────────────────────

    /**
     * Responsive Drift state machine — runs at HEAD of movementTick.
     * Active when profile == RESPONSIVE.
     *
     * Drift held + steering≠0 → slip-angle arcade drift with steering-driven modulation.
     * Drift held + steering=0 → braking is applied in momentum$applyResponsiveBrake (RETURN inject).
     */
    @Inject(method = "movementTick", at = @At("HEAD"))
    private void momentum$responsiveDriftStateMachine(CallbackInfo ci) {
        if (!MomentumConfig.get().enabled) return;
        boolean mHeld    = momentum$responsiveDriftKey();
        boolean prevMHeld = momentum$prevResponsiveDriftKeyHeld;
        MomentumConfig.MDrift cfg = MomentumConfig.get().mDrift;
        boolean mMcOnGnd = ((net.minecraft.entity.Entity)(Object)this).isOnGround();

        if (mHeld) {
            System.out.println("[Momentum-ResponsiveDrift] held | mActive=" + momentum$mDriftActive
                + " prevM=" + prevMHeld
                + " steer=" + steering + " hSpd=" + hSpeed
                + " onGnd=" + mMcOnGnd + " vanillaDrifting=" + drifting);
        }

        if (!momentum$mDriftActive) {
            if (mHeld) {
                momentum$mHeldTimer++;
                float mMinSpd = cfg.minSpeedKmh / 72f;
                if (!drifting && Math.abs(steering) > cfg.steerThreshold && hSpeed > mMinSpd && mMcOnGnd
                        && momentum$mHeldTimer >= cfg.minHoldTicks) {
                    momentum$mDriftActive = true;
                    momentum$mDriftDir    = steering > 0 ? 1 : -1;
                    momentum$mDriftTimer  = 0;
                    momentum$mDriftOffset = 0f;
                    momentum$mHeldTimer   = 0;
                    System.out.println("[Momentum-ResponsiveDrift] DRIFT STARTED dir=" + momentum$mDriftDir
                        + " steer=" + steering + " hSpd=" + hSpeed);
                } else {
                    int threshold = cfg.autoTriggerTicks;
                    if (threshold > 0 && momentum$mHeldTimer >= threshold
                            && !drifting && hSpeed > mMinSpd && mMcOnGnd) {
                        int dir = Math.random() > 0.5 ? 1 : -1;
                        momentum$mDriftActive = true;
                        momentum$mDriftDir    = dir;
                        momentum$mDriftTimer  = 0;
                        momentum$mDriftOffset = 0f;
                        momentum$mHeldTimer   = 0;
                        System.out.println("[Momentum-ResponsiveDrift] AUTO-TRIGGER dir=" + dir
                            + " hSpd=" + hSpeed);
                    }
                }
            } else {
                momentum$mHeldTimer = 0;
            }
        } else {
            if (mMcOnGnd) createDriftParticles();

            if (mHeld) {
                momentum$mDriftTimer++;
                if (cfg.boostEnabled) {
                    int t = momentum$mDriftTimer, min = cfg.minTicks;
                    if      (t >= min + 40) turboCharge = AutomobileEntity.LARGE_TURBO_TIME + 1;
                    else if (t >= min + 20) turboCharge = AutomobileEntity.MEDIUM_TURBO_TIME + 1;
                    else if (t >= min)      turboCharge = AutomobileEntity.SMALL_TURBO_TIME + 1;
                    else                    turboCharge = 0;
                }
                if (Math.abs(steering) > 0.05f) {
                    momentum$mSteerAccum = Math.min(1.0f, momentum$mSteerAccum + cfg.steerBuildRate);
                } else {
                    momentum$mSteerAccum = Math.max(0.0f, momentum$mSteerAccum - cfg.steerDecayRate);
                }
                int currentDir = steering > 0 ? 1 : (steering < 0 ? -1 : momentum$mDriftDir);
                float steerFactor = cfg.constantAngle ? 1.0f
                    : (float) Math.pow(momentum$mSteerAccum, cfg.steerSensitivity);
                float target = currentDir * cfg.slipAngle * steerFactor;
                momentum$mDriftOffset += (target - momentum$mDriftOffset) * cfg.slipConvergeRate;
                if (hSpeed < 0.3f) {
                    System.out.println("[Momentum-ResponsiveDrift] CANCELLED (too slow, hSpd=" + hSpeed + ")");
                    turboCharge = 0;
                    momentum$mDriftActive = false;
                    momentum$mDriftTimer  = 0;
                    momentum$mDriftOffset = 0f;
                    momentum$mSteerAccum  = 0f;
                }
            } else {
                float mDecay = cfg.slipDecaySpeedRef > 0
                    ? cfg.slipDecay * cfg.slipDecaySpeedRef / Math.max(0.1f, Math.abs(hSpeed))
                    : cfg.slipDecay;
                momentum$mDriftOffset = AUtils.zero(momentum$mDriftOffset, mDecay);
                if (Math.abs(momentum$mDriftOffset) < 0.5f) {
                    if (cfg.boostEnabled && momentum$mDriftTimer >= cfg.minTicks) {
                        System.out.println("[Momentum-ResponsiveDrift] BOOST GRANTED timer=" + momentum$mDriftTimer);
                        engineSpeed += cfg.boost;
                        boost(0.23f, cfg.boostDuration);
                    }
                    turboCharge = 0;
                    momentum$mDriftActive = false;
                    momentum$mDriftTimer  = 0;
                    momentum$mDriftOffset = 0f;
                    momentum$mSteerAccum  = 0f;
                }
            }
        }
        momentum$prevResponsiveDriftKeyHeld = mHeld;
    }

    /**
     * Applies the Responsive Drift slip angle by rotating the velocity vector.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyResponsiveDriftSlip(CallbackInfo ci) {
        if (!MomentumConfig.get().enabled) return;
        if (!momentum$mDriftActive || Math.abs(momentum$mDriftOffset) < 0.01f) return;
        Entity self = (Entity)(Object)this;
        Vec3d mov = self.getVelocity();
        double rad = Math.toRadians(momentum$mDriftOffset);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        self.setVelocity(
            mov.x * cos - mov.z * sin,
            mov.y,
            mov.x * sin + mov.z * cos
        );
    }

    /**
     * Applies braking when Responsive Drift is held and no drift is active.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyResponsiveBrake(CallbackInfo ci) {
        if (!MomentumConfig.get().enabled) return;
        if (!MomentumConfig.get().movement.enabled) return;
        if (!MomentumConfig.get().mDrift.brakeEnabled) return;
        if (!momentum$responsiveDriftKey()) return;
        if (momentum$mDriftActive) return;
        if (drifting) return;
        float decay = MomentumConfig.get().movement.brakeDecay;
        engineSpeed = Math.max(engineSpeed - decay, -0.25f);
        System.out.println("[Momentum-ResponsiveBrake] braking | engSpd=" + engineSpeed
            + " hSpd=" + hSpeed + " steer=" + steering);
    }

    /**
     * Applies braking when Arcade Drift is held and no drift is active.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyArcadeBrake(CallbackInfo ci) {
        if (!MomentumConfig.get().enabled) return;
        if (!MomentumConfig.get().movement.enabled) return;
        if (!MomentumConfig.get().kDrift.brakeEnabled) return;
        if (!momentum$arcadeDriftKey()) return;
        if (momentum$kDriftActive) return;
        if (momentum$mDriftActive) return;
        if (drifting) return;
        float decay = MomentumConfig.get().movement.brakeDecay;
        engineSpeed = Math.max(engineSpeed - decay, -0.25f);
    }

    // ── Brake ────────────────────────────────────────────────────────────────

    /**
     * Intercepts the `back` parameter of provideClientInput and forces it to false
     * when Momentum is enabled.
     */
    @ModifyVariable(
        method = "provideClientInput",
        at = @At("HEAD"),
        index = 2,
        remap = false
    )
    private boolean momentum$suppressVanillaBackInput(boolean back) {
        if (!MomentumConfig.get().enabled) return back;
        if (!MomentumConfig.get().movement.enabled) return back;
        return false;
    }

    /**
     * Suppresses Automobility's own braking decay while Momentum is enabled.
     */
    @Redirect(
        method = "movementTick",
        at = @At(
            value = "INVOKE",
            target = "Ljava/lang/Math;max(FF)F",
            ordinal = 3
        )
    )
    private float momentum$suppressVanillaBrakeDecay(float a, float b) {
        if (!MomentumConfig.get().enabled) return Math.max(a, b);
        if (!MomentumConfig.get().movement.enabled) return Math.max(a, b);
        return engineSpeed;  // no-op: Momentum's @Inject at RETURN owns braking
    }

    /**
     * Applies linear braking directly to engineSpeed at the end of movementTick,
     * using MomentumBrakeState.brakeHeld as the source of truth.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyBrake(CallbackInfo ci) {
        if (!MomentumConfig.get().enabled) return;
        if (!MomentumConfig.get().movement.enabled) return;
        if (!momentum$brake()) return;
        if (drifting) return;  // braking reduces hSpeed which would cancel the drift
        float decay = MomentumConfig.get().movement.brakeDecay;
        engineSpeed = Math.max(engineSpeed - decay, -0.25f);
        System.out.println("[Momentum-Brake] braking | engSpd=" + engineSpeed
            + " hSpd=" + hSpeed + " steer=" + steering
            + " drifting=" + drifting);
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
    @Unique @Override public boolean momentum$isMDriftActive()      { return momentum$mDriftActive; }
    @Unique @Override public float   momentum$getMDriftOffset()     { return momentum$mDriftOffset; }
    @Unique @Override public float   momentum$getEngineSpeed()      { return engineSpeed; }
}
