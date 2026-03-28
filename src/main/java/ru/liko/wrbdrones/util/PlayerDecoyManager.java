package ru.liko.wrbdrones.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.entity.PlayerDecoyEntity;
import ru.liko.wrbdrones.registry.ModEntityTypes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер декоев игроков.
 * 
 * Управляет созданием, обновлением и удалением декоев (обманок) игроков,
 * которые создаются на месте оператора дрона.
 */
public final class PlayerDecoyManager {
    
    private PlayerDecoyManager() {}
    
    private static final Map<UUID, DecoyData> activeDecoys = new ConcurrentHashMap<>();
    
    private static class DecoyData {
        final UUID decoyEntityId;
        final UUID droneId;
        final ServerLevel level;
        
        DecoyData(UUID decoyEntityId, UUID droneId, ServerLevel level) {
            this.decoyEntityId = decoyEntityId;
            this.droneId = droneId;
            this.level = level;
        }
    }
    
    /**
     * Создаёт декой для игрока на его текущей позиции.
     * 
     * @param player Игрок, для которого создаётся декой
     * @param drone Дрон, которым управляет игрок
     * @return Созданный декой или null если не удалось создать
     */
    @Nullable
    public static PlayerDecoyEntity createDecoy(ServerPlayer player, AddonDroneEntity drone) {
        if (player.level().isClientSide()) {
            return null;
        }
        
        ServerLevel level = player.serverLevel();
        
        if (hasDecoy(player.getUUID())) {
            removeDecoy(player.getUUID());
        }
        
        PlayerDecoyEntity decoy = ModEntityTypes.PLAYER_DECOY.get().create(level);
        if (decoy == null) {
            return null;
        }
        
        decoy.initFromPlayer(player);
        
        level.addFreshEntity(decoy);

        retargetMobsToDecoy(level, player, decoy);
        
        activeDecoys.put(player.getUUID(), new DecoyData(
            decoy.getUUID(),
            drone.getUUID(),
            level
        ));
        
        return decoy;
    }
    
    /**
     * Удаляет декой игрока.
     */
    public static void removeDecoy(UUID playerUUID) {
        DecoyData data = activeDecoys.remove(playerUUID);
        if (data == null) {
            return;
        }
        
        Entity entity = data.level.getEntity(data.decoyEntityId);
        if (entity instanceof PlayerDecoyEntity decoy) {
            decoy.discard();
        }
    }
    
    /**
     * Проверяет, есть ли декой у игрока.
     */
    public static boolean hasDecoy(UUID playerUUID) {
        return activeDecoys.containsKey(playerUUID);
    }
    
    /**
     * Получает декой игрока.
     */
    @Nullable
    public static PlayerDecoyEntity getDecoy(UUID playerUUID) {
        DecoyData data = activeDecoys.get(playerUUID);
        if (data == null) {
            return null;
        }
        
        Entity entity = data.level.getEntity(data.decoyEntityId);
        return entity instanceof PlayerDecoyEntity decoy ? decoy : null;
    }
    
    /**
     * Получает декой по UUID дрона.
     */
    @Nullable
    public static PlayerDecoyEntity getDecoyByDrone(UUID droneUUID) {
        for (DecoyData data : activeDecoys.values()) {
            if (data.droneId.equals(droneUUID)) {
                Entity entity = data.level.getEntity(data.decoyEntityId);
                if (entity instanceof PlayerDecoyEntity decoy) {
                    return decoy;
                }
            }
        }
        return null;
    }
    
    /**
     * Синхронизирует экипировку декоя с игроком.
     */
    public static void syncDecoyEquipment(ServerPlayer player) {
        PlayerDecoyEntity decoy = getDecoy(player.getUUID());
        if (decoy != null) {
            decoy.copyEquipment(player);
        }
    }
    
    /**
     * Синхронизирует здоровье декоя с игроком.
     */
    public static void syncDecoyHealth(ServerPlayer player) {
        PlayerDecoyEntity decoy = getDecoy(player.getUUID());
        if (decoy != null) {
            decoy.syncHealthFromOwner(player.getHealth());
        }
    }
    
    /**
     * Очищает все декои для уровня (при выгрузке мира).
     */
    public static void clearLevel(ServerLevel level) {
        activeDecoys.entrySet().removeIf(entry -> {
            if (entry.getValue().level == level) {
                Entity entity = level.getEntity(entry.getValue().decoyEntityId);
                if (entity != null) {
                    entity.discard();
                }
                return true;
            }
            return false;
        });
    }
    
    /**
     * Очищает все декои (при остановке сервера).
     */
    public static void clearAll() {
        for (DecoyData data : activeDecoys.values()) {
            try {
                Entity entity = data.level.getEntity(data.decoyEntityId);
                if (entity != null) {
                    entity.discard();
                }
            } catch (Exception ignored) {}
        }
        activeDecoys.clear();
    }
    
    /**
     * Проверяет, является ли сущность декоем игрока.
     */
    public static boolean isDecoy(Entity entity) {
        return entity instanceof PlayerDecoyEntity;
    }
    
    /**
     * Получает UUID игрока-владельца декоя.
     */
    @Nullable
    public static UUID getDecoyOwner(PlayerDecoyEntity decoy) {
        return decoy.getOwnerUUID();
    }

    private static void retargetMobsToDecoy(ServerLevel level, ServerPlayer owner, PlayerDecoyEntity decoy) {
        var searchBox = decoy.getBoundingBox().inflate(64.0);
        for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, searchBox)) {
            if (mob.getTarget() == owner) {
                mob.setTarget(decoy);
            }
        }
    }
}
