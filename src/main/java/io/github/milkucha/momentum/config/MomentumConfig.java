package io.github.milkucha.momentum.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MomentumConfig {

    public Movement movement = new Movement();
    public Steering steering = new Steering();
    public Camera  camera   = new Camera();
    public Hud     hud      = new Hud();
    public BarHud  barHud   = new BarHud();
    public KDrift  kDrift   = new KDrift();
    public MDrift  mDrift   = new MDrift();
    public NDrift  nDrift   = new NDrift();

    // ── Groups ────────────────────────────────────────────────────────────────

    public static class Movement {
        public float coastDecay               = 0.009f;
        public float accelerationScale        = 3.7f;
        public float brakeDecay               = 0.03f;
        public float comfortableSpeedMultiplier = 1.9f;
    }

    public static class Steering {
        public float rampRate       = 0.12f;
        public float understeer     = 3.0f;
        public float understeerCurve = 2.0f;
    }

    public static class Camera {
        public boolean lock               = true;
        public float   pitch              = 10f;
        public float   brakeZoomFov       = 10f;   // max FOV reduction clamp (degrees)
        // Spring-damper brake zoom: deceleration (hSpeed delta/tick) drives a mass-spring camera.
        // When the vehicle stops, accumulated velocity carries the zoom briefly — inertia feel.
        public float   brakeZoomInputScale = 30f;  // decel units → zoom force multiplier
        public float   brakeZoomSpring     = 0.06f; // spring constant (return-to-zero pull)
        public float   brakeZoomDamping    = 0.90f; // velocity decay per tick (0=none,1=freeze)
        public boolean driftCamera   = true;   // enable camera yaw offset during K/M drift
        public float   driftScale    = 3.0f;
        public float   driftLerpIn   = 0.05f;
        public float   driftLerpOut  = 0.15f;
    }

    public static class Hud {
        // Set to true to use the bar-based HUD instead of the texture-based one.
        public boolean useBarHud       = false;
        public int   x                = -1;
        public int   y                = -1;
        public int   marginRight      = 230;
        public int   marginBottom     = 29;
        public int   barOffsetX       = 22;
        public int   barOffsetY       = 10;
        public float barScale         = 1.0f;
        public int   animOffsetX      = 4;
        public int   animOffsetY      = 5;
        public float animScale        = 1.0f;
        public int   speedTextOffsetX = 30;
        public int   speedTextOffsetY = 0;
        public int   debugX           = -1;
        public int   debugY           = 10;
        public int   debugMarginRight = 10;
        public boolean debug          = true;
    }

    public static class BarHud {
        // Position. -1 = anchor to right/bottom edge using the margin fields.
        public int   x            = -1;
        public int   y            = -1;
        public int   marginRight  = 10;
        public int   marginBottom = 50;

        // Overall size of the velocimeter area in pixels.
        public int   totalWidth   = 210;
        public int   totalHeight  = 30;

        // Size of each individual bar segment and the gap between them.
        // numBars = floor((totalWidth + barSpacing) / (barWidth + barSpacing))
        public int   barWidth     = 3;
        public int   barSpacing   = 2;

        // Speed (km/h) at which all bar segments are filled.
        public float maxSpeedKmh  = 200.0f;

        // ARGB color of filled bar segments (e.g. 0xFFFFFFFF = opaque white).
        public int   barColor     = 0xFFFFFFFF;
        // ARGB color of bar segments that represent the boost contribution (hSpeed - engineSpeed).
        // These segments sit above the normal bars and revert to barColor when boost ends.
        public int   boostBarColor = 0xFFFF3333;

        // Speed text position relative to the bar's top-left corner.
        // Negative textOffsetY places the text above the bar.
        public int   textOffsetX  = 0;
        public int   textOffsetY  = -10;
        // ARGB color of the speed text.
        public int   textColor    = 0xFFFFFFFF;
    }

    public static class KDrift {
        public float   slipAngle         = 3f;
        public float   slipConvergeRate  = 4f;    // deg/tick the offset converges toward target while held
        public float   slipDecay         = 1.3f;
        public float   slipDecaySpeedRef = 0.6f;
        public float   boost             = 0.04f;
        public int     minTicks          = 15;
        public boolean boostEnabled      = true;
    }

    public static class MDrift {
        public float   slipAngle         = 5f;
        public float   slipConvergeRate  = 4f;    // deg/tick the offset converges toward target while held
        public float   slipDecay         = 2.1f;  // fade-out rate after M released
        public float   slipDecaySpeedRef = 0.6f;  // reference speed for speed-adjusted decay
        public float   boost             = 0.04f; // engine speed bonus on clean release
        public int     minTicks          = 15;    // minimum ticks held to earn boost
        public float   steerSensitivity  = 2.0f;
        // How fast the steering accumulator (0..1) climbs per tick while steering is held.
        // 1 / steerBuildRate = ticks to reach max angle from zero.
        // 0.02 = 50 ticks (~2.5 s)  ← default
        public float   steerBuildRate    = 0.0001f;
        // How fast the accumulator falls per tick when steering is released mid-drift.
        // 0.04 = 25 ticks (~1.25 s) to fully release  ← default
        public float   steerDecayRate    = 0.04f;
        public boolean constantAngle     = false;
        public int     minHoldTicks      = 0;
        public int     autoTriggerTicks  = 25;
        public float   steerThreshold    = 0.9f;
        public float   minSpeedKmh       = 45.0f;
        public boolean boostEnabled      = false;
    }

    public static class NDrift {
        public int brakeTicks = 15;
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("momentum.json");

    private static MomentumConfig instance;

    public static MomentumConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static void reload() {
        instance = load();
    }

    public static MomentumConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                MomentumConfig loaded = GSON.fromJson(json, MomentumConfig.class);
                if (loaded != null) {
                    // Ensure nested objects are never null (old flat config won't have them)
                    if (loaded.movement == null) loaded.movement = new Movement();
                    if (loaded.steering == null) loaded.steering = new Steering();
                    if (loaded.camera   == null) loaded.camera   = new Camera();
                    if (loaded.hud      == null) loaded.hud      = new Hud();
                    if (loaded.barHud   == null) loaded.barHud   = new BarHud();
                    if (loaded.kDrift   == null) loaded.kDrift   = new KDrift();
                    if (loaded.mDrift   == null) loaded.mDrift   = new MDrift();
                    if (loaded.nDrift   == null) loaded.nDrift   = new NDrift();
                    loaded.save();
                    return loaded;
                }
            } catch (IOException e) {
                System.err.println("[Momentum] Failed to read config, using defaults: " + e.getMessage());
            }
        }
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
