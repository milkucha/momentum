package io.github.milkucha.momentum.hud;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.config.MomentumConfig;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;

/**
 * MomentumHud — draws the velocimeter panel while the player is riding an AutomobileEntity.
 *
 * ── Speed conversion ──────────────────────────────────────────────────────────
 * AutomobileEntity.getEffectiveSpeed() returns blocks/tick (a double).
 * The original HUD multiplies by 20 (ticks/sec) to get blocks/sec, labelled as "m/s".
 * We multiply by 72 (= 20 ticks/s × 3.6) to get a km/h equivalent, which feels
 * more intuitive as a speedometer readout.
 *
 * ── Layout ────────────────────────────────────────────────────────────────────
 * Position is configurable via momentum.json (hudX, hudY, hudMarginRight, hudMarginBottom).
 * By default the panel sits in the bottom-right corner:
 *
 *   ┌─────────────────────────┐
 *   │  [████████████░░░░░░░░] │  ← speed bar
 *   │       142 km/h          │  ← readout: white number, grey unit
 *   └─────────────────────────┘
 *
 * ── Colors ────────────────────────────────────────────────────────────────────
 * Bar color reflects the same turbo states Automobility's original HUD uses
 * for text color, so the two systems stay visually consistent:
 *
 *   White / green   → normal
 *   Orange          → boost active (boostTimer > 0)
 *   Yellow          → small turbo charge
 *   Cyan            → medium turbo charge
 *   Purple          → large turbo charge
 */
public class MomentumHud {

    private static final int PANEL_WIDTH  = 120;
    private static final int PANEL_HEIGHT = 38;

    private static final int BAR_WIDTH  = 100;
    private static final int BAR_HEIGHT = 8;

    /** blocks/tick → km/h */
    private static final double TO_KMH = 72.0;

    /** Speed at which the bar is completely full */
    private static final double MAX_DISPLAY_KMH = 300.0;

    // Colors (ARGB)
    private static final int COL_PANEL_BG   = 0xAA000000;
    private static final int COL_PANEL_EDGE = 0xFF444444;
    private static final int COL_BAR_BG     = 0xFF222222;
    private static final int COL_TEXT        = 0xFFFFFFFF;
    private static final int COL_UNIT        = 0xFF999999;

    // Bar fill colors — mirroring Automobility's own turbo color thresholds
    private static final int COL_BAR_NORMAL  = 0xFF00CC55;
    private static final int COL_BAR_BOOST   = 0xFFFF6F00; // matches original 0xFF6F00
    private static final int COL_BAR_TURBO_S = 0xFFFFEA4A; // small  (SMALL_TURBO_TIME)
    private static final int COL_BAR_TURBO_M = 0xFF7DE9FF; // medium (MEDIUM_TURBO_TIME)
    private static final int COL_BAR_TURBO_L = 0xFF906EFF; // large  (LARGE_TURBO_TIME)

    public static void render(DrawContext graphics, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null) return;
        Entity vehicle = client.player.getVehicle();
        if (!(vehicle instanceof AutomobileEntity auto)) return;
        if (client.currentScreen != null) return; // hide when a menu is open

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        MomentumConfig cfg = MomentumConfig.get();

        // Resolve panel position from config
        int panelX = cfg.hudX >= 0
                ? cfg.hudX
                : screenW - PANEL_WIDTH - cfg.hudMarginRight;

        int panelY = cfg.hudY >= 0
                ? cfg.hudY
                : screenH - PANEL_HEIGHT - cfg.hudMarginBottom;

        // Speed and state
        double speedKmh = auto.getEffectiveSpeed() * TO_KMH;
        int boostTimer  = auto.getBoostTimer();
        int turbo       = auto.getTurboCharge();

