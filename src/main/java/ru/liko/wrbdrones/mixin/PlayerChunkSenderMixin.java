package ru.liko.wrbdrones.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.liko.wrbdrones.util.ChunkSendBooster;
import ru.liko.wrbdrones.util.PilotViewAnchors;

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

    @Unique
    private boolean wrbdrones$boostActive;

    @Unique
    private float wrbdrones$restoreDesiredChunksPerTick;

    @Unique
    private int wrbdrones$restoreMaxUnacknowledgedBatches;

    /**
     * Vanilla сортирует pending-чанки по расстоянию до физического тела игрока.
     * В self-chunk режиме очередь относится к дрону, поэтому и приоритет должен
     * считаться от него — иначе первыми уходят чанки со стороны оставленного тела.
     */
    @Redirect(
            method = "sendNextChunks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;chunkPosition()Lnet/minecraft/world/level/ChunkPos;"
            )
    )
    private ChunkPos wrbdrones$useDroneAsPriorityCenter(final ServerPlayer player) {
        if (!PilotViewAnchors.isEmpty()) {
            Entity drone = PilotViewAnchors.getAnchorDrone(player.getUUID());
            if (drone != null) {
                return drone.chunkPosition();
            }
        }
        return player.chunkPosition();
    }

    @Inject(method = "sendNextChunks", at = @At("HEAD"))
    private void wrbdrones$boostChunkSend(final ServerPlayer player, final CallbackInfo ci) {
        final boolean boosted = player != null && ChunkSendBooster.isBoosted(player.getUUID());
        if (!boosted) {
            if (this.wrbdrones$boostActive) {
                this.desiredChunksPerTick = this.wrbdrones$restoreDesiredChunksPerTick;
                this.maxUnacknowledgedBatches = this.wrbdrones$restoreMaxUnacknowledgedBatches;
                this.wrbdrones$boostActive = false;
            }
            return;
        }

        if (!this.wrbdrones$boostActive) {
            this.wrbdrones$restoreDesiredChunksPerTick = this.desiredChunksPerTick;
            this.wrbdrones$restoreMaxUnacknowledgedBatches = this.maxUnacknowledgedBatches;
            this.wrbdrones$boostActive = true;
        }

        // Только повышаем скорость: клиент может запросить больше.
        final float boostRate = ChunkSendBooster.desiredChunksPerTick();
        if (this.desiredChunksPerTick < boostRate) {
            this.desiredChunksPerTick = boostRate;
        }
        // Лимит батчей — также ограничитель: vanilla возвращает его к 10 после ACK.
        this.maxUnacknowledgedBatches = ChunkSendBooster.maxUnacknowledgedBatches();
    }

    /**
     * Сохраняет последние vanilla-значения и сразу восстанавливает лимит boost.
     * Без этого каждый ACK поднимает maxUnacknowledgedBatches обратно до 10.
     */
    @Inject(method = "onChunkBatchReceivedByClient", at = @At("TAIL"))
    private void wrbdrones$retainBoostAfterAcknowledgement(final float desiredChunksPerTick,
                                                            final CallbackInfo ci) {
        if (!this.wrbdrones$boostActive) {
            return;
        }

        this.wrbdrones$restoreDesiredChunksPerTick = this.desiredChunksPerTick;
        this.wrbdrones$restoreMaxUnacknowledgedBatches = this.maxUnacknowledgedBatches;
        final float boostRate = ChunkSendBooster.desiredChunksPerTick();
        if (this.desiredChunksPerTick < boostRate) {
            this.desiredChunksPerTick = boostRate;
        }
        this.maxUnacknowledgedBatches = ChunkSendBooster.maxUnacknowledgedBatches();
    }
}
