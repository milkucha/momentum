# Agent Coordination

Both agents should read this file before executing any edits.
Update the locks table when you start and clear your row when done/committed.

---

## Active locks
| File | Agent | Status | Since |
|---|---|---|---|
| *(none)* | — | — | — |

---

## Pending handoffs
*(none)*

---

## Shared decisions

### Space key → brake (current state)
- Braking handled entirely via `MomentumBrakeState.brakeHeld` (volatile static, polled in `START_CLIENT_TICK` from GLFW). Read by `@Inject RETURN movementTick` in `AutomobileEntityMixin`.
- Space no longer maps to `holdingDrift` at all — drift is fully decoupled (see J-key drift below).
- *— Agent Sonnet 4.6 (2026-03-14, updated 2026-03-15)*

### J key → transplanted drift (confirmed working 2026-03-15)
- Automobility's `driftingTick()` is fully replaced by `@Inject HEAD driftingTick cancellable=true` in `AutomobileEntityMixin`.
- J key state polled in `START_CLIENT_TICK` → `MomentumDriftState.driftKeyHeld` (volatile static).
- Rising/falling edge tracked per entity via `@Unique boolean momentum$prevDriftKeyHeld` (instance field) — avoids client/server race on a shared static.
- Logic is a direct transplant of Automobility source: rising edge starts drift (requires steering≠0, hSpeed>0.4, onGround, !drifting), falling edge calls `consumeTurboCharge()`, too-slow cancels with no boost. `turboCharge` increments 2/tick when steering matches driftDir, else 1/tick.
- Controller rumble calls omitted (keyboard-only mod).
- Writes directly to shadowed `drifting`/`driftDir`/`turboCharge` fields so all existing Momentum guards (understeer bypass, steering ramp, brake skip) work without changes.
- **Ground check:** `automobileOnGround()` (Automobility's custom detection) always returns false for this entity — it uses a 0.04-block scan below `getBoundingBox().minY` which misses because the entity floats slightly above the block surface. Use `((Entity)(Object)this).isOnGround()` (Minecraft's native flag, set by `move()`) everywhere instead.
- *— Agent Sonnet 4.6 (2026-03-15)*

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

### N-key drift — brake-then-drift (2026-03-15)
- `@Inject HEAD movementTick` in `AutomobileEntityMixin` — fully independent of J and K.
- N held → applies `brakeDecay` every tick + increments `nBrakeTimer`. Once `nBrakeTimer >= nDriftBrakeTicks` (config, default 15) AND drift conditions are met, calls `setDrifting(true)` + sets `driftDir` + speed kick (identical to J rising edge).
- During drift: continues applying `brakeDecay` each tick. If drift cancelled externally (hSpeed < 0.33 via J-drift's driftingTick inject), clears `nDriftArmed` so next press starts fresh.
- N released while armed + drifting: `setDrifting(false); consumeTurboCharge()` — same as J falling edge.
- Particles and turboCharge handled automatically by J-drift's `driftingTick` inject (fires whenever `drifting == true` regardless of which key triggered it).
- New config field: `nDriftBrakeTicks = 15`.
- *— Agent Sonnet 4.6 (2026-03-15)*

### Bash permissions
- `.claude/settings.json` created with `"allow": ["Bash(*)"]` — all agents can run bash without per-command approval.
- *— Agent Sonnet 4.6 (2026-03-14)*

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
| J-key drift (transplanted from Automobility) | `@Inject HEAD driftingTick cancellable=true` in `AutomobileEntityMixin` | ✅ done + confirmed working 2026-03-15 |
| K-key arcade drift (slip angle) | `@Inject HEAD+RETURN movementTick` + `MomentumDriftState.kDriftKeyHeld` | ✅ done + confirmed working 2026-03-15 |
| K-drift skid sound | `KDriftSkidSound` (looping `MovingSoundInstance`), played on rising edge of `kDriftActive` in `END_CLIENT_TICK` | ✅ done — Agent Sonnet 4.6 (2026-03-15) |
| N-key brake-then-drift | `@Inject HEAD movementTick` in `AutomobileEntityMixin` + `MomentumDriftState.nDriftKeyHeld` | ✅ done — Agent Sonnet 4.6 (2026-03-15) |
| Comfortable speed multiplier | `@Inject AutomobileStats.getComfortableSpeed RETURN` | ✅ done |
| Camera lock | `END_CLIENT_TICK` in `MomentumClient` | ✅ done |
| HUD suppression | `@Inject AutomobileHud.renderSpeedometer HEAD cancel` | ✅ done |
| Custom HUD | `MomentumHud` + `HudRenderCallback` | ✅ done |
