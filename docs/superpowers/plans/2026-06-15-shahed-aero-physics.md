# Аэрофизика Shahed-136 + общий FixedWingDynamics — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Перевести Shahed-136 с кинематики point-mass на энергетическую аэродинамическую модель уровня Lancet, вынеся общую энергоформулу в переиспользуемый `FixedWingDynamics`, на который мигрирует и Lancet без изменения поведения.

**Architecture:** Новый пакет `entity/flight` с чистым stateless-хелпером `FixedWingDynamics` (энергоинтеграция airspeed, сваливание, банк→рыскание, сборка motion), параметризованным `AircraftProfile`, и вынесенным из Lancet record `FlightDemand`. `ZalaLancetEntity` мигрирует на ядро 1:1 (те же константы). `Shahed136Entity` получает газ-серво, энергомодель, банкированные развороты, сваливание, удержание высоты, синхро-поле `AIRSPEED` и терминальное наведение с гарантированным подрывом.

**Tech Stack:** Java 21, NeoForge 1.21.1, GeckoLib. `net.minecraft.util.Mth`, `net.minecraft.world.phys.Vec3`.

**Верификация (вся работа):** В репозитории нет JUnit-обвязки и автотестов физики; аэродинамика feel-based. Поэтому per-task verification = `./gradlew compileJava` проходит без ошибок. Поведенческая проверка — финальный ручной чек-лист в `runClient` (Task 9). Это согласовано в spec `docs/superpowers/specs/2026-06-15-shahed-aero-physics-design.md`.

**Коммиты:** пользователь просил пока без коммитов в этой сессии. Шаги «Commit» оставлены в плане как опциональные — выполнять только если пользователь явно разрешит. По умолчанию вместо коммита просто переходить к следующей задаче.

---

## Файловая структура

- **Создаётся:**
  - `src/main/java/ru/liko/wrbdrones/entity/flight/AircraftProfile.java` — record параметров ЛА.
  - `src/main/java/ru/liko/wrbdrones/entity/flight/FlightDemand.java` — record «хотелки» автопилота.
  - `src/main/java/ru/liko/wrbdrones/entity/flight/FixedWingDynamics.java` — чистое ядро энергомодели.
- **Изменяется:**
  - `src/main/java/ru/liko/wrbdrones/entity/ZalaLancetEntity.java` — миграция на ядро (behavior-preserving).
  - `src/main/java/ru/liko/wrbdrones/entity/Shahed136Entity.java` — энергомодель, AIRSPEED, терминал.

---

## Task 1: Record `FlightDemand` в пакете flight

**Files:**
- Create: `src/main/java/ru/liko/wrbdrones/entity/flight/FlightDemand.java`

- [ ] **Step 1: Создать публичный record (вынос приватного из Lancet)**

```java
package ru.liko.wrbdrones.entity.flight;

/**
 * «Хотелка» курсового автопилота/пилота для аэродинамической модели неподвижного крыла.
 *
 * @param hasAutopilot активен ли автопилот (false — ручной/нейтральный режим)
 * @param pitch        желаемый тангаж, град. (вниз положительный — как в movement-коде)
 * @param yawDiff      рассогласование по курсу к цели, град. (wrapDegrees)
 * @param turnRate     максимальный темп разворота за тик, град.
 */
public record FlightDemand(boolean hasAutopilot, float pitch, float yawDiff, float turnRate) {
    /** Нейтральная команда: автопилота нет. */
    public static FlightDemand manual() {
        return new FlightDemand(false, 0.0f, 0.0f, 0.0f);
    }
}
```

- [ ] **Step 2: Компиляция**

Run: `./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL (новый файл компилируется).

---

## Task 2: Record `AircraftProfile`

**Files:**
- Create: `src/main/java/ru/liko/wrbdrones/entity/flight/AircraftProfile.java`

- [ ] **Step 1: Создать record профиля ЛА**

```java
package ru.liko.wrbdrones.entity.flight;

