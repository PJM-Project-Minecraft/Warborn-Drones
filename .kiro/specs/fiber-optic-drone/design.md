# Документ проектирования: Дрон с оптоволоконным кабелем

## Обзор

Эта функция добавляет новый тип дрона `FiberOpticDroneEntity` в мод SuperbWarfare для Minecraft, который управляется через физический оптоволоконный кабель. Дрон наследуется от `AddonDroneEntity` и использует существующую физику полета из `DroneEntity`. Ключевая особенность - реалистичная симуляция кабеля с использованием метода Verlet Integration, которая создает физические ограничения на движение дрона.

Кабель представлен как цепочка частиц (сегментов) с ограничениями расстояния между ними. Физика кабеля рассчитывается независимо от физики дрона, но влияет на него через применение сил. При обрыве кабеля дрон теряет управление и взрывается, используя существующую систему `handleSignalLoss()`.

## Архитектура

### Иерархия классов

```
DroneEntity (SuperbWarfare)
    └── AddonDroneEntity (wrbdrones)
            └── FiberOpticDroneEntity (новый класс)
```

### Основные компоненты

1. **FiberOpticDroneEntity** - Сущность дрона с кабелем
   - Наследуется от `AddonDroneEntity`
   - Хранит ссылку на `CablePhysicsComponent`
   - Хранит `operatorPosition` (точка начала кабеля)
   - Переопределяет `baseTick()` для обновления физики кабеля

2. **CablePhysicsComponent** - Компонент физики кабеля
   - Управляет массивом `CableSegment`
   - Выполняет Verlet Integration каждый тик
   - Проверяет коллизии с блоками
   - Рассчитывает натяжение и обрывы

3. **CableSegment** - Сегмент кабеля
   - Хранит текущую и предыдущую позицию
   - Имеет массу и скорость
   - Участвует в расчетах физики

4. **CableRenderer** - Рендерер кабеля
   - Отрисовывает кабель как Bezier кривую
   - Использует `RenderType.lines()` для отрисовки
   - Поддерживает различные текстуры для типов кабелей

5. **CableType** (enum) - Типы кабелей
   - BASIC - базовый кабель
   - REINFORCED - усиленный кабель
   - LIGHTWEIGHT - легкий кабель

## Компоненты и интерфейсы

### FiberOpticDroneEntity

```java
public class FiberOpticDroneEntity extends AddonDroneEntity {
    // Компонент физики кабеля
    private CablePhysicsComponent cablePhysics;
    
    // Позиция оператора (точка начала кабеля)
    @Nullable
    private Vec3 cableOriginPosition;
    
    // Тип кабеля
    private CableType cableType;
    
    // EntityData для синхронизации обрыва кабеля
    private static final EntityDataAccessor<Boolean> CABLE_BROKEN;
    
    public FiberOpticDroneEntity(EntityType<? extends DroneEntity> type, Level level);
    
    @Override
    public void baseTick();
    
    @Override
    public ResourceLocation getModelResource();
    
    @Override
    public ResourceLocation getTextureResource();
    
    @Override
    public void addAdditionalSaveData(CompoundTag compound);
    
    @Override
    public void readAdditionalSaveData(CompoundTag compound);
    
    // Инициализация кабеля при подключении монитора
    public void initializeCable(Vec3 originPos, CableType type);
    
    // Проверка состояния кабеля
    public boolean isCableBroken();
    
    // Получение текущей длины кабеля
    public double getCurrentCableLength();
    
    // Получение максимальной длины кабеля
    public double getMaxCableLength();
}
```

### CablePhysicsComponent

```java
public class CablePhysicsComponent {
    // Массив сегментов кабеля
    private final List<CableSegment> segments;
    
    // Ссылка на дрон
    private final FiberOpticDroneEntity drone;
    
    // Позиция начала кабеля
    private Vec3 originPosition;
    
    // Тип кабеля
    private final CableType cableType;
    
    // Текущее натяжение
    private double currentTension;
    
    // Урон кабеля (0.0 - 1.0)
    private double cableDamage;
    
    // Флаг обрыва
    private boolean broken;
    
    public CablePhysicsComponent(FiberOpticDroneEntity drone, Vec3 origin, CableType type);
    
    // Обновление физики (вызывается каждый тик)
    public void tick(Level level);
    
    // Verlet Integration для всех сегментов
    private void updateSegments(double deltaTime);
    
    // Применение ограничений расстояния
    private void applyConstraints();
    
    // Проверка коллизий с блоками
    private void checkCollisions(Level level);
    
    // Расчет натяжения
    private void calculateTension();
    
    // Применение силы к дрону от кабеля
    private void applyForceToDrone();
    
    // Проверка обрыва кабеля
    private void checkBreakage();
    
    // Получение позиций сегментов для рендеринга
    public List<Vec3> getSegmentPositions();
    
    // Получение текущего натяжения (0.0 - 1.0)
    public double getTensionRatio();
}
```

