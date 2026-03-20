# Agent Coordination

Both agents should read this file before executing any edits.
Update the locks table when you start and clear your row when done/committed.

---

## Active locks
| File | Agent | Status | Since |
|---|---|---|---|
| *(none)* | | | |

---

## Pending handoffs
*(none)*

---

## Shared decisions

### Brake key → registered Minecraft KeyBinding (2026-03-19)
- `BRAKE_KEY` registered in `MomentumClient` as `key.momentum.brake`, default S, category `key.categories.momentum`.
- Held state read via `isKeyHeld(BRAKE_KEY, win)` which reads `InputUtil.fromKeyName(binding.getBoundKeyTranslationKey())` → GLFW query. Respects player remapping.
- `MomentumConfig.brakeKey` field removed — no longer in config or YACL screen.
- *— Agent Sonnet 4.6 (2026-03-19)*

### Drift key → registered Minecraft KeyBinding (2026-03-19)
- `DRIFT_KEY` registered in `MomentumClient` as `key.momentum.handbrake_drift`, default Space, category `key.categories.momentum`.
- Single key triggers the active drift profile (Vanilla/Arcade/Responsive) — replaces J/K/M/N/O hardcoded keys.
- `MomentumDriftState` simplified to one field: `driftKeyHeld`. Held state polled from `DRIFT_KEY` each tick.
- `MomentumConfig.oDriftKey` field removed.
- *— Agent Sonnet 4.6 (2026-03-19)*

### Drift renaming (2026-03-19)
- O-drift → **Drift** (the registered keybinding; profile selects behaviour)
- J-drift → **Vanilla Drift** (`ODrift.Profile.VANILLA`)
- K-drift → **Arcade Drift** (`ODrift.Profile.ARCADE`)
- M-drift → **Responsive Drift** (`ODrift.Profile.RESPONSIVE`)
- N-drift → **removed entirely** (code, config, sound, packet fields)
- H-drift → was never implemented
- Sound files renamed: `JDriftSkidSound` → `VanillaDriftSkidSound`, `KDriftSkidSound` → `ArcadeDriftSkidSound`, `MDriftSkidSound` → `ResponsiveDriftSkidSound`. `NDriftSkidSound` deleted.
- *— Agent Sonnet 4.6 (2026-03-19)*

### Vanilla Drift (formerly J-drift, confirmed working 2026-03-15)
- Automobility's `driftingTick()` is fully replaced by `@Inject HEAD driftingTick cancellable=true` in `AutomobileEntityMixin`.
- Now fires when `momentum$vanillaDriftKey()` = driftKey held AND profile == VANILLA.
- Rising/falling edge tracked per entity via `@Unique boolean momentum$prevDriftKeyHeld` (instance field).
- Logic: rising edge starts drift (requires steering≠0, hSpeed>0.4, onGround, !drifting), falling edge calls `consumeTurboCharge()`, too-slow cancels with no boost. `turboCharge` increments 2/tick when steering matches driftDir, else 1/tick.
- **Ground check:** `automobileOnGround()` always returns false — use `((Entity)(Object)this).isOnGround()` everywhere.
- *— Agent Sonnet 4.6 (2026-03-15, updated 2026-03-19)*

### Brake decay — current state (linear, working)
Pure linear inject in `AutomobileEntityMixin` at `@Inject RETURN movementTick`:
```java
engineSpeed = Math.max(engineSpeed - decay, -0.25f);
```
Constant deceleration mirrors real friction braking. No asymptotic tail. Holds through zero into reverse (capped at -0.25f). `brakeDecay` config value is now an absolute engineSpeed units/tick rate (default `0.012f`). Skipped when `drifting == true` to avoid cancelling an active drift by dropping hSpeed below 0.33.
- *— Agent Sonnet 4.6 (2026-03-14, updated 2026-03-14)*

### Drift fix (resolved 2026-03-14)
Root cause: `driftingTick()` uses rising-edge detection (`!prevHoldDrift && holdingDrift`). Raw GLFW state for `holdingDrift` fired the rising edge on the first Space press (when `steering == 0`), then `prevHoldDrift` stayed true — no further rising edges while Space was held.
Two-part fix:
1. `AutomobileBrakeMixin` — `holdingDrift` is only true when already drifting OR (steering != 0 AND hSpeed > 0.4f), preserving the rising edge until the player turns.
2. `AutomobileEntityMixin.momentum$applyBrake` — `if (drifting) return;` guard prevents braking from reducing hSpeed below 0.33 and auto-cancelling the drift.
- *— Agent Sonnet 4.6 (2026-03-14)*

