package ru.liko.wrbdrones.entity;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Специальный FakePlayer который следует за дроном и обеспечивает загрузку чанков.
 * 
 * Этот игрок добавляется в мир как сущность и телепортируется к позиции дрона каждый тик,
 * что заставляет сервер загружать чанки вокруг него как для настоящего игрока.
 */
public class DroneChunkLoaderPlayer extends FakePlayer {
    
    private static final GameProfile LOADER_PROFILE = new GameProfile(
        UUID.nameUUIDFromBytes("WRBDrones-ChunkLoader".getBytes()), 
        "[DroneLoader]"
    );
    
    private final Entity targetDrone;
    private boolean isValid = true;
    
    public DroneChunkLoaderPlayer(ServerLevel level, Entity drone) {
        super(level, LOADER_PROFILE);
        this.targetDrone = drone;
        
        // Настраиваем FakePlayer
        this.setInvisible(true);
        this.setInvulnerable(true);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setSilent(true);
        
        // Устанавливаем начальную позицию
        this.setPos(drone.getX(), drone.getY(), drone.getZ());
        
        // Патчим connection.channel чтобы избежать NPE
        ensureChannelPresent();
    }
    
    /**
     * Патчит connection.channel если он null.
     */
    private void ensureChannelPresent() {
        if (this.connection == null) {
            return;
        }
        
        try {
            // Получаем Connection через рефлексию
            Connection netConnection = extractConnection(this.connection);
            if (netConnection == null) {
                return;
            }
            
            // Проверяем есть ли уже канал
            try {
                if (netConnection.channel() != null) {
                    return;
                }
            } catch (Throwable ignored) {
                return;
            }
            
            // Устанавливаем фейковый канал через рефлексию
            Channel channel = new EmbeddedChannel();
            setConnectionChannel(netConnection, channel);
        } catch (Exception ignored) {
        }
    }
    
    /**
     * Извлекает Connection из packet listener через рефлексию.
     */
    private static Connection extractConnection(Object packetListener) {
        for (Class<?> c = packetListener.getClass(); c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!Connection.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(packetListener);
                    if (value instanceof Connection conn) {
                        return conn;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }
    
    /**
     * Устанавливает канал в Connection через рефлексию.
     */
    private static void setConnectionChannel(Connection connection, Channel channel) {
        for (Class<?> c = connection.getClass(); c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!Channel.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object current = field.get(connection);
                    if (current == null) {
                        field.set(connection, channel);
                    }
                    return;
                } catch (Throwable ignored) {
                }
            }
        }
    }
    
    /**
     * Обновляет позицию загрузчика к позиции дрона.
     * Должен вызываться каждый тик.
     */
    public void followDrone() {
        if (!isValid || targetDrone == null || targetDrone.isRemoved()) {
            isValid = false;
            return;
        }
        
        // Телепортируемся к позиции дрона
        Vec3 dronePos = targetDrone.position();
        this.setPos(dronePos.x, dronePos.y + 1.0, dronePos.z);
        
        // Обновляем старые координаты
        this.xOld = this.getX();
        this.yOld = this.getY();
        this.zOld = this.getZ();
    }
    
    /**
     * Проверяет, валиден ли этот загрузчик.
     */
    public boolean isValid() {
        return isValid && targetDrone != null && !targetDrone.isRemoved();
    }
    
    /**
     * Помечает загрузчик как невалидный.
     */
    public void invalidate() {
        isValid = false;
    }
    
    /**
     * Возвращает целевой дрон.
     */
    public Entity getTargetDrone() {
        return targetDrone;
    }
    
    @Override
    public void tick() {
        // Минимальный tick - только обновляем позицию
        followDrone();
    }
    
    @Override
    public boolean isSpectator() {
        return true; // Spectator mode для минимального влияния на мир
    }
    
    @Override
    public boolean isCreative() {
        return true;
    }
}