### CableSegment

```java
public class CableSegment {
    // Текущая позиция
    private Vec3 position;
    
    // Предыдущая позиция (для Verlet)
    private Vec3 previousPosition;
    
    // Масса сегмента
    private final double mass;
    
    // Зафиксирован ли сегмент (первый и последний)
    private final boolean fixed;
    
    public CableSegment(Vec3 initialPos, double mass, boolean fixed);
    
    // Обновление позиции (Verlet Integration)
    public void update(double deltaTime, Vec3 gravity);
    
    // Применение силы
    public void applyForce(Vec3 force);
    
    // Установка позиции (для фиксированных сегментов)
    public void setPosition(Vec3 pos);
    
    // Получение скорости
    public Vec3 getVelocity();
}
```

### CableType (enum)

```java
public enum CableType {
    BASIC(100.0, 50.0, 0.5, 0x4488FF),      // maxLength, maxTension, weight, color
    REINFORCED(75.0, 100.0, 1.0, 0xFF8844),
    LIGHTWEIGHT(150.0, 30.0, 0.25, 0x88FF44);
    
    private final double maxLength;
    private final double maxTension;
    private final double segmentWeight;
    private final int color;
    
    CableType(double maxLength, double maxTension, double weight, int color);
    
    public double getMaxLength();
    public double getMaxTension();
    public double getSegmentWeight();
    public int getColor();
    public int getSegmentCount();  // Рассчитывается как maxLength / 2.0
}
```

### CableRenderer

```java
@OnlyIn(Dist.CLIENT)
public class CableRenderer {
    // Рендеринг кабеля
    public static void render(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        List<Vec3> segments,
        CableType type,
        double tensionRatio,
        int packedLight
    );
    
    // Создание Bezier кривой между сегментами
    private static List<Vec3> createBezierCurve(List<Vec3> controlPoints, int resolution);
    
    // Получение цвета с учетом натяжения
    private static int getColorWithTension(CableType type, double tensionRatio);
}
```

## Модели данных

### NBT структура для FiberOpticDroneEntity

```
{
    // Базовые данные от AddonDroneEntity
    ...
    
    // Данные кабеля
    "CableOriginX": double,
    "CableOriginY": double,
    "CableOriginZ": double,
    "CableType": string,  // "BASIC", "REINFORCED", "LIGHTWEIGHT"
    "CableDamage": double,
    "CableBroken": boolean,
    
    // Сегменты кабеля (для восстановления после перезагрузки)
    "CableSegments": [
        {
            "PosX": double,
            "PosY": double,
            "PosZ": double,
            "PrevX": double,
            "PrevY": double,
            "PrevZ": double
        },
        ...
    ]
}
```

### EntityData синхронизация

```java
// В FiberOpticDroneEntity
private static final EntityDataAccessor<Boolean> CABLE_BROKEN = 
    SynchedEntityData.defineId(FiberOpticDroneEntity.class, EntityDataSerializers.BOOLEAN);

private static final EntityDataAccessor<Float> CABLE_TENSION = 
    SynchedEntityData.defineId(FiberOpticDroneEntity.class, EntityDataSerializers.FLOAT);
```

## Физика кабеля: Verlet Integration

### Принцип работы

