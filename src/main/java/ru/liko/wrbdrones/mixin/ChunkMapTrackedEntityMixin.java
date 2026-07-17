package ru.liko.wrbdrones.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.liko.wrbdrones.util.PilotViewAnchors;

/**
 * Переносит центр трекинга СУЩНОСТЕЙ пилота на дрон.
 *
 * <p>{@code ChunkMap.move}/{@code updateChunkTracking} уже центрируют на дрон поток
 * ЧАНКОВ (см. {@link ChunkMapPilotAnchorMixin}), но трекинг сущностей живёт в
 * отдельном внутреннем классе {@code ChunkMap$TrackedEntity}. Его
 * {@code updatePlayer} решает, видит ли игрок сущность, по дистанции
 * {@code player.position() − entity.position()}. Пока пилот заморожен дома, эта
 * дистанция считается от дома, поэтому дрон (и сущности вокруг него) перестают
 * трекаться клиенту, как только улетают за entity-range от дома — дрон исчезает на
 * клиенте, камера-вид с дрона отваливается, а управление/HUD рвутся.</p>
 *
 * <p>Redirect подменяет именно вызов {@code p_140498_.position()} (получатель —
 * {@code ServerPlayer}; вызов {@code this.entity.position()} имеет owner {@code Entity}
 * и сюда не попадает) позицией дрона-якоря, чтобы дистанция мерилась от центра обзора
 * пилота (дрона), а не от его тела.</p>
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public class ChunkMapTrackedEntityMixin {

    @Shadow
    @Final
    Entity entity;

    @Redirect(
            method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;position()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 wrbdrones$anchorPlayerPosInUpdatePlayer(final ServerPlayer player) {
        if (!PilotViewAnchors.isEmpty()) {
            Entity drone = PilotViewAnchors.getAnchorDrone(player.getUUID());
            if (drone != null) {
                return drone.position();
            }
        }
        return player.position();
    }

    /**
     * Не даёт дрону-якорю пропасть с клиента пилота из-за гонки с отправкой чанков.
     *
     * <p>Ванильный {@code updatePlayer} трекает сущность только если её текущий чанк уже
     * ОТПРАВЛЕН игроку ({@code isChunkTracked}; под Moonrise — {@code isChunkSent}).
     * Быстрый дрон постоянно въезжает в чанки, которые сервер ещё не успел дослать, —
     * сущность мгновенно антрекается, клиент её удаляет, SBW-камера (чисто клиентская,
     * ищет дрон в мире) падает обратно в тело пилота, и он видит своё окружение
     * невыгруженным (поток чанков-то отцентрован на дроне). Для пары «пилот — его
     * дрон-якорь» проверку пропускаем: клиент нормально рендерит сущности в ещё не
     * присланных чанках, а чанки доедут следом.</p>
     */
    @ModifyExpressionValue(
            method = "updatePlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ChunkMap;isChunkTracked(Lnet/minecraft/server/level/ServerPlayer;II)Z"
            )
    )
    private boolean wrbdrones$alwaysTrackAnchorDrone(final boolean original, final ServerPlayer player) {
        if (!original && !PilotViewAnchors.isEmpty()
                && PilotViewAnchors.getAnchorDrone(player.getUUID()) == this.entity) {
            return true;
        }
        return original;
    }
}
