package ru.liko.wrbdrones.client.sound;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.NBTTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.registry.ModSounds;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Клиентский обработчик внутреннего звука двигателя дрона
 * ({@code wrbdrones:fpv_drone_engine_int}).
 *
 * Запускает {@link InteriorSound}, когда игрок управляет дроном через монитор
 * ({@code Linked && Using}), и останавливает звук при выходе из управления или
 * удалении дрона.
 *
 * Вынесен в отдельный top-level класс с {@link EventBusSubscriber}, чтобы
 * NeoForge гарантированно зарегистрировал слушатель {@link ClientTickEvent.Post},
 * независимо от того, дёргается ли где-либо {@code AddonDroneEntity#getEngineSoundVolume()}.
 */
@EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class DroneInteriorSoundHandler {

    private static final Map<AddonDroneEntity, InteriorSound> INTERIOR_SOUNDS = new WeakHashMap<>();

    private DroneInteriorSoundHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            INTERIOR_SOUNDS.clear();
            return;
        }

        AddonDroneEntity controlled = getControlledDrone(mc);
        if (controlled != null && controlled.isRemoved()) {
            controlled = null;
        }

        if (controlled != null) {
            InteriorSound sound = INTERIOR_SOUNDS.get(controlled);
            if (sound == null || sound.isTerminated()) {
                sound = new InteriorSound(controlled);
                INTERIOR_SOUNDS.put(controlled, sound);
                mc.getSoundManager().play(sound);
            }
        }

        final Iterator<Map.Entry<AddonDroneEntity, InteriorSound>> it = INTERIOR_SOUNDS.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<AddonDroneEntity, InteriorSound> entry = it.next();
            final AddonDroneEntity drone = entry.getKey();
            final InteriorSound sound = entry.getValue();

            final boolean keep = controlled != null && drone == controlled && !drone.isRemoved();
            if (!keep) {
                sound.markForStop();
                it.remove();
            }
        }
    }

    /**
     * Громкость внешнего звука для случая, когда наблюдатель сам управляет этим дроном.
     * Сейчас не вызывается (внешний звук перехвачен {@link DroneSoundHandler}),
     * но оставлен для обратной совместимости / возможных будущих интеграций.
     */
    public static float adjustExteriorVolume(AddonDroneEntity drone, float baseVolume) {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer listener = mc.player;
        if (listener == null) {
            return baseVolume;
        }

        if (isControlling(listener, drone)) {
            return 0.0f;
        }

        final double distance = Math.sqrt(drone.distanceToSqr(listener));
        final double maxDistance = 48.0;
        if (distance >= maxDistance) {
            return 0.0f;
        }

        final double factor = 1.0 - (distance / maxDistance);
        return (float) (baseVolume * factor);
    }

    private static AddonDroneEntity getControlledDrone(Minecraft mc) {
        final LocalPlayer player = mc.player;
        if (player == null) {
            return null;
        }

        final ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) {
            return null;
        }

        final var tag = NBTTool.getTag(stack);
        if (!tag.getBoolean(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED)) {
            return null;
        }
        if (!tag.getBoolean("Using")) {
            return null;
        }

        final String linkedId = tag.getString(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED_DRONE);
        final DroneEntity drone = EntityFindUtil.findDrone(player.level(), linkedId);
        return drone instanceof AddonDroneEntity addon ? addon : null;
    }

    private static boolean isControlling(LocalPlayer listener, AddonDroneEntity drone) {
        if (listener == null) {
            return false;
        }

        final ItemStack stack = listener.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) {
            return false;
        }

        final var tag = NBTTool.getTag(stack);
        if (!tag.getBoolean(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED)) {
            return false;
        }
        if (!tag.getBoolean("Using")) {
            return false;
        }

        return drone.getStringUUID().equals(tag.getString(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED_DRONE));
    }

    private static final class InteriorSound extends AbstractTickableSoundInstance {
        private final AddonDroneEntity drone;
        private boolean shouldStop;
        private boolean terminated;

        private InteriorSound(AddonDroneEntity drone) {
            super(ModSounds.FPV_DRONE_ENGINE_INT.get(), SoundSource.PLAYERS, drone.level().random);
            this.drone = drone;
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.volume = 0.2f;
            this.pitch = 1.0f;
        }

        @Override
        public void tick() {
            final LocalPlayer listener = Minecraft.getInstance().player;
            if (this.shouldStop || drone.isRemoved() || !isControlling(listener, drone)) {
                this.terminated = true;
                this.volume = 0.0f;
                return;
            }

            final float power = Math.abs(drone.getEntityData().get(DroneEntity.POWER));
            this.volume = Mth.clamp(0.2f + power * 1.2f, 0.2f, 1.0f);
            this.pitch = Mth.clamp(0.8f + power * 0.6f, 0.8f, 1.4f);
        }

        @Override
        public boolean isStopped() {
            return this.terminated || this.shouldStop || drone.isRemoved();
        }

        void markForStop() {
            this.shouldStop = true;
        }

        boolean isTerminated() {
            return this.terminated;
        }
    }
}
