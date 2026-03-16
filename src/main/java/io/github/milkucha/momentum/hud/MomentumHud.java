package io.github.milkucha.momentum.hud;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.MomentumDriftState;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

/**
 * MomentumHud — texture-based speedometer HUD.
 *
 * ── Textures (drop PNGs into assets/momentum/textures/hud/) ──────────────────
 *
 *   frame.png       — static background frame, drawn first
 *
 *   bar_0.png       — bar sprite at   0–19 km/h
 *   bar_1.png       — bar sprite at  20–39 km/h
 *   bar_2.png       — bar sprite at  40–59 km/h
 *   bar_3.png       — bar sprite at  60–79 km/h
 *   bar_4.png       — bar sprite at  80+  km/h
 *
 *   anim_0.png      — animated object, frame 0  (0–19 ticks into cycle)
 *   anim_1.png      — animated object, frame 1  (20–39 ticks)
 *   anim_2.png      — animated object, frame 2  (40–59 ticks)
 *   anim_3.png      — animated object, frame 3  (60–79 ticks)
 *   (cycle length = 4 × 20 = 80 ticks)
 *
 * ── Layout constants ──────────────────────────────────────────────────────────
 * Adjust FRAME_W/H, BAR_W/H, ANIM_W/H to match your actual PNG dimensions.
 * Adjust BAR_OFFSET_* and ANIM_OFFSET_* to position elements within the frame.
 *
 * ── Speed conversion ──────────────────────────────────────────────────────────
 * getEffectiveSpeed() → blocks/tick.  × 72 (= 20 t/s × 3.6) → km/h.
 */
public class MomentumHud {

    // ── Texture dimensions — edit to match your PNG files ─────────────────────
    private static final int FRAME_W = 76;
    private static final int FRAME_H = 24;

    private static final int BAR_W = 53;
    private static final int BAR_H = 10;

    private static final int ANIM_W = 17;
    private static final int ANIM_H = 16;

    // Offsets are now read from config (hudBarOffsetX/Y, hudAnimOffsetX/Y, hudSpeedTextOffsetX/Y)

    // ── Texture identifiers ───────────────────────────────────────────────────
    private static final Identifier TEX_FRAME =
            new Identifier("momentum", "textures/hud/frame.png");

    /** 5 frames — one per 20 km/h bracket: 0-19, 20-39, 40-59, 60-79, 80+ */
    private static final Identifier[] TEX_BAR = {
        new Identifier("momentum", "textures/hud/bar_0.png"),
        new Identifier("momentum", "textures/hud/bar_1.png"),
        new Identifier("momentum", "textures/hud/bar_2.png"),
        new Identifier("momentum", "textures/hud/bar_3.png"),
        new Identifier("momentum", "textures/hud/bar_4.png"),
    };

    /** 4 frames — advances one frame every 20 ticks (full cycle = 80 ticks) */
    private static final Identifier[] TEX_ANIM = {
        new Identifier("momentum", "textures/hud/anim_0.png"),
        new Identifier("momentum", "textures/hud/anim_1.png"),
        new Identifier("momentum", "textures/hud/anim_2.png"),
        new Identifier("momentum", "textures/hud/anim_3.png"),
    };

    private static final double TO_KMH = 72.0;

    // Colors for the debug overlay
    private static final int COL_PANEL_BG   = 0xAA000000;
    private static final int COL_PANEL_EDGE = 0xFF444444;
    private static final int COL_TEXT       = 0xFFFFFFFF;
    private static final int COL_UNIT       = 0xFF999999;

    public static void render(DrawContext graphics, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Entity vehicle = client.player.getVehicle();
        if (!(vehicle instanceof AutomobileEntity auto)) return;
        if (client.currentScreen != null) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        MomentumConfig cfg = MomentumConfig.get();

        int panelX = cfg.hud.x >= 0
                ? cfg.hud.x
                : screenW - FRAME_W - cfg.hud.marginRight;
        int panelY = cfg.hud.y >= 0
                ? cfg.hud.y
                : screenH - FRAME_H - cfg.hud.marginBottom;

        double speedKmh = auto.getEffectiveSpeed() * TO_KMH;

        // ── Bar — drawn first (behind frame) ──────────────────────────────────
        int barFrame = Math.min((int)(speedKmh / 20.0), TEX_BAR.length - 1);
        drawScaled(graphics, TEX_BAR[barFrame],
                panelX + cfg.hud.barOffsetX, panelY + cfg.hud.barOffsetY,
                BAR_W, BAR_H, cfg.hud.barScale);

        // ── Animated object — drawn second (behind frame) ─────────────────────
        int animFrame = 0;
        if (auto instanceof SteeringDebugAccessor acc && acc.momentum$isAccelerating()) {
            animFrame = (int)((client.world.getTime() / 2L) % TEX_ANIM.length);
        }
        drawScaled(graphics, TEX_ANIM[animFrame],
                panelX + cfg.hud.animOffsetX, panelY + cfg.hud.animOffsetY,
                ANIM_W, ANIM_H, cfg.hud.animScale);

        // ── Frame — drawn on top ───────────────────────────────────────────────
        graphics.drawTexture(TEX_FRAME,
                panelX, panelY, 0, 0, FRAME_W, FRAME_H, FRAME_W, FRAME_H);

        // ── Speed text ────────────────────────────────────────────────────────
        String speedStr = String.format("%.0f", speedKmh);
        String unitStr  = " km/h";
        int speedW = client.textRenderer.getWidth(speedStr);
        graphics.drawText(client.textRenderer, speedStr,
                panelX + cfg.hud.speedTextOffsetX, panelY + cfg.hud.speedTextOffsetY,
                COL_TEXT, true);
        graphics.drawText(client.textRenderer, unitStr,
                panelX + cfg.hud.speedTextOffsetX + speedW, panelY + cfg.hud.speedTextOffsetY,
                COL_UNIT, true);

    }

