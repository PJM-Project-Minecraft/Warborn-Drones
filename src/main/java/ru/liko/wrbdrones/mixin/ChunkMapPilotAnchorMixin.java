package ru.liko.wrbdrones.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.liko.wrbdrones.util.PilotViewAnchors;

/**
 * Переносит «центр обзора» пилотирующего игрока на дрон: redirect статического
 * {@code SectionPos.of(EntityAccess)} внутри {@code ChunkMap.move} так, что для
 * игрока с активным якорем (см. {@link PilotViewAnchors}) возвращается секция дрона.
 * Это заставляет сервер регистрировать игрока в {@code DistanceManager} по позиции
 * дрона, тем самым стримя чанки вокруг дрона, а не вокруг застывшего пилота.
 *
 * <p>Целевой вызов (NeoForge 1.21.1, Mojang-маппинги):
 * {@code SectionPos sectionpos1 = SectionPos.of(p_140185_);} — строка 1006
 * в декомпиле {@code ChunkMap.java}. Параметр объявлен как {@code EntityAccess}
 * (не {@code Entity}), поэтому дескриптор цели использует {@code EntityAccess}.
 */
@Mixin(ChunkMap.class)
public class ChunkMapPilotAnchorMixin {

    /**
     * Подменяет секцию игрока секцией дрона, если у игрока есть активный якорь.
     *
     * @param entityAccess игрок (приходит из {@code SectionPos.of(player)} в move)
     * @return секция дрона — если у игрока есть якорь; иначе — оригинальная секция
     */
    @Redirect(
            method = "move(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"
            )
    )
    private SectionPos wrbdrones$anchorSection(final EntityAccess entityAccess) {
        if (entityAccess instanceof ServerPlayer player && !PilotViewAnchors.isEmpty()) {
            Entity drone = PilotViewAnchors.getAnchorDrone(player.getUUID());
            if (drone != null) {
                return SectionPos.of(drone);
            }
        }
        return SectionPos.of(entityAccess);
    }
}
