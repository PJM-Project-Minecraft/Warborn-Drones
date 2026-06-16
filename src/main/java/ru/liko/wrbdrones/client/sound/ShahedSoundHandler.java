package ru.liko.wrbdrones.client.sound;

import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.entity.Shahed136Entity;
import ru.liko.wrbdrones.registry.ModSounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ShahedSoundHandler {
    private static final Map<UUID, Controller> CONTROLLERS = new HashMap<>();

    private ShahedSoundHandler() {
    }

    public static void onDroneExploded(final UUID droneId) {
        final Controller ctrl = CONTROLLERS.get(droneId);
        if (ctrl != null) {
            ctrl.markExploded();
        }
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
            if (!(entity instanceof Shahed136Entity shahed)) {
                continue;
            }
            CONTROLLERS.computeIfAbsent(shahed.getUUID(), id -> new Controller())
                    .updateFromEntity(shahed, now);
        }

        CONTROLLERS.entrySet().removeIf(e -> e.getValue().shouldRemove(now, e.getKey()));
    }

    private static final class Controller {
        private ShahedEngineLoopSoundInstance engine;
        private ShahedDiveLoopSoundInstance dive;

        private boolean seenThisTick;

        private Vec3 prevDronePos = null;
        private boolean exploded = false;

        private void ensureEngine(final Minecraft mc, double maxDistance) {
            if (engine != null && !engine.isStopped()) {
                return;
            }
            final SoundEvent sound = ModSounds.SHAHED136_ENGINE.get();
            engine = new ShahedEngineLoopSoundInstance(sound, maxDistance);
            mc.getSoundManager().play(engine);
        }

        private void ensureDive(final Minecraft mc, double maxDistance) {
            if (dive != null && !dive.isStopped()) {
                return;
            }
            final SoundEvent sound = ModSounds.SHAHED136_DIVE.get();
            dive = new ShahedDiveLoopSoundInstance(sound, maxDistance);
            mc.getSoundManager().play(dive);
        }

        private void updateFromEntity(final Shahed136Entity shahed, final long nowTick) {
            final Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                return;
            }

            seenThisTick = true;

            if (exploded) {
                stopHard();
                return;
            }

            // Страховка на случай discard без ShahedExplodePacket — обрываем звук мгновенно.
            if (shahed.isRemoved()) {
                exploded = true;
                stopHard();
                return;
            }

            if (!shahed.isLaunched()) {
                if (engine != null) {
                    engine.requestFadeOut();
                }
                if (dive != null) {
                    dive.requestFadeOut();
                }
                prevDronePos = null;
                return;
            }

            final Vec3 dronePos = shahed.position();
            final var motion = shahed.getDeltaMovement();
            final double speed = motion.length();

            Vec3 droneVelocity = motion;
            if (prevDronePos != null) {
                droneVelocity = dronePos.subtract(prevDronePos);
            }
            prevDronePos = dronePos;

            // Flight factors
            final float diveFactor = (float) Mth.clamp(-motion.y / 2.0, 0.0, 0.8);
            final float climbFactor = (float) Mth.clamp(motion.y / 1.8, 0.0, 1.0);
            final float speedFactor = (float) Mth.clamp(speed / 3.0, 0.0, 1.0);
            final float turnFactor = (float) Mth
                    .clamp(Math.abs(Mth.wrapDegrees(shahed.getYRot() - shahed.yRotO)) / 8.0f, 0.0, 1.0);
            final float engineMix = Mth.clamp(shahed.getCurrentSpeed() / 2.5f, 0.0f, 1.0f);

            // Listener position (camera entity for proper 3rd person support)
            Vec3 playerPos = dronePos;
            if (mc.getCameraEntity() != null) {
                playerPos = mc.getCameraEntity().position();
            }
            final double distance = dronePos.distanceTo(playerPos);
            final double maxDist = ServerConfig.SHAHED_SOUND_MAX_DISTANCE.get();

            // Compute EFX parameters
            final float dopplerPitch = ShahedSoundEffects.computeDopplerPitch(dronePos, droneVelocity, playerPos);
            final float gainHF = ShahedSoundEffects.computeCombinedGainHF(distance, dronePos.y, playerPos.y, maxDist);

            // Engine sound
            ensureEngine(mc, maxDist);
            if (engine != null) {
                engine.update(
                        dronePos.x, dronePos.y, dronePos.z,
                        engineMix,
                        diveFactor, climbFactor, speedFactor, turnFactor,
                        dopplerPitch, gainHF,
                        nowTick);
            }

            // Dive sound
            final boolean isDiving = motion.y < -0.5;
            if (isDiving) {
                ensureDive(mc, maxDist);
                final float diveIntensity = (float) Mth.clamp(-motion.y / 2.0, 0.0, 1.0);
                if (dive != null) {
                    dive.update(
                            dronePos.x, dronePos.y, dronePos.z,
                            diveIntensity, speedFactor,
                            dopplerPitch, gainHF,
                            nowTick);
                }
            } else {
                if (dive != null) {
                    dive.requestFadeOut();
                }
            }
        }

        private void markExploded() {
            this.exploded = true;
            stopHard();
        }

        private boolean shouldRemove(final long nowTick, final UUID droneUuid) {
            if (exploded) {
                stopHard();
                return true;
            }

            final boolean engineStopped = engine == null || engine.isStopped();
            final boolean diveStopped = dive == null || dive.isStopped();
            if (engineStopped && diveStopped) {
                return true;
            }

            // Дрон пропал из entitiesForRendering. Раньше тут шла экстраполяция позиции —
            // звук «висел» вне чанков до 25 с (фантомный двигатель там, где дрона уже нет,
            // расходящийся при манёврах и не глохнущий при взрыве вдали). Теперь, как в
            // DroneSoundHandler, ищем дрон в клиентском мире по UUID: есть и жив — обновляемся
            // от реальной позиции; нет (выгружен/вне трекинга/уничтожен) — глушим мгновенно.
            if (!seenThisTick) {
                final Minecraft mc = Minecraft.getInstance();
                final Entity entity = mc != null && mc.level != null
                        ? EntityFindUtil.findEntity(mc.level, droneUuid.toString())
                        : null;
                if (entity instanceof Shahed136Entity shahed && !shahed.isRemoved()) {
                    updateFromEntity(shahed, nowTick);
                    return false;
                }
                stopHard();
                return true;
            }

            return false;
        }

        private void stopHard() {
            if (engine != null) {
                engine.forceStop();
                engine = null;
            }
            if (dive != null) {
                dive.forceStop();
                dive = null;
            }
        }
    }
}
