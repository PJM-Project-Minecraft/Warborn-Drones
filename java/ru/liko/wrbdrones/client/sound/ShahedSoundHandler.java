package ru.liko.wrbdrones.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.Shahed136Entity;
import ru.liko.wrbdrones.registry.ModSounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT)
public final class ShahedSoundHandler {
    private static final Map<UUID, Controller> CONTROLLERS = new HashMap<>();

    private ShahedSoundHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.isPaused()) {
            CONTROLLERS.values().forEach(Controller::stopHard);
            CONTROLLERS.clear();
            return;
        }

        final long now = mc.level.getGameTime();

        // Помечаем всех как "не видели" в этом тике
        CONTROLLERS.values().forEach(c -> c.seenThisTick = false);

        // Обновляем те сущности, которые сейчас реально прогружены клиентом
        for (final var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Shahed136Entity shahed)) {
                continue;
            }
            CONTROLLERS.computeIfAbsent(shahed.getUUID(), id -> new Controller())
                .updateFromEntity(shahed, now);
        }

        // Те, кого не видели - не удаляем сразу. Даём звуку затухнуть по TIMEOUT в самих инстансах.
        CONTROLLERS.entrySet().removeIf(e -> e.getValue().shouldRemove(now));
    }

    private static final class Controller {
        private static final long HARD_REMOVE_TICKS = 200;

        private ShahedEngineLoopSoundInstance engine;
        private ShahedDiveLoopSoundInstance dive;

        private long lastSeenTick = -1;
        private boolean seenThisTick;

        private void ensureEngine(final Minecraft mc) {
            if (engine != null && !engine.isStopped()) {
                return;
            }
            final SoundEvent sound = ModSounds.SHAHED136_ENGINE.get();
            engine = new ShahedEngineLoopSoundInstance(sound);
            mc.getSoundManager().play(engine);
        }

        private void ensureDive(final Minecraft mc) {
            if (dive != null && !dive.isStopped()) {
                return;
            }
            final SoundEvent sound = ModSounds.SHAHED136_DIVE.get();
            dive = new ShahedDiveLoopSoundInstance(sound);
            mc.getSoundManager().play(dive);
        }

        private void updateFromEntity(final Shahed136Entity shahed, final long nowTick) {
            final Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null) {
                return;
            }

            seenThisTick = true;
            lastSeenTick = nowTick;

            // Если дрон не запущен - просим плавно затухнуть
            if (!shahed.isLaunched()) {
                if (engine != null) {
                    engine.requestFadeOut();
                }
                if (dive != null) {
                    dive.requestFadeOut();
                }
                return;
            }

            // Рассчитываем факторы движения (без сложной физики, но стабильно)
            final var motion = shahed.getDeltaMovement();
            final double speed = motion.length();

            final float diveFactor = (float) Mth.clamp(-motion.y / 2.0, 0.0, 0.8);
            final float climbFactor = (float) Mth.clamp(motion.y / 1.8, 0.0, 1.0);
            final float speedFactor = (float) Mth.clamp(speed / 3.0, 0.0, 1.0);
            final float turnFactor = (float) Mth.clamp(Math.abs(Mth.wrapDegrees(shahed.getYRot() - shahed.yRotO)) / 8.0f, 0.0, 1.0);

            // Условная "тяга" - по текущей скорости (0..1)
            final float engineMix = Mth.clamp(shahed.getCurrentSpeed() / 2.5f, 0.0f, 1.0f);

            ensureEngine(mc);
            if (engine != null) {
                engine.update(
                    shahed.getX(),
                    shahed.getY(),
                    shahed.getZ(),
                    engineMix,
                    diveFactor,
                    climbFactor,
                    speedFactor,
                    turnFactor,
                    nowTick
                );
            }

            final boolean isDiving = motion.y < -0.5;
            if (isDiving) {
                ensureDive(mc);
                final float diveIntensity = (float) Mth.clamp(-motion.y / 2.0, 0.0, 1.0);
                if (dive != null) {
                    dive.update(
                        shahed.getX(),
                        shahed.getY(),
                        shahed.getZ(),
                        diveIntensity,
                        speedFactor,
                        nowTick
                    );
                }
            } else {
                if (dive != null) {
                    dive.requestFadeOut();
                }
            }
        }

        private boolean shouldRemove(final long nowTick) {
            final boolean engineStopped = engine == null || engine.isStopped();
            final boolean diveStopped = dive == null || dive.isStopped();

            // если оба звука реально остановились - можно удалить
            if (engineStopped && diveStopped) {
                return true;
            }

            // если сущность давно не видели - форс удаление (на случай багов)
            if (!seenThisTick && lastSeenTick >= 0 && nowTick - lastSeenTick > HARD_REMOVE_TICKS) {
                stopHard();
                return true;
            }

            // если в этом тике не видели - удаляем и стопаем звук моментально
            if (!seenThisTick) {
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
