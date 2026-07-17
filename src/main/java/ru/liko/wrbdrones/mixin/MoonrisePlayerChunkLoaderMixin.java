package ru.liko.wrbdrones.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.liko.wrbdrones.util.PilotViewAnchors;

/**
 * Аналог {@link ChunkMapPilotAnchorMixin} для Moonrise (применяется только с ним, см.
 * {@link WrbdronesMixinPlugin}). Под Moonrise загрузку/отправку чанков игроку целиком
 * ведёт {@code RegionizedPlayerChunkLoader$PlayerChunkLoaderData.update()}: он каждый
 * тик берёт {@code player.chunkPosition()} как центр, сам ставит tickets, шлёт
 * center/radius-пакеты и сами чанки. Подмена этого одного вызова позицией дрона-якоря
 * ре-центрирует ВСЮ машинерию Moonrise на дрон.
 *
 * <p>Тело пилота при этом теряет свои load-tickets (они уезжают на дрон), поэтому
 * {@code AddonDroneEntity.beginRemoteControl} дополнительно держит чанк тела региональным
 * ticket'ом через {@code DroneChunkLoader.keepEntityLoaded(player)}.</p>
 *
 * <p>{@code require = 0}: при дрейфе внутренностей Moonrise фича деградирует молча,
 * а не валит сервер на старте.</p>
 */
@Mixin(targets = "ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader$PlayerChunkLoaderData", remap = false)
public class MoonrisePlayerChunkLoaderMixin {

    @Redirect(
            method = "update()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"
            ),
            require = 0
    )
    private ChunkPos wrbdrones$anchorChunkPosInUpdate(final ServerPlayer player) {
        if (!PilotViewAnchors.isEmpty()) {
            Entity drone = PilotViewAnchors.getAnchorDrone(player.getUUID());
            if (drone != null) {
                return drone.chunkPosition();
            }
        }
        return player.chunkPosition();
    }
}
