package io.github.milkucha.momentum.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import net.minecraft.client.util.InputUtil;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class MomentumConfigScreen {

    public static Screen create(Screen parent) {
        MomentumConfig cfg = MomentumConfig.get();
        MomentumConfig def = new MomentumConfig();

        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Momentum"))
                .category(general(cfg, def))
                .category(oDrift(cfg, def, parent))
                .category(movement(cfg, def))
                .category(steering(cfg, def))
                .category(camera(cfg, def))
                .category(hud(cfg, def))
                .save(cfg::save)
                .build()
                .generateScreen(parent);
    }

    // ── Movement ─────────────────────────────────────────────────────────────

    private static ConfigCategory movement(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Text.literal("Movement"))
                .option(floatOpt("Coast Decay",
                        "Speed lost per tick when coasting with no throttle.\n\nHigher values = stops quickly after releasing throttle.\nLower values = car rolls on longer.",
                        def.movement.coastDecay,
                        () -> cfg.movement.coastDecay, v -> cfg.movement.coastDecay = v,
                        0.001f, 0.05f, 0.001f))
                .option(floatOpt("Acceleration Scale",
                        "Divides the raw acceleration force.\n\nHigher values = slower, more gradual acceleration.\nLower values = faster acceleration from a stop.",
                        def.movement.accelerationScale,
                        () -> cfg.movement.accelerationScale, v -> cfg.movement.accelerationScale = v,
                        1.0f, 10.0f, 0.1f))
                .option(floatOpt("Brake Decay",
                        "Speed lost per tick while braking (Space).\n\nHigher values = hard stop.\nLower values = soft, progressive braking.",
                        def.movement.brakeDecay,
                        () -> cfg.movement.brakeDecay, v -> cfg.movement.brakeDecay = v,
                        0.001f, 0.1f, 0.001f))
                .option(floatOpt("Comfortable Speed Multiplier",
                        "Scales the car's comfortable speed cap.\n\nHigher values = higher top speed.\nLower values = lower top speed.",
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
                        "How fast steering builds toward full lock while holding a direction key.\n\nHigher values = snaps to full lock quickly.\nLower values = slow, smooth build-up.",
                        def.steering.rampRate,
                        () -> cfg.steering.rampRate, v -> cfg.steering.rampRate = v,
                        0.01f, 1.0f, 0.01f))
                .option(floatOpt("Center Rate",
                        "How fast steering returns to centre when no key is held.\n\nHigher values = snaps straight instantly.\nLower values = wheels drift back slowly.",
                        def.steering.centerRate,
                        () -> cfg.steering.centerRate, v -> cfg.steering.centerRate = v,
                        0.01f, 1.0f, 0.01f))
                .option(floatOpt("Understeer",
                        "How much turning is reduced at high speed.\n\nHigher values = car struggles to turn at speed (more realistic).\nLower values = sharp corners at any speed.",
                        def.steering.understeer,
                        () -> cfg.steering.understeer, v -> cfg.steering.understeer = v,
                        0.0f, 10.0f, 0.1f))
                .option(floatOpt("Understeer Curve",
                        "Exponent shaping when understeer kicks in relative to speed.\n\nHigher values = only affects very high speeds.\nLower values = understeer starts at lower speeds.",
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
                                "Camera pitch when lock is on. Positive = looking down.\n\nHigher values = steeper downward angle.\nLower values = more horizontal view.",
                                def.camera.pitch,
                                () -> cfg.camera.pitch, v -> cfg.camera.pitch = v,
                                -45.0f, 45.0f, 0.5f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Brake Zoom"))
                        .option(floatOpt("Brake Zoom FOV",
                                "Degrees of FOV reduction at peak braking.\n\nHigher values = dramatic cinematic zoom.\nLower values = subtle zoom.",
                                def.camera.brakeZoomFov,
                                () -> cfg.camera.brakeZoomFov, v -> cfg.camera.brakeZoomFov = v,
                                0.0f, 30.0f, 0.5f))
                        .option(floatOpt("Brake Zoom Input Scale",
                                "How much deceleration translates into zoom force.\n\nHigher values = aggressive zoom on any deceleration.\nLower values = subtle response even during hard braking.",
                                def.camera.brakeZoomInputScale,
                                () -> cfg.camera.brakeZoomInputScale, v -> cfg.camera.brakeZoomInputScale = v,
                                1.0f, 100.0f, 1.0f))
                        .option(floatOpt("Brake Zoom Spring",
                                "Strength pulling zoom back to normal.\n\nHigher values = snaps back immediately.\nLower values = zoom lingers after braking.",
                                def.camera.brakeZoomSpring,
                                () -> cfg.camera.brakeZoomSpring, v -> cfg.camera.brakeZoomSpring = v,
                                0.001f, 0.5f, 0.001f))
                        .option(floatOpt("Brake Zoom Damping",
                                "Zoom velocity carry-over per tick (0 = stops instantly, 1 = never stops).\n\nHigher values = coasts back slowly with inertia.\nLower values = zoom ends abruptly.",
                                def.camera.brakeZoomDamping,
                                () -> cfg.camera.brakeZoomDamping, v -> cfg.camera.brakeZoomDamping = v,
                                0.5f, 1.0f, 0.01f))
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
                        .option(intOpt("Margin Right", "Pixels from the right edge when X = -1. Higher = further from edge.", def.hud.marginRight,
                                () -> cfg.hud.marginRight, v -> cfg.hud.marginRight = v, 0, 500, 1))
                        .option(intOpt("Margin Bottom", "Pixels from the bottom edge when Y = -1. Higher = further from edge.", def.hud.marginBottom,
                                () -> cfg.hud.marginBottom, v -> cfg.hud.marginBottom = v, 0, 500, 1))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Bar Offsets"))
                        .option(intOpt("Bar Offset X", "Horizontal offset of the speed bar sprite from the HUD panel origin.", def.hud.barOffsetX,
                                () -> cfg.hud.barOffsetX, v -> cfg.hud.barOffsetX = v, -50, 200, 1))
                        .option(intOpt("Bar Offset Y", "Vertical offset of the speed bar sprite from the HUD panel origin.", def.hud.barOffsetY,
                                () -> cfg.hud.barOffsetY, v -> cfg.hud.barOffsetY = v, -50, 200, 1))
                        .option(floatOpt("Bar Scale", "Scale of the speed bar sprite. Lower = smaller bar. Higher = larger bar.", def.hud.barScale,
                                () -> cfg.hud.barScale, v -> cfg.hud.barScale = v, 0.1f, 5.0f, 0.05f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Animation Offsets"))
                        .option(intOpt("Anim Offset X", "Horizontal offset of the animated car sprite from the HUD panel origin.", def.hud.animOffsetX,
                                () -> cfg.hud.animOffsetX, v -> cfg.hud.animOffsetX = v, -50, 200, 1))
                        .option(intOpt("Anim Offset Y", "Vertical offset of the animated car sprite from the HUD panel origin.", def.hud.animOffsetY,
                                () -> cfg.hud.animOffsetY, v -> cfg.hud.animOffsetY = v, -50, 200, 1))
                        .option(floatOpt("Anim Scale", "Scale of the animated car sprite. Lower = smaller. Higher = larger.", def.hud.animScale,
                                () -> cfg.hud.animScale, v -> cfg.hud.animScale = v, 0.1f, 5.0f, 0.05f))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Speed Text"))
                        .option(intOpt("Speed Text Offset X", "Horizontal position of the km/h readout relative to the HUD panel origin.", def.hud.speedTextOffsetX,
                                () -> cfg.hud.speedTextOffsetX, v -> cfg.hud.speedTextOffsetX = v, -100, 200, 1))
                        .option(intOpt("Speed Text Offset Y", "Vertical position of the km/h readout relative to the HUD panel origin.", def.hud.speedTextOffsetY,
                                () -> cfg.hud.speedTextOffsetY, v -> cfg.hud.speedTextOffsetY = v, -100, 200, 1))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Debug Overlay Position"))
                        .option(intOpt("Debug X", "Debug overlay X. -1 = anchor from right.",
                                def.hud.debugX, () -> cfg.hud.debugX, v -> cfg.hud.debugX = v, -1, 1920, 1))
                        .option(intOpt("Debug Y", "Vertical position of the debug overlay on screen.", def.hud.debugY,
                                () -> cfg.hud.debugY, v -> cfg.hud.debugY = v, 0, 1080, 1))
                        .option(intOpt("Debug Margin Right", "Pixels from the right edge when Debug X = -1. Higher = further from edge.", def.hud.debugMarginRight,
                                () -> cfg.hud.debugMarginRight, v -> cfg.hud.debugMarginRight = v, 0, 500, 1))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Bar  |  Position"))
                        .option(intOpt("X", "Bar HUD X. -1 = anchor from right.",
                                def.barHud.x, () -> cfg.barHud.x, v -> cfg.barHud.x = v, -1, 1920, 1))
                        .option(intOpt("Y", "Bar HUD Y. -1 = anchor from bottom.",
                                def.barHud.y, () -> cfg.barHud.y, v -> cfg.barHud.y = v, -1, 1080, 1))
                        .option(intOpt("Margin Right", "Pixels from the right edge when X = -1. Higher = further from edge.", def.barHud.marginRight,
                                () -> cfg.barHud.marginRight, v -> cfg.barHud.marginRight = v, 0, 500, 1))
                        .option(intOpt("Margin Bottom", "Pixels from the bottom edge when Y = -1. Higher = further from edge.", def.barHud.marginBottom,
                                () -> cfg.barHud.marginBottom, v -> cfg.barHud.marginBottom = v, 0, 500, 1))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Text.literal("Bar  |  Size"))
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
                        .name(Text.literal("Bar  |  Colors"))
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
                        .name(Text.literal("Bar  |  Text Position"))
                        .option(intOpt("Text Offset X", "Speed text X relative to bar top-left.",
                                def.barHud.textOffsetX,
                                () -> cfg.barHud.textOffsetX, v -> cfg.barHud.textOffsetX = v, -100, 100, 1))
                        .option(intOpt("Text Offset Y", "Speed text Y relative to bar top-left. Negative = above bar.",
                                def.barHud.textOffsetY,
                                () -> cfg.barHud.textOffsetY, v -> cfg.barHud.textOffsetY = v, -100, 100, 1))
                        .build())
                .build();
    }

    // ── General ───────────────────────────────────────────────────────────────

    private static ConfigCategory general(MomentumConfig cfg, MomentumConfig def) {
        return ConfigCategory.createBuilder()
                .name(Text.literal("General"))
                .option(boolOpt("Enable Momentum",
                        "Turn the entire mod on or off. When off, Automobility's vanilla movement, HUD, and physics are used instead.",
                        def.enabled,
                        () -> cfg.enabled, v -> cfg.enabled = v))
                .option(keyOpt("Brake Key",
                        "Key that applies the Momentum brake.",
                        def.brakeKey,
                        () -> cfg.brakeKey, v -> cfg.brakeKey = v))
                .option(keyOpt("O-Drift Key",
                        "Key that activates the O-drift shortcut.",
                        def.oDriftKey,
                        () -> cfg.oDriftKey, v -> cfg.oDriftKey = v))
                .build();
    }

    // ── O-Drift ───────────────────────────────────────────────────────────────

    private static ConfigCategory oDrift(MomentumConfig cfg, MomentumConfig def, Screen parent) {

        // Profile selector — applies immediately to config and rebuilds screen
        Option<MomentumConfig.ODrift.Profile> profileOpt =
                Option.<MomentumConfig.ODrift.Profile>createBuilder()
                        .name(Text.literal("Drift Profile"))
                        .description(OptionDescription.of(Text.literal(
                                "Which drift type the O key activates.\n\n" +
                                "J — Automobility's own drift. No slip angle, just speed.\n" +
                                "K — Arcade sideslip. Quick, precise, easy to control.\n" +
                                "M — Deep slide. Wide angle, steering-driven, rewarding.\n\n" +
                                "Default: K")))
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
                        .listener((opt, val) -> {
                            // YACL fires listeners immediately on option construction with the
                            // current binding value. Guard against that here: if the value hasn't
                            // actually changed we're in the initial fire — skip to avoid infinite
                            // recursion (mc.execute runs synchronously on the render thread).
                            if (val == cfg.oDrift.profile) return;
                            cfg.oDrift.profile = val;
                            cfg.save();
                            MinecraftClient mc = MinecraftClient.getInstance();
                            mc.execute(() -> mc.setScreen(MomentumConfigScreen.create(parent)));
                        })
                        .build();

        // Build category with only the active profile's groups
        ConfigCategory.Builder b = ConfigCategory.createBuilder()
                .name(Text.literal("Drift"))
                .option(profileOpt);

        switch (cfg.oDrift.profile) {
            case K -> {
                BoostGroup  kg = kDriftBoostGroup(cfg, def);
                CameraGroup kc = kDriftCameraGroup(cfg, def);
                b.group(buildGroupFromList("Slip",    kDriftSlipOptions(cfg, def)));
                b.group(buildGroupFromList("Trigger", kDriftTriggerOptions(cfg, def)));
                b.group(buildGroupFromList("Boost",   kg.all()));
                b.group(buildGroupFromList("Camera",  kc.all()));
            }
            case M -> {
                List<Option<?>> mSlip = mDriftSlipOptions(cfg, def);
                BoostGroup      mg    = mDriftBoostGroup(cfg, def);
                CameraGroup     mc2   = mDriftCameraGroup(cfg, def);
                b.group(buildGroupFromList("Slip",     mSlip));
                b.group(buildGroupFromList("Steering", mDriftSteeringOptions(cfg, def)));
                b.group(buildGroupFromList("Trigger",  mDriftTriggerOptions(cfg, def)));
                b.group(buildGroupFromList("Boost",    mg.all()));
                b.group(buildGroupFromList("Camera",   mc2.all()));
            }
            case J -> { /* No groups — J uses Automobility defaults */ }
        }

        return b.build();
    }

    // ── K-Drift group builders ────────────────────────────────────────────────

    private static List<Option<?>> kDriftSlipOptions(MomentumConfig cfg, MomentumConfig def) {
        return List.of(
                floatOpt("Slip Angle",
                        "Maximum sideways slide angle in degrees.\n\nHigher values = big dramatic sideslip.\nLower values = subtle drift.",
                        def.kDrift.slipAngle,
                        () -> cfg.kDrift.slipAngle, v -> cfg.kDrift.slipAngle = v,
                        0.0f, 45.0f, 0.5f),
                floatOpt("Slip Converge Rate",
                        "Degrees per tick the slip angle snaps toward its target while K is held.\n\nHigher values = instantaneous snap.\nLower values = slow ease-in.",
                        def.kDrift.slipConvergeRate,
                        () -> cfg.kDrift.slipConvergeRate, v -> cfg.kDrift.slipConvergeRate = v,
                        0.1f, 20.0f, 0.1f),
                floatOpt("Slip Decay",
                        "How fast the drift angle fades after K is released.\n\nHigher values = car straightens out quickly.\nLower values = drift lingers.",
                        def.kDrift.slipDecay,
                        () -> cfg.kDrift.slipDecay, v -> cfg.kDrift.slipDecay = v,
                        0.0f, 5.0f, 0.05f),
                floatOpt("Slip Decay Speed Ref",
                        "Reference speed for speed-adjusted decay.\n\nHigher values = drift lingers longer at high speed.\nLower values = decay rate stays constant.",
                        def.kDrift.slipDecaySpeedRef,
                        () -> cfg.kDrift.slipDecaySpeedRef, v -> cfg.kDrift.slipDecaySpeedRef = v,
                        0.0f, 2.0f, 0.01f)
        );
    }

    private static List<Option<?>> kDriftTriggerOptions(MomentumConfig cfg, MomentumConfig def) {
        return List.of(
                floatOpt("Min Speed (km/h)",
                        "Minimum car speed to trigger a K-drift.\n\nHigher values = requires more speed to start.\nLower values = can start from low speed.",
                        def.kDrift.minSpeedKmh,
                        () -> cfg.kDrift.minSpeedKmh, v -> cfg.kDrift.minSpeedKmh = v,
                        0.0f, 200.0f, 5.0f),
                floatOpt("Steer Threshold",
                        "Minimum steering input needed to start a drift.\n\nHigher values = requires near-full lock.\nLower values = any slight input triggers it.",
                        def.kDrift.steerThreshold,
                        () -> cfg.kDrift.steerThreshold, v -> cfg.kDrift.steerThreshold = v,
                        0.0f, 1.0f, 0.05f),
                intOpt("Min Hold Ticks",
                        "K must be held this many ticks before drift can start.\n\nHigher values = adds a deliberate delay before starting.\nLower values = triggers almost instantly.",
                        def.kDrift.minHoldTicks,
                        () -> cfg.kDrift.minHoldTicks, v -> cfg.kDrift.minHoldTicks = v,
                        0, 100, 1),
                intOpt("Auto Trigger Ticks",
                        "Ticks without steering before drift starts automatically. 0 = disabled.\n\nHigher values = longer wait before auto-triggering.\nLower values = triggers quickly without steering.",
                        def.kDrift.autoTriggerTicks,
                        () -> cfg.kDrift.autoTriggerTicks, v -> cfg.kDrift.autoTriggerTicks = v,
                        0, 200, 1),
                boolOpt("Brake Enabled",
                        "When on, braking is applied while K is held but no drift has started yet.",
                        def.kDrift.brakeEnabled,
                        () -> cfg.kDrift.brakeEnabled, v -> cfg.kDrift.brakeEnabled = v)
        );
    }

    private static BoostGroup kDriftBoostGroup(MomentumConfig cfg, MomentumConfig def) {
        Option<Boolean> toggle   = boolOpt("Boost Enabled",
                "Grant a speed burst when K-drift ends cleanly. Turn off to disable the boost entirely.",
                def.kDrift.boostEnabled,
                () -> cfg.kDrift.boostEnabled, v -> cfg.kDrift.boostEnabled = v);
        Option<Float>   boost    = floatOpt("Boost",
                "Engine speed added instantly on clean release.\n\nHigher values = large burst of speed.\nLower values = small nudge.",
                def.kDrift.boost,
                () -> cfg.kDrift.boost, v -> cfg.kDrift.boost = v,
                0.0f, 0.5f, 0.005f);
        Option<Integer> duration = intOpt("Boost Duration",
                "Ticks the boost animation plays (20 = 1 s).\n\nHigher values = longer animation.\nLower values = brief flash.",
                def.kDrift.boostDuration,
                () -> cfg.kDrift.boostDuration, v -> cfg.kDrift.boostDuration = v,
                0, 200, 1);
        Option<Integer> minTicks = intOpt("Min Ticks",
                "Drift must last this long to earn the boost.\n\nHigher values = only sustained drifts are rewarded.\nLower values = short drifts qualify.",
                def.kDrift.minTicks,
                () -> cfg.kDrift.minTicks, v -> cfg.kDrift.minTicks = v,
                0, 120, 1);

        List<Option<?>> deps = List.of(boost, duration, minTicks);
        toggle.addListener((opt, val) -> deps.forEach(o -> o.setAvailable(val)));
        deps.forEach(o -> o.setAvailable(cfg.kDrift.boostEnabled));

        List<Option<?>> all = concat(List.of(toggle), deps);
        return new BoostGroup(toggle, deps, all);
    }

    private static CameraGroup kDriftCameraGroup(MomentumConfig cfg, MomentumConfig def) {
        Option<Boolean> toggle  = boolOpt("Camera Enabled",
                "Swings the camera to follow the drift angle. Turn off to keep the camera fixed.",
                def.kDrift.cameraEnabled,
                () -> cfg.kDrift.cameraEnabled, v -> cfg.kDrift.cameraEnabled = v);
        Option<Float> scale     = floatOpt("Camera Scale",
                "How much the camera yaw exaggerates the slip angle.\n\nHigher values = dramatic swing.\nLower values = subtle lean.",
                def.kDrift.cameraScale,
                () -> cfg.kDrift.cameraScale, v -> cfg.kDrift.cameraScale = v,
                0.0f, 10.0f, 0.1f);
        Option<Float> lerpIn    = floatOpt("Camera Lerp In",
                "How fast the camera moves toward the drift offset.\n\nHigher values = snappy, instant follow.\nLower values = smooth, gradual follow.",
                def.kDrift.cameraLerpIn,
                () -> cfg.kDrift.cameraLerpIn, v -> cfg.kDrift.cameraLerpIn = v,
                0.01f, 1.0f, 0.01f);
        Option<Float> lerpOut   = floatOpt("Camera Lerp Out",
                "How fast the camera returns to centre after drift ends.\n\nHigher values = snaps back instantly.\nLower values = slow settle.",
                def.kDrift.cameraLerpOut,
                () -> cfg.kDrift.cameraLerpOut, v -> cfg.kDrift.cameraLerpOut = v,
                0.01f, 1.0f, 0.01f);

        List<Option<?>> deps = List.of(scale, lerpIn, lerpOut);
        toggle.addListener((opt, val) -> deps.forEach(o -> o.setAvailable(val)));
        deps.forEach(o -> o.setAvailable(cfg.kDrift.cameraEnabled));

        List<Option<?>> all = concat(List.of(toggle), deps);
        return new CameraGroup(toggle, deps, all);
    }

    // ── M-Drift group builders ────────────────────────────────────────────────

    private static List<Option<?>> mDriftSlipOptions(MomentumConfig cfg, MomentumConfig def) {
        Option<Float>   slipAngle        = floatOpt("Slip Angle",
                "Maximum sideways slide angle in degrees.\n\nHigher values = big dramatic sideslip.\nLower values = subtle drift.",
                def.mDrift.slipAngle,
                () -> cfg.mDrift.slipAngle, v -> cfg.mDrift.slipAngle = v,
                0.0f, 90.0f, 0.5f);
        Option<Float>   slipConvergeRate = floatOpt("Slip Converge Rate",
                "Fraction of remaining distance closed per tick (exponential).\n\nHigher values = snaps to target quickly.\nLower values = slow ease-in.",
                def.mDrift.slipConvergeRate,
                () -> cfg.mDrift.slipConvergeRate, v -> cfg.mDrift.slipConvergeRate = v,
                0.01f, 1.0f, 0.01f);
        Option<Float>   slipDecay        = floatOpt("Slip Decay",
                "How fast the drift angle fades after release.\n\nHigher values = car straightens out quickly.\nLower values = drift lingers.",
                def.mDrift.slipDecay,
                () -> cfg.mDrift.slipDecay, v -> cfg.mDrift.slipDecay = v,
                0.0f, 10.0f, 0.1f);
        Option<Float>   slipDecaySpeedRef = floatOpt("Slip Decay Speed Ref",
                "Reference speed for speed-adjusted decay.\n\nHigher values = drift lingers longer at high speed.\nLower values = decay rate stays constant.",
                def.mDrift.slipDecaySpeedRef,
                () -> cfg.mDrift.slipDecaySpeedRef, v -> cfg.mDrift.slipDecaySpeedRef = v,
                0.0f, 2.0f, 0.01f);
        Option<Boolean> constantAngle    = boolOpt("Constant Angle",
                "Lock the slip angle at the configured maximum immediately, skipping the ease-in ramp. On = no build-up.",
                def.mDrift.constantAngle,
                () -> cfg.mDrift.constantAngle, v -> cfg.mDrift.constantAngle = v);

        // constantAngle=true → slipConvergeRate is bypassed, hide it
        constantAngle.addListener((opt, val) -> slipConvergeRate.setAvailable(!val));
        slipConvergeRate.setAvailable(!cfg.mDrift.constantAngle);

        // Order: slipAngle[0], slipConvergeRate[1], slipDecay[2], slipDecaySpeedRef[3], constantAngle[4]
        return List.of(slipAngle, slipConvergeRate, slipDecay, slipDecaySpeedRef, constantAngle);
    }

    private static List<Option<?>> mDriftSteeringOptions(MomentumConfig cfg, MomentumConfig def) {
        return List.of(
                floatOpt("Steer Sensitivity",
                        "Exponent on the steering accumulator.\n\nHigher values = requires sustained steering to reach full angle.\nLower values = slip angle builds more linearly.",
                        def.mDrift.steerSensitivity,
                        () -> cfg.mDrift.steerSensitivity, v -> cfg.mDrift.steerSensitivity = v,
                        0.1f, 10.0f, 0.1f),
                floatOpt("Steer Build Rate",
                        "How fast the steering accumulator climbs per tick.\n\nHigher values = reaches full angle quickly.\nLower values = slip angle builds slowly.",
                        def.mDrift.steerBuildRate,
                        () -> cfg.mDrift.steerBuildRate, v -> cfg.mDrift.steerBuildRate = v,
                        0.001f, 0.5f, 0.001f),
                floatOpt("Steer Decay Rate",
                        "How fast the accumulator falls when steering is released.\n\nHigher values = slip fades quickly.\nLower values = slip holds longer without input.",
                        def.mDrift.steerDecayRate,
                        () -> cfg.mDrift.steerDecayRate, v -> cfg.mDrift.steerDecayRate = v,
                        0.001f, 0.5f, 0.001f),
                floatOpt("Steer Threshold",
                        "Minimum steering input to maintain the drift angle.\n\nHigher values = requires clear steering to keep drift alive.\nLower values = small input is enough.",
                        def.mDrift.steerThreshold,
                        () -> cfg.mDrift.steerThreshold, v -> cfg.mDrift.steerThreshold = v,
                        0.0f, 1.0f, 0.05f)
        );
    }

    private static List<Option<?>> mDriftTriggerOptions(MomentumConfig cfg, MomentumConfig def) {
        return List.of(
                floatOpt("Min Speed (km/h)",
                        "Minimum car speed to trigger an M-drift.\n\nHigher values = requires more speed to start.\nLower values = can start from low speed.",
                        def.mDrift.minSpeedKmh,
                        () -> cfg.mDrift.minSpeedKmh, v -> cfg.mDrift.minSpeedKmh = v,
                        0.0f, 200.0f, 5.0f),
                intOpt("Min Hold Ticks",
                        "M must be held this many ticks before drift can start.\n\nHigher values = adds a deliberate delay before starting.\nLower values = triggers almost instantly.",
                        def.mDrift.minHoldTicks,
                        () -> cfg.mDrift.minHoldTicks, v -> cfg.mDrift.minHoldTicks = v,
                        0, 100, 1),
                intOpt("Auto Trigger Ticks",
                        "Ticks without steering before drift starts automatically. 0 = disabled.\n\nHigher values = longer wait before auto-triggering.\nLower values = triggers quickly without steering.",
                        def.mDrift.autoTriggerTicks,
                        () -> cfg.mDrift.autoTriggerTicks, v -> cfg.mDrift.autoTriggerTicks = v,
                        0, 200, 1),
                boolOpt("Brake Enabled",
                        "When on, braking is applied while M is held but no drift has started yet.",
                        def.mDrift.brakeEnabled,
                        () -> cfg.mDrift.brakeEnabled, v -> cfg.mDrift.brakeEnabled = v)
        );
    }

    private static BoostGroup mDriftBoostGroup(MomentumConfig cfg, MomentumConfig def) {
        Option<Boolean> toggle   = boolOpt("Boost Enabled",
                "Grant a speed burst when M-drift ends cleanly. Turn off to disable the boost entirely.",
                def.mDrift.boostEnabled,
                () -> cfg.mDrift.boostEnabled, v -> cfg.mDrift.boostEnabled = v);
        Option<Float>   boost    = floatOpt("Boost",
                "Engine speed added instantly on clean release.\n\nHigher values = large burst of speed.\nLower values = small nudge.",
                def.mDrift.boost,
                () -> cfg.mDrift.boost, v -> cfg.mDrift.boost = v,
                0.0f, 0.5f, 0.005f);
        Option<Integer> duration = intOpt("Boost Duration",
                "Ticks the boost animation plays (20 = 1 s).\n\nHigher values = longer animation.\nLower values = brief flash.",
                def.mDrift.boostDuration,
                () -> cfg.mDrift.boostDuration, v -> cfg.mDrift.boostDuration = v,
                0, 200, 1);
        Option<Integer> minTicks = intOpt("Min Ticks",
                "Drift must last this long to earn the boost.\n\nHigher values = only sustained drifts are rewarded.\nLower values = short drifts qualify.",
                def.mDrift.minTicks,
                () -> cfg.mDrift.minTicks, v -> cfg.mDrift.minTicks = v,
                0, 200, 1);

        List<Option<?>> deps = List.of(boost, duration, minTicks);
        toggle.addListener((opt, val) -> deps.forEach(o -> o.setAvailable(val)));
        deps.forEach(o -> o.setAvailable(cfg.mDrift.boostEnabled));

        List<Option<?>> all = concat(List.of(toggle), deps);
        return new BoostGroup(toggle, deps, all);
    }

    private static CameraGroup mDriftCameraGroup(MomentumConfig cfg, MomentumConfig def) {
        Option<Boolean> toggle  = boolOpt("Camera Enabled",
                "Swings the camera to follow the drift angle. Turn off to keep the camera fixed.",
                def.mDrift.cameraEnabled,
                () -> cfg.mDrift.cameraEnabled, v -> cfg.mDrift.cameraEnabled = v);
        Option<Float> scale     = floatOpt("Camera Scale",
                "How much the camera yaw exaggerates the slip angle.\n\nHigher values = dramatic swing.\nLower values = subtle lean.",
                def.mDrift.cameraScale,
                () -> cfg.mDrift.cameraScale, v -> cfg.mDrift.cameraScale = v,
                0.0f, 10.0f, 0.1f);
        Option<Float> lerpIn    = floatOpt("Camera Lerp In",
                "How fast the camera moves toward the drift offset.\n\nHigher values = snappy, instant follow.\nLower values = smooth, gradual follow.",
                def.mDrift.cameraLerpIn,
                () -> cfg.mDrift.cameraLerpIn, v -> cfg.mDrift.cameraLerpIn = v,
                0.01f, 1.0f, 0.01f);
        Option<Float> lerpOut   = floatOpt("Camera Lerp Out",
                "How fast the camera returns to centre after drift ends.\n\nHigher values = snaps back instantly.\nLower values = slow settle.",
                def.mDrift.cameraLerpOut,
                () -> cfg.mDrift.cameraLerpOut, v -> cfg.mDrift.cameraLerpOut = v,
                0.01f, 1.0f, 0.01f);

        List<Option<?>> deps = List.of(scale, lerpIn, lerpOut);
        toggle.addListener((opt, val) -> deps.forEach(o -> o.setAvailable(val)));
        deps.forEach(o -> o.setAvailable(cfg.mDrift.cameraEnabled));

        List<Option<?>> all = concat(List.of(toggle), deps);
        return new CameraGroup(toggle, deps, all);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private static OptionGroup buildGroupFromList(String name, List<Option<?>> opts) {
        OptionGroup.Builder b = OptionGroup.createBuilder().name(Text.literal(name));
        opts.forEach(b::option);
        return b.build();
    }

    @SafeVarargs
    private static List<Option<?>> concat(List<Option<?>>... lists) {
        List<Option<?>> result = new ArrayList<>();
        for (List<Option<?>> l : lists) result.addAll(l);
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Option<Float> floatOpt(String name, String desc, float def,
            java.util.function.Supplier<Float> get, java.util.function.Consumer<Float> set,
            float min, float max, float step) {
        String defStr = String.format("%.3f", def);
        Text descLine = desc.isEmpty()
                ? Text.literal("Default: " + defStr)
                : Text.literal(desc + "\n\nDefault: " + defStr);
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
                : Text.literal(desc + "\n\nDefault: " + def);
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
                : Text.literal(desc + "\n\nDefault: " + def);
        return Option.<Boolean>createBuilder()
                .name(Text.literal(name))
                .description(OptionDescription.of(descLine))
                .binding(def, get, set)
                .controller(BooleanControllerBuilder::create)
                .build();
    }

    // Space + A–Z, in that order — covers all practical remapping choices.
    private static final List<Integer> REMAP_KEYS;
    static {
        List<Integer> keys = new ArrayList<>();
        keys.add(32); // Space
        for (int k = 65; k <= 90; k++) keys.add(k); // A–Z
        REMAP_KEYS = java.util.Collections.unmodifiableList(keys);
    }

    private static Option<Integer> keyOpt(String name, String desc, int defCode,
            java.util.function.Supplier<Integer> get, java.util.function.Consumer<Integer> set) {
        String defName = InputUtil.Type.KEYSYM.createFromCode(defCode).getLocalizedText().getString();
        Text descLine = desc.isEmpty()
                ? Text.literal("Default: " + defName)
                : Text.literal(desc + "\n\nDefault: " + defName);
        return Option.<Integer>createBuilder()
                .name(Text.literal(name))
                .description(OptionDescription.of(descLine))
                .binding(defCode, get, set)
                .controller(opt -> CyclingListControllerBuilder.create(opt)
                        .values(REMAP_KEYS)
                        .formatValue(code -> InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText()))
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

    // ── Records ───────────────────────────────────────────────────────────────

    private record BoostGroup(
            Option<Boolean> toggle,
            List<Option<?>> dependents,
            List<Option<?>> all
    ) {}

    private record CameraGroup(
            Option<Boolean> toggle,
            List<Option<?>> dependents,
            List<Option<?>> all
    ) {}
}
