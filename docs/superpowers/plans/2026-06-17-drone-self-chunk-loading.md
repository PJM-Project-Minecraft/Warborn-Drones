# Дрон сам грузит чанки (пилот на месте) — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Дрон (FPV/Mavic/Lancet) сам держит и стримит чанки вокруг себя пилотирующему клиенту; реальное тело пилота остаётся на месте, заморожено, но уязвимо.

**Architecture:** Убираем телепорт игрока к дрону и декой-подсистему. Для пилотирующего игрока серверный «центр обзора» (выбор чанков для стриминга, центр кэша клиента, трекинг сущностей) перенаправляется на позицию дрона через мишень-redirect `SectionPos.of(player)` в `ChunkMap`. Тело пилота пинится на месте и держится загруженным форс-тикетом. Камера на клиенте переносится на дрон. Стратегия — спайк на FPV, затем распространение и удаление декоя.

**Tech Stack:** Java 21, NeoForge 1.21.1, Mixin (Mojang mappings), GeckoLib (рендер), композитная сборка с SuperbWarfare.

## Global Constraints

- NeoForge 1.21.1, Java 21. `mod_id = "wrbdrones"`, namespace `ru.liko.wrbdrones`.
- Комментарии/Javadoc — на русском (конвенция кодовой базы).
- Внутренние поля/методы на SBW-типах префиксуются `wrbdrones$`.
- Нет юнит-тест-харнесса для рендера/чанков. «Тест» каждой задачи = `./gradlew compileJava` (синтаксис) и/или ручной сценарий в `./gradlew runClient` с явным наблюдением. Game-test сервер (`runGameTestServer`) доступен для чисто серверной логики.
- Новые миксины добавляются в `src/main/resources/mixins.wrbdrones.json` (общие — в `"mixins"`, клиентские — в `"client"`).
- Единый источник правды по сигналу — `util/SignalCalculator`; не дублировать формулу.
- `SectionPos.of(Entity)` в Mojang-маппингах NeoForge 1.21.1; `ChunkMap.move(ServerPlayer)` использует его для центрирования трекинга. Проверять точные сигнатуры в декомпиле перед инъекцией.

---

## Файловая структура

**Создаём:**
- `src/main/java/ru/liko/wrbdrones/util/PilotViewAnchors.java` — серверный реестр `UUID игрока → дрон`, по которому миксины узнают «центр обзора» (позицию дрона) для пилотирующего игрока. Dist-neutral, без `@OnlyIn`.
- `src/main/java/ru/liko/wrbdrones/mixin/ChunkMapPilotAnchorMixin.java` — redirect `SectionPos.of(player)` в `ChunkMap` на позицию дрона для пилота.
- `src/main/java/ru/liko/wrbdrones/util/PilotChunkTicket.java` — постановка/снятие форс-тикета на домашний чанк пилота.

**Модифицируем:**
- `src/main/java/ru/liko/wrbdrones/entity/AddonDroneEntity.java` — `beginRemoteControl`/`endRemoteControl` (убрать телепорт/спектатор/декой; включить пин, тикет, anchor); серверный пин тела в `baseTick`.
- `src/main/java/ru/liko/wrbdrones/mixin/client/CameraMixinWRB.java` — обобщить камеру с Lancet на FPV/Mavic.
- `src/main/resources/mixins.wrbdrones.json` — регистрация нового миксина.

**Удаляем (Фаза 3, после успешного распространения):**
- `entity/PlayerDecoyEntity.java`, `util/PlayerDecoyManager.java`, `event/DecoyTargetHandler.java`, `event/DroneControlDamageHandler.java`, регистрация `ModEntityTypes.PLAYER_DECOY`, связанные ресурсы рендера декоя.

---

# ФАЗА 0 — Спайк на FPV (валидация рендера)

Цель фазы: доказать, что при отключённом телепорте клиент рендерит местность и
сущности вокруг дрона, пока тело игрока стоит дома. Всё под флагом «только FPV».

### Task 0.1: Реестр якорей обзора пилота (`PilotViewAnchors`)

**Files:**
- Create: `src/main/java/ru/liko/wrbdrones/util/PilotViewAnchors.java`

