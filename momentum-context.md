# Momentum Mod — Context Summary

## What this is
**Momentum** is a Fabric 1.20.1 client-side companion mod for the **Automobility** mod. It replaces Automobility's driving feel with realistic momentum physics and adds a custom HUD. The mod is built entirely with Mixins targeting Automobility's internal classes.

## Repository
- GitHub: `io.github.milkucha/momentum`
- The repository needs to be **deleted and reuploaded from scratch** — GitHub's contributors tab currently shows "Claude Code" as a contributor (due to co-authored commits), which is likely why the mod was rejected by Modrinth. A fresh repository will have a clean contributor history.
- Target platforms: **CurseForge** and **Modrinth**

---

## Features — all implemented and working

### Movement
- Coast decay: replaces Automobility's abrupt deceleration with configurable momentum (`coastDecay = 0.009f`)
- Acceleration scale: configurable torque feel (`accelerationScale = 3.3f`)
- Brake decay: registered Minecraft keybinding (default S), linear decel (`brakeDecay = 0.012f`, floored at -0.25)
- Comfortable speed multiplier: scales Automobility's speed cap (`comfortableSpeedMultiplier = 1.55f`)
- Acceleration steering gate removal: bypasses Automobility's corner acceleration suppression

### Steering
- Steering ramp rate: configurable time-to-lock (`steeringRampRate = 0.12f`, ~8 ticks)
- Steering center rate: separate return-to-center rate (`centerRate = 0.42f`)
- Speed-based understeer: quadratic scaling reduces cornering at speed

### Drift system
- Single registered keybinding (default Space) triggers whichever profile is selected
- Three profiles: **Vanilla** (transplanted from Automobility), **Arcade** (slip-angle with turbo), **Responsive** (deep slide with accumulator)
- Each profile has its own skid sound (looping `MovingSoundInstance`)
- N-drift removed; all naming cleaned up from J/K/M → Vanilla/Arcade/Responsive

### Camera
- Camera lock: forces player yaw/pitch to match car orientation each tick
- Brake zoom: spring-mass-damper driven by actual deceleration (inertia feel)
- Steering tilt: yaw offset lerps toward `steering × steeringTilt`
- Reverse camera flip: lerps 0→180° when reversing

### HUD
- Suppresses Automobility's built-in speedometer via Mixin
- Bar HUD: segmented speed bar, color mirrors turbo states (green→yellow/cyan/purple→orange for boost)
- Debug overlay: shows steering, hSpeed, angularSpeed, drift state
- All positions use `xFraction` (0.0–1.0) for resolution-independent layout

### Config & UI
- JSON config (`momentum.json`) with Gson, all fields with defaults
- Full YACL in-game config screen (`.` key to open), six categories: General, Drift, Movement, Steering, Camera, HUD
- ModMenu integration
- Feature enable/disable toggles per category (movement, steering, camera, HUD)
- Drift profile selector in General tab; drift tab rebuilds dynamically on profile change

### Multiplayer
- C2S packet (`momentum:key_state`) sends brake + drift key state to server each tick on change
- `ServerKeyState` map (ConcurrentHashMap by UUID) holds server-side key state
- Mixin helpers branch on logical side: client reads local key state, server reads packet state
- **Status: coded but untested on a real dedicated server — must test before release**

---

## Technical architecture

- All movement changes: `AutomobileEntityMixin.java` (Redirects, ModifyArgs, ModifyConstants, Injects)
- HUD suppression: `AutomobileHudMixin.java` (`@Inject HEAD cancellable` on `renderSpeedometer`, must be `static` because `AutomobileHud` is an enum)
- Ground check gotcha: `automobileOnGround()` always returns false — use `((Entity)(Object)this).isOnGround()` everywhere
- Mixins registered in `momentum.mixins.json`: movement mixins under `"mixins"`, HUD mixin under `"client"`
- Dependencies: `automobility-0.4.2.b+1.20.1-fabric.jar` + `jsonem-0.2.1+1.20.jar` in `libs/`; YACL and ModMenu as `modCompileOnly`

---

## Pending before release

1. **Test multiplayer on a real dedicated server** — the C2S packet path was written but never run on actual server hardware
2. **Delete and reupload GitHub repository** — clean contributor history for Modrinth/CurseForge submission
3. **Submit to Modrinth and CurseForge**
4. **Multi-version porting** (post-release) — plan exists for 1.20.x, 1.21, etc.; key changes are DrawContext API at 1.20 boundary, networking v2 at 1.21, Java 21 at 1.21

---

## Agent coordination notes
- Read `AGENTS.md` before doing any work — it has the full shared decision log
- Read `CLAUDE.md` for the coordination protocol
- Sign all entries with `— Agent [model] (date)`
- Use `FORUM.md` for inter-agent handoff messages
- All three files live locally but are gitignored (not in the remote repo)
