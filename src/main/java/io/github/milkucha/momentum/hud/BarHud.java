package io.github.milkucha.momentum.hud;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import io.github.milkucha.momentum.accessor.SteeringDebugAccessor;
import io.github.milkucha.momentum.config.MomentumConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;

/**
 * BarHud — minimal procedural velocimeter.
 *
 * Renders a horizontal row of thin vertical bar segments that fill left-to-right
 * proportional to speed. No textures — drawn entirely with fill() and drawText().
 *
 * Enable via  hud.useBarHud = true  in momentum.json.
 * Layout tuned under the "barHud" config section.
 *
 * Layout maths:
 *   numBars = floor((totalWidth + barSpacing) / (barWidth + barSpacing))
 *   filledBars = round(clamp(speedKmh / maxSpeedKmh, 0, 1) * numBars)
 *
 * With defaults (totalWidth=210, barWidth=3, barSpacing=2):
 *   numBars = (210 + 2) / (3 + 2) = 42 segments
 *   actual pixels used = 42×3 + 41×2 = 208 px  (fits inside 210)
 */
public class BarHud {

    private static final double TO_KMH = 72.0;

    public static void render(DrawContext graphics, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Entity vehicle = client.player.getVehicle();
        if (!(vehicle instanceof AutomobileEntity auto)) return;
        if (client.currentScreen != null) return;

        MomentumConfig.BarHud b = MomentumConfig.get().barHud;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        int originX = b.x >= 0 ? b.x : screenW - b.totalWidth - b.marginRight;
        int originY = b.y >= 0 ? b.y : screenH - b.totalHeight - b.marginBottom;

        double speedKmh = auto.getEffectiveSpeed() * TO_KMH;

        // Separate engine vs boost contributions so boost bars can be coloured differently.
        // engineSpeed is the base; hSpeed = engineSpeed + boostSpeed.
        double engineKmh = (auto instanceof SteeringDebugAccessor acc)
                ? acc.momentum$getEngineSpeed() * TO_KMH
                : speedKmh;
        engineKmh = Math.max(0.0, engineKmh); // clamp: engineSpeed can go negative in reverse

        // How many segments fit and how many should be lit
        int numBars         = (b.totalWidth + b.barSpacing) / (b.barWidth + b.barSpacing);
        int engineBars      = Math.max(1, (int) Math.round(Math.min(engineKmh / b.maxSpeedKmh, 1.0) * numBars));
        int totalFilledBars = Math.max(1, (int) Math.round(Math.min(speedKmh  / b.maxSpeedKmh, 1.0) * numBars));

        for (int i = 0; i < totalFilledBars; i++) {
            int bx    = originX + i * (b.barWidth + b.barSpacing);
            int color = i < engineBars ? b.barColor : b.boostBarColor;
            graphics.fill(bx, originY, bx + b.barWidth, originY + b.totalHeight, color);
        }

        // Speed text — positioned relative to bar origin via textOffsetX/Y
        String speedStr = String.format("%.0f km/h", speedKmh);
        graphics.drawText(client.textRenderer, speedStr,
                originX + b.textOffsetX,
                originY + b.textOffsetY,
                b.textColor, true);
    }
}