        // Bar color — same thresholds as Automobility's text color logic
        int barColor;
        if (turbo > AutomobileEntity.LARGE_TURBO_TIME)       barColor = COL_BAR_TURBO_L;
        else if (turbo > AutomobileEntity.MEDIUM_TURBO_TIME) barColor = COL_BAR_TURBO_M;
        else if (turbo > AutomobileEntity.SMALL_TURBO_TIME)  barColor = COL_BAR_TURBO_S;
        else if (boostTimer > 0)                             barColor = COL_BAR_BOOST;
        else                                                  barColor = COL_BAR_NORMAL;

        float fraction = (float) Math.min(speedKmh / MAX_DISPLAY_KMH, 1.0);

        // Panel background
        drawPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        // Speed bar
        int barX = panelX + (PANEL_WIDTH - BAR_WIDTH) / 2;
        int barY = panelY + 8;

        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, COL_BAR_BG);
        int fillW = (int) (BAR_WIDTH * fraction);
        if (fillW > 0) {
            graphics.fill(barX, barY, barX + fillW, barY + BAR_HEIGHT, barColor);
        }
        drawBorder(graphics, barX, barY, BAR_WIDTH, BAR_HEIGHT, 0xFF555555);

        // Speed text
        String speedStr = String.format("%.0f", speedKmh);
        String unitStr  = " km/h";
        int textY  = barY + BAR_HEIGHT + 6;
        int speedW = client.textRenderer.getWidth(speedStr);
        int unitW  = client.textRenderer.getWidth(unitStr);
        int textX  = panelX + (PANEL_WIDTH - speedW - unitW) / 2;

        graphics.drawText(client.textRenderer, speedStr, textX,          textY, COL_TEXT, true);
        graphics.drawText(client.textRenderer, unitStr,  textX + speedW, textY, COL_UNIT, true);

        // Debug overlay — shown only when debugHud = true in config
        if (cfg.debugHud && auto instanceof SteeringDebugAccessor dbg) {
            String line1 = String.format("steer:  %+.3f", dbg.momentum$getSteering());
            String line2 = String.format("hSpd:   %.3f",  dbg.momentum$getHSpeed());
            String line3 = String.format("angSpd: %+.3f", dbg.momentum$getAngularSpeed());
            String line4 = "space:  " + (dbg.momentum$isHoldingDrift() ? "YES" : "no");
            String line5 = "drift:  " + (dbg.momentum$isDrifting()     ? "YES" : "no");

            int dbgX = panelX;
            int dbgY = panelY - 54;
            int dbgW = PANEL_WIDTH;
            int dbgH = 52;

            drawPanel(graphics, dbgX, dbgY, dbgW, dbgH);
            graphics.drawText(client.textRenderer, line1, dbgX + 6, dbgY + 4,  0xFFFFFF55, true);
            graphics.drawText(client.textRenderer, line2, dbgX + 6, dbgY + 13, 0xFF55FFFF, true);
            graphics.drawText(client.textRenderer, line3, dbgX + 6, dbgY + 22, 0xFFFF55FF, true);
            graphics.drawText(client.textRenderer, line4, dbgX + 6, dbgY + 31,
                    dbg.momentum$isHoldingDrift() ? 0xFF55FF55 : 0xFF999999, true);
            graphics.drawText(client.textRenderer, line5, dbgX + 6, dbgY + 40,
                    dbg.momentum$isDrifting() ? 0xFFFF5555 : 0xFF999999, true);
        }
    }

    private static void drawPanel(DrawContext g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, COL_PANEL_BG);
        drawBorder(g, x, y, w, h, COL_PANEL_EDGE);
    }

    private static void drawBorder(DrawContext g, int x, int y, int w, int h, int color) {
        g.fill(x,         y,         x + w,     y + 1,     color); // top
        g.fill(x,         y + h - 1, x + w,     y + h,     color); // bottom
        g.fill(x,         y,         x + 1,     y + h,     color); // left
        g.fill(x + w - 1, y,         x + w,     y + h,     color); // right
    }
}