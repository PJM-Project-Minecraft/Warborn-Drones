package ru.liko.wrbdrones.client.sound;

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

        CONTROLLERS.entrySet().removeIf(e -> e.getValue().shouldRemove(now));
    }

    private static final class Controller {
        private static final long EXPLOSION_HARD_REMOVE_TICKS = 80;
        private static final long TRACKING_FADE_START_TICKS = 400;
        private static final long TRACKING_HARD_REMOVE_TICKS = 500;

        private ShahedEngineLoopSoundInstance engine;
        private ShahedDiveLoopSoundInstance dive;

        private long lastSeenTick = -1;
        private boolean seenThisTick;

        private Vec3 prevDronePos = null;
        private Vec3 lastDroneVelocity = Vec3.ZERO;
        private Vec3 extrapolatedPos = null;
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
            lastSeenTick = nowTick;

            if (exploded) {
                if (engine != null) engine.requestFadeOut();
                if (dive != null) dive.requestFadeOut();
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
            lastDroneVelocity = droneVelocity;
            extrapolatedPos = dronePos;

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

        private boolean shouldRemove(final long nowTick) {
            final boolean engineStopped = engine == null || engine.isStopped();
            final boolean diveStopped = dive == null || dive.isStopped();

            if (engineStopped && diveStopped) {
                return true;
            }

            if (exploded) {
                if (engine != null && !engine.isStopped()) engine.requestFadeOut();
                if (dive != null && !dive.isStopped()) dive.requestFadeOut();
                final long elapsed = lastSeenTick >= 0 ? nowTick - lastSeenTick : 0;
                if (elapsed > EXPLOSION_HARD_REMOVE_TICKS) {
                    stopHard();
                    return true;
                }
                return false;
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
                if (dive != null && !dive.isStopped()) dive.keepAlive(nowTick);
                return;
            }

            extrapolatedPos = extrapolatedPos.add(lastDroneVelocity);

            final Minecraft mc = Minecraft.getInstance();
            Vec3 playerPos = extrapolatedPos;
            if (mc != null && mc.getCameraEntity() != null) {
                playerPos = mc.getCameraEntity().position();
            }

            final double distance = extrapolatedPos.distanceTo(playerPos);
            final double maxDist = ServerConfig.SHAHED_SOUND_MAX_DISTANCE.get();
            final float dopplerPitch = ShahedSoundEffects.computeDopplerPitch(
                    extrapolatedPos, lastDroneVelocity, playerPos);
            final float gainHF = ShahedSoundEffects.computeCombinedGainHF(
                    distance, extrapolatedPos.y, playerPos.y, maxDist);

            if (engine != null && !engine.isStopped()) {
                engine.extrapolate(extrapolatedPos.x, extrapolatedPos.y, extrapolatedPos.z,
                        dopplerPitch, gainHF, nowTick);
            }
            if (dive != null && !dive.isStopped()) {
                dive.extrapolate(extrapolatedPos.x, extrapolatedPos.y, extrapolatedPos.z,
                        dopplerPitch, gainHF, nowTick);
            }

            final double fadeDist = maxDist > 0 ? maxDist : 3000.0;
            if (distance > fadeDist || elapsed > TRACKING_FADE_START_TICKS) {
                if (engine != null) engine.requestFadeOut();
                if (dive != null) dive.requestFadeOut();
            }
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