### Comfortable speed multiplier
- `AutomobileStatsMixin` injects `@Inject(at = RETURN, cancellable = true)` on `AutomobileStats.getComfortableSpeed()`.
- Multiplies the return value by `MomentumConfig.comfortableSpeedMultiplier` — covers all three call sites in `movementTick` simultaneously (acceleration cap, boost top-up, off-road cap).
- Registered in `momentum.mixins.json` under `"mixins"` (not `"client"` — AutomobileStats is shared).
- *— Agent Sonnet 4.6 (2026-03-14)*

### Camera lock
- `END_CLIENT_TICK` handler in `MomentumClient.java` forces `player.setYaw(auto.getYaw())` and `player.setPitch(cfg.lockCameraPitch)` every tick while riding an AutomobileEntity.
- Fires after all entity ticks and mouse input, before rendering — no jitter.
- Controlled by `lockCamera` (bool) and `lockCameraPitch` (float, degrees) in config.
- *— Agent Sonnet 4.6 (2026-03-14)*

### Reverse camera flip (2026-03-20)
- `reverseYawOffset` (float, `MomentumClient`) lerps toward `180f` when `accessor.momentum$getEngineSpeed() < -0.01f`, back to `0f` otherwise.
- Lerp each tick: `reverseYawOffset += (target - reverseYawOffset) * cfg.camera.reverseFlipLerp` — exponential ease-out naturally built in.
- Added to player yaw alongside all other offsets: `auto.getYaw() + … + reverseYawOffset`.
- Config: `camera.reverseFlip = true`, `camera.reverseFlipLerp = 0.2f`.
- YACL: "Reverse Camera" group in Camera category (toggle + speed slider).
- Reset to 0 in `resetTickState()`.
- *— Agent Sonnet 4.6 (2026-03-20)*

### Steering tilt camera (2026-03-20)
- `steeringTiltOffset` (float, `MomentumClient`) lerps toward `accessor.momentum$getSteering() * cfg.camera.steeringTilt` each tick.
- Lerp factor: `cfg.camera.steeringTiltLerp` (default `0.1f`).
- Added to the player yaw alongside drift camera offsets: `auto.getYaw() + kCameraDriftYawOffset + mCameraDriftYawOffset + steeringTiltOffset`.
- Config: `camera.steeringTilt = 5f` (degrees at ±1 steering), `camera.steeringTiltLerp = 0.1f`.
- YACL: "Steering Tilt" + "Steering Tilt Lerp" options in Camera → Lock group.
- Reset to 0 in `resetTickState()`.
- *— Agent Sonnet 4.6 (2026-03-20)*

### Brake zoom — inertia spring-damper (2026-03-16)
- Replaced the binary-lerp zoom (`anyBraking ? brakeZoomFov : 0`, lerp factor) with a spring-mass-damper driven by **actual deceleration** (`prevHSpeed - hSpeed` clamped ≥ 0).
- Physics each tick: `velocity = velocity * damping + decel * inputScale - spring * offset; offset += velocity` — no key-state gate needed.
- When the car stops (decel → 0) the accumulated velocity carries the zoom forward briefly before the spring returns it to zero — the "camera body still moving" inertia feel.
- Clamped to `[0, brakeZoomFov]` to prevent runaway or negative zoom-out.
- Config fields: `brakeZoomFov=10`, `brakeZoomInputScale=30`, `brakeZoomSpring=0.06`, `brakeZoomDamping=0.90`. Old `brakeZoomLerp` field removed (Gson ignores stale keys in existing JSON).
- *— Agent Sonnet 4.6 (2026-03-16)*

### N-drift — REMOVED (2026-03-19)
- Brake-then-drift behaviour removed. N key, `NDrift` config, `nDriftKeyHeld`, `NDriftSkidSound`, and packet field deleted.
- *— Agent Sonnet 4.6 (2026-03-19)*

