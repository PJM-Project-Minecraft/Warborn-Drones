package ru.liko.wrbdrones.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.liko.wrbdrones.util.PilotViewAnchors;

/**
 * Ре-центрирует клиентский поток ЧАНКОВ пилота на дрон, чтобы FPV-камера видела мир
 * вокруг дрона, а не вокруг замороженного дома пилота.
 *
 * <p>Подменяет ровно один вызов — {@code player.chunkPosition()} в
 * {@code updateChunkTracking} — позицией дрона-якоря. {@code updateChunkTracking}
 * вызывается ванильным {@code ChunkMap.tick()} для каждого игрока каждый тик; он
 * считает {@code ChunkTrackingView} и шлёт {@code ClientboundSetChunkCacheCenterPacket}
 * + сами чанки этому одному игроку. Это ЧИСТО player-local маршрутизация пакетов: он
 * НЕ трогает {@code DistanceManager} ({@code addPlayer}/{@code removePlayer},
 * {@code playersPerChunk}, уровни тикетов), поэтому не может повредить прогрузку чанков
 * у других игроков.</p>
 *
 * <p><b>История:</b> раньше здесь были ещё три redirect'а — {@code move},
 * {@code updatePlayerPos}, {@code updatePlayerStatus} — которые подменяли
 * {@code SectionPos.of(player)} секцией дрона и тем самым перевешивали PLAYER-ticket
 * пилота на дрон. Это рассинхронизировало общий учёт игроков в {@code DistanceManager}
 * (ванильный код рядом читал реальные поля игрока: {@code getLastSectionPos},
 * {@code getBlockX}), давало дисбаланс {@code addPlayer}/{@code removePlayer} и NPE в
 * серверном тике чанков → у ВСЕХ игроков ломалась прогрузка. Эти три redirect'а
 * удалены; сами чанки вокруг дрона теперь грузит независимый region-ticket
 * {@link ru.liko.wrbdrones.util.DroneChunkLoader}, а тело пилота сохраняет свой обычный
 * player-ticket. Трекинг СУЩНОСТЕЙ пилота центрирует на дрон отдельный
 * {@link ChunkMapTrackedEntityMixin} (тоже player-local, безопасен).</p>
 */
@Mixin(ChunkMap.class)
public class ChunkMapPilotAnchorMixin {

    /**
     * Подменяет {@code player.chunkPosition()} в {@code updateChunkTracking} позицией
     * дрона, чтобы {@code ClientboundSetChunkCacheCenterPacket} ре-центрировал
     * клиентский {@code ClientChunkCache} на дрон и клиент сохранял присланные чанки.
     *
     * @param player игрок, для которого обновляется трекинг чанков
     * @return позиция дрона-якоря — если у игрока есть якорь; иначе — реальная позиция
     */
    @Redirect(
            method = "updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"
            )
    )
    private ChunkPos wrbdrones$anchorChunkPosInUpdateChunkTracking(final ServerPlayer player) {
        if (!PilotViewAnchors.isEmpty()) {
            Entity drone = PilotViewAnchors.getAnchorDrone(player.getUUID());
            if (drone != null) {
                return drone.chunkPosition();
            }
        }
        return player.chunkPosition();
    }
}
