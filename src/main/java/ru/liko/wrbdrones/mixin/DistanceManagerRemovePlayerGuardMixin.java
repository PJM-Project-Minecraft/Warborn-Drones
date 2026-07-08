package ru.liko.wrbdrones.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Защита от краша сервера в {@link DistanceManager#removePlayer} при рассинхроне
 * {@code lastSectionPos} игрока и карты {@code playersPerChunk}.
 *
 * <p>В self-chunk режиме дрон сам центрирует трекинг чанков пилота через редиректы
 * {@link ChunkMapPilotAnchorMixin} (включая путь addPlayer в {@code updatePlayerStatus}).
 * Но ванильный {@code removePlayer} не защищён от {@code null}:
 * <pre>{@code
 * ObjectSet<ServerPlayer> objectset = this.playersPerChunk.get(i); // может быть null
 * objectset.remove(player);                                        // NPE, если null
 * }</pre>
 * Если при краевой ситуации (смена измерения в полёте, респавн с пережившим якорем,
 * интероп с другими модами) {@code lastSectionPos} указывает на чанк, где игрока нет в
 * {@code playersPerChunk}, ваниль падает NPE в тике сущности и уводит сервер.
 *
 * <p>Этот миксин делает {@code removePlayer} консистентным no-op'ом, когда игрока
 * реально нет в записи чанка: трекеры чанка (naturalSpawnChunkCounter, playerTicketManager,
 * tickingTicketsTracker) для этого игрока в этом чанке никогда не увеличивались, поэтому
 * пропуск деинкремента корректен. Нормальный путь (игрок есть в записи) не затрагивается.
 *
 * <p>Dist-neutral (без {@code @OnlyIn}) — серверная логика чанков.
 */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerRemovePlayerGuardMixin {

    @Shadow
    @Final
    Long2ObjectMap<ObjectSet<ServerPlayer>> playersPerChunk;

    @Inject(
            method = "removePlayer(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void wrbdrones$cancelLeaveIfNotRegistered(SectionPos pos, ServerPlayer player, CallbackInfo ci) {
        // Если игрока нет в записи чанка — removePlayer это no-op (нечего удалять).
        // Ванильный код упал бы NPE на objectset.remove при set == null.
        ObjectSet<ServerPlayer> set = this.playersPerChunk.get(pos.chunk().toLong());
        if (set == null || !set.contains(player)) {
            ci.cancel();
        }
    }
}