### Drift profile selector (2026-03-16, updated 2026-03-19)
- `MomentumConfig.oDrift.profile` — enum `{ VANILLA, ARCADE, RESPONSIVE }` (renamed from J/K/M), default `ARCADE`.
- Single `DRIFT_KEY` KeyBinding triggers whichever profile is active. Three profile-gated helpers in mixin: `momentum$vanillaDriftKey()`, `momentum$arcadeDriftKey()`, `momentum$responsiveDriftKey()`.
- YACL "Drift" category dynamically rebuilds its groups based on `oDrift.profile` — ARCADE shows Arcade groups, RESPONSIVE shows Responsive groups, VANILLA shows nothing. Profile listener rebuilds screen on change.
- *— Agent Sonnet 4.6 (2026-03-16, updated 2026-03-19)*

### Steering center rate — separate from ramp rate (2026-03-16)
- `MomentumConfig.Steering.centerRate` added (default `0.42f`). Controls how fast steering returns to centre when no key is held.
- `@ModifyConstant(0.42f)` in `steeringTick` now returns `steering.rampRate` when `steeringLeft || steeringRight`, and `steering.centerRate` otherwise (and `original` during a drift as before).
- Old behaviour was a single 0.42f replacement for both directions; the two fields now let players tune snap-back independently.
- *— Agent Sonnet 4.6 (2026-03-16)*

### Acceleration steering gate removal (2026-03-16)
- Automobility's `movementTick` suppresses normal acceleration with `calculateAcceleration` when `!drifting && steering != 0 && hSpeed > 0.5` — capping engineSpeed increment to 0.001 while cornering above ~36 km/h.
- Added `@ModifyConstant(doubleValue = 0.5)` in `movementTick` → returns `Double.MAX_VALUE` when Momentum is enabled, making `hSpeed > Double.MAX_VALUE` permanently false. The gate is bypassed entirely.
- Momentum's understeer system already handles speed-based corner resistance; the gate was redundant and caused sluggish corner acceleration.
- *— Agent Sonnet 4.6 (2026-03-16)*

### HUD horizontal position — xFraction (2026-03-19)
- `marginRight: int` replaced by `xFraction: float` (0.0–1.0) in both `Hud` and `BarHud` config classes. Same for `debugMarginRight` → `debugXFraction`.
- Formula: `panelX = screenW - FRAME_W - (int)(screenW * xFraction)`. Scales proportionally so the HUD stays at the same relative X regardless of windowed vs fullscreen.
- Defaults: `hud.xFraction=0.36f`, `barHud.xFraction=0.33f`, `hud.debugXFraction=0.016f`. User should re-tune once via options screen.
- YACL "Margin Right" int sliders replaced with "X Fraction" float sliders (0.0–1.0, step 0.01).
- Old `marginRight` JSON key is silently ignored by GSON on next load.
- *— Agent Sonnet 4.6 (2026-03-19)*

### Bar HUD (2026-03-16)
- `MomentumConfig.BarHud` inner class — position (`x`, `y`, `marginRight`, `marginBottom`), size (`totalWidth`, `totalHeight`, `barWidth`, `barSpacing`), `maxSpeedKmh` cap, `barColor`, `boostBarColor`, `textColor`, text offset.
- `hud.useBarHud` bool toggle (default `true`) selects bar HUD vs. the legacy texture HUD at render time.
- `BarHud.render()` draws a row of rectangular segments; filled count ∝ `hSpeed * 72 / maxSpeedKmh`. Segments above `engineSpeed / hSpeed` ratio are drawn in `boostBarColor` to show the boost contribution separately.
- Speed text rendered above or beside the bar at configurable offsets.
- All bar HUD fields exposed in the YACL "HUD" category under `Bar | Position`, `Bar | Size`, `Bar | Colors`, and `Bar | Text Position` groups.
- *— Agent Sonnet 4.6 (2026-03-16)*

### YACL in-game config screen (2026-03-16)
- `MomentumConfigScreen.create(parent)` builds a full YACL screen with six categories: General, Drift (O-key), Movement, Steering, Camera, HUD.
- `openOptionsKey` (default `.`) registered in `MomentumClient`; `END_CLIENT_TICK` opens the screen on press.
- All config fields exposed as typed options (`FloatSlider`, `IntegerSlider`, `Boolean`, `KeyCode`, `Color`).
- Boost and Camera sub-groups use `addListener` + `setAvailable(false)` to grey out dependent options when the parent toggle is off.
- M-drift `constantAngle` toggle greys out `slipConvergeRate` when on.
- Screen saves config via `cfg::save` on YACL's built-in save button.
- *— Agent Sonnet 4.6 (2026-03-16)*

