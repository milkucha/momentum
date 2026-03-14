# Momentum — Automobility Addon

A companion mod for [Automobility](https://modrinth.com/mod/automobility) (1.20.1) that improves car movement feel and adds a better HUD.

## Features

### Improved Movement
- **Smoother coasting** — cars roll to a stop gradually instead of snapping to a halt in 2 ticks
- **Tunable acceleration** — scale how punchy the acceleration feels near top speed

### Better HUD
- Replaces the plain `4.23 m/s` text with a speed panel (bottom-right corner by default)
- Speed bar that fills left-to-right and changes colour with turbo state (matching Automobility's own colour logic)
- Readout in km/h
- Fully configurable position

## Configuration

On first run, `momentum.json` is created in your `.minecraft/config/` folder:

```json
{
  "coastDecay": 0.004,
  "accelerationScale": 0.85,
  "hudX": -1,
  "hudY": -1,
  "hudMarginRight": 10,
  "hudMarginBottom": 10
}
```

| Field | Default | Description |
|---|---|---|
| `coastDecay` | `0.004` | Speed lost per tick when coasting. `0.025` = original Automobility feel. Lower = more momentum. |
| `accelerationScale` | `0.85` | Scales acceleration near top speed. `1.0` = unchanged. Lower = gentler top-end. |
| `hudX` | `-1` | Fixed X position in pixels from left edge. `-1` = use right-anchor instead. |
| `hudY` | `-1` | Fixed Y position in pixels from top edge. `-1` = use bottom-anchor instead. |
| `hudMarginRight` | `10` | Distance from right edge (when `hudX` is `-1`). |
| `hudMarginBottom` | `10` | Distance from bottom edge (when `hudY` is `-1`). |

Edit the file with any text editor and restart the game to apply changes.

## Building from source

1. Place the Automobility jar in `libs/`:
   ```
   momentum/
     libs/
       automobility-0_4_2_1_20_1-fabric.jar
   ```

2. Build:
   ```bash
   ./gradlew build
   ```

3. Output: `build/libs/momentum-1.0.0.jar` — drop into `mods/` alongside Automobility.

## Requirements
- Minecraft 1.20.1
- Fabric Loader ≥ 0.14.22
- Fabric API
- Automobility 0.4.2.x for 1.20.1

## Compatibility

Momentum uses `@Inject` at `TAIL` and `@ModifyArg` rather than `@Overwrite`, so it should be compatible with other mods that also modify `AutomobileEntity`. The HUD mixin only cancels `renderSpeedometer()` — control hints are unaffected.
