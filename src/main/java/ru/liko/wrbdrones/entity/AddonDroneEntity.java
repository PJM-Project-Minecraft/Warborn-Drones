package ru.liko.wrbdrones.entity;

import com.atsuishio.superbwarfare.data.CustomData;
import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.atsuishio.superbwarfare.tools.NBTTool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.liko.wrbdrones.registry.ModEntityTypes;
import ru.liko.wrbdrones.registry.ModSounds;

import java.util.Set;
import java.util.UUID;

/**
 * Base wrapper that lets us reuse SuperbWarfare's drone behaviour while
 * providing
 * per-drone assets and optional default loadouts.
 */
public abstract class AddonDroneEntity extends DroneEntity {

    /**
     * Возвращаем наш кастомный звук двигателя, зарегистрированный через
     * звукосистему SBW.
     */
    @Override
    public SoundEvent getEngineSound() {
        return ModSounds.FPV_DRONE_ENGINE.get();
    }

    @Override
    public float getEngineSoundVolume() {
        // Внешний звук дрона воспроизводится нашим DroneSoundHandler (Shahed-подобная система: доплер, HF-фильтр, distance volume).
        // Заглушаем родной SBW engine sound, чтобы параллельно не играла вторая звуковая ветка с MC-атенуацией, ломающая позиционирование.
        // Внутренний звук (FPV) играет независимый DroneInteriorSoundHandler.
        return 0.0f;
    }