/**
 * Параметры конкретного летательного аппарата для {@link FixedWingDynamics}.
 * Все скорости — в блоках/тик (1/72 ≈ 1 км/ч).
 *
 * @param thrustIdle    ускорение тяги на нулевом газу
 * @param thrustFull    ускорение тяги на полном газу
 * @param dragCoef      коэффициент квадратичного лобового сопротивления
 * @param pitchGravity  амплитуда гравитационной составляющей по тангажу (энергообмен)
 * @param stallSpeed    скорость сваливания
 * @param cruiseSpeed   крейсерская скорость (нормировка связи крен→рыскание)
 * @param diveMaxSpeed  потолок скорости в пикировании
 * @param stallLiftLoss потеря подъёмной силы (проседание Y) при сваливании
 * @param bankYawFactor базовый коэффициент связи крен→рыскание
 */
public record AircraftProfile(
        float thrustIdle, float thrustFull, float dragCoef, float pitchGravity,
        float stallSpeed, float cruiseSpeed, float diveMaxSpeed,
        float stallLiftLoss, float bankYawFactor) {
}
```

- [ ] **Step 2: Компиляция**

Run: `./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL.

---

## Task 3: Ядро `FixedWingDynamics`

**Files:**
- Create: `src/main/java/ru/liko/wrbdrones/entity/flight/FixedWingDynamics.java`

Формулы перенесены 1:1 из `ZalaLancetEntity.updateFixedWingFlight` (строки ~357-470 на момент написания), чтобы миграция Lancet была behavior-preserving. `integrateAirspeed` НЕ клампит — клампинг и спец-логику (терминальный импульс, манёвренный потолок) каждый ЛА делает у себя, как сейчас в Lancet.

- [ ] **Step 1: Создать хелпер**

```java
package ru.liko.wrbdrones.entity.flight;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Чистое (stateless) ядро аэродинамики неподвижного крыла — единственный носитель
 * энергетической формулы airspeed. Используется и Lancet, и Shahed (см. CLAUDE.md:
 * «single source of truth, do not parallel it elsewhere»).
 */
public final class FixedWingDynamics {

    private FixedWingDynamics() {
    }

    /**
     * Интеграция воздушной скорости за тик БЕЗ клампа:
     * airspeed += thrust(throttle) + pitchGravity·sin(pitch) − drag·airspeed².
     * Клампинг к диапазону [0, diveMax] и спец-логику оставляем вызывающему коду.
     *
     * @param pitchDeg тангаж в градусах (вниз положительный)
     * @param throttle газ [0..1]
     */
    public static float integrateAirspeed(float airspeed, float pitchDeg, float throttle, AircraftProfile p) {
        float pitchRad = (float) Math.toRadians(pitchDeg);
        float gravComp = p.pitchGravity() * Mth.sin(pitchRad);
        float thrustAccel = p.thrustIdle() + (p.thrustFull() - p.thrustIdle()) * throttle;
        float dragAccel = p.dragCoef() * airspeed * airspeed;
        return airspeed + thrustAccel + gravComp - dragAccel;
    }

    /** Дефицит сваливания [0..1]: 0 при airspeed ≥ stall, иначе 1 − airspeed/stall. */
    public static float stallDeficit(float airspeed, float stallSpeed) {
        if (airspeed >= stallSpeed) {
            return 0.0f;
        }
        return 1.0f - airspeed / stallSpeed;
    }

    /** Связь крен→рыскание: bankYawFactor·(v/cruise)², квадратично от скорости. */
    public static float bankFactor(float airspeed, AircraftProfile p) {
        float speedFrac = Mth.clamp(airspeed / Math.max(p.cruiseSpeed(), 0.01f), 0.3f, 1.6f);
        return p.bankYawFactor() * speedFrac * speedFrac;
    }

    /** Вектор движения из ориентации и airspeed + проседание Y при сваливании (квадратично). */
    public static Vec3 assembleMotion(float pitchDeg, float yawDeg, float airspeed,
                                      float stallDeficit, AircraftProfile p) {
        Vec3 motion = Vec3.directionFromRotation(pitchDeg, yawDeg).scale(airspeed);
        if (stallDeficit > 0.0f) {
            motion = motion.add(0.0, -p.stallLiftLoss() * stallDeficit * stallDeficit, 0.0);
        }
        return motion;
    }
}
```

