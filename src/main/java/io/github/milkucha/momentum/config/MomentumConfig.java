package io.github.milkucha.momentum.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MomentumConfig — a simple JSON config file written to .minecraft/config/momentum.json
 *
 * All fields have sensible defaults. The file is created automatically on first run.
 * Edit it with any text editor and restart the game (or reload via F3+T) to apply changes.
 */
public class MomentumConfig {

    // ── Movement ─────────────────────────────────────────────────────────────

    /**
     * How much engineSpeed is reduced per tick when coasting (not accelerating, not braking).
     * Automobility's original value is 0.025, which stops the car in ~2 ticks — very abrupt.
     *
     * Lower = more momentum, car rolls further before stopping.
     * Higher = quicker stop (approaching vanilla feel).
     *
     * Recommended range: 0.002 – 0.025
     *   0.004 → stops in ~12s at full speed
     *   0.008 → stops in ~6s at full speed  ← default
     *   0.012 → stops in ~4s at full speed
     *   0.025 → original Automobility behaviour (~2s)
     */
    public float coastDecay = 0.009f;

    /**
     * Scales the acceleration curve's top-end resistance.
     * Automobility's calculateAcceleration() already produces a curve, but this
     * multiplier lets you push back how quickly the car approaches comfortable speed.
     *
     * 1.0 = no change (vanilla Automobility acceleration)
     * Values < 1.0 make the car feel like it hits a "wall" sooner near top speed.
     * Values > 1.0 make acceleration feel more linear/punchy all the way to top speed.
     *
     * Recommended range: 0.6 – 1.4
     */
    public float accelerationScale = 3.7f;

    /**
     * Amount of engineSpeed removed per tick while braking (Space key held).
     * Braking is LINEAR: each tick, engineSpeed -= brakeDecay (floored at -0.25).
     * Constant deceleration mirrors real friction braking — no asymptotic tail near zero.
     * Holding Space long enough will push through zero into reverse.
     *
     * Lower = softer, more gradual braking.
     * Higher = sharper, more aggressive braking.
     *
     * Recommended range: 0.005 – 0.05
     *   0.005 → very gentle (~5s to stop from full speed)
     *   0.012 → moderate (~2.5s to stop from full speed)  ← default
     *   0.03  → aggressive (~1s to stop from full speed)
     */
    public float brakeDecay = 0.03f;

    // ── Steering ─────────────────────────────────────────────────────────────

    /**
     * How fast the steering value ramps toward full lock (±1) per tick when you hold left/right.
     * Also controls how fast it returns to centre when you release.
     * Automobility's original value is 0.42, reaching full lock in ~2.4 ticks (~120ms) — very snappy.
     *
     * Lower = slower, more car-like steering. Higher = more arcade/horse-like.
     * Does NOT apply during a drift (drift steering remains at original speed).
     *
     * Recommended range: 0.08 – 0.42
     *   0.42 → original Automobility behaviour
     *   0.12 → ~8 ticks to full lock (~400ms)  ← default
     *   0.08 → ~12 ticks to full lock (~600ms)
     */
    public float steeringRampRate = 0.12f;

    /**
     * Controls how much high speed reduces steering authority (understeer).
     * Applied by scaling the angularSpeed TARGET inside Automobility's AUtils.shift:
     *   effective_target = steering_target / (1 + steeringUndersteer * hSpeed^steeringUndersteerCurve)
     *
     * 0.0 = no understeer (vanilla feel, sharp turns at any speed)
     * Higher = more understeer (curves widen at high speed, like a real car).
     * Does NOT apply during a drift.
     *
     * With curve=2.0, approximate effect at hSpd=1.0 vs hSpd=0.3:
     *   1.0  → subtle (~1.5x wider circles at top speed vs slow)
     *   3.0  → moderate (~3x ratio)  ← default
     *   5.0  → strong (~5x ratio, high-speed corners feel very wide)
     *   10.0 → extreme (nearly straight-line at top speed)
     *
     * Recommended range: 0.0 – 10.0
     */
    public float steeringUndersteer = 3.0f;

    /**
     * Exponent applied to hSpeed before the understeer formula.
     * Controls the SHAPE of the understeer curve, not its overall strength.
     *
     * 1.0 = linear (understeer kicks in noticeably even at low speeds)
     * 2.0 = quadratic (barely noticeable below hSpd~0.3, ramps up strongly above) ← default
     * 3.0 = cubic (effect concentrated at very high speeds only)
     *
     * Recommended range: 1.0 – 3.0
     */
    public float steeringUndersteerCurve = 2.0f;

