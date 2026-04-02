package io.github.milkucha.momentum.hud;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * BarHud — minimal procedural velocimeter.
 *
 * Renders a horizontal row of thin vertical bar segments that fill left-to-right
 * proportional to speed. No textures — drawn entirely with fill() and drawText().
 *
 * Layout tuned under the "barHud" config section.
 *
 * Layout maths:
 *   numBars = floor((totalWidth + barSpacing) / (barWidth + barSpacing))
 *   filledBars = round(clamp(speedKmh / maxSpeedKmh, 0, 1) * numBars)
 *
 * With defaults (totalWidth=90, barWidth=5, barSpacing=2):
 *   numBars = (90 + 2) / (5 + 2) = 13 segments
 */
public class BarHud {

    private static final double TO_KMH = 72.0;

    // Debug panel colors
    private static final int COL_PANEL_BG   = 0xAA000000;
    private static final int COL_PANEL_EDGE = 0xFF444444;

    public static void render(MatrixStack matrices, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Entity vehicle = client.player.getVehicle();
        if (!(vehicle instanceof AutomobileEntity auto)) return;
        if (client.currentScreen != null) return;

        MomentumConfig.BarHud b = MomentumConfig.get().barHud;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        int originX = b.x >= 0 ? b.x : screenW - b.totalWidth - (int)(screenW * b.xFraction);
        int originY = b.y >= 0 ? b.y : screenH - b.totalHeight - b.marginBottom;

        double speedKmh = auto.getEffectiveSpeed() * TO_KMH;

        // Separate engine vs boost contributions so boost bars can be coloured differently.
        double engineKmh = (auto instanceof SteeringDebugAccessor acc)
                ? acc.momentum$getEngineSpeed() * TO_KMH
                : speedKmh;
        engineKmh = Math.max(0.0, engineKmh);

        int numBars         = (b.totalWidth + b.barSpacing) / (b.barWidth + b.barSpacing);
        int engineBars      = Math.max(1, (int) Math.round(Math.min(engineKmh / b.maxSpeedKmh, 1.0) * numBars));
        int totalFilledBars = Math.max(1, (int) Math.round(Math.min(speedKmh  / b.maxSpeedKmh, 1.0) * numBars));

        for (int i = 0; i < totalFilledBars; i++) {
            int bx    = originX + i * (b.barWidth + b.barSpacing);
            int color = i < engineBars ? b.barColor : b.boostBarColor;
            DrawableHelper.fill(matrices, bx, originY, bx + b.barWidth, originY + b.totalHeight, color);
        }

        String speedStr = String.format("%.0f km/h", speedKmh);
        client.textRenderer.drawWithShadow(matrices, speedStr,
                (float)(originX + b.textOffsetX),
                (float)(originY + b.textOffsetY),
                b.textColor);
    }

    // ── Debug overlay ─────────────────────────────────────────────────────────

    public static void renderDebug(MatrixStack matrices, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        Entity vehicle = client.player.getVehicle();
        if (!(vehicle instanceof AutomobileEntity auto)) return;
        if (client.currentScreen != null) return;

        MomentumConfig cfg = MomentumConfig.get();
        if (!cfg.barHud.debug) return;
        if (!(auto instanceof SteeringDebugAccessor dbg)) return;

        float   steering  = dbg.momentum$getSteering();
        float   hSpd      = dbg.momentum$getHSpeed();
        float   angSpd    = dbg.momentum$getAngularSpeed();
        float   engSpd    = dbg.momentum$getEngineSpeed();
        boolean drifting  = dbg.momentum$isDrifting();
        boolean onGround  = dbg.momentum$isOnGround();

        List<String>  texts  = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        row(texts, colors, "GENERAL",                                           0xFF999999);
        row(texts, colors, String.format("steer:  %+.3f", steering),            0xFFFFFF55);
        row(texts, colors, String.format("hSpd:   %.3f",  hSpd),                0xFF55FFFF);
        row(texts, colors, String.format("engSpd: %.3f",  engSpd),              0xFFAAAAAA);
        row(texts, colors, String.format("angSpd: %+.3f", angSpd),              0xFFFF55FF);
        row(texts, colors, "ground: " + yn(onGround),     onGround ? 0xFF55FF55 : 0xFF999999);
        row(texts, colors, "drift:  " + yn(drifting),     drifting ? 0xFF55FF55 : 0xFF999999);

        int lineH  = 9;
        int padX   = 6;
        int padY   = 4;
        int dbgW   = 152;
        int dbgH   = padY * 2 + texts.size() * lineH;

        int screenW = client.getWindow().getScaledWidth();
        int dbgX = cfg.barHud.debugX >= 0
                ? cfg.barHud.debugX
                : screenW - dbgW - (int)(screenW * cfg.barHud.debugXFraction);
        int dbgY = cfg.barHud.debugY;

        drawPanel(matrices, dbgX, dbgY, dbgW, dbgH);
        for (int i = 0; i < texts.size(); i++) {
            client.textRenderer.drawWithShadow(matrices, texts.get(i),
                    (float)(dbgX + padX), (float)(dbgY + padY + i * lineH), colors.get(i));
        }
    }

    private static void row(List<String> texts, List<Integer> colors, String text, int color) {
        texts.add(text);
        colors.add(color);
    }

    private static String yn(boolean v) { return v ? "YES" : "no"; }

    private static void drawPanel(MatrixStack matrices, int x, int y, int w, int h) {
        DrawableHelper.fill(matrices, x, y, x + w, y + h, COL_PANEL_BG);
        DrawableHelper.fill(matrices, x,         y,         x + w,     y + 1,     COL_PANEL_EDGE); // top
        DrawableHelper.fill(matrices, x,         y + h - 1, x + w,     y + h,     COL_PANEL_EDGE); // bottom
        DrawableHelper.fill(matrices, x,         y,         x + 1,     y + h,     COL_PANEL_EDGE); // left
        DrawableHelper.fill(matrices, x + w - 1, y,         x + w,     y + h,     COL_PANEL_EDGE); // right
    }
}
