package io.github.milkucha.momentum.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.awt.Color;

public class MomentumConfigScreen {

    public static Screen create(Screen parent) {
        MomentumConfig cfg = MomentumConfig.get();
        MomentumConfig def = new MomentumConfig();

        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Momentum"))
                .category(movement(cfg, def))
                .category(steering(cfg, def))
                .category(camera(cfg, def))
                .category(hud(cfg, def))
                .category(barHud(cfg, def))
                .category(kDrift(cfg, def))
                .category(mDrift(cfg, def))
                .category(nDrift(cfg, def))
                .category(oDrift(cfg, def))
                .save(cfg::save)
                .build()
                .generateScreen(parent);
    }

    // ── Movement ─────────────────────────────────────────────────────────────

    private static ConfigCategory movement(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Text.literal("Movement"))
                .option(floatOpt("Coast Decay",
                        "Speed lost per tick while coasting (no throttle). Lower = longer roll.",
                        def.movement.coastDecay,
                        () -> cfg.movement.coastDecay, v -> cfg.movement.coastDecay = v,
                        0.001f, 0.05f, 0.001f))
                .option(floatOpt("Acceleration Scale",
                        "Divides input acceleration. Higher = slower acceleration.",
                        def.movement.accelerationScale,
                        () -> cfg.movement.accelerationScale, v -> cfg.movement.accelerationScale = v,
                        1.0f, 10.0f, 0.1f))
                .option(floatOpt("Brake Decay",
                        "Engine speed lost per tick while braking (Space). Higher = harder brakes.",
                        def.movement.brakeDecay,
                        () -> cfg.movement.brakeDecay, v -> cfg.movement.brakeDecay = v,
                        0.001f, 0.1f, 0.001f))
                .option(floatOpt("Comfortable Speed Multiplier",
                        "Scales Automobility's comfortable speed cap. Higher = faster top speed.",
                        def.movement.comfortableSpeedMultiplier,
                        () -> cfg.movement.comfortableSpeedMultiplier, v -> cfg.movement.comfortableSpeedMultiplier = v,
                        0.5f, 5.0f, 0.05f))
                .build();
    }

    // ── Steering ─────────────────────────────────────────────────────────────

    private static ConfigCategory steering(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Text.literal("Steering"))
                .option(floatOpt("Ramp Rate",
                        "How fast steering builds up per tick while holding a direction key.",
                        def.steering.rampRate,
                        () -> cfg.steering.rampRate, v -> cfg.steering.rampRate = v,
                        0.01f, 1.0f, 0.01f))
                .option(floatOpt("Center Rate",
                        "How fast steering returns to center when no direction key is held.",
                        def.steering.centerRate,
                        () -> cfg.steering.centerRate, v -> cfg.steering.centerRate = v,
                        0.01f, 1.0f, 0.01f))
                .option(floatOpt("Understeer",
                        "How much high speed reduces steering. Higher = more understeer.",
                        def.steering.understeer,
                        () -> cfg.steering.understeer, v -> cfg.steering.understeer = v,
                        0.0f, 10.0f, 0.1f))
                .option(floatOpt("Understeer Curve",
                        "Exponent shaping how understeer scales with speed. 2 = quadratic.",
                        def.steering.understeerCurve,
                        () -> cfg.steering.understeerCurve, v -> cfg.steering.understeerCurve = v,
                        0.5f, 5.0f, 0.1f))
                .build();
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private static ConfigCategory camera(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Text.literal("Camera"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Lock"))
                        .option(boolOpt("Lock Camera",
                                "Forces camera to follow the car's yaw every tick.",
                                def.camera.lock,
                                () -> cfg.camera.lock, v -> cfg.camera.lock = v))
                        .option(floatOpt("Lock Pitch",
                                "Pitch angle (degrees) when camera lock is enabled. Positive = looking down.",
                                def.camera.pitch,
                                () -> cfg.camera.pitch, v -> cfg.camera.pitch = v,
                                -45.0f, 45.0f, 0.5f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Brake Zoom"))
                        .option(floatOpt("Brake Zoom FOV",
                                "Maximum FOV reduction (degrees) during hard braking.",
                                def.camera.brakeZoomFov,
                                () -> cfg.camera.brakeZoomFov, v -> cfg.camera.brakeZoomFov = v,
                                0.0f, 30.0f, 0.5f))
                        .option(floatOpt("Brake Zoom Input Scale",
                                "Multiplier converting deceleration into zoom force. Higher = more zoom per brake.",
                                def.camera.brakeZoomInputScale,
                                () -> cfg.camera.brakeZoomInputScale, v -> cfg.camera.brakeZoomInputScale = v,
                                1.0f, 100.0f, 1.0f))
                        .option(floatOpt("Brake Zoom Spring",
                                "Spring constant pulling zoom back to zero. Higher = snappier return.",
                                def.camera.brakeZoomSpring,
                                () -> cfg.camera.brakeZoomSpring, v -> cfg.camera.brakeZoomSpring = v,
                                0.001f, 0.5f, 0.001f))
                        .option(floatOpt("Brake Zoom Damping",
                                "Velocity decay per tick (0 = instant stop, 1 = never stops). Inertia feel.",
                                def.camera.brakeZoomDamping,
                                () -> cfg.camera.brakeZoomDamping, v -> cfg.camera.brakeZoomDamping = v,
                                0.5f, 1.0f, 0.01f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Drift Camera"))
                        .option(boolOpt("Enable Drift Camera",
                                "Adds a yaw offset to the camera during K/M drift.",
                                def.camera.driftCamera,
                                () -> cfg.camera.driftCamera, v -> cfg.camera.driftCamera = v))
                        .option(floatOpt("Drift Scale",
                                "How much the slip angle is exaggerated in the camera offset.",
                                def.camera.driftScale,
                                () -> cfg.camera.driftScale, v -> cfg.camera.driftScale = v,
                                0.0f, 10.0f, 0.1f))
                        .option(floatOpt("Drift Lerp In",
                                "Camera lerp speed toward the drift offset. 1 = instant.",
                                def.camera.driftLerpIn,
                                () -> cfg.camera.driftLerpIn, v -> cfg.camera.driftLerpIn = v,
                                0.01f, 1.0f, 0.01f))
                        .option(floatOpt("Drift Lerp Out",
                                "Camera lerp speed back to center on drift end. 1 = instant.",
                                def.camera.driftLerpOut,
                                () -> cfg.camera.driftLerpOut, v -> cfg.camera.driftLerpOut = v,
                                0.01f, 1.0f, 0.01f))
                        .build())
                .build();
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private static ConfigCategory hud(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Text.literal("HUD"))
                .option(boolOpt("Use Bar HUD",
                        "Use the bar-based HUD. When off, uses the texture-based HUD.",
                        def.hud.useBarHud,
                        () -> cfg.hud.useBarHud, v -> cfg.hud.useBarHud = v))
                .option(boolOpt("Debug Overlay",
                        "Show steering/speed/drift debug values above the HUD.",
                        def.hud.debug,
                        () -> cfg.hud.debug, v -> cfg.hud.debug = v))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Position"))
                        .option(intOpt("X", "HUD X position. -1 = anchor from right using margin.",
                                def.hud.x, () -> cfg.hud.x, v -> cfg.hud.x = v, -1, 1920, 1))
                        .option(intOpt("Y", "HUD Y position. -1 = anchor from bottom using margin.",
                                def.hud.y, () -> cfg.hud.y, v -> cfg.hud.y = v, -1, 1080, 1))
                        .option(intOpt("Margin Right", "", def.hud.marginRight,
                                () -> cfg.hud.marginRight, v -> cfg.hud.marginRight = v, 0, 500, 1))
                        .option(intOpt("Margin Bottom", "", def.hud.marginBottom,
                                () -> cfg.hud.marginBottom, v -> cfg.hud.marginBottom = v, 0, 500, 1))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Bar Offsets"))
                        .option(intOpt("Bar Offset X", "", def.hud.barOffsetX,
                                () -> cfg.hud.barOffsetX, v -> cfg.hud.barOffsetX = v, -50, 200, 1))
                        .option(intOpt("Bar Offset Y", "", def.hud.barOffsetY,
                                () -> cfg.hud.barOffsetY, v -> cfg.hud.barOffsetY = v, -50, 200, 1))
                        .option(floatOpt("Bar Scale", "", def.hud.barScale,
                                () -> cfg.hud.barScale, v -> cfg.hud.barScale = v, 0.1f, 5.0f, 0.05f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Animation Offsets"))
                        .option(intOpt("Anim Offset X", "", def.hud.animOffsetX,
                                () -> cfg.hud.animOffsetX, v -> cfg.hud.animOffsetX = v, -50, 200, 1))
                        .option(intOpt("Anim Offset Y", "", def.hud.animOffsetY,
                                () -> cfg.hud.animOffsetY, v -> cfg.hud.animOffsetY = v, -50, 200, 1))
                        .option(floatOpt("Anim Scale", "", def.hud.animScale,
                                () -> cfg.hud.animScale, v -> cfg.hud.animScale = v, 0.1f, 5.0f, 0.05f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Speed Text"))
                        .option(intOpt("Speed Text Offset X", "", def.hud.speedTextOffsetX,
                                () -> cfg.hud.speedTextOffsetX, v -> cfg.hud.speedTextOffsetX = v, -100, 200, 1))
                        .option(intOpt("Speed Text Offset Y", "", def.hud.speedTextOffsetY,
                                () -> cfg.hud.speedTextOffsetY, v -> cfg.hud.speedTextOffsetY = v, -100, 200, 1))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Debug Overlay Position"))
                        .option(intOpt("Debug X", "Debug overlay X. -1 = anchor from right.",
                                def.hud.debugX, () -> cfg.hud.debugX, v -> cfg.hud.debugX = v, -1, 1920, 1))
                        .option(intOpt("Debug Y", "", def.hud.debugY,
                                () -> cfg.hud.debugY, v -> cfg.hud.debugY = v, 0, 1080, 1))
                        .option(intOpt("Debug Margin Right", "", def.hud.debugMarginRight,
                                () -> cfg.hud.debugMarginRight, v -> cfg.hud.debugMarginRight = v, 0, 500, 1))
                        .build())
                .build();
    }

    // ── Bar HUD ───────────────────────────────────────────────────────────────

    private static ConfigCategory barHud(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Text.literal("Bar HUD"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Position"))
                        .option(intOpt("X", "Bar HUD X. -1 = anchor from right.",
                                def.barHud.x, () -> cfg.barHud.x, v -> cfg.barHud.x = v, -1, 1920, 1))
                        .option(intOpt("Y", "Bar HUD Y. -1 = anchor from bottom.",
                                def.barHud.y, () -> cfg.barHud.y, v -> cfg.barHud.y = v, -1, 1080, 1))
                        .option(intOpt("Margin Right", "", def.barHud.marginRight,
                                () -> cfg.barHud.marginRight, v -> cfg.barHud.marginRight = v, 0, 500, 1))
                        .option(intOpt("Margin Bottom", "", def.barHud.marginBottom,
                                () -> cfg.barHud.marginBottom, v -> cfg.barHud.marginBottom = v, 0, 500, 1))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Size"))
                        .option(intOpt("Total Width", "Total width of the bar area in pixels.",
                                def.barHud.totalWidth,
                                () -> cfg.barHud.totalWidth, v -> cfg.barHud.totalWidth = v, 10, 500, 1))
                        .option(intOpt("Total Height", "Total height of the bar area in pixels.",
                                def.barHud.totalHeight,
                                () -> cfg.barHud.totalHeight, v -> cfg.barHud.totalHeight = v, 1, 100, 1))
                        .option(intOpt("Bar Width", "Width of each individual bar segment.",
                                def.barHud.barWidth,
                                () -> cfg.barHud.barWidth, v -> cfg.barHud.barWidth = v, 1, 50, 1))
                        .option(intOpt("Bar Spacing", "Gap between bar segments.",
                                def.barHud.barSpacing,
                                () -> cfg.barHud.barSpacing, v -> cfg.barHud.barSpacing = v, 0, 20, 1))
                        .option(floatOpt("Max Speed (km/h)", "Speed at which all segments are filled.",
                                def.barHud.maxSpeedKmh,
                                () -> cfg.barHud.maxSpeedKmh, v -> cfg.barHud.maxSpeedKmh = v,
                                50.0f, 500.0f, 10.0f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Colors"))
                        .option(colorOpt("Bar Color", "ARGB color of filled bar segments.",
                                def.barHud.barColor,
                                () -> cfg.barHud.barColor, v -> cfg.barHud.barColor = v))
                        .option(colorOpt("Boost Bar Color", "ARGB color of bar segments showing boost contribution.",
                                def.barHud.boostBarColor,
                                () -> cfg.barHud.boostBarColor, v -> cfg.barHud.boostBarColor = v))
                        .option(colorOpt("Text Color", "ARGB color of the speed readout text.",
                                def.barHud.textColor,
                                () -> cfg.barHud.textColor, v -> cfg.barHud.textColor = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Text Position"))
                        .option(intOpt("Text Offset X", "Speed text X relative to bar top-left.",
                                def.barHud.textOffsetX,
                                () -> cfg.barHud.textOffsetX, v -> cfg.barHud.textOffsetX = v, -100, 100, 1))
                        .option(intOpt("Text Offset Y", "Speed text Y relative to bar top-left. Negative = above bar.",
                                def.barHud.textOffsetY,
                                () -> cfg.barHud.textOffsetY, v -> cfg.barHud.textOffsetY = v, -100, 100, 1))
                        .build())
                .build();
    }

    // ── K-Drift ───────────────────────────────────────────────────────────────

    private static ConfigCategory kDrift(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Text.literal("K-Drift"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Slip"))
                        .option(floatOpt("Slip Angle",
                                "Maximum sideslip angle in degrees while K-drift is active.",
                                def.kDrift.slipAngle,
                                () -> cfg.kDrift.slipAngle, v -> cfg.kDrift.slipAngle = v,
                                0.0f, 45.0f, 0.5f))
                        .option(floatOpt("Slip Converge Rate",
                                "Degrees per tick the offset converges toward the target while K is held.",
                                def.kDrift.slipConvergeRate,
                                () -> cfg.kDrift.slipConvergeRate, v -> cfg.kDrift.slipConvergeRate = v,
                                0.1f, 20.0f, 0.1f))
                        .option(floatOpt("Slip Decay",
                                "Degrees per tick the offset decays after K is released.",
                                def.kDrift.slipDecay,
                                () -> cfg.kDrift.slipDecay, v -> cfg.kDrift.slipDecay = v,
                                0.0f, 5.0f, 0.05f))
                        .option(floatOpt("Slip Decay Speed Ref",
                                "Reference speed for speed-adjusted decay. Higher speed = slower decay.",
                                def.kDrift.slipDecaySpeedRef,
                                () -> cfg.kDrift.slipDecaySpeedRef, v -> cfg.kDrift.slipDecaySpeedRef = v,
                                0.0f, 2.0f, 0.01f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Trigger"))
                        .option(floatOpt("Steer Threshold",
                                "Minimum |steering| required to start drift. 0 = any non-zero input.",
                                def.kDrift.steerThreshold,
                                () -> cfg.kDrift.steerThreshold, v -> cfg.kDrift.steerThreshold = v,
                                0.0f, 1.0f, 0.05f))
                        .option(intOpt("Min Hold Ticks",
                                "Ticks K must be held before drift can start. 0 = triggers immediately.",
                                def.kDrift.minHoldTicks,
                                () -> cfg.kDrift.minHoldTicks, v -> cfg.kDrift.minHoldTicks = v,
                                0, 100, 1))
                        .option(intOpt("Auto Trigger Ticks",
                                "Ticks without steering before drift auto-starts in a random direction. 0 = disabled.",
                                def.kDrift.autoTriggerTicks,
                                () -> cfg.kDrift.autoTriggerTicks, v -> cfg.kDrift.autoTriggerTicks = v,
                                0, 200, 1))
                        .option(boolOpt("Brake Enabled",
                                "Apply braking when K is held but drift has not started.",
                                def.kDrift.brakeEnabled,
                                () -> cfg.kDrift.brakeEnabled, v -> cfg.kDrift.brakeEnabled = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Boost"))
                        .option(boolOpt("Boost Enabled",
                                "Grant a speed boost on clean K-drift release.",
                                def.kDrift.boostEnabled,
                                () -> cfg.kDrift.boostEnabled, v -> cfg.kDrift.boostEnabled = v))
                        .option(floatOpt("Boost",
                                "Engine speed bonus added on clean release (if boost enabled).",
                                def.kDrift.boost,
                                () -> cfg.kDrift.boost, v -> cfg.kDrift.boost = v,
                                0.0f, 0.5f, 0.005f))
                        .option(intOpt("Min Ticks",
                                "Minimum ticks K must be held to earn the boost.",
                                def.kDrift.minTicks,
                                () -> cfg.kDrift.minTicks, v -> cfg.kDrift.minTicks = v,
                                0, 120, 1))
                        .build())
                .build();
    }

    // ── M-Drift ───────────────────────────────────────────────────────────────

    private static ConfigCategory mDrift(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Text.literal("M-Drift"))
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Slip"))
                        .option(floatOpt("Slip Angle",
                                "Maximum sideslip angle in degrees.",
                                def.mDrift.slipAngle,
                                () -> cfg.mDrift.slipAngle, v -> cfg.mDrift.slipAngle = v,
                                0.0f, 90.0f, 0.5f))
                        .option(floatOpt("Slip Converge Rate",
                                "Fraction of remaining distance closed per tick (exponential ease-out).",
                                def.mDrift.slipConvergeRate,
                                () -> cfg.mDrift.slipConvergeRate, v -> cfg.mDrift.slipConvergeRate = v,
                                0.01f, 1.0f, 0.01f))
                        .option(floatOpt("Slip Decay",
                                "Degrees per tick removed on release (linear).",
                                def.mDrift.slipDecay,
                                () -> cfg.mDrift.slipDecay, v -> cfg.mDrift.slipDecay = v,
                                0.0f, 10.0f, 0.1f))
                        .option(floatOpt("Slip Decay Speed Ref",
                                "Reference speed for speed-adjusted decay.",
                                def.mDrift.slipDecaySpeedRef,
                                () -> cfg.mDrift.slipDecaySpeedRef, v -> cfg.mDrift.slipDecaySpeedRef = v,
                                0.0f, 2.0f, 0.01f))
                        .option(boolOpt("Constant Angle",
                                "Lock the slip angle at the configured maximum (no ease-in).",
                                def.mDrift.constantAngle,
                                () -> cfg.mDrift.constantAngle, v -> cfg.mDrift.constantAngle = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Steering"))
                        .option(floatOpt("Steer Sensitivity",
                                "Multiplier on steering input while drift is active.",
                                def.mDrift.steerSensitivity,
                                () -> cfg.mDrift.steerSensitivity, v -> cfg.mDrift.steerSensitivity = v,
                                0.1f, 10.0f, 0.1f))
                        .option(floatOpt("Steer Build Rate",
                                "How fast the steering accumulator (0..1) climbs per tick.",
                                def.mDrift.steerBuildRate,
                                () -> cfg.mDrift.steerBuildRate, v -> cfg.mDrift.steerBuildRate = v,
                                0.001f, 0.5f, 0.001f))
                        .option(floatOpt("Steer Decay Rate",
                                "How fast the accumulator falls per tick when steering is released.",
                                def.mDrift.steerDecayRate,
                                () -> cfg.mDrift.steerDecayRate, v -> cfg.mDrift.steerDecayRate = v,
                                0.001f, 0.5f, 0.001f))
                        .option(floatOpt("Steer Threshold",
                                "Minimum steering input required to maintain drift angle.",
                                def.mDrift.steerThreshold,
                                () -> cfg.mDrift.steerThreshold, v -> cfg.mDrift.steerThreshold = v,
                                0.0f, 1.0f, 0.05f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Trigger"))
                        .option(floatOpt("Min Speed (km/h)",
                                "Minimum speed required to start an M-drift.",
                                def.mDrift.minSpeedKmh,
                                () -> cfg.mDrift.minSpeedKmh, v -> cfg.mDrift.minSpeedKmh = v,
                                0.0f, 200.0f, 5.0f))
                        .option(intOpt("Min Hold Ticks",
                                "Minimum ticks M must be held before drift can start.",
                                def.mDrift.minHoldTicks,
                                () -> cfg.mDrift.minHoldTicks, v -> cfg.mDrift.minHoldTicks = v,
                                0, 100, 1))
                        .option(intOpt("Auto Trigger Ticks",
                                "Ticks without steering before drift auto-starts in a random direction. 0 = disabled.",
                                def.mDrift.autoTriggerTicks,
                                () -> cfg.mDrift.autoTriggerTicks, v -> cfg.mDrift.autoTriggerTicks = v,
                                0, 200, 1))
                        .option(boolOpt("Brake Enabled",
                                "Apply braking when M is held but drift has not started.",
                                def.mDrift.brakeEnabled,
                                () -> cfg.mDrift.brakeEnabled, v -> cfg.mDrift.brakeEnabled = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Boost"))
                        .option(boolOpt("Boost Enabled",
                                "Grant a speed boost on clean M-drift release.",
                                def.mDrift.boostEnabled,
                                () -> cfg.mDrift.boostEnabled, v -> cfg.mDrift.boostEnabled = v))
                        .option(floatOpt("Boost",
                                "Engine speed bonus added on clean release.",
                                def.mDrift.boost,
                                () -> cfg.mDrift.boost, v -> cfg.mDrift.boost = v,
                                0.0f, 0.5f, 0.005f))
                        .option(intOpt("Boost Duration",
                                "Ticks the boost animation plays (20 = 1 second).",
                                def.mDrift.boostDuration,
                                () -> cfg.mDrift.boostDuration, v -> cfg.mDrift.boostDuration = v,
                                0, 200, 1))
                        .option(intOpt("Min Ticks",
                                "Minimum ticks held to earn the boost.",
                                def.mDrift.minTicks,
                                () -> cfg.mDrift.minTicks, v -> cfg.mDrift.minTicks = v,
                                0, 200, 1))
                        .build())
                .build();
    }

    // ── N-Drift ───────────────────────────────────────────────────────────────

    private static ConfigCategory nDrift(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Text.literal("N-Drift"))
                .option(intOpt("Brake Ticks",
                        "Ticks N must be held (braking) before the drift triggers.",
                        def.nDrift.brakeTicks,
                        () -> cfg.nDrift.brakeTicks, v -> cfg.nDrift.brakeTicks = v,
                        1, 100, 1))
                .build();
    }

    // ── O-Drift ───────────────────────────────────────────────────────────────

    private static ConfigCategory oDrift(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Text.literal("O-Drift"))
                .option(Option.<MomentumConfig.ODrift.Profile>createBuilder()
                        .name(Text.literal("Drift Profile"))
                        .description(OptionDescription.of(Text.literal(
                                "Which drift type the O key activates.\nDefault: K")))
                        .binding(def.oDrift.profile,
                                () -> cfg.oDrift.profile,
                                v  -> cfg.oDrift.profile = v)
                        .controller(opt -> EnumControllerBuilder.create(opt)
                                .enumClass(MomentumConfig.ODrift.Profile.class)
                                .formatValue(v -> Text.literal(switch (v) {
                                    case J -> "J-Drift  (Automobility style)";
                                    case K -> "K-Drift  (Arcade slip angle)";
                                    case M -> "M-Drift  (Combined / smart)";
                                })))
                        .build())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Option<Float> floatOpt(String name, String desc, float def,
            java.util.function.Supplier<Float> get, java.util.function.Consumer<Float> set,
            float min, float max, float step) {
        String defStr = String.format("%.3f", def);
        Text descLine = desc.isEmpty()
                ? Text.literal("Default: " + defStr)
                : Text.literal(desc + "\nDefault: " + defStr);
        return Option.<Float>createBuilder()
                .name(Text.literal(name))
                .description(OptionDescription.of(descLine))
                .binding(def, get, set)
                .controller(opt -> FloatSliderControllerBuilder.create(opt)
                        .range(min, max).step(step)
                        .formatValue(v -> Text.literal(String.format("%.3f", v))))
                .build();
    }

    private static Option<Integer> intOpt(String name, String desc, int def,
            java.util.function.Supplier<Integer> get, java.util.function.Consumer<Integer> set,
            int min, int max, int step) {
        Text descLine = desc.isEmpty()
                ? Text.literal("Default: " + def)
                : Text.literal(desc + "\nDefault: " + def);
        return Option.<Integer>createBuilder()
                .name(Text.literal(name))
                .description(OptionDescription.of(descLine))
                .binding(def, get, set)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(step))
                .build();
    }

    private static Option<Boolean> boolOpt(String name, String desc, boolean def,
            java.util.function.Supplier<Boolean> get, java.util.function.Consumer<Boolean> set) {
        Text descLine = desc.isEmpty()
                ? Text.literal("Default: " + def)
                : Text.literal(desc + "\nDefault: " + def);
        return Option.<Boolean>createBuilder()
                .name(Text.literal(name))
                .description(OptionDescription.of(descLine))
                .binding(def, get, set)
                .controller(BooleanControllerBuilder::create)
                .build();
    }

    private static Option<Color> colorOpt(String name, String desc, int defArgb,
            java.util.function.Supplier<Integer> get, java.util.function.Consumer<Integer> set) {
        String defStr = String.format("#%08X", defArgb);
        Text descLine = desc.isEmpty()
                ? Text.literal("Default: " + defStr)
                : Text.literal(desc + "\nDefault: " + defStr);
        return Option.<Color>createBuilder()
                .name(Text.literal(name))
                .description(OptionDescription.of(descLine))
                .binding(new Color(defArgb, true),
                        () -> new Color(get.get(), true),
                        v -> set.accept(v.getRGB()))
                .controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(true))
                .build();
    }
}