    // ── Debug overlay ─────────────────────────────────────────────────────────
    // Called from MomentumClient regardless of which HUD mode is active.
    public static void renderDebug(DrawContext graphics, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        Entity vehicle = client.player.getVehicle();
        if (!(vehicle instanceof AutomobileEntity auto)) return;
        if (client.currentScreen != null) return;

        MomentumConfig cfg = MomentumConfig.get();
        if (!cfg.hud.debug) return;
        if (!(auto instanceof SteeringDebugAccessor dbg)) return;

        // ── Live state ────────────────────────────────────────────────────────
        float   steering  = dbg.momentum$getSteering();
        float   hSpd      = dbg.momentum$getHSpeed();
        float   angSpd    = dbg.momentum$getAngularSpeed();
        float   engSpd    = dbg.momentum$getEngineSpeed();
        boolean drifting  = dbg.momentum$isDrifting();
        boolean onGround  = dbg.momentum$isOnGround();
        boolean braking   = dbg.momentum$isBraking();
        int     turbo     = dbg.momentum$getTurboCharge();
        boolean kActive   = dbg.momentum$isKDriftActive();
        float   kAngle    = dbg.momentum$getKDriftOffset();
        boolean mActive   = dbg.momentum$isMDriftActive();
        float   mAngle    = dbg.momentum$getMDriftOffset();

        boolean jHeld = MomentumDriftState.driftKeyHeld;
        boolean kHeld = MomentumDriftState.kDriftKeyHeld;
        boolean mHeld = MomentumDriftState.mKeyHeld;
        boolean nHeld = MomentumDriftState.nDriftKeyHeld;
        boolean oHeld = MomentumDriftState.oKeyHeld;

        List<String>  texts  = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (jHeld) {
            addJLines(texts, colors, cfg, steering, hSpd, turbo, drifting, onGround);
        } else if (kHeld) {
            addKLines(texts, colors, cfg, steering, hSpd, kActive, kAngle);
        } else if (mHeld) {
            addMLines(texts, colors, cfg, steering, hSpd, mActive, mAngle);
        } else if (nHeld) {
            addNLines(texts, colors, cfg, hSpd, engSpd, drifting, braking);
        } else if (oHeld) {
            MomentumConfig.ODrift.Profile profile = cfg.oDrift.profile;
            row(texts, colors, "O-DRIFT \u2192 " + profile.name(), 0xFFCCCCCC);
            switch (profile) {
                case J -> addJLines(texts, colors, cfg, steering, hSpd, turbo, drifting, onGround);
                case K -> addKLines(texts, colors, cfg, steering, hSpd, kActive, kAngle);
                case M -> addMLines(texts, colors, cfg, steering, hSpd, mActive, mAngle);
            }
        } else {
            row(texts, colors, "GENERAL",                                           0xFF999999);
            row(texts, colors, String.format("steer:  %+.3f", steering),            0xFFFFFF55);
            row(texts, colors, String.format("hSpd:   %.3f",  hSpd),                0xFF55FFFF);
            row(texts, colors, String.format("engSpd: %.3f",  engSpd),              0xFFAAAAAA);
            row(texts, colors, String.format("angSpd: %+.3f", angSpd),              0xFFFF55FF);
            row(texts, colors, "ground: " + yn(onGround),     onGround ? 0xFF55FF55 : 0xFF999999);
            row(texts, colors, "drift:  " + yn(drifting),     drifting ? 0xFF55FF55 : 0xFF999999);
        }

        int lineH = 9;
        int padX  = 6;
        int padY  = 4;
        int dbgW  = 152;
        int dbgH  = padY * 2 + texts.size() * lineH;

        int screenW = client.getWindow().getScaledWidth();
        int dbgX = cfg.hud.debugX >= 0
                ? cfg.hud.debugX
                : screenW - dbgW - cfg.hud.debugMarginRight;
        int dbgY = cfg.hud.debugY;

        drawPanel(graphics, dbgX, dbgY, dbgW, dbgH);
        for (int i = 0; i < texts.size(); i++) {
            graphics.drawText(client.textRenderer, texts.get(i),
                    dbgX + padX, dbgY + padY + i * lineH, colors.get(i), true);
        }
    }

