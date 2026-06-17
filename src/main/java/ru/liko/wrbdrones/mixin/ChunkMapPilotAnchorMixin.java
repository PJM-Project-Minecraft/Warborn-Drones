package ru.liko.wrbdrones.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.liko.wrbdrones.util.PilotViewAnchors;

/**
 * Делает дрон консистентным «центром обзора» пилота в трёх местах ChunkMap:
 * <ol>
 *   <li>{@code move} — уже был: подменяет {@code SectionPos.of(player)} секцией дрона,
 *       чтобы {@code DistanceManager} стримил чанки вокруг дрона.</li>
 *   <li>{@code updatePlayerPos} — подменяет тот же вызов, чтобы {@code lastSectionPos}
 *       писался по позиции дрона → убирает лишние add/remove в DistanceManager.</li>
 *   <li>{@code updateChunkTracking} — подменяет {@code player.chunkPosition()} секцией
 *       дрона, чтобы {@code ClientboundSetChunkCacheCenterPacket} ре-центрировал
 *       клиентский {@code ClientChunkCache} на дрон, и клиент сохранял присланные чанки.</li>
 * </ol>
 *
 * <p>Все три redirect используют общий хелпер {@link #wrbdrones$anchorDroneFor(Entity)}.
 */
@Mixin(ChunkMap.class)
public class ChunkMapPilotAnchorMixin {

    // -------------------------------------------------------------------------
    // Общий хелпер: возвращает дрон-якорь для данного игрока, или null
    // -------------------------------------------------------------------------

    /**
     * Возвращает дрон-якорь, если {@code entity} — {@code ServerPlayer} с активным
     * якорем в {@link PilotViewAnchors}; иначе {@code null}.
     */
    private static Entity wrbdrones$anchorDroneFor(Entity entity) {
        if (entity instanceof ServerPlayer player && !PilotViewAnchors.isEmpty()) {
            return PilotViewAnchors.getAnchorDrone(player.getUUID());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // 1. move → SectionPos.of(player)
    // -------------------------------------------------------------------------

    /**
     * Подменяет секцию игрока секцией дрона внутри {@code ChunkMap.move}.
     * Это уже существующий redirect из Task 0.3 — вынесен в общий хелпер.
     *
     * @param entityAccess игрок (приходит из {@code SectionPos.of(p_140185_)})
     * @return секция дрона — если у игрока есть якорь; иначе — оригинальная секция
     */
    @Redirect(
            method = "move(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"
            )
    )
    private SectionPos wrbdrones$anchorSectionInMove(final EntityAccess entityAccess) {
        if (entityAccess instanceof Entity entity) {
            Entity drone = wrbdrones$anchorDroneFor(entity);
            if (drone != null) {
                return SectionPos.of(drone);
            }
        }
        return SectionPos.of(entityAccess);
    }

    // -------------------------------------------------------------------------
    // 2. updatePlayerPos → SectionPos.of(player)  (убирает churn lastSectionPos)
    // -------------------------------------------------------------------------

    /**
     * Подменяет {@code SectionPos.of(player)} в {@code updatePlayerPos} секцией дрона.
     * Без этого {@code lastSectionPos} писался бы по реальной позиции игрока, что
     * вызывало бы паразитные add/remove в {@code DistanceManager}.
     *
     * <p>Целевой вызов (NeoForge 1.21.1 Mojang-маппинги): строка 992 в декомпиле —
     * {@code SectionPos sectionpos = SectionPos.of(p_140374_);}.
     *
     * @param entityAccess игрок
     * @return секция дрона или оригинальная секция игрока
     */
    @Redirect(
            method = "updatePlayerPos(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"
            )
    )
    private SectionPos wrbdrones$anchorSectionInUpdatePlayerPos(final EntityAccess entityAccess) {
        if (entityAccess instanceof Entity entity) {
            Entity drone = wrbdrones$anchorDroneFor(entity);
            if (drone != null) {
                return SectionPos.of(drone);
            }
        }
        return SectionPos.of(entityAccess);
    }

    // -------------------------------------------------------------------------
    // 3. updateChunkTracking → player.chunkPosition()  (ре-центрирует клиент)
    // -------------------------------------------------------------------------

    /**
     * Подменяет {@code player.chunkPosition()} в {@code updateChunkTracking} позицией
     * дрона, чтобы {@code ClientboundSetChunkCacheCenterPacket} ре-центрировал
     * клиентский {@code ClientChunkCache} на дрон.
     *
     * <p>В {@code updateChunkTracking} ровно один вызов {@code chunkPosition()} (строка
     * 1034 в декомпиле): {@code ChunkPos chunkpos = p_183755_.chunkPosition();}. Он
     * кормит и early-exit (уже ли view центрирован там?), и {@code ChunkTrackingView.of},
     * и в итоге {@code applyChunkTrackingView → send(ClientboundSetChunkCacheCenterPacket)}.
     *
     * @param player игрок, для которого обновляется трекинг
     * @return позиция дрона или оригинальная позиция игрока
     */
    @Redirect(
            method = "updateChunkTracking(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"
            )
    )
    private ChunkPos wrbdrones$anchorChunkPosInUpdateChunkTracking(final Entity player) {
        Entity drone = wrbdrones$anchorDroneFor(player);
        if (drone != null) {
            return drone.chunkPosition();
        }
        return player.chunkPosition();
    }
}
