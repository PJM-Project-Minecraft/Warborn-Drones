# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

WRBDrones — NeoForge 1.21.1 addon for the SuperbWarfare mod. Adds drones (FPV, Mavic, Shahed-136, ZALA Lancet), REB jammers, radio, monitor linkage, and an advanced signal model. Java 21, Gradle (Kotlin DSL), GeckoLib for animated entity models, Mixins for SBW interop.

`mod_id = "wrbdrones"`, namespace `ru.liko.wrbdrones`.

## Build & Run

```bash
./gradlew compileJava        # quick syntax check (~1 min cold, fast incremental)
./gradlew build              # full build with jar
./gradlew runClient          # launch dev client with mod loaded
./gradlew runServer          # dedicated server
./gradlew runData            # run datagen → output to src/generated/resources/
./gradlew runGameTestServer  # game-tests in mod_id namespace
```

Run configs are defined in `build.gradle.kts` (`neoForge { runs { ... } }`). All run configs auto-add `-XX:+IgnoreUnrecognizedVMOptions -XX:+AllowEnhancedClassRedefinition` for hot-swap.

There is no test suite beyond the `gameTestServer` config; no lint task wired.

## Composite build with SuperbWarfare

`settings.gradle.kts` `includeBuild`s `../../!libs and references/SuperbWarfare` (relative to repo root → `/home/liko/Разработка/NeoForge/!libs and references/SuperbWarfare`). The `SuperbWarfare/` folder *inside* this repo is a separate clone and is **not** what gets built — Gradle resolves `com.atsuishio:superbwarfare` to the sibling `!libs and references/SuperbWarfare` project. If you change SBW code, edit the sibling path; the in-tree copy is just a reference checkout.

When reading SBW source for context, prefer `/home/liko/Разработка/NeoForge/!libs and references/SuperbWarfare/src/main/{java,kotlin}/`. SBW is partially Kotlin (e.g. `EntityFindUtil.kt`).

## Architecture

### Drone hierarchy

`AddonDroneEntity extends com.atsuishio.superbwarfare.entity.vehicle.DroneEntity`. All custom drones (`FpvDroneEntity`, `MavicDroneNoDropEntity`, `MavicDroneWithDropEntity`, `Shahed136Entity`, `ZalaLancetEntity`) extend `AddonDroneEntity` and reuse SBW's operator/session machinery while providing per-drone assets, sounds, and loadouts. Quadcopter drones use SBW's built-in `travel()` flight model; **Lancet overrides `travel()` to a no-op and runs its own fixed-wing physics** (see below).

`AddonDroneEntity#beginRemoteControl` forces the operator's view (`yRot=drone.yaw`, `xRot=0`) on teleport-in so the FPV camera starts aligned with the drone nose rather than inheriting the player's prior head angles. Original angles are saved in `controlSession` and restored on exit.

`AddonDroneEntity` owns the **operator/control session abstraction** (`controlSession`, `operatorPosition`, `wrbdrones$controllerUuid`, `OPERATOR_POS_*` synced data). When a player engages remote control, the engine remembers the original player position and game mode; on signal loss or exit, `endRemoteControl` / `handleSignalLoss(player, selfDestruct)` restores them. Two distinct end paths:
- `selfDestruct=true` — kamikaze (LMB on Shahed/FPV with payload): unlinks all monitors, calls SBW `destroy()`, attributes via `LAST_ATTACKER_UUID`.
- `selfDestruct=false` — graceful signal-loss: ends control session, drone keeps existing.

### Signal model

Single source of truth: `util/SignalCalculator`. Returns a `SignalResult` record (`finalQuality`, `distanceFactor`, `altitudeFactor`, `wallAttenuation`, `rebFactor`). Inputs:

- **distance** — FPV uses `(1 − d/max)²` from zero; Mavic stays at 1.0 until `signal_loss_distance`, then quadratic falloff to `max_distance`. Pass `signalLossDistance = -1` for FPV behaviour.
- **altitude** — `dy = drone.y − operator.y`. Linear interpolation between `altitude_penalty_floor` (multiplier `altitude_min_multiplier`) and `altitude_bonus_ceil` (multiplier 1.0).
- **walls** — Amanatides–Woo voxel traversal from operator to `drone.getEyePosition()`. Hard occluders cost `wall_hard_per_block`; blocks in the `#wrbdrones:soft_obstacles` tag cost `wall_soft_per_block`; capped at `wall_max_total_attenuation` (95% by default), traversal limited to `wall_max_ray_blocks`.
- **REB** — `RebUtils.getRebFactor(entity)` — distance falloff with configurable `jamming_curve_exponent` and `jamming_multiplier`.