    /**
     * Предотвращает краш при слезании (IndexOutOfBoundsException в VehicleEntity).
     * Возвращает текущую позицию дрона как точку высадки.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        return this.position();
    }

    private static final GameProfile WRB_FAKE_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("WRBDrones-DroneLoader".getBytes()), "[WRB_Drone]");

    /**
     * EntityData для синхронизации позиции оператора с клиентом
     * Используем FLOAT для координат и отдельный флаг наличия позиции
     */
    private static final EntityDataAccessor<Float> OPERATOR_POS_X = SynchedEntityData.defineId(AddonDroneEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OPERATOR_POS_Y = SynchedEntityData.defineId(AddonDroneEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OPERATOR_POS_Z = SynchedEntityData.defineId(AddonDroneEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> OPERATOR_POS_VALID = SynchedEntityData
            .defineId(AddonDroneEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Сессия управления дроном
     */
    @Nullable
    private ControlSession controlSession;

    /**
     * Позиция оператора (хранится как обычное поле, без EntityData)
     */
    @Nullable
    private Vec3 operatorPosition;

    /**
     * Защита от рекурсии: endRemoteControl() вызывает stopRiding(), который
     * вызывает removePassenger()
     */
    private boolean wrbdrones$endingControl = false;
    @Nullable
    private UUID wrbdrones$controllerUuid = null;
    @Nullable
    private ChunkPos wrbdrones$loadedChunk = null;

    @SuppressWarnings("unchecked")
    protected AddonDroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super((EntityType<DroneEntity>) (EntityType<?>) type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OPERATOR_POS_X, 0.0f);
        builder.define(OPERATOR_POS_Y, 0.0f);
        builder.define(OPERATOR_POS_Z, 0.0f);
        builder.define(OPERATOR_POS_VALID, false);
    }

    /**
     * Переопределяем максимальное здоровье дрона на 2.5
     */
    @Override
    public float getMaxHealth() {
        return 2.5f;
    }

    /**
     * Force render дрона на любом расстоянии для всех игроков.
     * Решает проблему, когда дроны не отображаются для других игроков.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    /**
     * Client-side helpers supplying variant specific resources.
     */
    public abstract ResourceLocation getModelResource();

    public abstract ResourceLocation getTextureResource();

    public ResourceLocation getAnimationResource() {
        return ModEntityTypes.DEFAULT_ANIMATION;
    }

    /**
     * Hook for subclasses to preconfigure attachments when spawned via our items.
     */
    public void applySpawnLoadout() {
        // default no-op
    }

    /**
     * Определяет, какие боеприпасы может использовать этот дрон.
     * Возвращает null если все боеприпасы разрешены, или Set с разрешенными ID
     * предметов.
     */
    protected Set<String> getAllowedAttachments() {
        return null; // По умолчанию все разрешены
    }

    /**
     * Проверяет, может ли дрон использовать данный боеприпас
     */
    protected boolean canAcceptAttachment(ItemStack stack) {
        Set<String> allowed = getAllowedAttachments();
        if (allowed == null) {
            return true; // Все разрешены
        }

        String itemId = getItemId(stack);
        return allowed.contains(itemId);
    }

    protected String getItemId(ItemStack stack) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null)
            return "";
        return key.toString();
    }

    @Override
    public @NotNull InteractionResult interact(Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getMainHandItem();

        // Быстрая зарядка от предмета-аккумулятора
        if (!stack.isEmpty() && this.hasEnergyStorage()) {
            var energyCap = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            if (energyCap != null) {
                int needed = Math.max(0, this.getMaxEnergy() - this.getEnergy());
                if (needed > 0) {
                    int transferred = energyCap.extractEnergy(needed, false);
                    if (transferred > 0) {
                        if (!this.level().isClientSide()) {
                            this.setEnergy(this.getEnergy() + transferred);
                            player.displayClientMessage(Component.literal("+" + transferred + " FE"), true);
                        }
                        return InteractionResult.sidedSuccess(this.level().isClientSide());
                    }
                }
            }
        }

        // Проверяем, является ли предмет монитором из SuperbWarfare
        String itemId = getItemId(stack);
        boolean isMonitor = "superbwarfare:monitor".equals(itemId);

        // Если это монитор, обрабатываем подключение
        if (isMonitor) {
            if (!player.isCrouching()) {
                // Обычное подключение
                InteractionResult result = super.interact(player, hand);

                // Если подключение успешно, начинаем удаленное управление
                if (result.consumesAction() && !this.level().isClientSide()
                        && player instanceof ServerPlayer serverPlayer) {
                    if (this.entityData.get(DroneEntity.LINKED)
                            && this.entityData.get(DroneEntity.CONTROLLER).equals(serverPlayer.getStringUUID())) {
                        beginRemoteControl(serverPlayer);
                    }
                }

                return result;
            } else {
                // Отключение монитора (Shift+ПКМ)
                if (!this.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    String controllerId = this.entityData.get(DroneEntity.CONTROLLER);
                    if (controllerId != null && controllerId.equals(serverPlayer.getStringUUID())) {
                        endRemoteControl(serverPlayer);
                    }
                }
                return super.interact(player, hand);
            }
        }

        // Обрабатываем разборку дрона (Shift+ПКМ)
        // Если игрок приседает с пустой рукой, разбираем дрон
        if (player.isCrouching() && stack.isEmpty()) {
            return handleDisassembly(player);
        }

        // Проверяем ограничения на боеприпасы только при попытке установить боеприпас
        if (!stack.isEmpty() && !player.isCrouching()) {
            // Проверяем, является ли предмет боеприпасом для дрона
            // Если это боеприпас (имеет ID из SuperbWarfare), проверяем ограничения
            if (CustomData.DRONE_ATTACHMENT.get(itemId) != null && !canAcceptAttachment(stack)) {
                player.displayClientMessage(
                        Component.translatable("tips.wrbdrones.drone.attachment_not_allowed")
                                .withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.sidedSuccess(this.level().isClientSide());
            }
        }

        // Для всех остальных случаев (боеприпасы, пустая рука) вызываем super
        return super.interact(player, hand);
    }

    /**
     * Обрабатывает разборку дрона и возвращает правильный предмет
     */
    private InteractionResult handleDisassembly(Player player) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        // Восстанавливаем игрока, если он управляет дроном
        if (player instanceof ServerPlayer serverPlayer) {
            String controllerId = this.entityData.get(DroneEntity.CONTROLLER);
            if (controllerId != null && controllerId.equals(serverPlayer.getStringUUID())) {
                endRemoteControl(serverPlayer);
            }
        }

        // Получаем правильный предмет дрона на основе типа
        ItemStack droneItem = getDroneItem();
        if (droneItem.isEmpty()) {
            // Если не удалось определить тип, делегируем обработку родительскому классу
            return super.interact(player, InteractionHand.MAIN_HAND);
        }

        // Выдаем правильный предмет дрона
        player.getInventory().placeItemBackInInventory(droneItem);

        // Отключаем мониторы (если есть)
        // Отключаем мониторы (если есть)
        /*
         * try {
         * player.getInventory().items.stream()
         * .filter(stack -> stack.getItem() == ModItems.MONITOR.get())
         * .forEach(itemStack -> {
         * if
         * (NBTTool.getTag(itemStack).getString(com.atsuishio.superbwarfare.item.misc.MonitorItem
         * .LINKED_DRONE)
         * .equals(this.getStringUUID())) {
         * com.atsuishio.superbwarfare.item.misc.MonitorItem.disLink(itemStack, player);
         * }
         * });
         * } catch (Exception e) {
         * // Игнорируем ошибки при отключении мониторов
         * }
         */

        // Удаляем дрон
        this.discard();
        return InteractionResult.SUCCESS;
    }

    /**
     * Получает предмет дрона на основе типа сущности
     */
    private ItemStack getDroneItem() {
        var entityType = this.getType();
        var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (key == null) {
            return ItemStack.EMPTY;
        }

        String entityId = key.toString();

        // Определяем предмет на основе ID типа сущности
        if (entityId.equals("wrbdrones:mavic_drone_with_drop")) {
            return new ItemStack(ru.liko.wrbdrones.registry.ModItems.MAVIC_DRONE_WITH_DROP.get());
        } else if (entityId.equals("wrbdrones:mavic_drone_no_drop")) {
            return new ItemStack(ru.liko.wrbdrones.registry.ModItems.MAVIC_DRONE_NO_DROP.get());
        } else if (entityId.equals("wrbdrones:fpv_drone")) {
            return new ItemStack(ru.liko.wrbdrones.registry.ModItems.FPV_DRONE.get());
        } else if (entityId.equals("wrbdrones:zala_lancet")) {
            return new ItemStack(ru.liko.wrbdrones.registry.ModItems.ZALA_LANCET.get());
        }

        return ItemStack.EMPTY;
    }

    public void quickLoadAttachment(ItemStack stack) {
        if (stack.isEmpty() || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        FakePlayer fakePlayer = FakePlayerFactory.get(serverLevel, WRB_FAKE_PROFILE);
        ItemStack previous = fakePlayer.getMainHandItem();

        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        this.interact(fakePlayer, InteractionHand.MAIN_HAND);
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, previous);
    }

    /**
     * Вспомогательные методы для доступа к данным дрона через рефлексию
     * Используются рендерером для отображения боеприпасов
     */
    private static volatile boolean dataAccessorsInitialized = false;
    private static java.lang.reflect.Field DISPLAY_ENTITY_FIELD = null;
    private static java.lang.reflect.Field DISPLAY_ENTITY_TAG_FIELD = null;
    private static java.lang.reflect.Field DISPLAY_DATA_FIELD = null;
    private static java.lang.reflect.Field MAX_AMMO_FIELD = null;
    private static java.lang.reflect.Field AMMO_FIELD = null;

    private static synchronized void initDataAccessorFields() {
        if (dataAccessorsInitialized)
            return;

        try {
            // Используем getDeclaredField для доступа к полям, даже если они не публичные
            DISPLAY_ENTITY_FIELD = DroneEntity.class.getDeclaredField("DISPLAY_ENTITY");
            DISPLAY_ENTITY_TAG_FIELD = DroneEntity.class.getDeclaredField("DISPLAY_ENTITY_TAG");
            DISPLAY_DATA_FIELD = DroneEntity.class.getDeclaredField("DISPLAY_DATA");
            MAX_AMMO_FIELD = DroneEntity.class.getDeclaredField("MAX_AMMO");

            // AMMO объявлен в DroneEntity (не в VehicleEntity)
            try {
                AMMO_FIELD = DroneEntity.class.getDeclaredField("AMMO");
            } catch (NoSuchFieldException e) {
                Class<?> vehicleEntityClass = DroneEntity.class.getSuperclass();
                if (vehicleEntityClass != null) {
                    try {
                        AMMO_FIELD = vehicleEntityClass.getDeclaredField("AMMO");
                    } catch (NoSuchFieldException e2) {
                        try {
                            vehicleEntityClass = Class
                                    .forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
                            AMMO_FIELD = vehicleEntityClass.getDeclaredField("AMMO");
                        } catch (Exception e3) {
                            java.lang.reflect.Field[] fields = vehicleEntityClass != null
                                    ? vehicleEntityClass.getDeclaredFields()
                                    : new java.lang.reflect.Field[0];
                            for (java.lang.reflect.Field field : fields) {
                                if (field.getName().equals("AMMO") ||
                                        (field.getType().getName().contains("EntityDataAccessor") &&
                                                field.getName().toUpperCase().contains("AMMO"))) {
                                    AMMO_FIELD = field;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            if (AMMO_FIELD == null) {
                throw new NoSuchFieldException("AMMO field not found in VehicleEntity or its subclasses");
            }

            // Делаем поля доступными
            DISPLAY_ENTITY_FIELD.setAccessible(true);
            DISPLAY_ENTITY_TAG_FIELD.setAccessible(true);
            DISPLAY_DATA_FIELD.setAccessible(true);
            MAX_AMMO_FIELD.setAccessible(true);
            AMMO_FIELD.setAccessible(true);

            dataAccessorsInitialized = true;
        } catch (Exception e) {
            dataAccessorsInitialized = true;
        }
    }

    /**
     * Получает EntityDataAccessor для DISPLAY_ENTITY
     */
    @SuppressWarnings("unchecked")
    public static net.minecraft.network.syncher.EntityDataAccessor<String> getDisplayEntityAccessor() {
        initDataAccessorFields();
        if (DISPLAY_ENTITY_FIELD == null)
            return null;
        try {
            return (net.minecraft.network.syncher.EntityDataAccessor<String>) DISPLAY_ENTITY_FIELD.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Получает EntityDataAccessor для DISPLAY_ENTITY_TAG
     */
    @SuppressWarnings("unchecked")
    public static net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.nbt.CompoundTag> getDisplayEntityTagAccessor() {
        initDataAccessorFields();
        if (DISPLAY_ENTITY_TAG_FIELD == null)
            return null;
        try {
            return (net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.nbt.CompoundTag>) DISPLAY_ENTITY_TAG_FIELD
                    .get(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Получает EntityDataAccessor для DISPLAY_DATA
     */
    @SuppressWarnings("unchecked")
    public static net.minecraft.network.syncher.EntityDataAccessor<java.util.List<Float>> getDisplayDataAccessor() {
        initDataAccessorFields();
        if (DISPLAY_DATA_FIELD == null)
            return null;
        try {
            return (net.minecraft.network.syncher.EntityDataAccessor<java.util.List<Float>>) DISPLAY_DATA_FIELD
                    .get(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Получает EntityDataAccessor для MAX_AMMO
     */
    @SuppressWarnings("unchecked")
    public static net.minecraft.network.syncher.EntityDataAccessor<Integer> getMaxAmmoAccessor() {
        initDataAccessorFields();
        if (MAX_AMMO_FIELD == null)
            return null;
        try {
            return (net.minecraft.network.syncher.EntityDataAccessor<Integer>) MAX_AMMO_FIELD.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Получает EntityDataAccessor для AMMO
     */
    @SuppressWarnings("unchecked")
    public static net.minecraft.network.syncher.EntityDataAccessor<Integer> getAmmoAccessor() {
        initDataAccessorFields();
        if (AMMO_FIELD == null) {
            // Пробуем несколько способов доступа
            try {
                Class<?> vehicleEntityClass = DroneEntity.class.getSuperclass();
                if (vehicleEntityClass != null) {
                    java.lang.reflect.Field ammoField = vehicleEntityClass.getDeclaredField("AMMO");
                    ammoField.setAccessible(true);
                    net.minecraft.network.syncher.EntityDataAccessor<Integer> accessor = (net.minecraft.network.syncher.EntityDataAccessor<Integer>) ammoField
                            .get(null);
                    if (accessor != null) {
                        return accessor;
                    }
                }
            } catch (Exception e) {
                // Игнорируем ошибки
            }

            try {
                Class<?> vehicleEntityClass = Class
                        .forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
                java.lang.reflect.Field ammoField = vehicleEntityClass.getDeclaredField("AMMO");
                ammoField.setAccessible(true);
                net.minecraft.network.syncher.EntityDataAccessor<Integer> accessor = (net.minecraft.network.syncher.EntityDataAccessor<Integer>) ammoField
                        .get(null);
                if (accessor != null) {
                    return accessor;
                }
            } catch (Exception e) {
                // Игнорируем ошибки
            }

            return null;
        }
        try {
            return (net.minecraft.network.syncher.EntityDataAccessor<Integer>) AMMO_FIELD.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Класс для хранения данных сессии управления дроном
     */
    private static final class ControlSession {
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        final Vec3 originPos;
        final float originYaw;
        final float originPitch;
        final net.minecraft.world.level.GameType gameMode;

        ControlSession(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 originPos,
                float originYaw, float originPitch, net.minecraft.world.level.GameType gameMode) {
            this.dimension = dimension;
            this.originPos = originPos;
            this.originYaw = originYaw;
            this.originPitch = originPitch;
            this.gameMode = gameMode;
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        
        if (controlSession != null && wrbdrones$controllerUuid != null) {
            net.minecraft.nbt.CompoundTag sessionTag = new net.minecraft.nbt.CompoundTag();
            sessionTag.putString("Dimension", controlSession.dimension.location().toString());
            sessionTag.putDouble("OriginX", controlSession.originPos.x);
            sessionTag.putDouble("OriginY", controlSession.originPos.y);
            sessionTag.putDouble("OriginZ", controlSession.originPos.z);
            sessionTag.putFloat("OriginYaw", controlSession.originYaw);
            sessionTag.putFloat("OriginPitch", controlSession.originPitch);
            sessionTag.putInt("GameMode", controlSession.gameMode.getId());
            sessionTag.putUUID("ControllerUUID", wrbdrones$controllerUuid);
            compound.put("WRBControlSession", sessionTag);
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        
        if (compound.contains("WRBControlSession")) {
            net.minecraft.nbt.CompoundTag sessionTag = compound.getCompound("WRBControlSession");
            try {
                ResourceLocation dimLoc = ResourceLocation.parse(sessionTag.getString("Dimension"));
                net.minecraft.resources.ResourceKey<Level> dimension = net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, dimLoc);
                Vec3 originPos = new Vec3(
                        sessionTag.getDouble("OriginX"),
                        sessionTag.getDouble("OriginY"),
                        sessionTag.getDouble("OriginZ"));
                float originYaw = sessionTag.getFloat("OriginYaw");
                float originPitch = sessionTag.getFloat("OriginPitch");
                net.minecraft.world.level.GameType gameMode = net.minecraft.world.level.GameType.byId(sessionTag.getInt("GameMode"));
                
                controlSession = new ControlSession(dimension, originPos, originYaw, originPitch, gameMode);
                wrbdrones$controllerUuid = sessionTag.getUUID("ControllerUUID");
                updateOperatorPosition();
            } catch (Exception e) {
                controlSession = null;
                wrbdrones$controllerUuid = null;
            }
        }
    }

    /**
     * Получает позицию оператора (исходную позицию игрока)
     * Используется для расчета дистанции до дрона
     */
    @Nullable
    public Vec3 getOperatorPosition() {
        if (!this.level().isClientSide()) {
            if (controlSession != null) {
                return controlSession.originPos;
            }
            return operatorPosition;
        }

        if (!this.entityData.get(OPERATOR_POS_VALID)) {
            return null;
        }

        return new Vec3(
                this.entityData.get(OPERATOR_POS_X),
                this.entityData.get(OPERATOR_POS_Y),
                this.entityData.get(OPERATOR_POS_Z));
    }

    /**
     * Обновляет позицию оператора и синхронизирует с клиентом через EntityData
     */
    private void updateOperatorPosition() {
        Vec3 pos = null;

        if (controlSession != null) {
            pos = controlSession.originPos;
        } else if (operatorPosition != null) {
            pos = operatorPosition;
        }

        if (pos != null) {
            operatorPosition = pos;
            this.entityData.set(OPERATOR_POS_X, (float) pos.x);
            this.entityData.set(OPERATOR_POS_Y, (float) pos.y);
            this.entityData.set(OPERATOR_POS_Z, (float) pos.z);
            this.entityData.set(OPERATOR_POS_VALID, true);
        } else {
            operatorPosition = null;
            this.entityData.set(OPERATOR_POS_X, 0.0f);
            this.entityData.set(OPERATOR_POS_Y, 0.0f);
            this.entityData.set(OPERATOR_POS_Z, 0.0f);
            this.entityData.set(OPERATOR_POS_VALID, false);
        }
    }

    /**
     * Начинает удаленное управление дроном.
     * <p>
     * Для FPV-дронов: игрок остаётся на месте, дрон сам центрирует прогрузку чанков
     * (self-chunk режим). Декой, спектатор и телепорт не применяются.
     * <p>
     * Для остальных дронов (Mavic, Lancet): старый путь — декой + спектатор + телепорт.
     */
    public boolean beginRemoteControl(final ServerPlayer player) {
        String controllerId = this.entityData.get(DroneEntity.CONTROLLER);
        if (controllerId != null && !controllerId.equals("undefined") && !controllerId.isEmpty()
                && !controllerId.equals(player.getStringUUID())) {
            return false; // Дрон уже связан с другим игроком
        }

        // Запоминаем ДО создания сессии: является ли этот вызов первым вхождением
        // в управление. Нужно для предотвращения утечки PilotChunkTicket при
        // повторном вызове beginRemoteControl для того же игрока.
        boolean freshSession = (controlSession == null);

        if (controlSession == null) {
            controlSession = new ControlSession(
                    player.level().dimension(),
                    player.position(),
                    player.getYRot(),
                    player.getXRot(),
                    player.gameMode.getGameModeForPlayer());
            updateOperatorPosition();
        }
        wrbdrones$controllerUuid = player.getUUID();

        if (!player.level().isClientSide() && this.level() instanceof ServerLevel droneLevel) {

            boolean selfChunkMode = this instanceof ru.liko.wrbdrones.entity.FpvDroneEntity;

            if (selfChunkMode) {
                // НОВЫЙ путь: игрок остаётся на месте, дрон сам центрирует прогрузку чанков.
                // hold/setAnchor — только при свежей сессии, иначе держанный чанк утечёт.
                if (freshSession) {
                    ru.liko.wrbdrones.util.PilotChunkTicket.hold(player);           // держим домашний чанк
                    ru.liko.wrbdrones.util.PilotViewAnchors.setAnchor(player.getUUID(), this); // центр обзора -> дрон
                }
            } else {
                // СТАРЫЙ путь (Mavic/Lancet до распространения фичи).

                // Создаем декой (фейкового игрока) на месте игрока ПЕРЕД тем, как он
                // телепортируется к дрону
                ru.liko.wrbdrones.util.PlayerDecoyManager.createDecoy(player, this);

                // Переводим в спектаторский режим — гарантированно невидим для всех игроков
                player.gameMode.changeGameModeForPlayer(GameType.SPECTATOR);

                // Телепортируем игрока к дрону для работы FPV камеры и загрузки чанков.
                // Принудительно выравниваем взгляд оператора по курсу дрона (yaw) и горизонту (pitch=0),
                // чтобы камера-карданчик стартовала вперёд, а не повторяла случайное положение головы
                // игрока в момент входа в управление. Исходные углы уже сохранены в controlSession
                // и будут восстановлены при выходе.
                player.teleportTo(
                        droneLevel,
                        this.getX(),
                        this.getY() + this.getBbHeight() + 4.0,
                        this.getZ(),
                        this.getYRot(),
                        0.0f);
                player.setYHeadRot(this.getYRot());
            }
        }

        return true;
    }

    /**
     * Завершает удаленное управление дроном.
     * <p>
     * FPV (режим self-chunk): игрок не перемещался — снимает якорь вида
     * ({@link ru.liko.wrbdrones.util.PilotViewAnchors}) и тикет удержания
     * домашнего чанка ({@link ru.liko.wrbdrones.util.PilotChunkTicket}),
     * затем восстанавливает углы взгляда без какого-либо телепорта.
     * <p>
     * Прочие дроны (Mavic, Lancet и т.д.): удаляет декой и телепортирует
     * игрока обратно на исходную позицию с восстановлением игрового режима.
     */
    public void endRemoteControl(final ServerPlayer player) {
        if (controlSession == null) {
            return;
        }
        // Разрешаем завершение только контроллеру (по UUID, а не по строке из
        // entityData)
        if (wrbdrones$controllerUuid != null && !wrbdrones$controllerUuid.equals(player.getUUID())) {
            return;
        }

        wrbdrones$endingControl = true;
        try {

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

        } finally {
            wrbdrones$endingControl = false;
        }

        controlSession = null;
        operatorPosition = null;
        wrbdrones$controllerUuid = null;
        updateOperatorPosition();

        // Снимаем форсированную отправку чанков — все пути выхода из управления идут сюда.
        ru.liko.wrbdrones.util.ChunkSendBooster.setBoosted(player.getUUID(), false);
    }

    /**
     * Если дрон удаляется/взрывается — обязательно возвращаем контроллера на точку
     * запуска.
     */
    private void wrbdrones$restoreControllerOnRemoval(ServerLevel serverLevel) {
        if (controlSession == null || wrbdrones$controllerUuid == null)
            return;
        // Глобальный поиск по всему серверу: игрок мог быть телепортирован
        // в другое измерение, и getPlayerByUUID(level) его бы не нашёл.
        ServerPlayer sp = null;
        net.minecraft.server.MinecraftServer server = serverLevel.getServer();
        if (server != null) {
            sp = server.getPlayerList().getPlayer(wrbdrones$controllerUuid);
        }
        if (sp != null) {
            endRemoteControl(sp);
        } else {
            // Игрок не найден (вышел?) — принудительно удаляем декой
            ru.liko.wrbdrones.util.PlayerDecoyManager.removeDecoy(wrbdrones$controllerUuid);
            // Снимаем FPV-ресурсы: якорь вида и форс-загрузку домашнего чанка.
            // Для не-FPV дронов эти ресурсы никогда не ставились — вызовы являются no-op.
            ru.liko.wrbdrones.util.PilotViewAnchors.clearAnchor(wrbdrones$controllerUuid);
            ru.liko.wrbdrones.util.PilotChunkTicket.release(wrbdrones$controllerUuid);
        }
    }

    @Override
    protected void removePassenger(@NotNull net.minecraft.world.entity.Entity passenger) {
        super.removePassenger(passenger);

        // Если игрок сам "вышел" (Shift), возвращаем его на точку старта
        if (wrbdrones$endingControl)
            return;
        if (this.level().isClientSide())
            return;

        if (passenger instanceof ServerPlayer serverPlayer && controlSession != null) {
            String controllerId = this.entityData.get(DroneEntity.CONTROLLER);
            if (controllerId != null && controllerId.equals(serverPlayer.getStringUUID())) {
                endRemoteControl(serverPlayer);
            }
        }
    }

    /**
     * @param selfDestruct {@code true} только для ЛКМ по FPV (см. клиентский хендлер): {@link DroneEntity#destroy()} (боеприпас/камикадзе SBW)
     *                     и полная отвязка мониторов. {@code false} — потеря сигнала с HUD: выход WRB/FPV,
     *                     дрон не уничтожается.
     */
    public void handleSignalLoss(@Nullable ServerPlayer source, boolean selfDestruct) {
        if (this.level().isClientSide()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.isRemoved()) {
            return;
        }

        if (selfDestruct) {
            // Атрибуция ЛКМ-подрыва: камикадзе-взрыв в SBW читает LAST_ATTACKER_UUID.
            // Перезаписываем его оператором, иначе автором будет последний попавший (напр. РПГ).
            String attackerUuid = null;
            if (source != null) {
                attackerUuid = source.getStringUUID();
            } else {
                String controllerId = this.entityData.get(com.atsuishio.superbwarfare.entity.vehicle.DroneEntity.CONTROLLER);
                if (controllerId != null && !controllerId.isEmpty() && !controllerId.equalsIgnoreCase("undefined")) {
                    attackerUuid = controllerId;
                }
            }
            if (attackerUuid != null) {
                this.entityData.set(
                        com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity.LAST_ATTACKER_UUID,
                        attackerUuid);
            }

            // Двойная отвязка: SBW destroy() сам трогает мониторы контроллера и теоретически может
            // перетереть состояние, поэтому страхуемся проходом ДО и ПОСЛЕ destroy().
            unlinkAllLinkedMonitors(serverLevel);
            // Контролёр должен быть в измерении дрона, иначе SBW destroy() не находит игрока.
            this.destroy();
            unlinkAllLinkedMonitors(serverLevel);

            // Явно завершаем remote control после destroy(), чтобы гарантировать удаление декоя.
            // destroy() → discard() → remove() уже пытался это сделать, но мог не найти
            // игрока через serverLevel.getPlayerByUUID (локальный поиск по одному уровню).
            // Здесь используем глобальный поиск через getPlayerList() как страховку.
            if (controlSession != null && wrbdrones$controllerUuid != null) {
                ServerPlayer operator = source;
                if (operator == null) {
                    net.minecraft.server.MinecraftServer server = serverLevel.getServer();
                    if (server != null) {
                        operator = server.getPlayerList().getPlayer(wrbdrones$controllerUuid);
                    }
                }
                if (operator != null) {
                    endRemoteControl(operator);
                } else {
                    // Крайний случай: игрок не найден — принудительно удаляем декой
                    ru.liko.wrbdrones.util.PlayerDecoyManager.removeDecoy(wrbdrones$controllerUuid);
                    controlSession = null;
                    wrbdrones$controllerUuid = null;
                }
            }
            return;
        }

        ServerPlayer operator = source;
        if (operator == null && wrbdrones$controllerUuid != null) {
            MinecraftServer server = serverLevel.getServer();
            if (server != null) {
                operator = server.getPlayerList().getPlayer(wrbdrones$controllerUuid);
            }
        }
        if (operator != null && controlSession != null) {
            endRemoteControl(operator);
        } else {
            wrbdrones$restoreControllerOnRemoval(serverLevel);
        }
        wrbdrones$clearMonitorFpvOnly(serverLevel);
    }

    /** Снимает только FPV ({@code Using=false}), связь с дроном в SBW сохраняется. */
    private void wrbdrones$clearMonitorFpvOnly(ServerLevel level) {
        Item monitorItem = ModItems.MONITOR.get();
        if (monitorItem == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        String droneId = this.getStringUUID();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            wrbdrones$clearUsingOnMonitorStacks(player.getInventory().items, player, droneId, monitorItem);
            wrbdrones$clearUsingOnMonitorStacks(player.getInventory().offhand, player, droneId, monitorItem);
        }
    }

    private void wrbdrones$clearUsingOnMonitorStacks(java.util.List<ItemStack> stacks, ServerPlayer player,
            String droneId, Item monitorItem) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || !stack.is(monitorItem)) {
                continue;
            }
            var tag = NBTTool.getTag(stack);
            if (tag == null || !droneId.equals(tag.getString(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED_DRONE))) {
                continue;
            }
            tag.putBoolean("Using", false);
            NBTTool.saveTag(stack, tag);
        }
    }

    @Override
    public void baseTick() {
        super.baseTick();

        if (this.level().isClientSide()) {
            spawnClientParticles();
        }

        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            // Проверяем контроллера дрона
            String controllerId = this.entityData.get(DroneEntity.CONTROLLER);
            boolean hasController = controllerId != null && !controllerId.equals("undefined")
                    && !controllerId.isEmpty();

            // Обновляем загрузку чанков: держим чанк загруженным, если есть контроллер
            wrbdrones$updateChunkLoading(serverLevel, hasController);

            if (hasController) {
                Player controller = EntityFindUtil.findPlayer(serverLevel, controllerId);
                if (controller instanceof ServerPlayer serverPlayer) {
                    // Проверяем, использует ли игрок монитор
                    // Согласно Monitor.inventoryTick, монитор работает только если он выбран
                    // (selected)
                    // Поэтому проверяем только главную руку, так как это единственное место, где
                    // монитор может быть активен
                    boolean isUsingMonitor = false;
                    String droneId = this.getStringUUID();

                    // Проверяем главную руку (монитор работает только если он в главной руке и
                    // выбран)
                    ItemStack mainHand = serverPlayer.getMainHandItem();
                    if (mainHand.is(ModItems.MONITOR.get())) {
                        var tag = NBTTool.getTag(mainHand);
                        if (tag.getBoolean(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED)
                                && tag.getString(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED_DRONE)
                                        .equals(droneId)) {

                            if (tag.getBoolean("Using")) {
                                isUsingMonitor = true;
                            }
                        }
                    }

                    // Если игрок использует монитор - начинаем управление
                    if (isUsingMonitor && controlSession == null) {
                        beginRemoteControl(serverPlayer);
                    }
                    // Если игрок не использует монитор - завершаем управление
                    if (!isUsingMonitor && controlSession != null) {
                        endRemoteControl(serverPlayer);
                    }

                    // Пока управляет — телепортируем игрока к дрону для работы FPV камеры
                    // (только для не-FPV дронов; FPV использует self-chunk режим — см. ниже)
                    if (isUsingMonitor && controlSession != null
                            && !(this instanceof ru.liko.wrbdrones.entity.FpvDroneEntity)) {
                        serverPlayer.teleportTo(
                                serverLevel,
                                this.getX(),
                                this.getY() + this.getBbHeight() + 4.0,
                                this.getZ(),
                                serverPlayer.getYRot(),
                                serverPlayer.getXRot());
                        serverPlayer.fallDistance = 0.0f;

                        // КЛЮЧЕВОЕ: тащим серверный трекинг/загрузку чанков за дроном.
                        // teleportTo идёт через connection.teleport (handshake подтверждения), и пока
                        // ждётся ack, handleMovePlayer пропускает getChunkSource().move (см. ванильный
                        // updateAwaitingTeleport). При ежетиковом телепорте ack всегда отстаёт, поэтому
                        // ChunkMap.move для пилота почти не отрабатывает, и сервер грузит вокруг дрона
                        // лишь синхронно форснутые чанки — на выделенном сервере под дроном видно 1-2
                        // чанка, остальное пусто. Серверная позиция игрока уже выставлена на дрон
                        // (absMoveTo внутри connection.teleport), поэтому двигаем трекинг напрямую:
                        // это и загружает чанки вокруг дрона (player ticket), и ставит их в очередь
                        // отправки клиенту (ChunkTrackingView).
                        serverLevel.getChunkSource().move(serverPlayer);

                        // Дополнительно ускоряем сам стриминг чанков пилоту, чтобы быстрый дрон
                        // (Lancet/FPV) не обгонял загрузку уже отслеживаемых чанков. Снимается в
                        // endRemoteControl.
                        ru.liko.wrbdrones.util.ChunkSendBooster.setBoosted(serverPlayer.getUUID(), true);

                        // Синхронизируем экипировку декоя с игроком (каждые 20 тиков)
                        if (this.tickCount % 20 == 0) {
                            ru.liko.wrbdrones.util.PlayerDecoyManager.syncDecoyEquipment(serverPlayer);
                        }
                    }

                    // Self-chunk режим: тело пилота заморожено на месте (но уязвимо).
                    if (this instanceof ru.liko.wrbdrones.entity.FpvDroneEntity
                            && controlSession != null
                            && wrbdrones$controllerUuid != null) {
                        // Пилот стоит неподвижно — handleMovePlayer никогда не вызывает
                        // getChunkSource().move для него, поэтому ChunkMap.move и редирект
                        // ChunkMapPilotAnchorMixin никогда не сработают сами по себе. Вызываем
                        // явно каждый тик, чтобы стриминг чанков следовал за движущимся FPV-дроном.
                        serverLevel.getChunkSource().move(serverPlayer);

                        // Ускоряем отправку чанков клиенту, чтобы быстрый FPV-дрон не обгонял
                        // загрузку. Снимается в endRemoteControl.
                        ru.liko.wrbdrones.util.ChunkSendBooster.setBoosted(serverPlayer.getUUID(), true);

                        net.minecraft.world.phys.Vec3 anchor = controlSession.originPos;
                        if (anchor != null && (serverPlayer.position().distanceToSqr(anchor) > 1.0E-4
                                || !serverPlayer.getDeltaMovement().equals(net.minecraft.world.phys.Vec3.ZERO))) {
                            serverPlayer.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                            serverPlayer.teleportTo(anchor.x, anchor.y, anchor.z);
                            serverPlayer.fallDistance = 0.0f;
                        }
                    }

                }
            }

            // Обновляем позицию оператора каждый тик для корректного расчета дистанции на
            // клиенте
            updateOperatorPosition();
        }
    }

    @Override
    public void remove(@NotNull net.minecraft.world.entity.Entity.@NotNull RemovalReason reason) {
        // Завершаем управление при удалении дрона
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            wrbdrones$updateChunkLoading(serverLevel, false);

            wrbdrones$restoreControllerOnRemoval(serverLevel);
            String controllerId = this.entityData.get(DroneEntity.CONTROLLER);
            if (controllerId != null && !controllerId.equals("undefined") && !controllerId.isEmpty()) {
                Player controller = EntityFindUtil.findPlayer(serverLevel, controllerId);
                if (controller instanceof ServerPlayer serverPlayer && controlSession != null) {
                    endRemoteControl(serverPlayer);
                }
            }

            // Финальная страховка: гарантированно отвязываем все мониторы, привязанные к этому дрону,
            // независимо от способа удаления (взрыв по ЛКМ, /kill, очистка чанка и т. п.).
            unlinkAllLinkedMonitors(serverLevel);
        }
        super.remove(reason);
    }

    private void unlinkAllLinkedMonitors(ServerLevel droneLevel) {
        Item monitorItem = ModItems.MONITOR.get();
        if (monitorItem == null) {
            return;
        }
        MinecraftServer server = droneLevel.getServer();
        if (server == null) {
            return;
        }
        String droneId = this.getStringUUID();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            unlinkMonitorsFromPlayer(player, droneId, monitorItem);
        }
    }

    private void unlinkMonitorsFromPlayer(ServerPlayer player, String droneId, Item monitorItem) {
        boolean changed = false;
        changed |= unlinkMonitorCollection(player.getInventory().items, player, droneId, monitorItem);
        changed |= unlinkMonitorCollection(player.getInventory().offhand, player, droneId, monitorItem);
        changed |= unlinkMonitorCollection(player.getInventory().armor, player, droneId, monitorItem);
        // Эндер-сундук игрока — на случай, если монитор хранится там.
        try {
            net.minecraft.world.SimpleContainer ender = player.getEnderChestInventory();
            if (ender != null) {
                java.util.List<ItemStack> enderStacks = new java.util.ArrayList<>(ender.getContainerSize());
                for (int i = 0; i < ender.getContainerSize(); i++) {
                    enderStacks.add(ender.getItem(i));
                }
                changed |= unlinkMonitorCollection(enderStacks, player, droneId, monitorItem);
            }
        } catch (Throwable ignored) {
            // защитно: если структура эндер-сундука изменится в будущей версии — не падаем.
        }

        if (changed) {
            // Принудительная синхронизация инвентаря клиенту, чтобы Linked=false дошёл сразу.
            try {
                player.inventoryMenu.broadcastChanges();
                if (player.containerMenu != null && player.containerMenu != player.inventoryMenu) {
                    player.containerMenu.broadcastChanges();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean unlinkMonitorCollection(java.util.List<ItemStack> stacks, ServerPlayer player, String droneId,
            Item monitorItem) {
        boolean changed = false;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || !stack.is(monitorItem)) {
                continue;
            }
            var tag = NBTTool.getTag(stack);
            if (tag == null) {
                continue;
            }
            String linkedDrone = tag.getString(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED_DRONE);
            // Чистим как мониторы, привязанные именно к этому дрону, так и "висячие" (Linked=true, без LinkedDrone).
            boolean linkedToThis = droneId.equals(linkedDrone);
            boolean orphanLinked = tag.getBoolean(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED)
                    && (linkedDrone == null || linkedDrone.isEmpty() || "none".equals(linkedDrone));
            if (!linkedToThis && !orphanLinked) {
                continue;
            }
            // Явно проставляем все ключи ДО Monitor.disLink — на случай, если NBTTool.saveTag смерджит
            // и забудет про неустановленные значения.
            tag.putBoolean("Using", false);
            tag.putBoolean(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED, false);
            tag.putString(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED_DRONE, "none");
            com.atsuishio.superbwarfare.item.misc.MonitorItem.disLink(tag, player);
            NBTTool.saveTag(stack, tag);
            changed = true;
        }
        return changed;
    }

    /**
     * Управляет принудительной загрузкой чанков.
     * Если force=true, текущий чанк дрона будет загружен принудительно.
     * Если force=false, принудительная загрузка будет снята.
     */
    private void wrbdrones$updateChunkLoading(ServerLevel level, boolean force) {
        ChunkPos currentChunk = this.chunkPosition();

        // Если нам нужно держать чанк загруженным
        if (force) {
            // Если чанк изменился или не был загружен
            if (!currentChunk.equals(wrbdrones$loadedChunk)) {
                // Выгружаем старый
                if (wrbdrones$loadedChunk != null) {
                    level.setChunkForced(wrbdrones$loadedChunk.x, wrbdrones$loadedChunk.z, false);
                }
                // Загружаем новый
                level.setChunkForced(currentChunk.x, currentChunk.z, true);
                wrbdrones$loadedChunk = currentChunk;
            }
        } else {
            // Если нужно перестать держать чанк (force=false)
            if (wrbdrones$loadedChunk != null) {
                level.setChunkForced(wrbdrones$loadedChunk.x, wrbdrones$loadedChunk.z, false);
                wrbdrones$loadedChunk = null;
            }
        }
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private void spawnClientParticles() {

        // Engine power check for ground effects
        float power = 0.0f;
        try {
            power = Math.abs(this.entityData.get(DroneEntity.POWER));
        } catch (Exception e) {
            return;
        }

        if (power < 0.1f) return;

        Level level = this.level();
        Vec3 pos = this.position();
        BlockPos blockPos = BlockPos.containing(pos);

        // Ground/Water interaction (Check down up to 5 blocks)
        for (int i = 1; i <= 5; i++) {
            BlockPos p = blockPos.below(i);
            BlockState state = level.getBlockState(p);

            if (!state.isAir()) {
                double dist = pos.y - (p.getY() + 1.0);
                if (dist < 0 || dist > 4.0) break;

                float intensity = (float) (1.0 - (dist / 4.0));
                if (intensity <= 0) break;

                if (state.getFluidState().isSource()) {
                    if (level.random.nextFloat() < intensity * 0.5f) {
                        level.addParticle(ParticleTypes.SPLASH,
                                pos.x + (level.random.nextDouble() - 0.5) * 2.0 * intensity,
                                p.getY() + 0.9,
                                pos.z + (level.random.nextDouble() - 0.5) * 2.0 * intensity,
                                0, 0, 0);
                    }
                } else if (state.isSolidRender(level, p)) {
                    // Block dust
                    int particleCount = (int) (intensity * 20.0f);
                    if (particleCount < 2 && level.random.nextFloat() < intensity) {
                        particleCount = 2;
                    }
                    for (int k = 0; k < particleCount; k++) {
                        double px = pos.x + (level.random.nextDouble() - 0.5) * 1.5;
                        double pz = pos.z + (level.random.nextDouble() - 0.5) * 1.5;
                        double vx = (px - pos.x) * 0.1;
                        double vz = (pz - pos.z) * 0.1;

                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                                px, p.getY() + 1.1, pz, vx, 0.1, vz);
                    }
                    // Smoke/Cloud for wind effect
                    if (level.random.nextFloat() < intensity * 0.2f) {
                        double px = pos.x + (level.random.nextDouble() - 0.5) * 1.0;
                        double pz = pos.z + (level.random.nextDouble() - 0.5) * 1.0;
                        level.addParticle(ParticleTypes.CLOUD,
                                px, p.getY() + 1.2, pz,
                                (px - pos.x) * 0.15, 0, (pz - pos.z) * 0.15);
                    }
                    if (level.random.nextFloat() < intensity * 0.1f) {
                        double px = pos.x + (level.random.nextDouble() - 0.5) * 1.0;
                        double pz = pos.z + (level.random.nextDouble() - 0.5) * 1.0;
                        level.addParticle(ParticleTypes.LARGE_SMOKE,
                                px, p.getY() + 1.0, pz,
                                (px - pos.x) * 0.1, 0, (pz - pos.z) * 0.1);
                    }
                }
                break;
            }
        }
    }

}
