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

    // Per-entity previous J-key state for rising/falling edge detection.
    // Instance field (not static) so client and integrated-server entities
    // each track their own edge independently.
    @Unique private boolean momentum$prevDriftKeyHeld = false;

    // ── N-drift state ─────────────────────────────────────────────────────────
    // Per-entity N-key drift state. nDriftArmed becomes true once the brake-phase
    // timer has elapsed and the drift has been triggered; used to own the drift
    // falling-edge (N released → end drift + grant boost).
    @Unique private boolean momentum$nDriftArmed  = false;
    @Unique private int     momentum$nBrakeTimer  = 0;

    // ── K-drift state ─────────────────────────────────────────────────────────
    // Per-entity previous K-key state — same pattern as momentum$prevDriftKeyHeld
    // (instance field so client and server entities track edges independently).
    @Unique private boolean momentum$prevKDriftKeyHeld = false;
    @Unique private boolean momentum$kDriftActive = false;
    @Unique private float   momentum$kDriftOffset = 0f;   // current slip angle (degrees)
    @Unique private int     momentum$kDriftTimer  = 0;    // ticks drift has been active
    @Unique private int     momentum$kDriftDir    = 0;    // ±1

    // ── M-drift state ─────────────────────────────────────────────────────────
    // M key: K-drift when steering≠0, brake when steering=0.
    @Unique private boolean momentum$prevMKeyHeld   = false;
    @Unique private boolean momentum$mDriftActive   = false;
    @Unique private float   momentum$mDriftOffset   = 0f;
    @Unique private int     momentum$mDriftTimer    = 0;   // ticks drift has been active
    @Unique private int     momentum$mDriftDir      = 0;
    @Unique private int     momentum$mHeldTimer     = 0;   // ticks M held without drift active
    @Unique private float   momentum$mSteerAccum    = 0f;  // 0..1 steering time accumulator

    // ── Key-state helpers (client vs. dedicated server) ───────────────────────
    //
    // On the client logical side we read the volatile statics set by GLFW polling.
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
    @Unique private boolean momentum$jKey() {
        if (((Entity)(Object)this).getWorld().isClient) return MomentumDriftState.driftKeyHeld;
        UUID id = momentum$getRiderUuid(); return id != null && ServerKeyState.getJ(id);
    }
    @Unique private boolean momentum$kKey() {
        if (((Entity)(Object)this).getWorld().isClient) return MomentumDriftState.kDriftKeyHeld;
        UUID id = momentum$getRiderUuid(); return id != null && ServerKeyState.getK(id);
    }
    @Unique private boolean momentum$nKey() {
        if (((Entity)(Object)this).getWorld().isClient) return MomentumDriftState.nDriftKeyHeld;
        UUID id = momentum$getRiderUuid(); return id != null && ServerKeyState.getN(id);
    }
    @Unique private boolean momentum$mKey() {
        if (((Entity)(Object)this).getWorld().isClient) return MomentumDriftState.mKeyHeld;
        UUID id = momentum$getRiderUuid(); return id != null && ServerKeyState.getM(id);
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
        if (momentum$brake()) return value;  // brake inject handles decel this tick
        return AUtils.zero(value, MomentumConfig.get().movement.coastDecay);
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
        if (drifting) return original;
        return MomentumConfig.get().steering.rampRate;
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
        if (drifting || momentum$kDriftActive || momentum$mDriftActive) return target;
        MomentumConfig.Steering s = MomentumConfig.get().steering;
        float speedCurved = (float) Math.pow(Math.abs(hSpeed), s.understeerCurve);
        return target / (1f + s.understeer * speedCurved);
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

        boolean jHeld  = momentum$jKey();
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
        boolean kHeld     = momentum$kKey();
        boolean prevKHeld = momentum$prevKDriftKeyHeld;
        MomentumConfig.KDrift cfg = MomentumConfig.get().kDrift;

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
                    momentum$kDriftOffset = momentum$kDriftDir * cfg.slipAngle;
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
            // K-drift is active — emit smoke particles every tick (matches J-drift behaviour)
            if (kMcOnGnd) createDriftParticles();

            if (kHeld) {
                // Maintain slip angle while K held.
                // Use current steering direction so slip angle can be redirected mid-drift.
                momentum$kDriftTimer++;
                int currentDir = steering > 0 ? 1 : (steering < 0 ? -1 : momentum$kDriftDir);
                float target = currentDir * cfg.slipAngle;
                momentum$kDriftOffset = AUtils.shift(momentum$kDriftOffset, cfg.slipConvergeRate, target);
                // Cancel drift if speed drops too low
                if (hSpeed < 0.3f) {
                    System.out.println("[Momentum-KDrift] CANCELLED (too slow, hSpd=" + hSpeed + ")");
                    momentum$kDriftActive = false;
                    momentum$kDriftTimer  = 0;
                    momentum$kDriftOffset = 0f;
                }
            } else {
                // K released: fade slip angle back to zero (speed-adjusted)
                MomentumConfig.KDrift kCfg = MomentumConfig.get().kDrift;
                float kDecay = kCfg.slipDecaySpeedRef > 0
                    ? kCfg.slipDecay * kCfg.slipDecaySpeedRef / Math.max(0.1f, Math.abs(hSpeed))
                    : kCfg.slipDecay;
                momentum$kDriftOffset = AUtils.zero(momentum$kDriftOffset, kDecay);
                if (Math.abs(momentum$kDriftOffset) < 0.5f) {
                    if (kCfg.boostEnabled && momentum$kDriftTimer >= kCfg.minTicks) {
                        System.out.println("[Momentum-KDrift] BOOST GRANTED timer=" + momentum$kDriftTimer);
                        engineSpeed += kCfg.boost;
                        boost(0.23f, 9);
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

    // ── N-drift ───────────────────────────────────────────────────────────────

    /**
     * N-key brake-then-drift — runs at HEAD of movementTick.
     *
     * Phase 1 (N held, not yet armed):
     *   Apply brakeDecay every tick. Increment nBrakeTimer.
     *   Once nBrakeTimer >= nDriftBrakeTicks AND drift conditions are met,
     *   trigger drift identical to J-drift rising edge and mark nDriftArmed.
     *
     * Phase 2 (N held, armed — drift active):
     *   Continue applying brakeDecay every tick.
     *   If drift was cancelled externally (too slow, via J-drift's hSpeed check),
     *   clear the armed flag so the next N press starts fresh.
     *
     * N released (armed):
     *   End drift and grant turbo boost — same as J-drift falling edge.
     *
     * N released (not armed):
     *   Just reset the timer; no-op.
     *
     * Particles and turboCharge increment are handled automatically by J-drift's
     * driftingTick inject (which fires whenever drifting == true, regardless of
     * which key triggered the drift).
     */
    @Inject(method = "movementTick", at = @At("HEAD"))
    private void momentum$nDriftTick(CallbackInfo ci) {
        boolean nHeld  = momentum$nKey();
        MomentumConfig cfg = MomentumConfig.get();
        boolean mcOnGnd = ((Entity)(Object)this).isOnGround();

        if (nHeld) {
            // Apply brakeDecay every tick N is held (both phases)
            engineSpeed = Math.max(engineSpeed - cfg.movement.brakeDecay, -0.25f);

            System.out.println("[Momentum-NDrift] N held | armed=" + momentum$nDriftArmed
                + " timer=" + momentum$nBrakeTimer + "/" + cfg.nDrift.brakeTicks
                + " steer=" + steering + " hSpd=" + hSpeed
                + " onGnd=" + mcOnGnd + " drifting=" + drifting
                + " engSpd=" + engineSpeed);

            if (!momentum$nDriftArmed) {
                // Phase 1: waiting for brake timer
                momentum$nBrakeTimer++;
                boolean canDrift = steering != 0 && !drifting && hSpeed > 0.4f && mcOnGnd;
                if (momentum$nBrakeTimer >= cfg.nDrift.brakeTicks && canDrift) {
                    setDrifting(true);
                    driftDir = steering > 0 ? 1 : -1;
                    engineSpeed -= 0.028f * engineSpeed;
                    momentum$nDriftArmed = true;
                    System.out.println("[Momentum-NDrift] DRIFT STARTED dir=" + driftDir
                        + " engSpd=" + engineSpeed);
                } else if (momentum$nBrakeTimer >= cfg.nDrift.brakeTicks) {
                    System.out.println("[Momentum-NDrift] TIMER MET but conditions not met — "
                        + "steer!=0:" + (steering != 0)
                        + " hSpd>0.4:" + (hSpeed > 0.4f)
                        + " onGnd:" + mcOnGnd
                        + " drifting:" + drifting);
                }
            } else {
                // Phase 2: drift is (or was) active
                if (!drifting) {
                    // Drift was cancelled externally (too slow) — reset for next press
                    System.out.println("[Momentum-NDrift] DRIFT CANCELLED externally — resetting");
                    momentum$nDriftArmed = false;
                    momentum$nBrakeTimer = 0;
                }
            }
        } else {
            // N released
            if (momentum$nDriftArmed && drifting) {
                System.out.println("[Momentum-NDrift] FALLING EDGE — consuming turboCharge=" + turboCharge);
                setDrifting(false);
                consumeTurboCharge();
            }
            momentum$nDriftArmed = false;
            momentum$nBrakeTimer = 0;
        }
    }

    // ── M-drift ───────────────────────────────────────────────────────────────

    /**
     * M-key combined drift/brake — state machine, runs at HEAD of movementTick.
     *
     * M held + steering≠0 → behaves exactly like K-drift (slip angle arcade drift).
     * M held + steering=0 → braking is applied in momentum$applyMBrake (RETURN inject).
     *
     * Once a drift starts it is maintained regardless of steering, until M is released
     * or speed drops below threshold — matching K-drift behaviour.
     */
    @Inject(method = "movementTick", at = @At("HEAD"))
    private void momentum$mDriftStateMachine(CallbackInfo ci) {
        boolean mHeld    = momentum$mKey();
        boolean prevMHeld = momentum$prevMKeyHeld;
        MomentumConfig.MDrift cfg = MomentumConfig.get().mDrift;
        boolean mMcOnGnd = ((Entity)(Object)this).isOnGround();

        if (mHeld) {
            System.out.println("[Momentum-MDrift] M held | mActive=" + momentum$mDriftActive
                + " prevM=" + prevMHeld
                + " steer=" + steering + " hSpd=" + hSpeed
                + " onGnd=" + mMcOnGnd + " jDrifting=" + drifting);
        }

        if (!momentum$mDriftActive) {
            if (mHeld) {
                momentum$mHeldTimer++;
                float mMinSpd = cfg.minSpeedKmh / 72f;
                if (!drifting && Math.abs(steering) > cfg.steerThreshold && hSpeed > mMinSpd && mMcOnGnd
                        && momentum$mHeldTimer >= cfg.minHoldTicks) {
                    // Steering detected and held long enough — start drift in steering direction
                    momentum$mDriftActive = true;
                    momentum$mDriftDir    = steering > 0 ? 1 : -1;
                    momentum$mDriftTimer  = 0;
                    float initSteerFactor = cfg.constantAngle ? 1.0f
                        : (float) Math.pow(Math.abs(steering), cfg.steerSensitivity);
                    momentum$mDriftOffset = momentum$mDriftDir * cfg.slipAngle * initSteerFactor;
                    momentum$mHeldTimer   = 0;
                    System.out.println("[Momentum-MDrift] DRIFT STARTED dir=" + momentum$mDriftDir
                        + " offset=" + momentum$mDriftOffset
                        + " steer=" + steering + " hSpd=" + hSpeed);
                } else {
                    // Conditions not yet met — check auto-trigger threshold
                    int threshold = cfg.autoTriggerTicks;
                    if (threshold > 0 && momentum$mHeldTimer >= threshold
                            && !drifting && hSpeed > mMinSpd && mMcOnGnd) {
                        int dir = Math.random() > 0.5 ? 1 : -1;
                        momentum$mDriftActive = true;
                        momentum$mDriftDir    = dir;
                        momentum$mDriftTimer  = 0;
                        momentum$mDriftOffset = dir * cfg.slipAngle;
                        momentum$mHeldTimer   = 0;
                        System.out.println("[Momentum-MDrift] AUTO-TRIGGER dir=" + dir
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
                // Advance or retreat the steering accumulator based on whether player is steering.
                if (Math.abs(steering) > 0.05f) {
                    momentum$mSteerAccum = Math.min(1.0f, momentum$mSteerAccum + cfg.steerBuildRate);
                } else {
                    momentum$mSteerAccum = Math.max(0.0f, momentum$mSteerAccum - cfg.steerDecayRate);
                }
                // Use current steering direction for target — allows redirecting slip mid-drift.
                // Fall back to locked mDriftDir only when steering is exactly 0.
                int currentDir = steering > 0 ? 1 : (steering < 0 ? -1 : momentum$mDriftDir);
                float steerFactor = cfg.constantAngle ? 1.0f
                    : (float) Math.pow(momentum$mSteerAccum, cfg.steerSensitivity);
                float target = currentDir * cfg.slipAngle * steerFactor;
                momentum$mDriftOffset = AUtils.shift(momentum$mDriftOffset, cfg.slipConvergeRate, target);
                if (hSpeed < 0.3f) {
                    System.out.println("[Momentum-MDrift] CANCELLED (too slow, hSpd=" + hSpeed + ")");
                    momentum$mDriftActive = false;
                    momentum$mDriftTimer  = 0;
                    momentum$mDriftOffset = 0f;
                    momentum$mSteerAccum  = 0f;
                }
            } else {
                // M released: fade slip angle back to zero, grant boost if sustained (speed-adjusted)
                float mDecay = cfg.slipDecaySpeedRef > 0
                    ? cfg.slipDecay * cfg.slipDecaySpeedRef / Math.max(0.1f, Math.abs(hSpeed))
                    : cfg.slipDecay;
                momentum$mDriftOffset = AUtils.zero(momentum$mDriftOffset, mDecay);
                if (Math.abs(momentum$mDriftOffset) < 0.5f) {
                    if (cfg.boostEnabled && momentum$mDriftTimer >= cfg.minTicks) {
                        System.out.println("[Momentum-MDrift] BOOST GRANTED timer=" + momentum$mDriftTimer);
                        engineSpeed += cfg.boost;
                        boost(0.23f, 9);
                    }
                    momentum$mDriftActive = false;
                    momentum$mDriftTimer  = 0;
                    momentum$mDriftOffset = 0f;
                    momentum$mSteerAccum  = 0f;
                }
            }
        }
        momentum$prevMKeyHeld = mHeld;
    }

    /**
     * Applies the M-drift slip angle by rotating the velocity vector.
     * Identical to K-drift slip, just using M-drift state.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyMDriftSlip(CallbackInfo ci) {
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
     * Applies braking when M is held and no drift is active (steering was 0 on press).
     * Skipped if mDriftActive or any other drift is running.
     */
    @Inject(method = "movementTick", at = @At("RETURN"))
    private void momentum$applyMBrake(CallbackInfo ci) {
        if (!momentum$mKey()) return;
        if (momentum$mDriftActive) return;
        if (drifting) return;
        float decay = MomentumConfig.get().movement.brakeDecay;
        engineSpeed = Math.max(engineSpeed - decay, -0.25f);
        System.out.println("[Momentum-MBrake] braking | engSpd=" + engineSpeed
            + " hSpd=" + hSpeed + " steer=" + steering);
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
        if (!momentum$brake()) return;
        if (drifting) return;  // braking reduces hSpeed which would cancel the drift
        float decay = MomentumConfig.get().movement.brakeDecay;
        engineSpeed = Math.max(engineSpeed - decay, -0.25f);
        System.out.println("[Momentum-Brake] Space braking | engSpd=" + engineSpeed
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
