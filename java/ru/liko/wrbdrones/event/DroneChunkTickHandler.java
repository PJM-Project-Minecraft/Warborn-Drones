package ru.liko.wrbdrones.event;

import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.item.Monitor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.util.ChunkLoadManager;

import java.util.UUID;

/**
 * Обработчик тика сервера для загрузки чанков дронов.
 * Проверяет мониторы у игроков и загружает чанки связанных дронов,
 * чтобы дроны могли тикать даже когда находятся далеко от игрока.
 */
@Mod.EventBusSubscriber(modid = Wrbdrones.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DroneChunkTickHandler {
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer() == null) return;
        
        // Проверяем каждого игрока
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            checkPlayerMonitor(player);
        }
    }
    
    private static void checkPlayerMonitor(ServerPlayer player) {
        // Проверяем монитор в главной руке
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(ModItems.MONITOR.get())) {
            return;
        }
        
        var tag = mainHand.getOrCreateTag();
        if (!tag.getBoolean(Monitor.LINKED)) {
            return;
        }
        
        String linkedDroneId = tag.getString(Monitor.LINKED_DRONE);
        if (linkedDroneId == null || linkedDroneId.isEmpty() || linkedDroneId.equals("none")) {
            return;
        }
        
        // Принудительно загружаем чанк дрона по сохранённой позиции
        if (tag.contains("PosX") && tag.contains("PosZ")) {
            double posX = tag.getDouble("PosX");
            double posZ = tag.getDouble("PosZ");
            
            ServerLevel level = player.serverLevel();
            int chunkX = (int) Math.floor(posX) >> 4;
            int chunkZ = (int) Math.floor(posZ) >> 4;
            
            // Принудительно загружаем чанк (это синхронная операция)
            level.getChunk(chunkX, chunkZ);
            
            // Также загружаем соседние чанки для надёжности
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.getChunk(chunkX + dx, chunkZ + dz);
                }
            }
            
            // Добавляем тикет чтобы чанки оставались загруженными
            try {
                int entityId = UUID.fromString(linkedDroneId).hashCode();
                ChunkLoadManager.ensureChunksLoaded(level, entityId, new ChunkPos(chunkX, chunkZ));
            } catch (IllegalArgumentException ignored) {
            }
        }
        
        // Теперь ищем дрон - чанки уже загружены
        try {
            UUID droneUuid = UUID.fromString(linkedDroneId);
            
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity entity = level.getEntity(droneUuid);
                if (entity instanceof AddonDroneEntity drone) {
                    ChunkLoadManager.ensureChunksLoaded(level, drone.getId(), drone.chunkPosition());
                    return;
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
    }
}