**Interfaces:**
- Produces:
  - `static void setAnchor(UUID playerId, Entity drone)`
  - `static void clearAnchor(UUID playerId)`
  - `static @Nullable Entity getAnchorDrone(UUID playerId)`
  - `static boolean isEmpty()` — дешёвая проверка для горячего пути миксина.

- [ ] **Step 1: Реализовать реестр**

```java
package ru.liko.wrbdrones.util;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный реестр «центров обзора» пилотов: пока игрок управляет дроном без
 * телепорта, его поток чанков/сущностей центрируется не на нём, а на дроне.
 * Миксины видят только {@link net.minecraft.server.level.ServerPlayer}; этот
 * реестр — мост от UUID игрока к сущности дрона, чью позицию надо использовать
 * как центр трекинга. Dist-neutral (без {@code @OnlyIn}).
 */
public final class PilotViewAnchors {

    private static final Map<UUID, Entity> ANCHORS = new ConcurrentHashMap<>();

    private PilotViewAnchors() {}

    public static void setAnchor(final UUID playerId, final Entity drone) {
        if (playerId == null || drone == null) return;
        ANCHORS.put(playerId, drone);
    }

    public static void clearAnchor(final UUID playerId) {
        if (playerId == null) return;
        ANCHORS.remove(playerId);
    }

    @Nullable
    public static Entity getAnchorDrone(final UUID playerId) {
        if (ANCHORS.isEmpty()) return null;
        Entity drone = ANCHORS.get(playerId);
        if (drone != null && drone.isRemoved()) {
            ANCHORS.remove(playerId);
            return null;
        }
        return drone;
    }

    /** Дешёвая проверка пустоты — горячий путь миксина ChunkMap. */
    public static boolean isEmpty() {
        return ANCHORS.isEmpty();
    }
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/wrbdrones/util/PilotViewAnchors.java
git commit -m "Реестр якорей обзора пилота (PilotViewAnchors)"
```

---

### Task 0.2: Форс-тикет домашнего чанка пилота (`PilotChunkTicket`)

**Files:**
- Create: `src/main/java/ru/liko/wrbdrones/util/PilotChunkTicket.java`

**Interfaces:**
- Produces:
  - `static void hold(ServerPlayer player)` — форс-загрузить чанк под игроком.
  - `static void release(ServerPlayer player)` — снять форс-загрузку.

**Контекст:** когда центр трекинга уезжает на дрон, собственный player-ticket
игрока уходит туда же, и домашний чанк может выгрузиться, заморозив тело. Держим
его отдельным тикетом. Используем `ServerLevel.setChunkForced(x, z, true)` —
простейший постоянный форс-чанк (тикающий), снимаемый на выходе.

- [ ] **Step 1: Реализовать**

```java
package ru.liko.wrbdrones.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Держит домашний чанк пилота загруженным, пока тот управляет дроном без телепорта.
 * Без этого собственный player-ticket игрока «уезжает» вместе с центром трекинга на
 * дрон, и тело пилота во выгруженном чанке перестаёт тикать/быть уязвимым.
 */
public final class PilotChunkTicket {

    private record Held(UUID dimensionLevelKey, long chunkPos) {}

    // playerId -> (level, chunkPos), чтобы корректно снять именно тот чанк, что держали.
    private static final Map<UUID, ServerLevel> LEVELS = new ConcurrentHashMap<>();
    private static final Map<UUID, ChunkPos> CHUNKS = new ConcurrentHashMap<>();

    private PilotChunkTicket() {}

    public static void hold(final ServerPlayer player) {
        if (player == null) return;
        ServerLevel level = player.serverLevel();
        ChunkPos pos = new ChunkPos(player.blockPosition());
        level.setChunkForced(pos.x, pos.z, true);
        LEVELS.put(player.getUUID(), level);
        CHUNKS.put(player.getUUID(), pos);
    }

    public static void release(final ServerPlayer player) {
        if (player == null) return;
        UUID id = player.getUUID();
        ServerLevel level = LEVELS.remove(id);
        ChunkPos pos = CHUNKS.remove(id);
        if (level != null && pos != null) {
            level.setChunkForced(pos.x, pos.z, false);
        }
    }
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/wrbdrones/util/PilotChunkTicket.java
git commit -m "Форс-тикет домашнего чанка пилота (PilotChunkTicket)"
```