### Bash permissions
- `.claude/settings.json` created with `"allow": ["Bash(*)"]` — all agents can run bash without per-command approval.
- *— Agent Sonnet 4.6 (2026-03-14)*

---

### Multiplayer support (dedicated server) — 2026-03-15
- `fabric.mod.json` — `environment: "*"`, added `"main"` entrypoint → `Momentum.java`.
- `Momentum.java` (common init) — registers `ServerPlayNetworking` receiver for `momentum:key_state` C2S packet; registers `ServerPlayConnectionEvents.DISCONNECT` cleanup.
- `network/KeyStatePacket.java` — packet ID + read/write for 5 booleans (brake, J, K, N, M).
- `network/ServerKeyState.java` — `ConcurrentHashMap<UUID, boolean[]>` written by packet handler (network thread), read by mixin on server.
- `AutomobileEntityMixin` — 5 `@Unique` helpers (`momentum$brake/jKey/kKey/nKey/mKey`) that return volatile statics on client logical side and `ServerKeyState` lookups on server logical side. All drift/brake injects replaced to call helpers instead of statics.
- `MomentumClient` — tracks previous packet state; sends `KeyStatePacket` in `START_CLIENT_TICK` when any key changes (only while network handler is present).
- **No changes needed** to HUD, camera, or sound — all pure client rendering.
- *— Agent Sonnet 4.6 (2026-03-15)*

### Options screen — feature toggles + General tab reorganisation (2026-03-19)
- **Drift Profile selector** moved from the Drift tab to the General tab (sits below "Enable Momentum"). Drift tab now shows only the active profile's tuning groups.
- **Feature toggles** group added to General tab: one toggle each for Movement, Steering, Camera, HUD. Toggle controls both the UI display in that session and runtime behaviour — guarded in the relevant mixin/client code.
- **Read-only key labels** added at the bottom of General tab: "Brake: [key]" and "Handbrake (Drift): [key]" via `LabelOption`, reflecting whatever the player has bound in Options → Controls.
- **Debug Overlay** option removed from HUD tab UI. `hud.debug` field remains in `MomentumConfig.Hud` and in `momentum.json` — edit manually if needed.
- **Config additions**: `movement.enabled`, `steering.enabled`, `camera.enabled`, `hud.enabled` (all `true` by default). Old configs will pick up the defaults gracefully via Gson.
- **Runtime guards**: `movement.enabled` gates coast/accel/brake/comfortable-speed mixin paths + vanilla brake decay suppression + back-input suppression; `steering.enabled` gates ramp-rate and understeer modifiers; `camera.enabled` gates lock, pitch, and brake-zoom; `hud.enabled` gates the HUD render callback.
- *— Agent Sonnet 4.6 (2026-03-19)*

---

### Codebase audit — cleanup complete (2026-03-19)
- Removed `AutomobileBrakeMixin` (empty shell) + mixins.json entry.
- Removed all `System.out.println` debug logs (~15 calls across Vanilla/Arcade/Responsive/Brake state machines).
- Removed 6 unused `SteeringDebugAccessor` methods (`isHoldingDrift`, `isAccelerating`, `isBraking`, `isSteeringLeft`, `isSteeringRight`, `getTurboCharge`) and their `@Shadow`/`@Unique` backing in the mixin (`holdingDrift`, `accelerating`, `braking`, `wasOnGround` shadows also removed).
- Removed stale local vars `prevKHeld` and `prevMHeld` (only existed to feed debug prints).
- Renamed all K/M-drift naming to Arcade/Responsive throughout:
  - `MomentumConfig.KDrift` → `ArcadeDrift`, field `kDrift` → `arcadeDrift`
  - `MomentumConfig.MDrift` → `ResponsiveDrift`, field `mDrift` → `responsiveDrift`
  - All `@Unique` mixin fields renamed (`momentum$kDrift*` → `momentum$arcadeDrift*`, `momentum$m*` → `momentum$responsive*`)
  - Accessor methods renamed (`isKDriftActive` → `isArcadeDriftActive`, etc.) across interface, mixin, MomentumClient, both skid sounds.
- *— Agent Sonnet 4.6 (2026-03-19)*

## Pending before release

| # | Task | Notes |
|---|---|---|
| 1 | ~~**Codebase audit — inconsistencies & redundancies**~~ | ✅ Done — see entry above. |
| 2 | **Multiplayer / server support — untested** | The C2S packet + `ServerKeyState` path was written but never run on a real dedicated server. Must be tested before release. |

