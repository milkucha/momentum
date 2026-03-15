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
    public KDrift  kDrift   = new KDrift();
    public MDrift  mDrift   = new MDrift();
    public NDrift  nDrift   = new NDrift();

    // ── Groups ────────────────────────────────────────────────────────────────

    public static class Movement {
        public float coastDecay               = 0.009f;
        public float accelerationScale        = 3.7f;
        public float brakeDecay               = 0.03f;
        public float comfortableSpeedMultiplier = 1.55f;
    }

    public static class Steering {
        public float rampRate       = 0.12f;
        public float understeer     = 3.0f;
        public float understeerCurve = 2.0f;
    }

    public static class Camera {
        public boolean lock          = true;
        public float   pitch         = 10f;
        public float   brakeZoomFov  = 10f;
        public float   brakeZoomLerp = 0.3f;
        public float   driftScale    = 3.0f;
        public float   driftLerpIn   = 0.05f;
        public float   driftLerpOut  = 0.15f;
    }

    public static class Hud {
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

    public static class KDrift {
        public float   slipAngle         = 30f;
        public float   slipDecay         = 2.1f;
        public float   slipDecaySpeedRef = 0.6f;
        public float   boost             = 0.04f;
        public int     minTicks          = 15;
        public boolean boostEnabled      = true;
    }

    public static class MDrift {
        public float   slipAngle        = 22f;
        public float   slipConvergeRate = 4f;
        public float   steerSensitivity = 2.0f;
        // How fast the steering accumulator (0..1) climbs per tick while steering is held.
        // 1 / steerBuildRate = ticks to reach max angle from zero.
        // 0.02 = 50 ticks (~2.5 s)  ← default
        public float   steerBuildRate   = 0.02f;
        // How fast the accumulator falls per tick when steering is released mid-drift.
        // 0.04 = 25 ticks (~1.25 s) to fully release  ← default
        public float   steerDecayRate   = 0.04f;
        public boolean constantAngle    = false;
        public int     minHoldTicks     = 0;
        public int     autoTriggerTicks = 25;
        public float   steerThreshold   = 0.9f;
        public float   minSpeedKmh      = 45.0f;
        public boolean boostEnabled     = false;
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