---

### Task 0.3: Redirect центра трекинга в ChunkMap на дрон

**Files:**
- Create: `src/main/java/ru/liko/wrbdrones/mixin/ChunkMapPilotAnchorMixin.java`
- Modify: `src/main/resources/mixins.wrbdrones.json` (добавить в `"mixins"`)

**Interfaces:**
- Consumes: `PilotViewAnchors.getAnchorDrone(UUID)`, `PilotViewAnchors.isEmpty()`.

**Контекст:** `ChunkMap.move(ServerPlayer)` вызывает `SectionPos.of(player)` для
определения, какие чанки трекать/слать игроку и куда слать
`ClientboundSetChunkCacheCenterPacket`. Redirect-им этот вызов: если у игрока есть
якорь — возвращаем `SectionPos.of(drone)`. Тогда сервер сам начинает слать чанки
вокруг дрона и ре-центрирует клиентский кэш ванильным пакетом.

**ВАЖНО:** точное имя/дескриптор метода (`move`/`updatePlayerStatus`) и того, что
именно вызывает `SectionPos.of`, проверить в декомпиле NeoForge 1.21.1 перед
инъекцией. Ниже — целевой каркас под Mojang-маппинги.

- [ ] **Step 1: Написать миксин-redirect**

```java
package ru.liko.wrbdrones.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.liko.wrbdrones.util.PilotViewAnchors;

/**
 * Переносит «центр обзора» пилотирующего игрока на дрон: redirect статического
 * {@code SectionPos.of(Entity)} внутри {@code ChunkMap.move} так, что для игрока с
 * активным якорем (см. {@link PilotViewAnchors}) возвращается секция дрона. Это
 * заставляет сервер стримить чанки вокруг дрона и слать клиенту cache-center на дрон.
 */
@Mixin(ChunkMap.class)
public class ChunkMapPilotAnchorMixin {

    @Redirect(
            method = "move(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/core/SectionPos;"))
    private SectionPos wrbdrones$anchorSection(final Entity entity) {
        if (entity instanceof ServerPlayer player && !PilotViewAnchors.isEmpty()) {
            Entity drone = PilotViewAnchors.getAnchorDrone(player.getUUID());
            if (drone != null) {
                return SectionPos.of(drone);
            }
        }
        return SectionPos.of(entity);
    }
}
```

- [ ] **Step 2: Зарегистрировать миксин**

В `src/main/resources/mixins.wrbdrones.json`, массив `"mixins"`, после
`"PlayerChunkSenderMixin"` добавить `"ChunkMapPilotAnchorMixin"`.

- [ ] **Step 3: Проверить компиляцию + применимость миксина**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. Если редактор refmap ругается на target — открыть
декомпиль `ChunkMap` и поправить `method`/`target` дескриптор под фактический
(возможные имена: `move`, `updatePlayerStatus`; цель может быть `SectionPos.of`
с другим дескриптором или `player.getLastSectionPos`).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ru/liko/wrbdrones/mixin/ChunkMapPilotAnchorMixin.java src/main/resources/mixins.wrbdrones.json
git commit -m "Миксин: центр трекинга чанков пилота -> дрон"
```

---

### Task 0.4: Вход в управление FPV без телепорта (за флагом)

**Files:**
- Modify: `src/main/java/ru/liko/wrbdrones/entity/AddonDroneEntity.java` (метод `beginRemoteControl`, ~657-700)

**Interfaces:**
- Consumes: `PilotViewAnchors.setAnchor`, `PilotChunkTicket.hold`.
- Produces: новое поведение входа для `FpvDroneEntity` (по `this instanceof ru.liko.wrbdrones.entity.FpvDroneEntity`).

**Контекст спайка:** меняем поведение только для FPV. Для остальных дронов оставляем
старый путь (телепорт+декой), чтобы ничего не сломать до валидации.

- [ ] **Step 1: Разветвить beginRemoteControl для FPV**

Внутри `if (!player.level().isClientSide() && this.level() instanceof ServerLevel droneLevel)` заменить безусловный блок «декой + спектатор + телепорт» на ветвление:

```java
boolean selfChunkMode = this instanceof ru.liko.wrbdrones.entity.FpvDroneEntity;

