# Аэрофизика Shahed-136 + общий FixedWingDynamics

**Дата:** 2026-06-15
**Статус:** дизайн одобрен, ожидает ревью spec

## Цель

Перевести `Shahed136Entity` с кинематической модели point-mass (`motion = направление·скорость`)
на полноценную энергетическую аэродинамическую модель уровня `ZalaLancetEntity`: импульс/инерция,
тяга вдоль носа, квадратичное лобовое сопротивление, гравитационная составляющая по тангажу
(энергообмен: разгон в пикировании, потеря скорости в наборе), банкированные координированные
развороты, сваливание. Энергоформула должна жить в **одном** месте (требование CLAUDE.md:
«single source of truth, do not parallel it elsewhere»), поэтому общее ядро выносится в
переиспользуемый helper, на который мигрирует и Lancet — строго без изменения его поведения.

Объём итерации: всё сразу (ядро + миграция Lancet + модель Shahed + терминальное наведение/подрыв).

## Нефункциональные ограничения

- Java 21, NeoForge 1.21.1. Комментарии/Javadoc — на русском (конвенция репозитория).
- Сервер-сайд физика; `noGravity=true` сохраняется (гравитация уже в энергочлене), как у Lancet.
- Поведение Lancet после миграции — **идентичное** (те же константы через профиль).
- Операторская семантика Shahed (`setSpeed`/`setAltitude`/`evasiveMode`, UI радио/монитора) сохраняется.
- Автотестов физики в проекте нет; верификация — `compileJava` + ручной прогон в `runClient`.

## Архитектура

### Новый пакет `ru.liko.wrbdrones.entity.flight`

#### `FixedWingDynamics` (чистый stateless helper — единственный носитель энергоформулы)

```java
public final class FixedWingDynamics {
    /** Интеграция воздушной скорости за тик: airspeed += thrust(throttle) + g·sin(pitch) − drag·v². */
    public static float integrateAirspeed(float airspeed, float pitchDeg, float throttle, AircraftProfile p);

    /** Дефицит сваливания [0..1]: 0 — нет, 1 — airspeed=0. */
    public static float stallDeficit(float airspeed, float stallSpeed);

    /** Связь крен→рыскание: bankYawFactor·(v/cruise)², квадратично от скорости. */
    public static float bankFactor(float airspeed, AircraftProfile p);

    /** Вектор движения из ориентации и airspeed + проседание Y при сваливании (квадратично от дефицита). */
    public static Vec3 assembleMotion(float pitchDeg, float yawDeg, float airspeed,
                                      float stallDeficit, AircraftProfile p);
}
```

#### `AircraftProfile` (record — параметры конкретного ЛА)

```java
public record AircraftProfile(
    float thrustIdle, float thrustFull, float dragCoef, float pitchGravity,
    float stallSpeed, float cruiseSpeed, float diveMaxSpeed,
    float stallLiftLoss, float bankYawFactor) {}
```

#### `FlightDemand` (record — «хотелка» автопилота/пилота; выносится из Lancet, где он приватный)

```java
public record FlightDemand(boolean hasAutopilot, float pitch, float yawDiff, float turnRate) {
    public static FlightDemand manual();
}
```

### Что остаётся в каждой сущности (различается, НЕ выносится)

- Политика газа (throttle): у Lancet — режимы recon/course/terminal; у Shahed — газ-серво на `setSpeed` + полный газ в терминале.
- Геометрия автопилота (вычисление `FlightDemand`): у Lancet — loiter/course/terminal; у Shahed — крейсер/подлёт/терминальное пикирование на `targetPos`/`setAltitude`.
- Ветки применения attitude (response-константы, clamps), столкновения и подрыв.

### Что уходит в `FixedWingDynamics` (идентично для всех ЛА)

- Энергоинтеграция airspeed (тяга + g·sin(pitch) − drag·v², clamp к diveMax).
- Сваливание (детект дефицита; довод носа вниз — вызывающий код применяет, helper даёт дефицит).
- `bankFactor` (крен→рыскание).
- Сборка `motion` (direction·airspeed + потеря подъёмной силы при сваливании).

## Миграция Lancet (behavior-preserving)

`ZalaLancetEntity.updateFixedWingFlight` переписывается так, что вместо инлайновых формул
вызывает `FixedWingDynamics` с `AircraftProfile`, заполненным из текущих `LANCET_*` config-геттеров
(`getStallSpeed/getCruiseSpeed/getDiveMaxSpeed`) и нынешних private-констант
(`THRUST_IDLE/THRUST_FULL/DRAG_COEF/PITCH_GRAVITY/STALL_LIFT_LOSS/BANK_YAW_FACTOR`).
Все ветки демпфирования/режимов/response остаются на месте. Приватный `FlightDemand`
заменяется на `flight.FlightDemand` (тот же контракт). Формула 1:1 → траектории не меняются.