- [ ] **Step 2: Компиляция**

Run: `./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL.

---

## Task 4: Миграция Lancet на ядро (behavior-preserving)

**Files:**
- Modify: `src/main/java/ru/liko/wrbdrones/entity/ZalaLancetEntity.java`

Заменяем инлайновые формулы вызовами `FixedWingDynamics` с профилем из текущих констант/геттеров. Приватный record `FlightDemand` → публичный `flight.FlightDemand`. Поведение идентично (те же значения).

- [ ] **Step 1: Добавить импорты**

В блок импортов `ZalaLancetEntity.java` добавить:

```java
import ru.liko.wrbdrones.entity.flight.AircraftProfile;
import ru.liko.wrbdrones.entity.flight.FixedWingDynamics;
import ru.liko.wrbdrones.entity.flight.FlightDemand;
```

- [ ] **Step 2: Удалить приватный record FlightDemand**

Найти и удалить вложенный приватный record (объявление вида `private record FlightDemand(boolean hasAutopilot, float pitch, float yawDiff, float turnRate) { static FlightDemand manual() {...} }`). Все обращения `FlightDemand.manual()` и `new FlightDemand(...)` теперь резолвятся в импортированный публичный record с тем же контрактом — менять их не нужно.

- [ ] **Step 3: Добавить фабрику профиля Lancet**

Добавить приватный метод (рядом с `updateFixedWingFlight`):

```java
/** Профиль аэродинамики Lancet из текущих констант и config-геттеров. */
private AircraftProfile lancetProfile() {
    return new AircraftProfile(
            THRUST_IDLE, THRUST_FULL, DRAG_COEF, PITCH_GRAVITY,
            getStallSpeed(), getCruiseSpeed(), getDiveMaxSpeed(),
            STALL_LIFT_LOSS, BANK_YAW_FACTOR);
}
```

- [ ] **Step 4: Заменить интеграцию airspeed**

В `updateFixedWingFlight` блок:

```java
        float airspeed = getAirspeed();
        float pitchRad = (float) Math.toRadians(this.getXRot());
        float gravComp = PITCH_GRAVITY * Mth.sin(pitchRad);
        float thrustAccel = THRUST_IDLE + (THRUST_FULL - THRUST_IDLE) * getThrottle();
        float dragAccel = DRAG_COEF * airspeed * airspeed;
        airspeed += thrustAccel + gravComp - dragAccel;
```

заменить на:

```java
        float airspeed = FixedWingDynamics.integrateAirspeed(
                getAirspeed(), this.getXRot(), getThrottle(), lancetProfile());
```

(Ниже сохранить как есть: `if (isTerminalAttack()) { airspeed += 0.005f; }`, затем `airspeed = Mth.clamp(airspeed, 0.0f, getDiveMaxSpeed());`, затем манёвренный потолок и `setAirspeed(airspeed);`.)

- [ ] **Step 5: Заменить расчёт стол-дефицита**

Блок:

```java
        float stallDeficit = 0.0f;
        if (airspeed < getStallSpeed()) {
            stallDeficit = 1.0f - airspeed / getStallSpeed();
            requestedPitch = Mth.approachDegrees(requestedPitch, 22.0f, 1.0f + 1.5f * stallDeficit);
        }
```

заменить на:

```java
        float stallDeficit = FixedWingDynamics.stallDeficit(airspeed, getStallSpeed());
        if (stallDeficit > 0.0f) {
            requestedPitch = Mth.approachDegrees(requestedPitch, 22.0f, 1.0f + 1.5f * stallDeficit);
        }
```

- [ ] **Step 6: Заменить bankFactor**

Блок:

```java
        float speedFrac = Mth.clamp(airspeed / Math.max(getCruiseSpeed(), 0.01f), 0.3f, 1.6f);
        float bankFactor = BANK_YAW_FACTOR * speedFrac * speedFrac;