if (selfChunkMode) {
    // НОВЫЙ путь: игрок остаётся на месте, дрон сам центрирует прогрузку чанков.
    // Тело замораживается уязвимым (без спектатора, без декоя, без телепорта).
    ru.liko.wrbdrones.util.PilotChunkTicket.hold(player);           // держим домашний чанк
    ru.liko.wrbdrones.util.PilotViewAnchors.setAnchor(player.getUUID(), this); // центр обзора -> дрон
} else {
    // СТАРЫЙ путь (Mavic/Lancet до распространения фичи).
    ru.liko.wrbdrones.util.PlayerDecoyManager.createDecoy(player, this);
    player.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);
    player.teleportTo(
            droneLevel,
            this.getX(),
            this.getY() + this.getBbHeight() + 4.0,
            this.getZ(),
            this.getYRot(),
            0.0f);
    player.setYHeadRot(this.getYRot());
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/wrbdrones/entity/AddonDroneEntity.java
git commit -m "FPV: вход в управление без телепорта (за флагом self-chunk)"
```

---

### Task 0.5: Заморозка тела пилота на месте (сервер)

**Files:**
- Modify: `src/main/java/ru/liko/wrbdrones/entity/AddonDroneEntity.java` (серверный тик; рядом с `baseTick`/`updateOperatorPosition`, ~979-996)

**Контекст:** в self-chunk режиме тело не должно «уезжать» от чужого ввода и падать.
Каждый серверный тик, пока FPV-пилот активен, пиним позицию игрока к зафиксированной
точке и обнуляем скорость. Точку фиксации берём из `controlSession.originPos`
(см. Task 0.7 — сохраняем её и в новом пути) либо из текущей позиции на момент входа.

- [ ] **Step 1: Добавить пин в серверный тик контроллера**

В блоке `baseTick`, где обрабатывается активный контроллер (там, где сейчас
`updateOperatorPosition()` и `ChunkSendBooster`), добавить:

```java
// Self-chunk режим: тело пилота заморожено на месте (но уязвимо).
if (this instanceof ru.liko.wrbdrones.entity.FpvDroneEntity
        && controlSession != null
        && wrbdrones$controllerUuid != null) {
    net.minecraft.world.phys.Vec3 anchor = controlSession.originPos;
    if (anchor != null && (serverPlayer.position().distanceToSqr(anchor) > 1.0E-4
            || !serverPlayer.getDeltaMovement().equals(net.minecraft.world.phys.Vec3.ZERO))) {
        serverPlayer.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        serverPlayer.teleportTo(anchor.x, anchor.y, anchor.z);
        serverPlayer.fallDistance = 0.0f;
    }
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/wrbdrones/entity/AddonDroneEntity.java
git commit -m "FPV self-chunk: пин тела пилота на месте (сервер)"
```

---

### Task 0.6: Камера FPV на дрон (клиент)

**Files:**
- Modify: `src/main/java/ru/liko/wrbdrones/mixin/client/CameraMixinWRB.java`

**Контекст:** сейчас перехват `Camera.setup` срабатывает только для `ZalaLancetEntity`.
Для FPV без телепорта камера должна так же ставиться в точку дрона. Обобщаем: если
монитор смотрит на наш `FpvDroneEntity`, ставим камеру в его обзор (носовой маунт).
HUD/носовая поза FPV уже считаются в существующем коде — переиспользуем доступную
позицию/поворот дрона (нос + текущий yaw/pitch тела дрона).

- [ ] **Step 1: Расширить перехват на FPV**

В `wrbdrones$onSetup`, после получения `drone`, добавить ветку для FPV перед
проверкой на Lancet:

```java
if (drone instanceof ru.liko.wrbdrones.entity.FpvDroneEntity fpv) {
    // Камера в носовую точку FPV: позиция = глаза дрона, поворот = курс/тангаж дрона.
    net.minecraft.world.phys.Vec3 eye = fpv.getEyePosition(partialTicks);
    float yaw = fpv.getViewYRot(partialTicks);
    float pitch = fpv.getViewXRot(partialTicks);
    setRotation(yaw, pitch);
    setPosition(eye.x, eye.y, eye.z);
    info.cancel();
    return;
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. Если `getViewYRot/getViewXRot` отсутствуют у FPV —
использовать `fpv.getYRot()`/`fpv.getXRot()` (проверить в `FpvDroneEntity`/SBW
`DroneEntity`, как сейчас считается ориентация для HUD).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/wrbdrones/mixin/client/CameraMixinWRB.java
git commit -m "FPV self-chunk: камера на дрон (клиент)"
```

---

### Task 0.7: Выход из управления FPV без телепорта

**Files:**
- Modify: `src/main/java/ru/liko/wrbdrones/entity/AddonDroneEntity.java` (`endRemoteControl`, ~706-747; `ControlSession` originPos уже есть)

**Контекст:** на выходе из FPV-управления НЕ телепортировать (игрок не двигался),
а снять якорь, тикет и заморозку. Старый путь (телепорт-назад + декой) оставить для
не-FPV.

- [ ] **Step 1: Разветвить endRemoteControl**

В `endRemoteControl`, в блоке телепорта-назад, обернуть старую логику в ветку:

```java
boolean selfChunkMode = this instanceof ru.liko.wrbdrones.entity.FpvDroneEntity;

if (selfChunkMode) {
    // Игрок не перемещался — только снимаем режим self-chunk.
    ru.liko.wrbdrones.util.PilotViewAnchors.clearAnchor(player.getUUID());
    ru.liko.wrbdrones.util.PilotChunkTicket.release(player);
    // Вернуть углы взгляда оператора (тело не двигалось, но камера была на дроне).
    var session = controlSession;
    if (session != null) {
        player.setYRot(session.originYaw);
        player.setXRot(session.originPitch);
        player.setYHeadRot(session.originYaw);
    }
} else {
    // СТАРЫЙ путь: телепорт-назад + восстановление режима.
    var session = controlSession;
    if (session != null && player.getServer() != null) {
        ServerLevel target = player.getServer().getLevel(session.dimension);
        if (target != null) {
            player.teleportTo(target, session.originPos.x, session.originPos.y, session.originPos.z,
                    session.originYaw, session.originPitch);
            player.fallDistance = 0.0f;
        }
        player.setInvisible(false);
        player.gameMode.changeGameModeForPlayer(session.gameMode);
    }
    ru.liko.wrbdrones.util.PlayerDecoyManager.removeDecoy(player.getUUID());
}
```

- [ ] **Step 2: Проверить компиляцию**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ru/liko/wrbdrones/entity/AddonDroneEntity.java
git commit -m "FPV self-chunk: выход без телепорта (снятие якоря/тикета)"
```

---

### Task 0.8: РУЧНАЯ ВАЛИДАЦИЯ спайка (гейт)

**Files:** нет (наблюдение).

- [ ] **Step 1: Полная сборка**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Запустить клиент**

Run: `./gradlew runClient`

- [ ] **Step 3: Сценарий проверки**

1. Создать мир (creative для удобства), спавн FPV-дрона, привязать монитор.
2. Запомнить точку, где стоит игрок. Взять монитор (вход в управление).
3. **Наблюдать:** игрок НЕ телепортировался; камера показывает вид с дрона.
4. Отлететь дроном далеко за пределы render distance от точки игрока.
5. **Наблюдать (главное):** местность вокруг дрона прогружается и рендерится,
   не «пустота/void»; сущности (мобы/блоки-сущности) вокруг дрона видны.
6. Выйти из управления (Shift). **Наблюдать:** камера/взгляд вернулись, игрок на
   своём месте, мир вокруг игрока снова прогружен.
7. Переключиться в survival, повторить вход; пока «в трансе» получить урон от моба
   рядом с телом — **наблюдать:** урон проходит.

- [ ] **Step 4: РЕШЕНИЕ (decision gate)**

- **Если местность/сущности рендерятся** → спайк успешен, перейти к Фазе 1.
- **Если местность не рендерится** (void вокруг дрона при том, что сервер шлёт
  чанки) → нужен клиентский ре-центр рендера. Добавить Task 0.9 (ниже) и
  перепроверить.

---

### Task 0.9 (УСЛОВНО, если Step 4 показал void): клиентский ре-центр рендера

**Files:**
- Create: `src/main/java/ru/liko/wrbdrones/mixin/client/ClientChunkCacheCenterMixin.java` (или `LevelRendererMixin`)
- Modify: `mixins.wrbdrones.json` (`"client"`)

**Контекст:** если `LevelRenderer.setupRender`/`ClientChunkCache` отбрасывают чанки/
секции по позиции `LocalPlayer`, а не камеры, перенаправить центр на позицию дрона
на клиенте, когда активен FPV-монитор. Точный таргет (`getViewCenterX/Z` в
`ClientChunkCache.Storage`, либо `LevelRenderer.viewArea` recenter) определить в
декомпиле по факту проявления бага.

- [ ] **Step 1:** Реализовать клиентский redirect центра кэша/рендера на позицию
  дрона при активном FPV-мониторе (зеркало серверного якоря, но на клиенте — через
  `EntityFindUtil.findDrone` от монитора, как в `CameraMixinWRB`).
- [ ] **Step 2:** `./gradlew compileJava` → `BUILD SUCCESSFUL`.
- [ ] **Step 3:** Повторить сценарий Task 0.8; местность должна рендериться.
- [ ] **Step 4:** Commit: `git commit -m "FPV self-chunk: клиентский ре-центр рендера на дрон"`.

---

# ФАЗА 1 — Распространение на Mavic и Lancet

Предусловие: Фаза 0 успешна.

### Task 1.1: Обобщить флаг self-chunk на все WRB-дроны

**Files:**
- Modify: `AddonDroneEntity.java` (заменить `this instanceof FpvDroneEntity` на общий предикат), `CameraMixinWRB.java` (камера для Mavic; Lancet уже имеет свой маунт).

- [ ] **Step 1:** Завести метод `protected boolean wrbdrones$usesSelfChunkLoading() { return true; }` в `AddonDroneEntity` и переопределять при необходимости. Заменить все `this instanceof FpvDroneEntity` (Tasks 0.4, 0.5, 0.7) на `wrbdrones$usesSelfChunkLoading()`.
- [ ] **Step 2:** В `CameraMixinWRB` добавить ветку для `MavicDroneNoDropEntity`/`MavicDroneWithDropEntity` (бортовая камера). Lancet-ветку оставить.
- [ ] **Step 3:** `./gradlew build` → `BUILD SUCCESSFUL`.
- [ ] **Step 4:** runClient: проверить Mavic и Lancet по сценарию Task 0.8 (для Lancet — режимы COURSE/RECON, камера и прогрузка по курсу полёта).
- [ ] **Step 5:** Commit: `git commit -m "Self-chunk loading для Mavic и Lancet"`.

### Task 1.2: Трекинг сущностей вокруг дрона (если на Шаге 1.1 сущности не видны)

**Files:**
- Create: `src/main/java/ru/liko/wrbdrones/mixin/ChunkMapTrackedEntityMixin.java`

- [ ] **Step 1:** Если в runClient сущности вокруг дрона не появляются (видна только местность), добавить redirect в `ChunkMap$TrackedEntity.updatePlayer`, считающий дистанцию до игрока от позиции дрона (якорь) вместо позиции игрока. Точный таргет — в декомпиле.
- [ ] **Step 2:** `./gradlew build`; runClient: сущности вокруг дрона видны.
- [ ] **Step 3:** Commit: `git commit -m "Трекинг сущностей вокруг дрона для пилота"`.

---

# ФАЗА 2 — Смерть пилота во время полёта

### Task 2.1: Обрыв управления при летальном уроне/смерти пилота

**Files:**
- Create: `src/main/java/ru/liko/wrbdrones/event/PilotDeathHandler.java`

**Interfaces:**
- Consumes: `AddonDroneEntity.endRemoteControl(ServerPlayer)`, реестр активных пилотов (по `wrbdrones$controllerUuid`/`PilotViewAnchors.getAnchorDrone`).

- [ ] **Step 1: Хендлер смерти/урона**

```java
package ru.liko.wrbdrones.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.util.PilotViewAnchors;

/**
 * Если тело пилота умирает во время управления дроном (self-chunk режим), обрываем
 * управление: дрон остаётся в мире, игрок умирает штатно.
 */
@EventBusSubscriber(modid = Wrbdrones.MODID)
public final class PilotDeathHandler {

    @SubscribeEvent
    public static void onPilotDeath(final LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity drone = PilotViewAnchors.getAnchorDrone(player.getUUID());
        if (drone instanceof AddonDroneEntity addonDrone) {
            addonDrone.endRemoteControl(player);
        }
    }
}
```

- [ ] **Step 2:** `./gradlew compileJava` → `BUILD SUCCESSFUL`.
- [ ] **Step 3:** runClient (survival): во время полёта убить тело пилота → управление
  обрывается, экран смерти штатный, дрон остаётся в мире.
- [ ] **Step 4:** Commit: `git commit -m "Обрыв управления при смерти пилота, дрон остаётся"`.

---

# ФАЗА 3 — Удаление декой-подсистемы

Предусловие: Фазы 1-2 успешны, все три дрона работают через self-chunk.

### Task 3.1: Убрать старый путь и удалить декой

**Files:**
- Modify: `AddonDroneEntity.java` (удалить не-self-chunk ветки из `beginRemoteControl`/`endRemoteControl`, вызовы `PlayerDecoyManager`), `ModEntityTypes.java` (удалить `PLAYER_DECOY`), `mixins.wrbdrones.json` (если есть декой-миксины).
- Delete: `entity/PlayerDecoyEntity.java`, `util/PlayerDecoyManager.java`, `event/DecoyTargetHandler.java`, `event/DroneControlDamageHandler.java`.

- [ ] **Step 1:** Удалить из `beginRemoteControl`/`endRemoteControl` ветки `else` (телепорт/спектатор/декой); оставить только self-chunk путь. Упростить `ControlSession` (убрать неиспользуемые `dimension`/`gameMode`, если больше не нужны).
- [ ] **Step 2:** Удалить регистрацию `ModEntityTypes.PLAYER_DECOY` и связанные ассеты/`createAttributes` регистрацию (проверить `Wrbdrones`/event registration декоя).
- [ ] **Step 3:** Удалить файлы декой-подсистемы. Прогнать поиск ссылок: `grep -rn "PlayerDecoy\|player_decoy\|DecoyTarget\|DroneControlDamage" src/`. Должно быть пусто.
- [ ] **Step 4:** `./gradlew build` → `BUILD SUCCESSFUL`.
- [ ] **Step 5:** runClient: финальная проверка всех трёх дронов; декой нигде не появляется.
- [ ] **Step 6:** Commit: `git commit -m "Удаление декой-подсистемы (заменена self-chunk loading)"`.

---

## Самопроверка плана (по спеку)

- **Секция 1 спека (убрать телепорт/спектатор/декой):** Tasks 0.4, 0.7 (за флагом), 3.1 (полное удаление). ✓
- **Секция 2 (заморозка уязвимого тела):** Task 0.5 (пин), отсутствие блокировок урона — Task 3.1 удаляет `DroneControlDamageHandler`; до того старый хендлер не мешает FPV, т.к. в self-chunk декоя нет и урон идёт по игроку. ⚠ Проверить в Task 0.8 Step 3 п.7, что `DroneControlDamageHandler` не блокирует урон FPV-пилоту; если блокирует — добавить ранний выход для self-chunk режима. ✓ (учтено в сценарии валидации)
- **Секция 3 (центр трекинга на дрон + форс-тикет дома):** Tasks 0.1, 0.2, 0.3; трекинг сущностей — Task 1.2. ✓
- **Секция 4 (камера/рендер клиент):** Tasks 0.6, 0.9 (условно), 1.1. ✓
- **Секция 5 (смерть/выход):** Tasks 0.7, 2.1. ✓
- **Секция 6 (удаление устаревшего):** Task 3.1. ✓
- **Критерии успеха спека:** покрыты Tasks 0.8 (валидация), 1.1, 3.1.

**Заметка по DroneControlDamageHandler:** в Фазе 0 он ещё существует. Нужно убедиться
(Task 0.8), что он не блокирует урон в self-chunk режиме (раньше он пропускал урон
только «от декоя»). Если блокирует реальному игроку — в Task 0.5 добавить в начало
`DroneControlDamageHandler` ранний выход, если игрок в self-chunk режиме
(`PilotViewAnchors.getAnchorDrone(player) != null`) → урон не трогаем.
