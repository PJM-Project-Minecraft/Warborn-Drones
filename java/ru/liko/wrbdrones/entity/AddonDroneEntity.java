package ru.liko.wrbdrones.entity;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.liko.wrbdrones.registry.ModEntityTypes;
import ru.liko.wrbdrones.registry.ModSounds;
import ru.liko.wrbdrones.util.ChunkLoadManager;
import ru.liko.wrbdrones.util.PlayerDecoyManager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Base wrapper that lets us reuse SuperbWarfare's drone behaviour while providing
 * per-drone assets and optional default loadouts.
 */
public abstract class AddonDroneEntity extends DroneEntity {

    /**
     * Возвращаем наш кастомный звук двигателя, зарегистрированный через звукосистему SBW.
     */
    @Override
    public SoundEvent getEngineSound() {
        return ModSounds.FPV_DRONE_ENGINE.get();
    }

    @Override
    public float getEngineSoundVolume() {
        float base = super.getEngineSoundVolume();
        return ClientAudio.adjustExteriorVolume(this, base);
    }

    private static final GameProfile WRB_FAKE_PROFILE = new GameProfile(UUID.nameUUIDFromBytes("WRBDrones-DroneLoader".getBytes()), "[WRB_Drone]");
    
    /**
     * EntityData для синхронизации позиции оператора с клиентом
     * Используем FLOAT для координат и отдельный флаг наличия позиции
     */
    private static final EntityDataAccessor<Float> OPERATOR_POS_X = SynchedEntityData.defineId(AddonDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OPERATOR_POS_Y = SynchedEntityData.defineId(AddonDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OPERATOR_POS_Z = SynchedEntityData.defineId(AddonDroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> OPERATOR_POS_VALID = SynchedEntityData.defineId(AddonDroneEntity.class, EntityDataSerializers.BOOLEAN);
    
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
     * Защита от рекурсии: endRemoteControl() вызывает stopRiding(), который вызывает removePassenger()
     */
    private boolean wrbdrones$endingControl = false;
    @Nullable
    private UUID wrbdrones$controllerUuid = null;

    @SuppressWarnings("unchecked")
    protected AddonDroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super((EntityType<DroneEntity>) (EntityType<?>) type, level);
        // Инициализируем позицию оператора нулевыми координатами
        this.entityData.define(OPERATOR_POS_X, 0.0f);
        this.entityData.define(OPERATOR_POS_Y, 0.0f);
        this.entityData.define(OPERATOR_POS_Z, 0.0f);
        this.entityData.define(OPERATOR_POS_VALID, false);
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
     * Возвращает null если все боеприпасы разрешены, или Set с разрешенными ID предметов.
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
        var key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return "";
        return key.toString();
    }

    @Override
    public @NotNull InteractionResult interact(Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getMainHandItem();
        
        // Быстрая зарядка от предмета-аккумулятора
        if (!stack.isEmpty() && this.hasEnergyStorage()) {
            var energyCap = stack.getCapability(ForgeCapabilities.ENERGY).resolve();
            if (energyCap.isPresent()) {
                int needed = Math.max(0, this.getMaxEnergy() - this.getEnergy());
                if (needed > 0) {
                    int transferred = energyCap.get().extractEnergy(needed, false);
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
                if (result.consumesAction() && !this.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                    if (this.entityData.get(DroneEntity.LINKED) && this.entityData.get(DroneEntity.CONTROLLER).equals(serverPlayer.getStringUUID())) {
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
            if (itemId.startsWith("superbwarfare:") && !canAcceptAttachment(stack)) {
                player.displayClientMessage(
                    Component.translatable("tips.wrbdrones.drone.attachment_not_allowed")
                        .withStyle(ChatFormatting.RED), 
                    true
                );
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
        net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player, droneItem);

        // Отключаем мониторы (если есть)
        try {
            player.getInventory().items.stream()
                    .filter(stack -> stack.getItem() == ModItems.MONITOR.get())
                    .forEach(itemStack -> {
                        if (itemStack.getOrCreateTag().getString(com.atsuishio.superbwarfare.item.Monitor.LINKED_DRONE).equals(this.getStringUUID())) {
                            com.atsuishio.superbwarfare.item.Monitor.disLink(itemStack, player);
                        }
                    });
        } catch (Exception e) {
            // Игнорируем ошибки при отключении мониторов
        }

        // Удаляем дрон
        this.discard();
        return InteractionResult.SUCCESS;
    }

    /**
     * Получает предмет дрона на основе типа сущности
     */
    private ItemStack getDroneItem() {
        var entityType = this.getType();
        var key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entityType);
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
        if (dataAccessorsInitialized) return;
        
        try {
            // Используем getDeclaredField для доступа к полям, даже если они не публичные
            DISPLAY_ENTITY_FIELD = DroneEntity.class.getDeclaredField("DISPLAY_ENTITY");
            DISPLAY_ENTITY_TAG_FIELD = DroneEntity.class.getDeclaredField("DISPLAY_ENTITY_TAG");
            DISPLAY_DATA_FIELD = DroneEntity.class.getDeclaredField("DISPLAY_DATA");
            MAX_AMMO_FIELD = DroneEntity.class.getDeclaredField("MAX_AMMO");
            
            // Пробуем получить AMMO из VehicleEntity через DroneEntity (наследование)
            // Сначала пробуем через родительский класс DroneEntity
            Class<?> vehicleEntityClass = DroneEntity.class.getSuperclass();
            
            if (vehicleEntityClass != null) {
                try {
                    AMMO_FIELD = vehicleEntityClass.getDeclaredField("AMMO");
                } catch (NoSuchFieldException e) {
                    // Если не найдено, пробуем через Class.forName
                    try {
                        vehicleEntityClass = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
                        AMMO_FIELD = vehicleEntityClass.getDeclaredField("AMMO");
                    } catch (Exception e2) {
                        // Если поле не найдено, пробуем через все поля класса
                        java.lang.reflect.Field[] fields = vehicleEntityClass.getDeclaredFields();
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
        if (DISPLAY_ENTITY_FIELD == null) return null;
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
        if (DISPLAY_ENTITY_TAG_FIELD == null) return null;
        try {
            return (net.minecraft.network.syncher.EntityDataAccessor<net.minecraft.nbt.CompoundTag>) DISPLAY_ENTITY_TAG_FIELD.get(null);
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
        if (DISPLAY_DATA_FIELD == null) return null;
        try {
            return (net.minecraft.network.syncher.EntityDataAccessor<java.util.List<Float>>) DISPLAY_DATA_FIELD.get(null);
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
        if (MAX_AMMO_FIELD == null) return null;
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
                    net.minecraft.network.syncher.EntityDataAccessor<Integer> accessor = (net.minecraft.network.syncher.EntityDataAccessor<Integer>) ammoField.get(null);
                    if (accessor != null) {
                        return accessor;
                    }
                }
            } catch (Exception e) {
                // Игнорируем ошибки
            }
            
            try {
                Class<?> vehicleEntityClass = Class.forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
                java.lang.reflect.Field ammoField = vehicleEntityClass.getDeclaredField("AMMO");
                ammoField.setAccessible(true);
                net.minecraft.network.syncher.EntityDataAccessor<Integer> accessor = (net.minecraft.network.syncher.EntityDataAccessor<Integer>) ammoField.get(null);
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

        ControlSession(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, Vec3 originPos, float originYaw, float originPitch, net.minecraft.world.level.GameType gameMode) {
            this.dimension = dimension;
            this.originPos = originPos;
            this.originYaw = originYaw;
            this.originPitch = originPitch;
            this.gameMode = gameMode;
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
            this.entityData.get(OPERATOR_POS_Z)
        );
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
     * Создаёт декой (копию) игрока на его исходной позиции.
     * Телепортирует игрока к дрону для загрузки чанков.
     */
    public boolean beginRemoteControl(final ServerPlayer player) {
        String controllerId = this.entityData.get(DroneEntity.CONTROLLER);
        if (controllerId != null && !controllerId.equals("undefined") && !controllerId.isEmpty() && !controllerId.equals(player.getStringUUID())) {
            return false; // Дрон уже связан с другим игроком
        }
        
        if (controlSession == null) {
            controlSession = new ControlSession(
                player.level().dimension(),
                player.position(),
                player.getYRot(),
                player.getXRot(),
                player.gameMode.getGameModeForPlayer()
            );
            updateOperatorPosition();
        }
        wrbdrones$controllerUuid = player.getUUID();

        if (!player.level().isClientSide() && this.level() instanceof ServerLevel droneLevel) {
            // Создаём декой (копию) игрока на его ИСХОДНОЙ позиции (до телепорта)
            PlayerDecoyManager.createDecoy(player, this);
            
            // Загружаем чанк дрона перед телепортом
            ChunkLoadManager.ensureChunksLoaded(droneLevel, this.getId(), this.chunkPosition());
            
            // Телепортируем игрока к дрону для загрузки чанков
            player.teleportTo(
                droneLevel,
                this.getX(),
                this.getY() + this.getBbHeight() + 4.0,
                this.getZ(),
                player.getYRot(),
                player.getXRot()
            );
            player.fallDistance = 0.0f;
            player.setNoGravity(true);
            player.setInvisible(true);
            player.setSilent(true);
            player.noPhysics = true;
        }
        
        return true;
    }

    /**
     * Завершает удаленное управление дроном.
     * Удаляет декой и телепортирует игрока обратно на исходную позицию.
     */
    public void endRemoteControl(final ServerPlayer player) {
        if (controlSession == null) {
            return;
        }
        // Разрешаем завершение только контроллеру (по UUID, а не по строке из entityData)
        if (wrbdrones$controllerUuid != null && !wrbdrones$controllerUuid.equals(player.getUUID())) {
            return;
        }

        wrbdrones$endingControl = true;
        try {
            // Удаляем декой игрока
            PlayerDecoyManager.removeDecoy(player.getUUID());
            
            // Восстанавливаем состояние игрока
            player.setInvisible(false);
            player.setSilent(false);
            player.setNoGravity(false);
            player.noPhysics = false;
            
            // Телепортируем назад на исходную позицию
            var session = controlSession;
            if (session != null && player.getServer() != null) {
                ServerLevel target = player.getServer().getLevel(session.dimension);
                if (target != null) {
                    // Загружаем чанк перед телепортом
                    ChunkPos chunkPos = new ChunkPos(net.minecraft.core.BlockPos.containing(session.originPos));
                    target.getChunkSource().addRegionTicket(
                        net.minecraft.server.level.TicketType.POST_TELEPORT, 
                        chunkPos, 1, player.getId()
                    );
                    
                    player.teleportTo(target, session.originPos.x, session.originPos.y, session.originPos.z, 
                        session.originYaw, session.originPitch);
                    player.fallDistance = 0.0f;
                }
            }
            
            // Освобождаем тикет загрузки чанков
            if (this.level() instanceof ServerLevel serverLevel) {
                ChunkLoadManager.releaseChunks(serverLevel, this.getId());
            }
        } finally {
            wrbdrones$endingControl = false;
        }

        controlSession = null;
        operatorPosition = null;
        wrbdrones$controllerUuid = null;
        updateOperatorPosition();
    }
    /**
     * Если дрон удаляется/взрывается — обязательно возвращаем контроллера на точку запуска.
     */
    private void wrbdrones$restoreControllerOnRemoval(ServerLevel serverLevel) {
        if (controlSession == null || wrbdrones$controllerUuid == null) return;
        Player p = serverLevel.getPlayerByUUID(wrbdrones$controllerUuid);
        if (p instanceof ServerPlayer sp) {
            endRemoteControl(sp);
        }
    }

    @Override
    protected void removePassenger(@NotNull net.minecraft.world.entity.Entity passenger) {
        super.removePassenger(passenger);

        // Если игрок сам "вышел" (Shift), возвращаем его на точку старта
        if (wrbdrones$endingControl) return;
        if (this.level().isClientSide()) return;

        if (passenger instanceof ServerPlayer serverPlayer && controlSession != null) {
            String controllerId = this.entityData.get(DroneEntity.CONTROLLER);
            if (controllerId != null && controllerId.equals(serverPlayer.getStringUUID())) {
                endRemoteControl(serverPlayer);
            }
        }
    }

    /**
     * Вызывается при потере сигнала оператора.
     * Инициирует взрыв дрона на сервере без периодических проверок.
     */
    public void handleSignalLoss(@Nullable ServerPlayer source) {
        if (this.level().isClientSide()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.isRemoved()) {
            return;
        }

        // Восстанавливаем игрока при потере сигнала (даже если source=null)
        if (source != null) {
            endRemoteControl(source);
        } else {
            wrbdrones$restoreControllerOnRemoval(serverLevel);
        }

        unlinkAllLinkedMonitors(serverLevel);

        // Взрыв через систему SuperbWarfare
        this.createCustomExplosion()
            .radius(3.5f)
            .damage(8)
            .explode();
        this.discard();
    }

    @Override
    public void baseTick() {
        super.baseTick();
        
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            // Проверяем контроллера дрона
            String controllerId = this.entityData.get(DroneEntity.CONTROLLER);
            if (controllerId != null && !controllerId.equals("undefined") && !controllerId.isEmpty()) {
                Player controller = EntityFindUtil.findPlayer(serverLevel, controllerId);
                if (controller instanceof ServerPlayer serverPlayer) {
                    // Проверяем, использует ли игрок монитор
                    // Согласно Monitor.inventoryTick, монитор работает только если он выбран (selected)
                    // Поэтому проверяем только главную руку, так как это единственное место, где монитор может быть активен
                    boolean isUsingMonitor = false;
                    String droneId = this.getStringUUID();
                    
                    // Проверяем главную руку (монитор работает только если он в главной руке и выбран)
                    ItemStack mainHand = serverPlayer.getMainHandItem();
                    if (mainHand.is(ModItems.MONITOR.get())) {
                        var tag = mainHand.getOrCreateTag();
                        if (tag.getBoolean(com.atsuishio.superbwarfare.item.Monitor.LINKED)
                            && tag.getString(com.atsuishio.superbwarfare.item.Monitor.LINKED_DRONE).equals(droneId)) {
                            // Если монитор связан с этим дроном - загружаем чанки чтобы дрон тикал
                            ChunkLoadManager.ensureChunksLoaded(serverLevel, this.getId(), this.chunkPosition());
                            
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

                    // Пока управляет — телепортируем игрока к дрону для загрузки чанков
                    if (isUsingMonitor && controlSession != null) {
                        // Телепортируем игрока к дрону (невидимый, без физики)
                        serverPlayer.teleportTo(
                            serverLevel,
                            this.getX(),
                            this.getY() + this.getBbHeight() + 4.0,
                            this.getZ(),
                            serverPlayer.getYRot(),
                            serverPlayer.getXRot()
                        );
                        serverPlayer.fallDistance = 0.0f;
                        serverPlayer.setNoGravity(true);
                        serverPlayer.setInvisible(true);
                        serverPlayer.setSilent(true);
                        serverPlayer.noPhysics = true;
                        
                        // Синхронизируем экипировку декоя с игроком (каждые 20 тиков)
                        if (this.tickCount % 20 == 0) {
                            PlayerDecoyManager.syncDecoyEquipment(serverPlayer);
                        }
                    }
                }
            }
            
            // Обновляем позицию оператора каждый тик для корректного расчета дистанции на клиенте
            updateOperatorPosition();
        }
    }

    @Override
    public void remove(@NotNull net.minecraft.world.entity.Entity.@NotNull RemovalReason reason) {
        // Завершаем управление при удалении дрона
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            // Освобождаем тикет загрузки чанков
            ChunkLoadManager.releaseChunks(serverLevel, this.getId());
            
            wrbdrones$restoreControllerOnRemoval(serverLevel);
            String controllerId = this.entityData.get(DroneEntity.CONTROLLER);
            if (controllerId != null && !controllerId.equals("undefined") && !controllerId.isEmpty()) {
                Player controller = EntityFindUtil.findPlayer(serverLevel, controllerId);
                if (controller instanceof ServerPlayer serverPlayer && controlSession != null) {
                    endRemoteControl(serverPlayer);
                }
            }
        }
        super.remove(reason);
    }

    private void unlinkAllLinkedMonitors(ServerLevel serverLevel) {
        Item monitorItem = ModItems.MONITOR.get();
        if (monitorItem == null) {
            return;
        }
        String droneId = this.getStringUUID();
        for (ServerPlayer player : serverLevel.players()) {
            unlinkMonitorsFromPlayer(player, droneId, monitorItem);
        }
    }

    private void unlinkMonitorsFromPlayer(ServerPlayer player, String droneId, Item monitorItem) {
        unlinkMonitorCollection(player.getInventory().items, player, droneId, monitorItem);
        unlinkMonitorCollection(player.getInventory().offhand, player, droneId, monitorItem);
    }

    private void unlinkMonitorCollection(java.util.List<ItemStack> stacks, ServerPlayer player, String droneId, Item monitorItem) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty() || !stack.is(monitorItem)) {
                continue;
            }
            var tag = stack.getTag();
            if (tag != null && droneId.equals(tag.getString(com.atsuishio.superbwarfare.item.Monitor.LINKED_DRONE))) {
                com.atsuishio.superbwarfare.item.Monitor.disLink(stack, player);
            }
        }
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static final class ClientAudio {

        private static final Map<AddonDroneEntity, InteriorSound> INTERIOR_SOUNDS = new WeakHashMap<>();

        static {
            MinecraftForge.EVENT_BUS.addListener(ClientAudio::onClientTick);
        }

        private static float adjustExteriorVolume(AddonDroneEntity drone, float baseVolume) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            var listener = mc.player;
            if (listener == null) {
                return baseVolume;
            }

            if (isControlling(listener, drone)) {
                return 0.0f;
            }

            double distance = Math.sqrt(drone.distanceToSqr(listener));
            double maxDistance = 48.0;
            if (distance >= maxDistance) {
                return 0.0f;
            }

            double factor = 1.0 - (distance / maxDistance);
            return (float) (baseVolume * factor);
        }

        private static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != Phase.END) return;

            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) {
                INTERIOR_SOUNDS.clear();
                return;
            }

            AddonDroneEntity controlled = getControlledDrone(mc);
            if (controlled != null && controlled.isRemoved()) {
                controlled = null;
            }

            if (controlled != null) {
                InteriorSound sound = INTERIOR_SOUNDS.get(controlled);
                if (sound == null || sound.isTerminated()) {
                    sound = new InteriorSound(controlled);
                    INTERIOR_SOUNDS.put(controlled, sound);
                    mc.getSoundManager().play(sound);
                }
            }

            var iterator = INTERIOR_SOUNDS.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                AddonDroneEntity drone = entry.getKey();
                InteriorSound sound = entry.getValue();

                boolean keep = controlled != null && drone == controlled && !drone.isRemoved();
                if (!keep) {
                    sound.markForStop();
                    iterator.remove();
                }
            }
        }

        private static AddonDroneEntity getControlledDrone(net.minecraft.client.Minecraft mc) {
            var player = mc.player;
            if (player == null) {
                return null;
            }

            ItemStack stack = player.getMainHandItem();
            if (!stack.is(ModItems.MONITOR.get())) {
                return null;
            }

            var tag = stack.getOrCreateTag();
            if (!tag.getBoolean(com.atsuishio.superbwarfare.item.Monitor.LINKED)) {
                return null;
            }
            if (!tag.getBoolean("Using")) {
                return null;
            }

            String linkedId = tag.getString(com.atsuishio.superbwarfare.item.Monitor.LINKED_DRONE);
            DroneEntity drone = EntityFindUtil.findDrone(player.level(), linkedId);
            return drone instanceof AddonDroneEntity addon ? addon : null;
        }

        private static boolean isControlling(net.minecraft.client.player.LocalPlayer listener, AddonDroneEntity drone) {
            if (listener == null) return false;

            ItemStack stack = listener.getMainHandItem();
            if (!stack.is(ModItems.MONITOR.get())) return false;

            var tag = stack.getOrCreateTag();
            if (!tag.getBoolean(com.atsuishio.superbwarfare.item.Monitor.LINKED)) return false;
            if (!tag.getBoolean("Using")) return false;

            return drone.getStringUUID().equals(tag.getString(com.atsuishio.superbwarfare.item.Monitor.LINKED_DRONE));
        }

        private static final class InteriorSound extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
            private final AddonDroneEntity drone;
            private boolean shouldStop;
            private boolean terminated;

            private InteriorSound(AddonDroneEntity drone) {
                super(ModSounds.FPV_DRONE_ENGINE_INT.get(), net.minecraft.sounds.SoundSource.PLAYERS, drone.level().random);
                this.drone = drone;
                this.looping = true;
                this.delay = 0;
                this.relative = true;
                this.volume = 0.2f;
                this.pitch = 1.0f;
            }

            @Override
            public void tick() {
                var listener = net.minecraft.client.Minecraft.getInstance().player;
                if (this.shouldStop || drone.isRemoved() || !isControlling(listener, drone)) {
                    this.terminated = true;
                    this.volume = 0.0f;
                    return;
                }

                float power = Math.abs(drone.entityData.get(DroneEntity.POWER));
                this.volume = net.minecraft.util.Mth.clamp(0.2f + power * 1.2f, 0.2f, 1.0f);
                this.pitch = net.minecraft.util.Mth.clamp(0.8f + power * 0.6f, 0.8f, 1.4f);
            }

            @Override
            public boolean isStopped() {
                return this.terminated || this.shouldStop || drone.isRemoved();
            }

            void markForStop() {
                this.shouldStop = true;
            }

            boolean isTerminated() {
                return this.terminated;
            }
        }
    }
}

