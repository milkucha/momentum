package io.github.milkucha.momentum.hud;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.MomentumDriftState;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

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

        int panelX = cfg.hudX >= 0
                ? cfg.hudX
                : screenW - FRAME_W - cfg.hudMarginRight;
        int panelY = cfg.hudY >= 0
                ? cfg.hudY
                : screenH - FRAME_H - cfg.hudMarginBottom;

        double speedKmh = auto.getEffectiveSpeed() * TO_KMH;

        // ── Bar — drawn first (behind frame) ──────────────────────────────────
        int barFrame = Math.min((int)(speedKmh / 20.0), TEX_BAR.length - 1);
        drawScaled(graphics, TEX_BAR[barFrame],
                panelX + cfg.hudBarOffsetX, panelY + cfg.hudBarOffsetY,
                BAR_W, BAR_H, cfg.hudBarScale);

        // ── Animated object — drawn second (behind frame) ─────────────────────
        int animFrame = 0;
        if (auto instanceof SteeringDebugAccessor acc && acc.momentum$isAccelerating()) {
            animFrame = (int)((client.world.getTime() / 2L) % TEX_ANIM.length);
        }
        drawScaled(graphics, TEX_ANIM[animFrame],
                panelX + cfg.hudAnimOffsetX, panelY + cfg.hudAnimOffsetY,
                ANIM_W, ANIM_H, cfg.hudAnimScale);

        // ── Frame — drawn on top ───────────────────────────────────────────────
        graphics.drawTexture(TEX_FRAME,
                panelX, panelY, 0, 0, FRAME_W, FRAME_H, FRAME_W, FRAME_H);

        // ── Speed text ────────────────────────────────────────────────────────
        String speedStr = String.format("%.0f", speedKmh);
        String unitStr  = " km/h";
        int speedW = client.textRenderer.getWidth(speedStr);
        graphics.drawText(client.textRenderer, speedStr,
                panelX + cfg.hudSpeedTextOffsetX, panelY + cfg.hudSpeedTextOffsetY,
                COL_TEXT, true);
        graphics.drawText(client.textRenderer, unitStr,
                panelX + cfg.hudSpeedTextOffsetX + speedW, panelY + cfg.hudSpeedTextOffsetY,
                COL_UNIT, true);

        // ── Debug overlay — separate panel, upper-right by default ────────────
        if (cfg.debugHud && auto instanceof SteeringDebugAccessor dbg) {
            boolean jHeld   = MomentumDriftState.driftKeyHeld;
            boolean drifting = dbg.momentum$isDrifting();
            boolean onGround = dbg.momentum$isOnGround();
            float   steering = dbg.momentum$getSteering();
            float   hSpd     = dbg.momentum$getHSpeed();

            // Condition breakdown: what's blocking drift start
            String condStr = (steering != 0 ? "S" : "s")
                           + (hSpd > 0.4f   ? "V" : "v")
                           + (onGround       ? "G" : "g")
                           + (!drifting      ? "D" : "d");  // uppercase = condition met

            boolean kDrift = dbg.momentum$isKDriftActive();

            String line1 = String.format("steer:  %+.3f", steering);
            String line2 = String.format("hSpd:   %.3f",  hSpd);
            String line3 = String.format("angSpd: %+.3f", dbg.momentum$getAngularSpeed());
            String line4 = "J key:  " + (jHeld   ? "YES" : "no");
            String line5 = "drift:  " + (drifting ? "YES" : "no");
            String line6 = "turbo:  " + dbg.momentum$getTurboCharge();
            String line7 = String.format("K drft: %s  %.1f°",
                    kDrift ? "ON " : "off", dbg.momentum$getKDriftOffset());
            String line8 = "cond:   " + condStr + " (SVGd=ok)";

            int dbgW = 120;
            int dbgH = 92;
            int dbgX = cfg.debugHudX >= 0
                    ? cfg.debugHudX
                    : screenW - dbgW - cfg.debugHudMarginRight;
            int dbgY = cfg.debugHudY;

            drawPanel(graphics, dbgX, dbgY, dbgW, dbgH);
            graphics.drawText(client.textRenderer, line1, dbgX + 6, dbgY + 4,  0xFFFFFF55, true);
            graphics.drawText(client.textRenderer, line2, dbgX + 6, dbgY + 13, 0xFF55FFFF, true);
            graphics.drawText(client.textRenderer, line3, dbgX + 6, dbgY + 22, 0xFFFF55FF, true);
            graphics.drawText(client.textRenderer, line4, dbgX + 6, dbgY + 31,
                    jHeld    ? 0xFF55FF55 : 0xFF999999, true);
            graphics.drawText(client.textRenderer, line5, dbgX + 6, dbgY + 40,
                    drifting ? 0xFFFF5555 : 0xFF999999, true);
            graphics.drawText(client.textRenderer, line6, dbgX + 6, dbgY + 49,
                    dbg.momentum$getTurboCharge() > 0 ? 0xFFFFAA00 : 0xFF999999, true);
            graphics.drawText(client.textRenderer, line7, dbgX + 6, dbgY + 58,
                    kDrift ? 0xFF00AAFF : 0xFF999999, true);
            graphics.drawText(client.textRenderer, line8, dbgX + 6, dbgY + 67,
                    condStr.equals("SVGd") ? 0xFF55FF55 : 0xFFFF5555, true);
        }
    }

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