    private static void row(List<String> texts, List<Integer> colors, String text, int color) {
        texts.add(text);
        colors.add(color);
    }

    private static void addJLines(List<String> texts, List<Integer> colors,
            MomentumConfig cfg, float steering, float hSpd, int turbo,
            boolean drifting, boolean onGround) {
        row(texts, colors, "J-DRIFT",                                               0xFFFFAA00);
        row(texts, colors, String.format("steer:   %+.3f", steering),               0xFFFFFF55);
        row(texts, colors, String.format("hSpd:    %.3f",  hSpd),                   0xFF55FFFF);
        row(texts, colors, "drifting: " + yn(drifting),    drifting  ? 0xFF55FF55 : 0xFF999999);
        row(texts, colors, "ground:   " + yn(onGround),    onGround  ? 0xFF55FF55 : 0xFF999999);
        row(texts, colors, "turbo: " + turbo,              turbo > 0 ? 0xFFFFAA00 : 0xFF999999);
    }

    private static void addKLines(List<String> texts, List<Integer> colors,
            MomentumConfig cfg, float steering, float hSpd,
            boolean kActive, float kAngle) {
        row(texts, colors, "K-DRIFT",                                               0xFF55AAFF);
        row(texts, colors, String.format("steer:   %+.3f", steering),               0xFFFFFF55);
        row(texts, colors, String.format("hSpd:    %.3f",  hSpd),                   0xFF55FFFF);
        row(texts, colors, "active:  " + yn(kActive),      kActive   ? 0xFF55FF55 : 0xFF999999);
        row(texts, colors, String.format("angle:   %+.2f", kAngle),                 0xFFFF55FF);
        row(texts, colors, String.format("target:  %.1f",  cfg.kDrift.slipAngle),   0xFFAAAAAA);
    }

    private static void addMLines(List<String> texts, List<Integer> colors,
            MomentumConfig cfg, float steering, float hSpd,
            boolean mActive, float mAngle) {
        row(texts, colors, "M-DRIFT",                                               0xFFFF55AA);
        row(texts, colors, String.format("steer:   %+.3f", steering),               0xFFFFFF55);
        row(texts, colors, String.format("hSpd:    %.3f",  hSpd),                   0xFF55FFFF);
        row(texts, colors, "active:  " + yn(mActive),      mActive   ? 0xFF55FF55 : 0xFF999999);
        row(texts, colors, String.format("angle:   %+.2f", mAngle),                 0xFFFF55FF);
        row(texts, colors, String.format("target:  %.1f",  cfg.mDrift.slipAngle),   0xFFAAAAAA);
    }

    private static void addNLines(List<String> texts, List<Integer> colors,
            MomentumConfig cfg, float hSpd, float engSpd,
            boolean drifting, boolean braking) {
        row(texts, colors, "N-DRIFT",                                               0xFF55FF55);
        row(texts, colors, String.format("hSpd:    %.3f",  hSpd),                   0xFF55FFFF);
        row(texts, colors, String.format("engSpd:  %.3f",  engSpd),                 0xFFAAAAAA);
        row(texts, colors, "drifting: " + yn(drifting),    drifting  ? 0xFF55FF55 : 0xFF999999);
        row(texts, colors, "braking:  " + yn(braking),     braking   ? 0xFFFF5555 : 0xFF999999);
        row(texts, colors, "brakeTicks: " + cfg.nDrift.brakeTicks,                  0xFFAAAAAA);
    }

    private static String yn(boolean v) { return v ? "YES" : "no"; }

    private static void drawScaled(DrawContext g, Identifier tex, int x, int y, int w, int h, float scale) {
        var matrices = g.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0);
        matrices.scale(scale, scale, 1f);
        g.drawTexture(tex, 0, 0, 0, 0, w, h, w, h);
        matrices.pop();
    }

    private static void drawPanel(DrawContext g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, COL_PANEL_BG);
        g.fill(x,         y,         x + w,     y + 1,     COL_PANEL_EDGE); // top
        g.fill(x,         y + h - 1, x + w,     y + h,     COL_PANEL_EDGE); // bottom
        g.fill(x,         y,         x + 1,     y + h,     COL_PANEL_EDGE); // left
        g.fill(x + w - 1, y,         x + w,     y + h,     COL_PANEL_EDGE); // right
    }
}
