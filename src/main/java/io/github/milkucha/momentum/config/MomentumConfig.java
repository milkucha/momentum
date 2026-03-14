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
    public float coastDecay = 0.008f;

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
    public float accelerationScale = 0.85f;

    /**
     * Fraction of engineSpeed removed per tick while braking (Space key held).
     * Braking is MULTIPLICATIVE: each tick, engineSpeed *= (1 - brakeDecay).
     * This means deceleration is proportional to current speed — strong at high speed,
     * tapering naturally at low speed — so a brief press reduces speed by a fraction
     * rather than driving it abruptly to zero. Floor is 0 (Space never pushes into reverse).
     *
     * Lower = softer, more gradual braking.
     * Higher = sharper initial speed drop per tick.
     *
     * Recommended range: 0.05 – 0.25
     *   0.25 → aggressive: ~25% speed drop per tick
     *   0.10 → moderate: ~10% per tick  ← default
     *   0.05 → gentle: ~5% per tick
     */
    public float brakeDecay = 0.10f;

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
     * Default: 10
     */
    public int hudMarginRight = 10;

    /**
     * Distance from the bottom edge of the screen when hudY is -1 (bottom-anchored).
     * Ignored if hudY is set to a positive value.
     *
     * Default: 10
     */
    public int hudMarginBottom = 10;

    /**
     * When true, draws a small debug overlay above the speedometer showing
     * raw steering (-1..1), hSpeed, and drifting state.
     * Useful for tuning steeringRampRate and steeringUndersteer.
     *
     * Default: false
     */
    public boolean debugHud = false;

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
