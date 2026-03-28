package ru.liko.wrbdrones.client.sound;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.NBTTool;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.registry.ModSounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Обработчик звука двигателя для обычных дронов (AddonDroneEntity).
 * Реализует систему, аналогичную Shahed: допплер, затухание по расстоянию и высоте.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class DroneSoundHandler {
    private static final Map<UUID, Controller> CONTROLLERS = new HashMap<>();

    private DroneSoundHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.isPaused()) {
            CONTROLLERS.values().forEach(Controller::stopHard);
            CONTROLLERS.clear();
            return;
        }

        final long now = mc.level.getGameTime();

        CONTROLLERS.values().forEach(c -> c.seenThisTick = false);

        for (final var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof AddonDroneEntity drone)) {
                continue;
            }
            CONTROLLERS.computeIfAbsent(drone.getUUID(), id -> new Controller())
                    .updateFromEntity(drone, now);
        }

        CONTROLLERS.entrySet().removeIf(e -> e.getValue().shouldRemove(now));
    }

    private static boolean isControlledByMonitor(AddonDroneEntity drone) {
        var mc = Minecraft.getInstance();
        var listener = mc.player;
        if (listener == null) return false;

        var stack = listener.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) return false;

        var tag = NBTTool.getTag(stack);
        if (!tag.getBoolean(com.atsuishio.superbwarfare.item.Monitor.LINKED)) return false;
        if (!tag.getBoolean("Using")) return false;

        return drone.getStringUUID().equals(tag.getString(com.atsuishio.superbwarfare.item.Monitor.LINKED_DRONE));
    }

    private static final class Controller {
        private static final long TRACKING_FADE_START_TICKS = 200;
        private static final long TRACKING_HARD_REMOVE_TICKS = 250;

        private DroneEngineLoopSoundInstance engine;

        private long lastSeenTick = -1;
        private boolean seenThisTick;

        private Vec3 prevDronePos = null;
        private Vec3 lastDroneVelocity = Vec3.ZERO;
        private Vec3 extrapolatedPos = null;

        private void ensureEngine(final Minecraft mc, double maxDistance) {
            if (engine != null && !engine.isStopped()) {
                return;
            }
            final SoundEvent sound = ModSounds.FPV_DRONE_ENGINE.get();
            engine = new DroneEngineLoopSoundInstance(sound, maxDistance);
            mc.getSoundManager().play(engine);
        }

        private void updateFromEntity(final AddonDroneEntity drone, final long nowTick) {
            final Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                return;
            }

            seenThisTick = true;
            lastSeenTick = nowTick;

            if (!drone.engineRunning()) {
                if (engine != null) {
                    engine.requestFadeOut();
                }
                prevDronePos = null;
                return;
            }

            // Не воспроизводим внешний звук, если игрок управляет дроном через монитор
            if (isControlledByMonitor(drone)) {
                if (engine != null) {
                    engine.requestFadeOut();
                }
                prevDronePos = null;
                return;
            }

            final Vec3 dronePos = drone.position();
            final var motion = drone.getDeltaMovement();
            final double speed = motion.horizontalDistance();

            Vec3 droneVelocity = motion;
            if (prevDronePos != null) {
                droneVelocity = dronePos.subtract(prevDronePos);
            }
            prevDronePos = dronePos;
            lastDroneVelocity = droneVelocity;
            extrapolatedPos = dronePos;

            // Мощность двигателя и фактор скорости
            final float power = Math.abs(drone.getEntityData().get(VehicleEntity.POWER));
            final float powerMix = Mth.clamp(power / 0.2f, 0.0f, 1.0f);
            final float speedFactor = (float) Mth.clamp(speed / 0.5, 0.0, 1.0);

            Vec3 playerPos = dronePos;
            if (mc.getCameraEntity() != null) {
                playerPos = mc.getCameraEntity().position();
            }
            final double distance = dronePos.distanceTo(playerPos);
            final double maxDist = ServerConfig.FPV_SOUND_MAX_DISTANCE.get();

            final float dopplerPitch = ShahedSoundEffects.computeDopplerPitch(dronePos, droneVelocity, playerPos);
            final float gainHF = ShahedSoundEffects.computeCombinedGainHF(distance, dronePos.y, playerPos.y, maxDist);

            ensureEngine(mc, maxDist);
            if (engine != null) {
                engine.update(
                        dronePos.x, dronePos.y, dronePos.z,
                        powerMix, speedFactor,
                        dopplerPitch, gainHF,
                        nowTick);
            }
        }

        private boolean shouldRemove(final long nowTick) {
            if (engine == null || engine.isStopped()) {
                return true;
            }

            if (!seenThisTick) {
                final long elapsed = lastSeenTick >= 0 ? nowTick - lastSeenTick : 0;
                if (elapsed > TRACKING_HARD_REMOVE_TICKS) {
                    stopHard();
                    return true;
                }
                extrapolateAndUpdate(nowTick, elapsed);
            }

            return false;
        }

        private void extrapolateAndUpdate(final long nowTick, final long elapsed) {
            if (extrapolatedPos == null) {
                if (engine != null && !engine.isStopped()) engine.keepAlive(nowTick);
                return;
            }

            extrapolatedPos = extrapolatedPos.add(lastDroneVelocity);

            final Minecraft mc = Minecraft.getInstance();
            Vec3 playerPos = extrapolatedPos;
            if (mc != null && mc.getCameraEntity() != null) {
                playerPos = mc.getCameraEntity().position();
            }

            final double distance = extrapolatedPos.distanceTo(playerPos);
            final double maxDist = ServerConfig.FPV_SOUND_MAX_DISTANCE.get();
            final float dopplerPitch = ShahedSoundEffects.computeDopplerPitch(
                    extrapolatedPos, lastDroneVelocity, playerPos);
            final float gainHF = ShahedSoundEffects.computeCombinedGainHF(
                    distance, extrapolatedPos.y, playerPos.y, maxDist);

            if (engine != null && !engine.isStopped()) {
                engine.extrapolate(extrapolatedPos.x, extrapolatedPos.y, extrapolatedPos.z,
                        dopplerPitch, gainHF, nowTick);
            }

            final double fadeDist = maxDist > 0 ? maxDist : 1500.0;
            if (distance > fadeDist || elapsed > TRACKING_FADE_START_TICKS) {
                if (engine != null) engine.requestFadeOut();
            }
        }

        private void stopHard() {
            if (engine != null) {
                engine.forceStop();
                engine = null;
            }
        }
    }
}