```

заменить на:

```java
        float bankFactor = FixedWingDynamics.bankFactor(airspeed, lancetProfile());
```

- [ ] **Step 7: Заменить сборку motion**

Блок:

```java
        Vec3 motion = Vec3.directionFromRotation(this.getXRot(), this.getYRot()).scale(airspeed);
        if (stallDeficit > 0.0f) {
            // Падение подъёмной силы: чем глубже сваливание, тем сильнее проседание (квадратично).
            motion = motion.add(0.0, -STALL_LIFT_LOSS * stallDeficit * stallDeficit, 0.0);
        }
```

заменить на:

```java
        Vec3 motion = FixedWingDynamics.assembleMotion(
                this.getXRot(), this.getYRot(), airspeed, stallDeficit, lancetProfile());
```

- [ ] **Step 8: Компиляция**

Run: `./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL. Если есть ошибка «cannot find symbol Mth/Vec3» — проверить, что импорты `Mth`/`Vec3` ещё используются в файле (они используются в других местах, удалять не нужно).

---

## Task 5: Shahed — синхро-поле AIRSPEED + газ + NBT

**Files:**
- Modify: `src/main/java/ru/liko/wrbdrones/entity/Shahed136Entity.java`

- [ ] **Step 1: Добавить импорты ядра**

В блок импортов добавить:

```java
import ru.liko.wrbdrones.entity.flight.AircraftProfile;
import ru.liko.wrbdrones.entity.flight.FixedWingDynamics;
import ru.liko.wrbdrones.entity.flight.FlightDemand;
```

- [ ] **Step 2: Объявить синхро-аксессор AIRSPEED**

Рядом с другими `EntityDataAccessor` (после `ROLL`):

```java
    private static final EntityDataAccessor<Float> AIRSPEED = SynchedEntityData.defineId(Shahed136Entity.class,
            EntityDataSerializers.FLOAT);
```

- [ ] **Step 3: Зарегистрировать дефолт**

В `defineSynchedData` после `builder.define(ROLL, 0.0f);` добавить:

```java
        builder.define(AIRSPEED, 0.0f);
```

- [ ] **Step 4: Геттеры/сеттер airspeed + транзиентный газ**

В блок instance-полей добавить:

```java
    /** Газ [0..1], серверная физика; не синхронизируется. */
    private float throttle = 0.5f;
```

В блок геттеров/сеттеров добавить:

```java
    public float getAirspeed() {
        return this.entityData.get(AIRSPEED);
    }

    public void setAirspeed(float airspeed) {
        this.entityData.set(AIRSPEED, airspeed);
    }
```

- [ ] **Step 5: NBT save/load для airspeed**

В методе чтения NBT (рядом с `if (tag.contains("SetSpeed")) ...`) добавить:

```java
        if (tag.contains("Airspeed")) setAirspeed(tag.getFloat("Airspeed"));
        if (tag.contains("Throttle")) throttle = tag.getFloat("Throttle");
```

В методе записи NBT (рядом с `tag.putFloat("SetSpeed", ...)`) добавить:

```java
        tag.putFloat("Airspeed", getAirspeed());
        tag.putFloat("Throttle", throttle);
```

- [ ] **Step 6: Инициализация airspeed при запуске**

В методе `launch()` после `this.setDeltaMovement(forward.scale(LAUNCH_INITIAL_SPEED));` добавить:

```java
        // Энергомодель: стартуем сразу на заданной крейсерской скорости, чтобы не сваливаться на старте.
        setAirspeed(getSetSpeed());
        this.throttle = 0.5f;
```

- [ ] **Step 7: Компиляция**

