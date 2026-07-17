package ru.liko.wrbdrones.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.liko.wrbdrones.util.PilotViewAnchors;

/**
 * Гасит ванильный кик «Flying is not enabled» для пилота, который удалённо управляет
 * дроном. Тело пилота стоит дома на земле, но поток чанков центрируется на дроне
 * ({@link ru.liko.wrbdrones.mixin.PlayerChunkSenderMixin}), поэтому при удалении дрона
 * клиент теряет домашние чанки, тело «проваливается» и шлёт серверу floating-пакеты.
 * Ванильный счётчик {@code aboveGroundTickCount} копится и через ~80 тиков дисконнектит.
 *
 * <p>Пока в {@link PilotViewAnchors} есть якорь этого игрока, каждый серверный тик
 * обнуляем счётчик и флаг — при выходе из управления защита снимается сама.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketFloatingGuardMixin {

    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int aboveGroundTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void wrbdrones$suppressFloatKick(CallbackInfo ci) {
        if (this.player == null || PilotViewAnchors.isEmpty()) {
            return;
        }
        if (PilotViewAnchors.getAnchorDrone(this.player.getUUID()) != null) {
            this.clientIsFloating = false;
            this.aboveGroundTickCount = 0;
        }
    }
}