Verlet Integration - это численный метод интегрирования уравнений движения, который хорошо подходит для симуляции связанных частиц (источник: [Unreal Engine Documentation](https://docs.unrealengine.com/en-US/Engine/Components/Rendering/CableComponent/index.html)).

Основная идея:
1. Кабель представлен как цепочка частиц (сегментов)
2. Между соседними сегментами есть ограничения расстояния
3. Каждый тик обновляем позиции сегментов с учетом гравитации
4. Применяем ограничения, чтобы сохранить расстояния между сегментами

### Алгоритм обновления (каждый тик)

```
1. Для каждого сегмента (кроме фиксированных):
   velocity = position - previousPosition
   previousPosition = position
   position = position + velocity + gravity * deltaTime^2

2. Применить ограничения (несколько итераций для стабильности):
   Для каждой пары соседних сегментов:
     delta = segment2.position - segment1.position
     distance = length(delta)
     restLength = 2.0  // Расстояние между сегментами
     difference = (distance - restLength) / distance
     offset = delta * difference * 0.5
     
     if (!segment1.fixed) segment1.position += offset
     if (!segment2.fixed) segment2.position -= offset

3. Обновить фиксированные сегменты:
   segments[0].position = originPosition
   segments[last].position = dronePosition

4. Проверить коллизии с блоками:
   Для каждого сегмента:
     if (блок на позиции сегмента твердый):
       Сдвинуть сегмент из блока
       Добавить урон кабелю при высоком натяжении

5. Рассчитать натяжение:
   tension = 0
   Для каждой пары сегментов:
     distance = расстояние между сегментами
     tension += abs(distance - restLength)
   
   if (tension > cableType.maxTension):
     cableDamage += 0.01
   
   if (cableDamage >= 1.0):
     broken = true

6. Применить силу к дрону:
   if (currentLength > maxLength * 0.9):
     direction = normalize(originPosition - dronePosition)
     force = direction * (currentLength - maxLength * 0.9) * 0.5
     drone.addDeltaMovement(force)
```

### Оптимизация

- Количество сегментов: `maxLength / 2.0` (например, 50 сегментов для 100 блоков)
- Ограничение: максимум 75 сегментов для производительности
- Итерации ограничений: 3-5 итераций для стабильности
- Пространственное разбиение для проверки коллизий (проверяем только близкие блоки)

## Интеграция с существующей системой

### Наследование от AddonDroneEntity

`FiberOpticDroneEntity` наследует всю функциональность:
- Физика полета из `DroneEntity`
- Система управления через монитор
- Система энергии и боеприпасов
- Рендеринг и анимации
- Система декоев и телепортации игрока

### Переопределяемые методы

```java
@Override
public void baseTick() {
    super.baseTick();
    
    // Обновление физики кабеля только на сервере
    if (!level().isClientSide() && cablePhysics != null) {
        cablePhysics.tick(level());
        
        // Проверка обрыва
        if (cablePhysics.isBroken() && !isCableBroken()) {
            onCableBreak();
        }
        
        // Синхронизация с клиентом
        entityData.set(CABLE_BROKEN, cablePhysics.isBroken());
        entityData.set(CABLE_TENSION, (float) cablePhysics.getTensionRatio());
    }
}

private void onCableBreak() {
    if (level() instanceof ServerLevel serverLevel) {
        // Звук обрыва
        level().playSound(null, position(), ModSounds.CABLE_BREAK.get(), 
            SoundSource.PLAYERS, 1.0f, 1.0f);
        
        // Эффект искр
        for (int i = 0; i < 20; i++) {
            Vec3 sparkPos = position().add(
                (random.nextDouble() - 0.5) * 0.5,
                (random.nextDouble() - 0.5) * 0.5,
                (random.nextDouble() - 0.5) * 0.5
            );
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                sparkPos.x, sparkPos.y, sparkPos.z,
                1, 0, 0, 0, 0.1);
        }
        
        // Вызов handleSignalLoss из AddonDroneEntity
        String controllerId = entityData.get(DroneEntity.CONTROLLER);
        ServerPlayer controller = null;
        if (controllerId != null && !controllerId.isEmpty()) {
            controller = (ServerPlayer) EntityFindUtil.findPlayer(serverLevel, controllerId);
        }
        handleSignalLoss(controller);
    }
}
```

### Инициализация кабеля

Кабель инициализируется при подключении монитора:

```java
@Override
public boolean beginRemoteControl(ServerPlayer player) {
    boolean success = super.beginRemoteControl(player);
    
    if (success && cablePhysics == null) {
        // Получаем тип кабеля из NBT предмета дрона
        // (устанавливается при создании дрона)
        Vec3 origin = getOperatorPosition();
        if (origin != null) {
            cableOriginPosition = origin;
            cablePhysics = new CablePhysicsComponent(this, origin, cableType);
        }
    }
    
    return success;
}
```

### Регистрация сущности

```java
// В ModEntityTypes.java
public static final DeferredHolder<EntityType<?>, EntityType<FiberOpticDroneEntity>> FIBER_OPTIC_DRONE =
    ENTITY_TYPES.register("fiber_optic_drone", () ->
        EntityType.Builder.<FiberOpticDroneEntity>of(FiberOpticDroneEntity::new, MobCategory.MISC)
            .sized(0.6f, 0.3f)
            .clientTrackingRange(10)
            .build("fiber_optic_drone")
    );
```

### Регистрация предмета

```java
// В ModItems.java
public static final DeferredHolder<Item, DroneItem> FIBER_OPTIC_DRONE_BASIC =
    ITEMS.register("fiber_optic_drone_basic", () ->
        new DroneItem(ModEntityTypes.FIBER_OPTIC_DRONE.get(), 
            new Item.Properties(), CableType.BASIC)
    );

public static final DeferredHolder<Item, DroneItem> FIBER_OPTIC_DRONE_REINFORCED =
    ITEMS.register("fiber_optic_drone_reinforced", () ->
        new DroneItem(ModEntityTypes.FIBER_OPTIC_DRONE.get(), 
            new Item.Properties(), CableType.REINFORCED)
    );

public static final DeferredHolder<Item, DroneItem> FIBER_OPTIC_DRONE_LIGHTWEIGHT =
    ITEMS.register("fiber_optic_drone_lightweight", () ->
        new DroneItem(ModEntityTypes.FIBER_OPTIC_DRONE.get(), 
            new Item.Properties(), CableType.LIGHTWEIGHT)
    );
```

### DroneItem с типом кабеля

```java
public class DroneItem extends Item {
    private final EntityType<? extends DroneEntity> entityType;
    private final CableType cableType;
    
    public DroneItem(EntityType<? extends DroneEntity> type, Properties props, CableType cable) {
        super(props);
        this.entityType = type;
        this.cableType = cable;
    }
    
    @Override
    public InteractionResult useOn(UseOnContext context) {
        // При размещении дрона сохраняем тип кабеля в NBT
        // ...
    }
}
```

## Рендеринг кабеля

### Клиентская отрисовка

Кабель отрисовывается в событии `RenderLevelStageEvent` с типом `AFTER_ENTITIES`:

```java
@SubscribeEvent
public static void onRenderLevel(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
        return;
    }
    
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) {
        return;
    }
    
    // Найти все FiberOpticDroneEntity в радиусе рендеринга
    for (Entity entity : mc.level.entitiesForRendering()) {
        if (entity instanceof FiberOpticDroneEntity drone && !drone.isCableBroken()) {
            Vec3 cameraPos = event.getCamera().getPosition();
            
            // Получить позиции сегментов
            List<Vec3> segments = drone.getCableSegmentPositions();
            if (segments.isEmpty()) continue;
            
            // Рендерить кабель
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            
            CableRenderer.render(
                poseStack,
                mc.renderBuffers().bufferSource(),
                segments,
                drone.getCableType(),
                drone.getCableTensionRatio(),
                LevelRenderer.getLightColor(mc.level, drone.blockPosition())
            );
            
            poseStack.popPose();
        }
    }
}
```

### Алгоритм рендеринга

```java
public static void render(PoseStack poseStack, MultiBufferSource bufferSource,
                         List<Vec3> segments, CableType type, 
                         double tensionRatio, int packedLight) {
    if (segments.size() < 2) return;
    
    // Создать Bezier кривую для плавности
    List<Vec3> curve = createBezierCurve(segments, 2);
    
    // Получить цвет с учетом натяжения
    int color = getColorWithTension(type, tensionRatio);
    float r = ((color >> 16) & 0xFF) / 255.0f;
    float g = ((color >> 8) & 0xFF) / 255.0f;
    float b = (color & 0xFF) / 255.0f;
    float a = 1.0f;
    
    // Отрисовать линии
    VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
    Matrix4f matrix = poseStack.last().pose();
    
    for (int i = 0; i < curve.size() - 1; i++) {
        Vec3 p1 = curve.get(i);
        Vec3 p2 = curve.get(i + 1);
        
        buffer.addVertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z)
              .setColor(r, g, b, a)
              .setNormal(0, 1, 0)
              .setLight(packedLight);
              
        buffer.addVertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z)
              .setColor(r, g, b, a)
              .setNormal(0, 1, 0)
              .setLight(packedLight);
    }
}

private static int getColorWithTension(CableType type, double tensionRatio) {
    int baseColor = type.getColor();
    
    // При высоком натяжении добавляем красный оттенок
    if (tensionRatio > 0.7) {
        float redFactor = (float) ((tensionRatio - 0.7) / 0.3);
        int r = Math.min(255, ((baseColor >> 16) & 0xFF) + (int)(redFactor * 100));
        int g = ((baseColor >> 8) & 0xFF);
        int b = (baseColor & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
    
    return baseColor;
}
```

## Обработка ошибок

### Обрыв кабеля

1. **Превышение максимального натяжения**
   - Накопление урона: `cableDamage += 0.01` каждый тик при `tension > maxTension`
   - Обрыв при `cableDamage >= 1.0`

2. **Трение о блоки**
   - Дополнительный урон при коллизии: `cableDamage += 0.005 * tensionRatio`
   - Острые края (углы блоков) увеличивают урон в 2 раза

3. **Последствия обрыва**
   - Звуковой эффект `ModSounds.CABLE_BREAK`
   - Визуальный эффект искр (`ParticleTypes.ELECTRIC_SPARK`)
   - Вызов `handleSignalLoss()` → взрыв дрона
   - Восстановление игрока на исходную позицию

### Потеря связи

Используется существующая система из `AddonDroneEntity`:
- Метод `handleSignalLoss(ServerPlayer)` обрабатывает все случаи потери управления
- Автоматическое восстановление игрока через `endRemoteControl()`
- Взрыв дрона через `createCustomExplosion().radius(3.5f).damage(8).explode()`

### Сохранение и загрузка

1. **Сохранение состояния кабеля**
   - Позиция начала кабеля
   - Тип кабеля
   - Урон кабеля
   - Позиции всех сегментов

2. **Восстановление после перезагрузки**
   - Если дрон загружается с активным кабелем, восстанавливаем все сегменты
   - Если игрок не в сети, кабель остается в последнем состоянии
   - При повторном подключении игрока кабель продолжает работать

## Стратегия тестирования

### Dual Testing Approach

Используем комбинацию unit-тестов и property-based тестов:
- **Unit-тесты**: конкретные примеры, граничные случаи, интеграция
- **Property-тесты**: универсальные свойства для всех входных данных

### Property-Based Testing Configuration

- Библиотека: **jqwik** (для Java)
- Минимум 100 итераций на тест
- Каждый тест помечен комментарием с ссылкой на свойство из дизайна

### Тестовые категории

1. **Физика кабеля**
   - Property-тесты для Verlet Integration
   - Property-тесты для ограничений расстояния
   - Unit-тесты для граничных случаев (0 сегментов, 1 сегмент)

2. **Коллизии**
   - Property-тесты для обнаружения коллизий
   - Unit-тесты для специфических блоков (воздух, твердые, жидкости)

3. **Натяжение и обрыв**
   - Property-тесты для расчета натяжения
   - Property-тесты для накопления урона
   - Unit-тесты для точки обрыва

4. **Интеграция с дроном**
   - Unit-тесты для применения сил к дрону
   - Unit-тесты для вызова `handleSignalLoss()`
   - Integration-тесты для полного цикла управления

5. **Рендеринг**
   - Unit-тесты для генерации Bezier кривых
   - Unit-тесты для расчета цвета с натяжением

## Correctness Properties

*Свойство (property) - это характеристика или поведение, которое должно выполняться для всех допустимых выполнений системы. По сути, это формальное утверждение о том, что система должна делать. Свойства служат мостом между человекочитаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*

### Property 1: Инициализация кабеля при подключении

*For any* FiberOpticDroneEntity и любой позиции размещения, когда игрок подключается к дрону через монитор, система должна инициализировать Cable с началом в Operator_Position и концом в позиции дрона, и Cable не должен быть обрывным.

**Validates: Requirements 1.2, 1.3**

### Property 2: Обрыв кабеля вызывает потерю управления

*For any* FiberOpticDroneEntity с активным Cable, когда Cable обрывается (broken = true), система должна вызвать handleSignalLoss() ровно один раз.

**Validates: Requirements 1.5, 7.1**

### Property 3: Сегментация кабеля

*For any* Cable с типом CableType, количество сегментов должно быть равно maxLength / 2.0, но не более 75 сегментов.

**Validates: Requirements 2.1, 10.4**

### Property 4: Гравитация применяется ко всем сегментам

*For any* Cable с незафиксированными сегментами, после одного тика обновления все незафиксированные сегменты должны иметь отрицательную вертикальную скорость (падение вниз), пропорциональную весу Cable_Type.

**Validates: Requirements 2.4**

### Property 5: Натяжение вызывает обрыв

*For any* Cable, если Tension превышает maxTension для Cable_Type в течение достаточного времени, Cable должен в конечном итоге оборваться (broken = true).

**Validates: Requirements 2.3, 4.3**

### Property 6: Ограничение длины кабеля

*For any* FiberOpticDroneEntity с активным Cable, когда расстояние между дроном и Operator_Position превышает maxLength * 0.9, к дрону должна применяться сила в направлении Operator_Position.

**Validates: Requirements 2.5**

### Property 7: Рендеринг соответствует типу кабеля

*For any* Cable с типом CableType, рендерер должен использовать цвет, соответствующий CableType.getColor(), и при tensionRatio > 0.7 цвет должен содержать больше красного компонента.

**Validates: Requirements 3.3, 3.4**

### Property 8: Коллизии выталкивают сегменты

*For any* CableSegment, если позиция сегмента находится внутри твердого блока, после обновления физики сегмент должен быть вытолкнут из блока (не находиться внутри твердого блока).

**Validates: Requirements 4.1, 4.4**

### Property 9: Трение наносит урон

*For any* Cable с Tension > 0, когда CableSegment находится в контакте с твердым блоком, cableDamage должен увеличиваться с каждым тиком.

**Validates: Requirements 4.2**

### Property 10: Острые края увеличивают урон

*For any* Cable, урон от трения на углах блоков (где сегмент касается двух или более граней блока) должен быть больше, чем урон от трения на плоской поверхности.

**Validates: Requirements 4.5**

### Property 11: Сохранение и восстановление типа кабеля

*For any* FiberOpticDroneEntity с Cable_Type, после сохранения в NBT и восстановления из NBT, Cable_Type должен остаться неизменным (round-trip property).

**Validates: Requirements 6.2**

### Property 12: Сматывание уменьшает длину

*For any* FiberOpticDroneEntity с активным Cable, когда активируется функция сматывания, maxLength должен уменьшаться, и расстояние между дроном и Operator_Position должно уменьшаться.

**Validates: Requirements 6.4**

### Property 13: Обрыв создает эффекты

*For any* FiberOpticDroneEntity, когда Cable обрывается, система должна воспроизвести звуковой эффект CABLE_BREAK и создать частицы ELECTRIC_SPARK в позиции дрона.

**Validates: Requirements 7.3, 7.4**

### Property 14: Самопересечение увеличивает натяжение

*For any* Cable, когда обнаружено самопересечение (два сегмента находятся на расстоянии < 0.5 блока), Tension в точке пересечения должен быть выше, чем Tension в сегментах без пересечений.

**Validates: Requirements 9.1, 9.2**

### Property 15: Запутывание через обороты

*For any* FiberOpticDroneEntity, когда дрон совершает N полных оборотов вокруг Operator_Position (N > пороговое значение), Tension должен значительно увеличиться, и выполнение N обратных оборотов должно вернуть Tension к исходному уровню.

**Validates: Requirements 9.3, 9.4, 9.5**

### Property 16: Оптимизация на расстоянии

*For any* FiberOpticDroneEntity, когда расстояние до ближайшего игрока превышает 64 блока, частота обновления физики Cable должна быть ниже, чем когда расстояние < 32 блоков.

**Validates: Requirements 10.3**

### Примеры для unit-тестов

**Example 1: Типы кабелей существуют**
Система должна иметь три типа кабелей: BASIC, REINFORCED, LIGHTWEIGHT.

**Validates: Requirements 5.1**

**Example 2: Характеристики базового кабеля**
BASIC Cable_Type должен иметь maxLength = 100.0, maxTension = 50.0, segmentWeight = 0.5.

**Validates: Requirements 5.2**

**Example 3: Характеристики усиленного кабеля**
REINFORCED Cable_Type должен иметь maxLength = 75.0, maxTension = 100.0, segmentWeight = 1.0.

**Validates: Requirements 5.3**

**Example 4: Характеристики легкого кабеля**
LIGHTWEIGHT Cable_Type должен иметь maxLength = 150.0, maxTension = 30.0, segmentWeight = 0.25.

**Validates: Requirements 5.4**

**Example 5: Регистрация сущности**
FiberOpticDroneEntity должен быть зарегистрирован в ModEntityTypes с ключом "fiber_optic_drone".

**Validates: Requirements 8.7**

**Example 6: Регистрация предметов**
Система должна иметь три предмета дронов: fiber_optic_drone_basic, fiber_optic_drone_reinforced, fiber_optic_drone_lightweight.

**Validates: Requirements 8.8**