Run: `./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL.

---

## Task 6: Shahed — новые константы и профиль, удаление устаревших

**Files:**
- Modify: `src/main/java/ru/liko/wrbdrones/entity/Shahed136Entity.java`

- [ ] **Step 1: Удалить устаревшие кинематические константы**

Из блока Flight Constants удалить (заменяются энергомоделью):

```java
    private static final float ACCELERATION = 0.05f;
    private static final float DIVE_ACCELERATION = 0.15f;
    private static final float DIVE_SPEED_MULTIPLIER = 2.5f;
    private static final float ROLL_SMOOTHING = 2.0f;
    private static final float ROLL_FROM_YAW_FACTOR = 15.0f;
    private static final double GRAVITY = 0.04;
```

- [ ] **Step 2: Добавить константы энергомодели и терминала**

В блок Flight Constants добавить:

```java
    // ── Энергомодель (калибровка; скорости в блоках/тик) ────────────
    private static final float THRUST_IDLE = 0.005f;
    private static final float THRUST_FULL = 0.06f;
    private static final float DRAG_COEF = 0.0052f;
    private static final float PITCH_GRAVITY = 0.09f;
    private static final float STALL_LIFT_LOSS = 0.10f;
    private static final float BANK_YAW_FACTOR = 0.05f;
    private static final float STALL_SPEED_FACTOR = 0.5f;   // stall = cruise·0.5
    private static final float DIVE_MAX_FACTOR = 2.2f;      // diveMax = setSpeed·2.2
    // Газ-серво: подводит throttle к удержанию getSetSpeed().
    private static final float THROTTLE_KP = 0.6f;          // пропорц. коэффициент по ошибке airspeed
    private static final float THROTTLE_RATE = 0.05f;       // макс. изменение газа за тик
    // Привязка курсового автопилота к крену (как course-режим Lancet).
    private static final float COURSE_DIRECT_YAW_GAIN = 0.06f;
    private static final float COURSE_ROLL_GAIN = 2.2f;
    private static final float PITCH_RESPONSE = 1.2f;
    private static final float TERMINAL_PITCH_RESPONSE = 2.2f;
    private static final float STALL_PITCH_DOWN_RATE = 1.2f;
    private static final float TERMINAL_TURN_RATE_CLOSE = 14.0f;
    private static final float TERMINAL_HOMING_RANGE = 40.0f;
    private static final float TERMINAL_MANEUVER_SPEED_FACTOR = 1.45f;
    // Взрыватель в терминале.
    private static final float PROXIMITY_ARM_RADIUS = 12.0f;
    private static final float PROXIMITY_CONTACT_RADIUS = 2.5f;
```

- [ ] **Step 3: Поле трекинга дальности для взрывателя**

В блок instance-полей добавить:

```java
    /** Квадрат дальности до цели в прошлый тик (взрыватель «ближайшего подхода»). −1 = не армирован. */
    private double prevTargetDistSqr = -1.0;
