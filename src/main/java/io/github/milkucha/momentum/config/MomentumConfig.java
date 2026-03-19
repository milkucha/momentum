package io.github.milkucha.momentum.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MomentumConfig {

    public boolean enabled   = true;

    /** GLFW key code for the brake action. Default: S (83). Edit in momentum.json to remap. */
    public int brakeKey = GLFW.GLFW_KEY_S;
    /** GLFW key code for the handbrake/drift action. Default: Space (32). Edit in momentum.json to remap. */
    public int driftKey = GLFW.GLFW_KEY_SPACE;

    public Movement movement = new Movement();
    public Steering steering = new Steering();
    public Camera  camera   = new Camera();
    public BarHud  barHud   = new BarHud();
    public ArcadeDrift    arcadeDrift    = new ArcadeDrift();
    public ResponsiveDrift responsiveDrift = new ResponsiveDrift();
    public ODrift  oDrift   = new ODrift();
    public Sound   sound    = new Sound();

    // ── Groups ────────────────────────────────────────────────────────────────

    public static class Movement {
        public boolean enabled                = true;
        public float coastDecay               = 0.009f;
        public float accelerationScale        = 5.5f;
        public float brakeDecay               = 0.03f;
        public float comfortableSpeedMultiplier = 1.25f;
    }

    public static class Steering {
        public boolean enabled       = true;
        public float rampRate       = 0.12f;
        public float centerRate     = 0.42f;  // rate back to center when no steering key held
        public float understeer     = 3.0f;
        public float understeerCurve = 2.0f;
    }

    public static class Camera {
        public boolean enabled            = true;
        public boolean lock               = true;
        public float   pitch              = 10f;
        public float   brakeZoomFov       = 10f;   // max FOV reduction clamp (degrees)
        // Spring-damper brake zoom: deceleration (hSpeed delta/tick) drives a mass-spring camera.
        // When the vehicle stops, accumulated velocity carries the zoom briefly — inertia feel.
        public float   brakeZoomInputScale = 10f;  // decel units → zoom force multiplier
        public float   brakeZoomSpring     = 0.02f; // spring constant (return-to-zero pull)
        public float   brakeZoomDamping    = 0.90f; // velocity decay per tick (0=none,1=freeze)
    }

    public static class BarHud {
        public boolean enabled    = true;
        // Position. -1 = anchor to right/bottom edge using the margin fields.
        public int   x            = -1;
        public int   y            = -1;
        public float xFraction    = 0.5f;    // fraction of screenW from right edge; resolution-independent
        public int   marginBottom = 29;

        // Overall size of the velocimeter area in pixels.
        public int   totalWidth   = 90;
        public int   totalHeight  = 15;

        // Size of each individual bar segment and the gap between them.
        // numBars = floor((totalWidth + barSpacing) / (barWidth + barSpacing))
        public int   barWidth     = 5;
        public int   barSpacing   = 2;

        // Speed (km/h) at which all bar segments are filled.
        public float maxSpeedKmh  = 150.0f;

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

        // Debug overlay.
        public boolean debug          = false;
        public int     debugX         = -1;
        public int     debugY         = 10;
        public float   debugXFraction = 0.016f; // fraction of screenW from right edge
    }

    public static class ArcadeDrift {
        public float   slipAngle         = 3f;
        public float   slipConvergeRate  = 4f;    // deg/tick the offset converges toward target while held
        public float   slipDecay         = 0.9f;
        public float   slipDecaySpeedRef = 0.41f;
        public float   boost             = 0.04f;
        public int     boostDuration     = 44;
        public int     minTicks          = 60;
        public boolean boostEnabled      = true;
        public boolean brakeEnabled      = true;
        public float   steerThreshold    = 0.1f;  // minimum |steering| to start drift (0 = any non-zero)
        public int     minHoldTicks      = 0;     // ticks drift key must be held before drift can start
        public int     autoTriggerTicks  = 8;     // ticks before auto-start in random direction (0 = disabled)
        public float   minSpeedKmh       = 45.0f; // minimum speed to start drift
        public boolean cameraEnabled     = true;
        public float   cameraScale       = 10.0f;
        public float   cameraLerpIn      = 0.1f;
        public float   cameraLerpOut     = 0.1f;
    }

    public static class ResponsiveDrift {
        public float   slipAngle         = 33f;
        public float   slipConvergeRate  = 0.18f; // fraction of remaining distance closed per tick (exponential ease-out toward target)
        public float   slipDecay         = 3.5f;  // deg/tick removed on release (linear, same formula as Arcade drift)
        public float   slipDecaySpeedRef = 0.6f;  // reference speed for speed-adjusted decay
        public float   boost             = 0.04f; // engine speed bonus on clean release
        public int     boostDuration     = 40;    // ticks the boost animation plays (20 ticks = 1 s)
        public int     minTicks          = 60;    // minimum ticks held to earn boost
        public float   steerSensitivity  = 2.0f;
        // How fast the steering accumulator (0..1) climbs per tick while steering is held.
        public float   steerBuildRate    = 0.08f;
        // How fast the accumulator falls per tick when steering is released mid-drift.
        public float   steerDecayRate    = 0.118f;
        public boolean constantAngle     = false;
        public int     minHoldTicks      = 0;
        public int     autoTriggerTicks  = 10;
        public float   steerThreshold    = 0.7f;
        public float   minSpeedKmh       = 45.0f;
        public boolean boostEnabled      = true;
        public boolean brakeEnabled      = true;
        public boolean cameraEnabled     = true;
        public float   cameraScale       = 1.8f;
        public float   cameraLerpIn      = 0.18f;
        public float   cameraLerpOut     = 0.25f;
    }

    public static class Sound {
        // km/h at which the engine sound reaches its pitch ceiling (Minecraft clamps pitch at 2.0).
        // Original Automobility value: ~91.7. Raise to hear pitch-bend continue to higher speeds.
        public float enginePitchCeiling = 120.0f;
    }

    public static class ODrift {
        public enum Profile { VANILLA, ARCADE, RESPONSIVE }
        public Profile profile = Profile.ARCADE;
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
                    if (loaded.barHud   == null) loaded.barHud   = new BarHud();
                    if (loaded.arcadeDrift    == null) loaded.arcadeDrift    = new ArcadeDrift();
                    if (loaded.responsiveDrift == null) loaded.responsiveDrift = new ResponsiveDrift();
                    if (loaded.oDrift   == null) loaded.oDrift   = new ODrift();
                    if (loaded.oDrift.profile == null) loaded.oDrift.profile = new ODrift().profile; // old JSON had J/K/M enum values
                    if (loaded.sound    == null) loaded.sound    = new Sound();
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