    /**
     * Multiplier applied to each car's comfortable speed threshold.
     * Above this threshold, Automobility drops acceleration to 25% of normal —
     * it's the point where the car feels like it's "hitting a wall".
     *
     * 1.0 = vanilla (no change)
     * 1.5 = threshold 50% higher (e.g. 60 km/h → 90 km/h)  ← default
     * 2.0 = threshold doubled (e.g. 60 km/h → 120 km/h)
     *
     * Also scales the boost top-up speed and off-road speed cap by the same factor.
     *
     * Recommended range: 1.0 – 3.0
     */
    public float comfortableSpeedMultiplier = 1.55f;

    // ── Camera ───────────────────────────────────────────────────────────────

    /**
     * When true, the camera is locked to face the front of the car.
     * The player cannot look around while driving.
     *
     * Default: true
     */
    public boolean lockCamera = true;

    /**
     * Pitch angle (degrees) the camera is locked to while driving.
     * 0 = horizontal, positive = looking down.
     *
     * Default: 10 (slight downward look, like a driver's eye view)
     */
    public float lockCameraPitch = 10f;

    /**
     * How many FOV degrees to zoom in when the brake key is first pressed.
     * The zoom is applied once on the rising edge and held while braking,
     * then lerps back to 0 on release.
     *
     * Positive = zoom in (narrower FOV). Negative = zoom out.
     * Recommended range: 2 – 10
     *
     * Default: 5
     */
    public float brakeZoomFov = 10f;

    /**
     * Lerp factor for the brake zoom FOV offset, applied each tick for both
     * zoom-in (brake pressed) and zoom-out (brake released).
     * offset += (target - offset) * brakeZoomLerp
     *
     * Lower = smoother transition. Higher = snappier.
     * 1.0 = instant.
     *
     * Recommended range: 0.05 – 0.5
     *   0.1  → smooth (~16 ticks)  ← default
     *   0.3  → snappy (~5 ticks)
     */
    public float brakeZoomLerp = 0.3f;

    // ── HUD ──────────────────────────────────────────────────────────────────

    /**
     * Horizontal position of the HUD panel, measured in pixels from the LEFT edge of the screen.
     * Set to -1 to anchor to the RIGHT edge instead (using hudMarginRight as the offset).
     *
     * Default: -1 (right-anchored)
     */
    public int hudX = -1;

    /**
     * Vertical position of the HUD panel, measured in pixels from the TOP edge of the screen.
     * Set to -1 to anchor to the BOTTOM edge instead (using hudMarginBottom as the offset).
     *
     * Default: -1 (bottom-anchored)
     */
    public int hudY = -1;

    /**
     * Distance from the right edge of the screen when hudX is -1 (right-anchored).
     * Ignored if hudX is set to a positive value.
     *
     * Default: 230
     */
    public int hudMarginRight = 230;

    /**
     * Distance from the bottom edge of the screen when hudY is -1 (bottom-anchored).
     * Ignored if hudY is set to a positive value.
     *
     * Default: 29
     */
    public int hudMarginBottom = 29;

    /**
     * Pixel offset of the bar element from the panel top-left corner.
     *
     * Default: 22, 10
     */
    public int hudBarOffsetX = 22;
    public int hudBarOffsetY = 10;

    /**
     * Scale multiplier for the bar texture. 1.0 = native size.
     *
     * Default: 1.0
     */
    public float hudBarScale = 1.0f;

    /**
     * Pixel offset of the animated object from the panel top-left corner.
     *
     * Default: 4, 5
     */
    public int hudAnimOffsetX = 4;
    public int hudAnimOffsetY = 5;

    /**
     * Scale multiplier for the animated object texture. 1.0 = native size.
     *
     * Default: 1.0
     */
    public float hudAnimScale = 1.0f;

    /**
     * Pixel offset of the speed text (km/h readout) from the panel top-left corner.
     *
     * Default: 30, 0
     */
    public int hudSpeedTextOffsetX = 30;
    public int hudSpeedTextOffsetY = 0;

    /**
     * Position of the debug overlay panel.
     * Set debugHudX to -1 to anchor to the RIGHT edge (uses debugHudMarginRight).
     * debugHudY is always measured from the TOP edge.
     *
     * Default: top-right corner
     */
    public int debugHudX = -1;
    public int debugHudY = 10;
    public int debugHudMarginRight = 10;