Final formula: `q = distance · altitude · (1 − walls) · (1 − reb)`, clamped to `[0,1]`.

Per-drone `SignalResult` is cached for `cache_ttl_ticks` (default 5) keyed by `Entity#getUUID()` — HUD renders at 60+ FPS, voxel traversal must not run every frame. **Do not duplicate the formula.** Three call sites use it:

- `client/overlay/DroneHudOverlay` (FPV HUD, 0–99 + bars)
- `client/overlay/MavicHudOverlay` (Mavic HUD, 0–5 bars)
- `client/DronePostChainHandler.calculateSignalQuality` (FPV post-process noise shader uniform)

`event/DroneChunkTickHandler` runs an authoritative server-side check every `signal.server_check_interval_ticks` (default 10) using `SignalCalculator.computeUncached` — if quality drops below `server_cutoff_threshold`, the server itself calls `handleSignalLoss`, defending against modified clients.

`util/RebUtils` is **dist-neutral** (no `@OnlyIn`) so the server-side cutoff can use it. `EntityFindUtil.getEntities(level)` works for both `ClientLevel` and `ServerLevel`.

### ZALA Lancet (fixed-wing barrage munition)

`ZalaLancetEntity` is a fixed-wing kamikaze drone with an entirely separate flight model from the quadcopter line — `travel()` is overridden empty, all movement happens in `updateFixedWingFlight` invoked from `serverFlightTick`.

**Energy-based airspeed model** (single source of truth, do not parallel it elsewhere):

```
airspeed += THRUST_IDLE + (THRUST_FULL − THRUST_IDLE)·throttle
          + PITCH_GRAVITY · sin(pitch)
          − DRAG_COEF · airspeed²
```

Constants are calibrated so equilibrium at `throttle=0.45 (DEFAULT_THROTTLE)` ≈ `LANCET_CRUISE_SPEED`, at `throttle=1` ≈ `LANCET_MAX_SPEED`. Pitch-down sin term gives natural energy gain in dives; climb costs speed via the same term. Final airspeed clamped to `LANCET_DIVE_MAX_SPEED`. Stall (`airspeed < LANCET_STALL_SPEED`) forces nose-down and adds quadratic lift-loss to motion's Y.

Inputs (`smoothedRudder`) are first-order-filtered (`INPUT_SMOOTHING=0.25`) to remove jitter. Roll rate and bank→yaw coupling scale with `(airspeed/cruise)²`. There is **no manual WASD attitude stick** — pitch/roll are owned entirely by the course autopilot; the pilot only steers the cursor, fires the rudder for fine yaw, and works the throttle.

**Two operating modes** (`LANCET_MODE` synced int):
- `MODE_COURSE` (= 0, default) — cursor-directed flight. The pilot aims a screen cursor and presses LMB; the client projects that cursor into a **world point** (`DroneInputHandler.computeCourseWorldTarget`, a raycast inverse of the HUD projection) and sends `LancetCourseCommandPacket(droneId, x, y, z)`. The drone steers yaw+pitch toward `COURSE_TARGET_*` (geometry in `computeFlightDemand`). The HUD locked frame is the **back-projection of that same world point** (`DroneHudOverlay.projectWorldToScreen`), so the marker sits exactly where the pilot aimed and drifts to centre as the nose aligns — no jump. FPV camera locked to nose (`LancetCameraMount.getCameraBonePitch` returns `lancet.getBodyPitch`, ignoring `player.xRot`).
- `MODE_RECON` — autopilot loiters around `RECON_TARGET_*`. Turn radius derived physically: `R = baseRadius·(v/cruise)²`, `ω = v/R`. The orbit is **closed-loop**: each tick the aim point is placed `LOITER_LOOKAHEAD (≈0.4 rad)` ahead of the drone's *actual* bearing around the centre (not an open-loop phase integrator, which used to desync and cause hunting). `loiterDir` (CW/CCW) is seeded by `seedLoiterDirection` from the sign of `rel × forward` so orbit entry matches the current velocity vector. Throttle auto-locks to `DEFAULT_THROTTLE`. Recon `isFreeCamera()` substate enables `DroneInputHandler.getFreeCameraPitch/Yaw` (free look decoupled from body).

Loiter math lives in `computeLoiterDemand` and is reused for both recon and "circle a designated target".

