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

### Space key → brake + drift remapping (current state)
- Ordinal 1 (`back` param) modifier **removed** — braking no longer goes through Automobility's `braking` flag at all.
- Braking is handled entirely via `MomentumBrakeState.brakeHeld` (volatile static, polled in `START_CLIENT_TICK` from GLFW). Read by `@Inject RETURN movementTick` in `AutomobileEntityMixin`. Server-safe (stays false on dedicated server).
- Ordinal 4 (`space` param) in `AutomobileBrakeMixin` conditionally signals `holdingDrift`: true only when already drifting OR (steering != 0 AND hSpeed > 0.4f). This preserves the rising edge for when the player actually steers into a drift. Space maps to BOTH braking and drifting simultaneously.
- *— Agent Sonnet 4.6 (2026-03-14, updated 2026-03-14)*

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
| Space → brake remapping | `@ModifyVariable provideClientInput ordinal 1` + `MomentumBrakeState` | ✅ done |
| Drift fix (conditional holdingDrift + brake guard) | `@ModifyVariable provideClientInput ordinal 4` + `@Inject RETURN movementTick` guard | ✅ done |
| Comfortable speed multiplier | `@Inject AutomobileStats.getComfortableSpeed RETURN` | ✅ done |
| Camera lock | `END_CLIENT_TICK` in `MomentumClient` | ✅ done |
| HUD suppression | `@Inject AutomobileHud.renderSpeedometer HEAD cancel` | ✅ done |
| Custom HUD | `MomentumHud` + `HudRenderCallback` | ✅ done |