```

- [ ] **Step 4: Фабрика профиля Shahed**

Добавить приватный метод рядом с `updateFlight`:

```java
/** Профиль аэродинамики Shahed: крейсер = заданная операторская скорость. */
private AircraftProfile shahedProfile() {
    float cruise = getSetSpeed();
    float diveMax = Math.max(cruise * DIVE_MAX_FACTOR, cruise + 0.5f);
    float stall = cruise * STALL_SPEED_FACTOR;
    return new AircraftProfile(
            THRUST_IDLE, THRUST_FULL, DRAG_COEF, PITCH_GRAVITY,
            stall, cruise, diveMax,
            STALL_LIFT_LOSS, BANK_YAW_FACTOR);
}
```

- [ ] **Step 5: Компиляция**

Run: `./gradlew compileJava -q`
Expected: ОШИБКИ компиляции в `updateFlight` (использует удалённые `ACCELERATION`/`DIVE_*`/`ROLL_*`). Это ожидаемо — `updateFlight` переписывается в Task 7. Если других ошибок нет — продолжать. (Можно временно не запускать компиляцию до Task 7; шаг оставлен для явности.)

---

## Task 7: Shahed — энергетический `updateFlight` + `computeFlightDemand`

**Files:**
- Modify: `src/main/java/ru/liko/wrbdrones/entity/Shahed136Entity.java`

- [ ] **Step 1: Полностью заменить метод `updateFlight()`**

Заменить весь текущий метод `private void updateFlight() { ... }` на:

```java
    private void updateFlight() {
        Vec3 currentPos = this.position();
        Vec3 targetPos = getTargetPos();
        double distXZ = Math.sqrt(currentPos.distanceToSqr(targetPos.x, currentPos.y, targetPos.z));
        boolean terminal = distXZ < TERMINAL_PHASE_DISTANCE;

        AircraftProfile profile = shahedProfile();

        // ── 1. Газ-серво: держим заданную крейсерскую скорость; в терминале — полный газ ──
        float airspeed = getAirspeed();
        if (terminal) {
            throttle = Mth.lerp(0.2f, throttle, 1.0f);
        } else {
            float err = getSetSpeed() - airspeed;
            throttle += Mth.clamp(err * THROTTLE_KP, -THROTTLE_RATE, THROTTLE_RATE);
        }
        throttle = Mth.clamp(throttle, 0.0f, 1.0f);

        // ── 2. Энергоинтеграция airspeed ──
        airspeed = FixedWingDynamics.integrateAirspeed(airspeed, this.getXRot(), throttle, profile);
        airspeed = Mth.clamp(airspeed, 0.0f, profile.diveMaxSpeed());
        // На сближении в терминале гасим до манёвренной — тугой радиус R = v/ω доворачивает на точку.
        if (terminal && distXZ < TERMINAL_HOMING_RANGE) {
            float maneuverCap = Math.min(profile.diveMaxSpeed(), getSetSpeed() * TERMINAL_MANEUVER_SPEED_FACTOR);
            airspeed = Math.min(airspeed, maneuverCap);
        }
        setAirspeed(airspeed);

        // ── 3. Команда автопилота ──
        FlightDemand demand = computeFlightDemand(currentPos, targetPos, distXZ, terminal);

        // ── 4. Тангаж: доводим к demand.pitch(), при сваливании нос вниз ──
        float pitchResponse = terminal ? TERMINAL_PITCH_RESPONSE : PITCH_RESPONSE;
        float requestedPitch = Mth.approachDegrees(this.getXRot(), demand.pitch(), pitchResponse);
        float stallDeficit = FixedWingDynamics.stallDeficit(airspeed, profile.stallSpeed());
        if (stallDeficit > 0.0f) {
            requestedPitch = Mth.approachDegrees(requestedPitch, 22.0f,
                    STALL_PITCH_DOWN_RATE * (1.0f + 1.5f * stallDeficit));
        }
        float maxPitch = terminal ? TERMINAL_MAX_PITCH : CRUISE_MAX_PITCH;
        this.setXRot(Mth.clamp(requestedPitch, -maxPitch, maxPitch));

        // ── 5. Координированный банкированный разворот (паттерн course-режима Lancet) ──
        float bankFactor = FixedWingDynamics.bankFactor(airspeed, profile);
        float autoYawChange = Mth.clamp(demand.yawDiff() * COURSE_DIRECT_YAW_GAIN,
                -demand.turnRate(), demand.turnRate());
        float targetRoll = Mth.clamp(demand.yawDiff() * COURSE_ROLL_GAIN, -MAX_BANK_ANGLE, MAX_BANK_ANGLE);
        float rollRate = ROLL_RATE_BASE * Mth.clamp(airspeed / Math.max(getSetSpeed(), 0.01f), 0.4f, 1.4f);
        setRoll(Mth.approach(getRoll(), targetRoll, rollRate));
        float bankYaw = getRoll() * bankFactor;
        this.setYRot(this.getYRot() + bankYaw + autoYawChange);

        // ── 6. Сборка движения ──
        Vec3 motion = FixedWingDynamics.assembleMotion(
                this.getXRot(), this.getYRot(), airspeed, stallDeficit, profile);

        // ── 7. Столкновения и подрыв ──
        if (launchTicks > FAILSAFE_DELAY_TICKS) {
            Vec3 start = this.position();
            Vec3 end = start.add(motion.scale(RAYTRACE_SCALE));
            HitResult result = this.level()
                    .clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (result.getType() != HitResult.Type.MISS) {
                this.setPos(result.getLocation());
                explode();
                return;
            }
        }

        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);

        // ── 8. Терминальный взрыватель (контакт + ближайший подход) ──
        if (terminal) {
            checkTerminalDetonation(targetPos);
            if (this.isRemoved()) return;
        } else {
            prevTargetDistSqr = -1.0;
        }

        if (launchTicks > FAILSAFE_DELAY_TICKS
                && (this.horizontalCollision || this.verticalCollision || this.onGround())) {
            explode();
        }
    }