**Terminal attack** (kamikaze run from RECON): select a target (`SELECTED_TARGET_ID`, cycle keys) and LMB → `LancetTargetPacket` + `LancetAttackPacket` → `setTerminalAttack(true)`. In terminal the demand homes straight at the target with turn rate ramping `TERMINAL_TURN_RATE → TERMINAL_TURN_RATE_CLOSE` as range falls below `TERMINAL_HOMING_RANGE`, and airspeed is capped to `cruise·TERMINAL_MANEUVER_SPEED_FACTOR` inside that range so the turn radius `R = v/ω` actually tightens onto a point target instead of orbiting it. **Detonation is guaranteed** by `checkTerminalDetonation`: contact fuze inside `PROXIMITY_CONTACT_RADIUS`, and a proximity fuze that fires at closest approach (range stops decreasing) once armed inside `PROXIMITY_ARM_RADIUS` — this is what kills the old "circles the target on the floor forever without exploding" bug. `prevTargetDistSqr` tracks the range; it resets on `setTerminalAttack`.

**Launch chain**: `LancetLaunchPlatformEntity` (a separate `Entity` with its own GeckoLib model) holds a loaded Lancet at the offset `LAUNCH_PLATFORM_DRONE_OFFSET` (model-unit, applied through `getLaunchPlatformDronePosition`). `launchFromPlatform()` → `startFlight()` initialises airspeed to `LAUNCH_SPEED_KMH` and sets `noGravity=true`.

Lancet impact: any collision with a `canImpactEntity` target, hitting a block (via `willHitBlock` raycast or `horizontal/verticalCollision`), or touching ground/water past `SAFE_LAUNCH_TICKS` triggers `explode()` (SBW `CustomExplosion` with `LANCET_EXPLOSION_*` config). Self-destruct distance limit: `LANCET_MAX_DISTANCE` from spawn.

### Config

NeoForge `ModConfigSpec`. Single SERVER config, registered in `Wrbdrones.<init>`. All keys live in `config/ServerConfig.java`, sectioned by `builder.push("…")`:

- `fpv_drone` — FPV noise shader and signal-loss distance
- `mavic_drone` — distance thresholds
- `reb` — radii + jamming curve parameters
- `signal` — LOS toggle, wall attenuation, altitude curve, server cutoff
- `shahed136` — kinematics and explosion
- `lancet` — stall/cruise/max/dive airspeeds (blocks/tick; 1/72 ≈ 1 km/h), loiter radius/altitude, explosion, max distance
- `sound` — engine audibility ranges

When adding a new tunable: declare a `public static ModConfigSpec.{Boolean,Double,Int}Value` field, then `builder.comment(...).defineInRange(...)` inside the matching `push/pop` block. The config file lives at `<world>/serverconfig/wrbdrones-server.toml` after first load.

### Networking

`network/ModNetworking` registers payloads in the mod-bus event handler. Custom packets:
- `DroneSignalLostPacket` (C→S, with `selfDestruct` flag — only the controller may send)
- `LaunchShahedPacket`, `ShahedExplodePacket`, `ExitDroneControlPacket`, `OpenRadioScreenPacket`

Client-side dispatch via `ClientPacketHandler`.

### Mixins

`mixins.wrbdrones.json` (refmap `mixins.wrbdrones.refmap.json`). Common-side mixins: `VehicleDamageParticleMixin`, `DroneDropMixin`. Client-side: `DroneHudOverlayMixin`, `DroneRendererMixin`, plus accessor mixins for `SoundManager`, `SoundEngine`, `Channel` (used by drone interior/Doppler audio). Add new mixin classes to the appropriate array; SBW classes are valid mixin targets thanks to the composite build.

### Audio (drones)

Drones bypass the SBW engine sound (`getEngineSoundVolume() = 0`) and run a parallel pipeline in `client/sound/` — `DroneSoundHandler`, `DroneInteriorSoundHandler`, Shahed loops with Doppler/HF filter via OpenAL accessors. Audibility distance is `sound.fpv_max_distance` / `sound.shahed_max_distance`.

## Conventions

- Comments and Javadoc are written in **Russian** throughout this codebase. New code follows that convention.
- Internal mod fields/methods on SBW types are prefixed `wrbdrones$` to avoid name clashes.
- Resources live in `src/main/resources/` (hand-authored) and `src/generated/resources/` (datagen output, included in source set automatically).
- Block tag for soft obstacles: `data/wrbdrones/tags/blocks/soft_obstacles.json`.
