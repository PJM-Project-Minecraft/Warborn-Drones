package ru.liko.wrbdrones.util;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Сторож границы прогруза для летающих дронов: сущность, вошедшая в непрогруженный
 * чанк, попадает в HIDDEN-секцию — перестаёт тикать, пропадает с клиентов и «застревает
 * в чанке» до (не)скорой генерации; если её тикеты к тому времени истекли — навсегда.
 *
 * <p>Поэтому серверное движение в непрогруженный чанк просто откладывается: дрон
 * зависает на границе на несколько тиков, пока его же tickets (preload-линия Шахеда,
 * {@link DroneChunkLoader}) не догрузят чанк, и летит дальше. Dist-neutral.</p>
 */
public final class ChunkEdgeGuard {

    private ChunkEdgeGuard() {
    }

    /**
     * {@code true}, если движение {@code movement} НЕ надо выполнять в этом тике:
     * оно пересекает границу чанка, а чанк назначения ещё не прогружен до FULL.
     */
    public static boolean shouldHoldMove(final Entity entity, final Vec3 movement) {
        if (entity.level().isClientSide() || (movement.x == 0.0 && movement.z == 0.0)) {
            return false;
        }
        int destX = Mth.floor(entity.getX() + movement.x) >> 4;
        int destZ = Mth.floor(entity.getZ() + movement.z) >> 4;
        if (destX == entity.chunkPosition().x && destZ == entity.chunkPosition().z) {
            return false;
        }
        return !entity.level().hasChunk(destX, destZ);
    }
}