## Энергомодель Shahed (`Shahed136Entity`)

### Профиль (тюнинг-константы `private static final`, калибровка)

- Тихоходный пологий крейсер; равновесие airspeed на крейсерском газу ≈ `getSetSpeed()`.
- `cruiseSpeed` из `SHAHED136_*_SPEED_KMH` (1/72 ≈ 1 км/ч → blocks/tick); `diveMaxSpeed` — потолок пикирования.
- `dragCoef/thrust*` подобраны под равновесие; `pitchGravity` даёт ощутимый энергообмен.
- `bankYawFactor` подобран под радиус разворота, отвечающий неповоротливому Shahed.

### Цикл `updateFlight()` (новый)

1. **Газ-серво**: пропорциональный регулятор подводит throttle к удержанию `getSetSpeed()` в крейсере;
   в терминальном пикировании — полный газ.
2. `airspeed = FixedWingDynamics.integrateAirspeed(...)`; на сближении в терминале — поджатие до манёвренной.
3. **`FlightDemand`** (новый метод `computeFlightDemand`): yaw на цель по XZ; pitch на удержание `setAltitude`
   (крейсер) либо пикирование (терминал). `EVASIVE_MODE` — подмешивание синусоиды в demand (как сейчас).
   Сохраняются фазы по дистанции: крейсер (`> CRUISE_PHASE_DISTANCE`), подлёт, терминал (`< TERMINAL_PHASE_DISTANCE`).
4. **Attitude**: pitch доводится к `demand.pitch()` (response-константа), при сваливании нос вниз; clamp.
   Крен — координированный (target из demand/bank), `setRoll`; рыскание = bankYaw + autoYaw (`bankFactor`).
   Существующий косметический `ROLL_FROM_YAW_FACTOR` удаляется (заменён реальным банком).
5. `motion = FixedWingDynamics.assembleMotion(...)`; `willHitBlock`-рейтрейс → `explode()`; `move`; пост-проверка коллизий → `explode()`.

### Синхро-поле `AIRSPEED`

Добавляется `EntityDataAccessor<Float> AIRSPEED` (как у Lancet) — для звука/HUD и плавной экстраполяции.
`getCurrentSpeed()` может опираться на airspeed вместо `getDeltaMovement().length()`.

## Терминальное наведение и гарантированный подрыв

Инертная модель с овершутами means простого «взорваться при столкновении» мало — дрон может закружить.
По образцу Lancet:

- **Поджатие скорости** на сближении (`dist < TERMINAL_HOMING_RANGE`) до `cruiseSpeed·factor` — затягивает `R=v/ω` на точку.
- **Взрыватель** (`checkTerminalDetonation`-аналог): контактный (`PROXIMITY_CONTACT_RADIUS`) +
  неконтактный по «ближайшему подходу» (дистанция перестала падать после арминга в `PROXIMITY_ARM_RADIUS`),
  плюс существующие триггеры (`willHitBlock`/`horizontalCollision`/`verticalCollision`/`onGround`/вода/`MAX_DISTANCE`).
  `prevTargetDistSqr` сбрасывается при входе в терминал.

## Затрагиваемые файлы

- **Новые:** `entity/flight/FixedWingDynamics.java`, `entity/flight/AircraftProfile.java`, `entity/flight/FlightDemand.java`.
- **Изменяются:** `entity/ZalaLancetEntity.java` (миграция на ядро), `entity/Shahed136Entity.java` (энергомодель + терминал + AIRSPEED).
- **Возможно:** `config/ServerConfig.java` — только если по ходу понадобится вынести тюнинг Shahed в конфиг (по умолчанию — нет, YAGNI).

## Риски и митигировка

- **Главный риск:** регрессия Lancet при миграции. Митигировка: формула переносится 1:1 с теми же
  константами; верификация ручным прогоном Lancet (крейсер/орбита/course/терминал).
- **Калибровка Shahed:** новые константы потребуют подгонки на глаз; начинаем от значений, дающих
  равновесие ≈ `setSpeed`, и правим по ощущению.
- **Совместимость сейвов:** новое синхро-поле/NBT `airspeed` — добавить дефолт при чтении старых сущностей.

## Критерии приёмки (ручная верификация в runClient)

1. Shahed: пологий крейсер с удержанием `setAltitude`.
2. Банкированные развороты с креном и энергопотерей (замедление в развороте/наборе).
3. Разгон в терминальном пикировании, выход на `diveMaxSpeed`.
4. Гарантированный подрыв по цели без «наматывания кругов».
5. Сваливание при слишком крутом наборе (нос вниз, проседание).
6. Lancet: поведение не изменилось во всех режимах.