```

- [ ] **Step 2: Добавить недостающую константу темпа крена**

В блок Flight Constants добавить (используется в `updateFlight`):

```java
    private static final float ROLL_RATE_BASE = 2.5f;
```

- [ ] **Step 3: Добавить метод `computeFlightDemand`**

Добавить рядом с `updateFlight`:

```java
    /**
     * Команда курсового автопилота к точке-цели. Тангаж — на удержание заданной высоты
     * (крейсер) либо на пикирование (терминал); рыскание — рассогласование курса.
     * Режим уклонения подмешивает синусоиду в курс и высоту (как прежде).
     */
    private FlightDemand computeFlightDemand(Vec3 currentPos, Vec3 targetPos, double distXZ, boolean terminal) {
        double desiredY = targetPos.y;
        if (distXZ > CRUISE_PHASE_DISTANCE) {
            desiredY = getSetAltitude();
        }

        double dx = targetPos.x - currentPos.x;
        double dz = targetPos.z - currentPos.z;
        float desiredYaw = (float) (Mth.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;

        if (isEvasiveMode() && !terminal) {
            long time = this.level().getGameTime();
            desiredYaw += (float) Math.sin(time * 0.05) * EVASIVE_YAW_AMPLITUDE;
            desiredY += Math.cos(time * 0.03) * EVASIVE_ALTITUDE_AMPLITUDE;
        }

        double dy = desiredY - currentPos.y;
        float yawDiff = Mth.wrapDegrees(desiredYaw - this.getYRot());

        float maxPitch = terminal ? TERMINAL_MAX_PITCH : CRUISE_MAX_PITCH;
        float desiredPitch = (float) (-(Mth.atan2(dy, distXZ) * (180D / Math.PI)));
        if (distXZ > CLOSE_RANGE_DISTANCE) {
            desiredPitch = Mth.clamp(desiredPitch, -maxPitch, maxPitch);
        } else {
            desiredPitch = Mth.clamp(desiredPitch, -FULL_PITCH_RANGE, FULL_PITCH_RANGE);
        }

        // Темп разворота: в терминале растёт по мере сближения, чтобы доворачивать на точку.
        float turnRate;
        if (terminal) {
            float t = (float) Mth.clamp(1.0 - distXZ / TERMINAL_HOMING_RANGE, 0.0, 1.0);
            turnRate = Mth.lerp(t, TERMINAL_TURN_SPEED, TERMINAL_TURN_RATE_CLOSE);
        } else {
            turnRate = TURN_SPEED;
        }

        return new FlightDemand(true, desiredPitch, yawDiff, turnRate);
    }
```

- [ ] **Step 4: Компиляция**

Run: `./gradlew compileJava -q`
Expected: ОШИБКА «cannot find symbol: method checkTerminalDetonation» — реализуется в Task 8. Прочие ошибки (удалённые константы, типы) должны отсутствовать. Если есть другие ошибки — исправить до перехода.

---

## Task 8: Shahed — терминальный взрыватель `checkTerminalDetonation`

**Files:**
- Modify: `src/main/java/ru/liko/wrbdrones/entity/Shahed136Entity.java`

- [ ] **Step 1: Добавить метод взрывателя**

Добавить рядом с `explode()`:

```java
    /**
     * Гарантия подрыва в терминальной фазе: контактный взрыватель в {@link #PROXIMITY_CONTACT_RADIUS}
     * и неконтактный «по ближайшему подходу» — после арминга в {@link #PROXIMITY_ARM_RADIUS}
     * подрыв, когда дальность до точки-цели снова начала расти (дрон прошёл мимо).
     * Это исключает бесконечное наматывание кругов вокруг точки.
     */
    private void checkTerminalDetonation(Vec3 targetPos) {
        double distSqr = this.position().distanceToSqr(targetPos);

        if (distSqr <= PROXIMITY_CONTACT_RADIUS * PROXIMITY_CONTACT_RADIUS) {
            explode();
            return;
        }

        if (distSqr <= PROXIMITY_ARM_RADIUS * PROXIMITY_ARM_RADIUS
                && prevTargetDistSqr >= 0.0 && distSqr > prevTargetDistSqr) {
            explode();
            return;
        }

        prevTargetDistSqr = distSqr;
    }
```

- [ ] **Step 2: Сброс трекинга при входе в терминал**

Уже учтено в `updateFlight` (ветка `else { prevTargetDistSqr = -1.0; }` сбрасывает армирование вне терминала). Дополнительно в `explode()` ничего менять не нужно. Убедиться, что в `updateFlight` присутствует этот сброс (см. Task 7 Step 1, блок 8).

- [ ] **Step 3: Компиляция**

Run: `./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL (единственное ожидаемое замечание — про deprecated API в `Shahed136Model`, не связано).

---

## Task 9: Интеграционная верификация

**Files:** —

- [ ] **Step 1: Полная компиляция**

Run: `./gradlew compileJava -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Ручной прогон в клиенте**

Run: `./gradlew runClient`

Проверить по чек-листу (критерии приёмки из spec):

1. **Shahed крейсер:** пологий полёт, удержание `setAltitude`, плавные банкированные развороты к цели.
2. **Энергообмен:** замедление в наборе высоты/развороте, разгон в пикировании.
3. **Терминал:** на подлёте < 100 блоков — пикирование, разгон к `diveMax`, поджатие скорости у самой цели.
4. **Подрыв гарантирован:** дрон подрывается у точки-цели, НЕ наматывает круги по земле.
5. **Сваливание:** при искусственно завышенной цели/крутом наборе нос проседает вниз, нет «зависания».
6. **Lancet регресс:** запустить Lancet (course/recon/terminal) — траектории и поведение как до изменений.

- [ ] **Step 3: Зафиксировать результат**

Если все пункты проходят — сообщить пользователю и спросить разрешение на коммит (пользователь ранее просил без коммитов). Если есть отклонения по ощущению — подстроить калибровочные константы Task 6 Step 2 (`THRUST_*`, `DRAG_COEF`, `PITCH_GRAVITY`, `BANK_YAW_FACTOR`, `*_PITCH_RESPONSE`) и повторить Step 1-2.

---

## Self-Review (заполнено автором плана)

- **Покрытие spec:** §1 ядро → Task 1-3; §2 миграция Lancet → Task 4; §3 энергомодель Shahed → Task 5-7; §4 терминал/взрыватель → Task 7-8; §5 совместимость (AIRSPEED/NBT/UI) → Task 5; §6 верификация → Task 9. Пробелов нет.
- **Плейсхолдеры:** отсутствуют — весь код приведён целиком, команды и ожидаемый вывод указаны.
- **Согласованность типов:** `FlightDemand(boolean,float,float,float)` и `AircraftProfile(9×float)` совпадают между Task 1-3 и потребителями (Task 4, 7). `getAirspeed/setAirspeed`, `shahedProfile()/lancetProfile()`, `computeFlightDemand(Vec3,Vec3,double,boolean)`, `checkTerminalDetonation(Vec3)` — сигнатуры консистентны между задачами.
- **Замечание по порядку:** Task 5 Step 5 и Task 6 Step 4/Task 7 промежуточно не компилируются по одному — это явно отмечено в Expected. Цельная компиляция гарантируется к Task 8 Step 3.
```