    /**
     * When true, draws a small debug overlay above the speedometer showing
     * raw steering (-1..1), hSpeed, and drifting state.
     * Useful for tuning steeringRampRate and steeringUndersteer.
     *
     * Default: false
     */
    public boolean debugHud = true;

    // ── K-Drift ───────────────────────────────────────────────────────────────

    /**
     * Maximum slip angle (degrees) applied to the movement vector during K-drift.
     * Larger values = more dramatic sideways slide.
     *
     * Recommended range: 10 – 40
     */
    public float kDriftSlipAngle = 30f;

    /**
     * Base decay rate: degrees per tick the slip angle fades back to zero after
     * K/M is released, measured at kDriftSlipDecaySpeedRef.
     * Lower = longer, floaty tail. Higher = snappier grip recovery.
     *
     * Recommended range: 1.0 – 5.0
     */
    public float kDriftSlipDecay = 2.1f;

    /**
     * Reference speed (hSpeed units) at which kDriftSlipDecay applies exactly.
     * At lower speeds the decay is proportionally faster (shorter tail);
     * at higher speeds it is proportionally slower (longer tail).
     *
     * Formula: effectiveDecay = kDriftSlipDecay * kDriftSlipDecaySpeedRef / max(|hSpeed|, 0.1)
     *
     * Example with default 0.6f:
     *   hSpeed = 0.3  → decay × 2.0  (exits drift twice as fast)
     *   hSpeed = 0.6  → decay × 1.0  (nominal — matches kDriftSlipDecay exactly)
     *   hSpeed = 1.2  → decay × 0.5  (exits drift twice as slowly)
     *
     * Set to 0 to disable speed scaling and always use kDriftSlipDecay as-is.
     *
     * Default: 0.6
     */
    public float kDriftSlipDecaySpeedRef = 0.6f;

    /**
     * engineSpeed bonus applied when K is released after a sustained drift.
     * Only granted if the drift lasted at least kDriftMinTicks ticks.
     *
     * Recommended range: 0.01 – 0.08
     */
    public float kDriftBoost = 0.04f;

    /**
     * Minimum number of ticks K must be held (while drifting) to earn the boost.
     *
     * Default: 15 (~0.75 s)
     */
    public int kDriftMinTicks = 15;

    // ── M-Drift ───────────────────────────────────────────────────────────────

    /**
     * Maximum slip angle (degrees) for M-drift. Independent of K-drift's kDriftSlipAngle.
     *
     * Recommended range: 10 – 40
     * Default: 22
     */
    public float mDriftSlipAngle = 22f;

    /**
     * Degrees per tick the slip angle converges toward its target while M is held.
     * Higher = snappier, reaches full angle faster.
     * Lower = slower build-up, more gradual slide.
     *
     * Recommended range: 1.0 – 8.0
     * Default: 4.0
     */
    public float mDriftSlipConvergeRate = 4f;

    /**
     * Exponent applied to |steering| when computing the target slip angle.
     * Controls how easy it is to reach the maximum angle with partial steering input.
     *
     * Formula: steerFactor = |steering| ^ mDriftSteerSensitivity
     *
     * 0.5 = square root  — half-lock steering already gives ~70% of max angle (easy)
     * 1.0 = linear       — half-lock gives exactly 50% of max angle  ← default
     * 2.0 = quadratic    — need ~70% lock to reach 50% of max angle (harder)
     *
     * Only applies when mDriftConstantAngle = false.
     *
     * Recommended range: 0.3 – 2.0
     * Default: 1.0
     */
    public float mDriftSteerSensitivity = 2.0f;

    /**
     * When true, the M-drift slip angle is a constant kDriftSlipAngle regardless of steering input.
     * When false (default), the slip angle scales with |steering| — more steering = wider angle,
     * releasing steering = angle converges back toward 0 (stabilises).
     *
     * Set to true to restore the old constant-angle behaviour for testing/comparison.
     *
     * Default: false
     */
    public boolean mDriftConstantAngle = false;

    /**
     * Minimum number of ticks M must be held before a drift can trigger (manual or auto).
     * This acts as a deliberate "charge" window — short taps are ignored.
     *
     * 0  = instant (no hold required, old behaviour)
     * 5  = ~0.25 s  ← default
     * 10 = ~0.5 s
     *
     * Default: 5
     */
    public int mDriftMinHoldTicks = 0;

