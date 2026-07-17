package ru.liko.wrbdrones.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.liko.wrbdrones.util.PilotViewAnchors;

/**
 * Якорь трекинга СУЩНОСТЕЙ пилота под Moonrise (применяется только с ним, см.
 * {@link WrbdronesMixinPlugin}). Moonrise выбирает, каким игрокам обновлять tracked
 * entities, по своей структуре {@code NearbyPlayers}, а та регистрирует игрока по
 * {@code player.chunkPosition()}. Без подмены дрон и сущности вокруг него перестают
 * трекаться пилоту, как только улетают за entity-range от его тела — камера с дрона
 * отваливается. Ванильный {@link ChunkMapTrackedEntityMixin} (дистанция внутри
 * {@code updatePlayer}) продолжает работать и с Moonrise — эти два миксина дополняют
 * друг друга.
 */
@Mixin(targets = "ca.spottedleaf.moonrise.common.misc.NearbyPlayers", remap = false)
public class MoonriseNearbyPlayersMixin {

    @Redirect(
            method = {"addPlayer", "tickPlayer"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"
            ),
            require = 0
    )
    private ChunkPos wrbdrones$anchorChunkPos(final ServerPlayer player) {
        if (!PilotViewAnchors.isEmpty()) {
            Entity drone = PilotViewAnchors.getAnchorDrone(player.getUUID());
            if (drone != null) {
                return drone.chunkPosition();
            }
        }
        return player.chunkPosition();
    }
}