*— Agent Sonnet 4.6 (2026-03-17, updated 2026-03-19)*

---

## Current feature status

| Feature | Mixin / mechanism | Status |
|---|---|---|
| Coast decay | `@Redirect AUtils.zero ordinal 1` in `movementTick` | ✅ done |
| Acceleration scale | `@ModifyArg calculateAcceleration index 0` in `movementTick` | ✅ done |
| Steering ramp rate | `@ModifyConstant 0.42f` in `steeringTick` | ✅ done |
| Speed-based understeer | `@ModifyArg AUtils.shift ordinal 1 index 2` in `postMovementTick` | ✅ done |
| Brake decay (formula) | `@Inject RETURN movementTick` + `MomentumBrakeState` | ✅ done (linear, `brakeDecay = 0.012f`) |
| Space → brake remapping | `@Inject RETURN movementTick` + `MomentumBrakeState` | ✅ done |
| Vanilla Drift (transplanted from Automobility) | `@Inject HEAD driftingTick cancellable=true`; fires when profile==VANILLA + drift key held | ✅ done — renamed 2026-03-19 |
| Arcade Drift (slip angle) | `@Inject HEAD+RETURN movementTick`; fires when profile==ARCADE + drift key held | ✅ done — renamed 2026-03-19 |
| Arcade Drift skid sound | `ArcadeDriftSkidSound` (looping `MovingSoundInstance`), played on rising edge of `kDriftActive` | ✅ done — renamed 2026-03-19 |
| N-drift | ~~removed~~ | ❌ deleted 2026-03-19 |
| Comfortable speed multiplier | `@Inject AutomobileStats.getComfortableSpeed RETURN` | ✅ done |
| Camera lock | `END_CLIENT_TICK` in `MomentumClient` | ✅ done |
| HUD suppression | `@Inject AutomobileHud.renderSpeedometer HEAD cancel` | ✅ done |
| Custom HUD | `MomentumHud` + `HudRenderCallback` | ✅ done |
| Responsive Drift (deep slide) | `@Inject HEAD+RETURN movementTick`; fires when profile==RESPONSIVE + drift key held; steering≠0 → slip-angle w/ accumulator + boost; steering=0 → brake | ✅ done — renamed 2026-03-19 |
| Multiplayer (dedicated server) | C2S `KeyStatePacket` (2 bools: brake+drift) + `ServerKeyState` + dual-path helpers in mixin | ⚠️ coded, **untested** — must test on real dedicated server before release |
| Drift profile selector (Handbrake key) | `DRIFT_KEY` KeyBinding (Space); `ODrift.Profile` enum {VANILLA/ARCADE/RESPONSIVE}; YACL Drift category rebuilds on profile change | ✅ done — Agent Sonnet 4.6 (2026-03-19) |
| Brake KeyBinding | `BRAKE_KEY` KeyBinding (S); held state read via `isKeyHeld()`; removed from config | ✅ done — Agent Sonnet 4.6 (2026-03-19) |
| Steering tilt camera | `steeringTiltOffset` lerps toward `steering * steeringTilt`; added to player yaw alongside drift offsets | ✅ done — Agent Sonnet 4.6 (2026-03-20) |
| Reverse camera flip | `reverseYawOffset` lerps 0→180° when `engineSpeed < -0.01f`; ease-out via exponential lerp | ✅ done — Agent Sonnet 4.6 (2026-03-20) |
| Steering center rate | `@ModifyConstant(0.42f)` returns `centerRate` on release vs `rampRate` when steering | ✅ done — Agent Sonnet 4.6 (2026-03-16) |
| Acceleration steering gate removal | `@ModifyConstant(doubleValue=0.5)` → `Double.MAX_VALUE` in `movementTick` | ✅ done — Agent Sonnet 4.6 (2026-03-16) |
| Bar HUD | `BarHud` renderer + `hud.useBarHud` toggle; `BarHud` config group in YACL HUD category | ✅ done — Agent Sonnet 4.6 (2026-03-16) |
| YACL in-game config screen | `MomentumConfigScreen` (`.` key) with all six categories; greyed-out dependent options; key pickers removed (use MC controls screen) | ✅ done — Agent Sonnet 4.6 (2026-03-16, updated 2026-03-19) |