    /**
     * Ticks M must be held (while braking, no drift active) before a drift is
     * auto-triggered in a random direction.
     * Set to 0 to disable.
     *
     * Default: 30 (~1.5 s)
     */
    public int mDriftAutoTriggerTicks = 25;

    /**
     * Steering dead zone for M-drift. Drift only starts when |steering| exceeds this value.
     * Steering ranges from -1.0 (full left) to 1.0 (full right).
     *
     * 0.0  = any steering input triggers drift (no dead zone)
     * 0.15 = ignore small inputs, require intentional turn  ← default
     * 0.5  = only trigger at half-lock or more
     *
     * Recommended range: 0.0 – 0.5
     */
    public float mDriftSteerThreshold = 0.9f;

    /**
     * Minimum speed in km/h required for M-drift to start.
     * Below this speed the drift trigger is suppressed (braking continues).
     *
     * Default: 60.0 km/h
     */
    public float mDriftMinSpeedKmh = 45.0f;

    /**
     * When true, releasing M after a sustained drift grants the engineSpeed + turbo boost.
     * Set to false to disable all post-drift boost for M-drift.
     *
     * Default: true
     */
    public boolean mDriftBoostEnabled = false;

    /**
     * How much of the K-drift slip angle is translated into a camera yaw offset.
     * The camera is shifted by (kDriftOffset * kDriftCameraScale) degrees from the car heading,
     * revealing the car's angled body mid-drift. Smoothly lerps in and out.
     *
     * 0.0 = no camera shift (pure locked camera)
     * 0.5 = camera shifts half the slip angle (e.g. 11° at full 22° drift)  ← default
     * 1.0 = camera shifts by the full slip angle
     * Negative values flip the direction if the shift feels backwards.
     *
     * Recommended range: 0.3 – 0.8
     */
    public float kDriftCameraScale = 3.0f;

    /**
     * Lerp factor when the camera yaw offset is moving TOWARD the drift angle (K pressed).
     * Applied each tick: offset += (target - offset) * kDriftCameraLerpIn
     * Keep this low for a slow, dramatic camera swing into the drift.
     *
     * Lower = slower / more lag. Higher = snappier.
     * 1.0 = instant.
     *
     * Recommended range: 0.03 – 0.2
     *   0.05 → very slow (~40 ticks to settle)  ← default
     *   0.15 → moderate (~11 ticks)
     */
    public float kDriftCameraLerpIn = 0.05f;

    /**
     * Lerp factor when the camera yaw offset is snapping BACK to zero (K released).
     * Keep this high for a quick, snappy return to the car heading on drift exit.
     *
     * Lower = slower return. Higher = snappier return.
     * 1.0 = instant.
     *
     * Recommended range: 0.1 – 0.6
     *   0.3  → snappy (~5 ticks)  ← default
     *   0.15 → moderate (~11 ticks)
     */
    public float kDriftCameraLerpOut = 0.15f;

    // ── N-Drift ───────────────────────────────────────────────────────────────

    /**
     * Number of ticks the N key must be held (braking) before the drift is triggered.
     * While this timer is counting, brakeDecay is applied to engineSpeed each tick.
     * Once the threshold is reached and steering conditions are met, the drift starts
     * (identical to J-drift) and braking continues throughout.
     *
     * Default: 15 (~0.75 s)
     */
    public int nDriftBrakeTicks = 15;

    // ── Serialisation ─────────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("momentum.json");

    private static MomentumConfig instance;

    public static MomentumConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static void reload() {
        instance = load();
    }

    /**
     * Loads config from disk, or creates a default one if the file doesn't exist yet.
     */
    public static MomentumConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                MomentumConfig loaded = GSON.fromJson(json, MomentumConfig.class);
                if (loaded != null) {
                    // Re-save to pick up any new fields added in a newer version of the mod
                    loaded.save();
                    return loaded;
                }
            } catch (IOException e) {
                System.err.println("[Momentum] Failed to read config, using defaults: " + e.getMessage());
            }
        }

        // First run — write defaults to disk so the user can see and edit them
        MomentumConfig defaults = new MomentumConfig();
        defaults.save();
        return defaults;
    }

    public void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[Momentum] Failed to save config: " + e.getMessage());
        }
    }
}
