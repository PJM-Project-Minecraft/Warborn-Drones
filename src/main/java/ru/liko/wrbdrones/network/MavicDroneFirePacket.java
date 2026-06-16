package ru.liko.wrbdrones.network;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.item.misc.MonitorItem;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.NBTTool;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.MavicDroneWithDropEntity;

import java.util.Objects;

/**
 * Запрос «выстрела» с дрона Mavic With Drop (аналог {@code drone.fire = true} в SBW).
 * <p>
 * SBW отправляет это через {@code DroneFireMessage}; при конфликте привязок клавиш / порядке
 * событий ввода пакет может не привести к сбросу. Дублируем надёжный путь только для
 * {@link MavicDroneWithDropEntity}, не трогая режим миномёта (левая рука — параметры огня).
 */
public record MavicDroneFirePacket() implements CustomPacketPayload {

    public static final Type<MavicDroneFirePacket> TYPE = new Type<>(Wrbdrones.loc("mavic_drone_fire"));

    public static final StreamCodec<ByteBuf, MavicDroneFirePacket> STREAM_CODEC = StreamCodec
            .unit(new MavicDroneFirePacket());

    public static void handler(MavicDroneFirePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }

            ItemStack main = player.getMainHandItem();
            var monitorItem = Objects.requireNonNull(ModItems.MONITOR.get(), "monitor");
            if (!main.is(monitorItem)) {
                return;
            }

            var tag = NBTTool.getTag(main);
            if (!tag.getBoolean(MonitorItem.LINKED) || !tag.getBoolean(MonitorItem.USING)) {
                return;
            }

            ItemStack off = player.getOffhandItem();
            if (off.is(ModItems.FIRING_PARAMETERS.get()) || off.is(ModItems.ARTILLERY_INDICATOR.get())) {
                return;
            }

            String droneId = tag.getString(MonitorItem.LINKED_DRONE);
            if (droneId == null || droneId.isEmpty() || droneId.equals("none")) {
                return;
            }

            var drone = EntityFindUtil.findDrone(player.level(), droneId);
            if (!(drone instanceof MavicDroneWithDropEntity mavic) || mavic.isRemoved()) {
                return;
            }

            String controllerId = mavic.getEntityData().get(DroneEntity.CONTROLLER);
            if (controllerId == null || controllerId.isEmpty()
                    || controllerId.equalsIgnoreCase("undefined")
                    || controllerId.equalsIgnoreCase("none")) {
                return;
            }
            if (!player.getStringUUID().equals(controllerId)) {
                return;
            }

            mavic.fire = true;
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
