package ru.liko.wrbdrones.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.liko.wrbdrones.util.ChunkSendBooster;

/**
 * Поднимает пропускную способность отправки чанков пилоту дрона, чтобы быстрые
 * аппараты (FPV, Lancet) не обгоняли стриминг террейна и не влетали в пустые чанки.
 *
 * <p>Почему именно здесь, а не в тике дрона: {@code sendNextChunks} вызывается в
 * {@code MinecraftServer.tickServer} уже ПОСЛЕ {@code connection.tick()}, поэтому
 * клиентский ack ({@code PlayerChunkSender#onChunkBatchReceivedByClient}) успевает
 * сбросить {@code desiredChunksPerTick} к низкому клиентскому значению до отправки.
 * {@code HEAD} {@code sendNextChunks} — последнее место перед фактической отправкой
 * батча, где значение ещё можно поднять.</p>
 */
@Mixin(PlayerChunkSender.class)
public class PlayerChunkSenderMixin {

    @Shadow
    private float desiredChunksPerTick;

    @Shadow
    private int maxUnacknowledgedBatches;

    @Inject(method = "sendNextChunks", at = @At("HEAD"))
    private void wrbdrones$boostChunkSend(final ServerPlayer player, final CallbackInfo ci) {
        if (player == null || !ChunkSendBooster.isBoosted(player.getUUID())) {
            return;
        }
        // Только повышаем — никогда не опускаем то, что клиент уже запросил выше.
        final float boostRate = ChunkSendBooster.desiredChunksPerTick();
        if (this.desiredChunksPerTick < boostRate) {
            this.desiredChunksPerTick = boostRate;
        }
        final int boostBatches = ChunkSendBooster.maxUnacknowledgedBatches();
        if (this.maxUnacknowledgedBatches < boostBatches) {
            this.maxUnacknowledgedBatches = boostBatches;
        }
    }
}
