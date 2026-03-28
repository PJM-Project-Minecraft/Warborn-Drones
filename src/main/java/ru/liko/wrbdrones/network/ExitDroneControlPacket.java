package ru.liko.wrbdrones.network;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.NBTTool;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.AddonDroneEntity;

import java.util.Objects;

public record ExitDroneControlPacket() implements CustomPacketPayload {

    public static final Type<ExitDroneControlPacket> TYPE = new Type<>(Wrbdrones.loc("exit_drone_control"));

    public static final StreamCodec<ByteBuf, ExitDroneControlPacket> STREAM_CODEC = StreamCodec
            .unit(new ExitDroneControlPacket());

    public static void handler(ExitDroneControlPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sender)) {
                return;
            }

            // Проверяем, что игрок держит монитор
            ItemStack stack = sender.getMainHandItem();
            if (!stack.is(ModItems.MONITOR.get())) {
                return;
            }

            // Проверяем, что монитор связан и используется
            var tag = NBTTool.getTag(stack);
            if (!tag.getBoolean(com.atsuishio.superbwarfare.item.Monitor.LINKED)
                    || !tag.getBoolean("Using")) {
                return;
            }

            // Получаем дрон
            String droneId = tag.getString(com.atsuishio.superbwarfare.item.Monitor.LINKED_DRONE);
            if (droneId == null || droneId.isEmpty() || droneId.equals("none")) {
                return;
            }

            var drone = com.atsuishio.superbwarfare.tools.EntityFindUtil.findDrone(sender.level(), droneId);

            // Если дрон найден и игрок его контролирует, выполняем штатный выход
            if (drone instanceof AddonDroneEntity addonDrone) {
                String controllerId = addonDrone.getEntityData().get(DroneEntity.CONTROLLER);
                if (controllerId != null && !controllerId.isEmpty() && !controllerId.equals("undefined")
                        && Objects.equals(sender.getStringUUID(), controllerId)) {
                    // Выходим из управления дроном
                    addonDrone.endRemoteControl(sender);
                }
            }

            // ВСЕГДА сбрасываем флаг "Using", даже если дрон не найден или игрок не в нем
            // Это разблокирует клавиатуру игроку

            // Также переключаем флаг "Using" в мониторе через NBTTool
            tag.putBoolean("Using", false);
            NBTTool.saveTag(stack, tag);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
