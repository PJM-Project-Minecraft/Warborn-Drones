package ru.liko.wrbdrones.api.event;

import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.Event;
import ru.liko.wrbdrones.entity.Shahed136Entity;

/**
 * Постится на NeoForge.EVENT_BUS (server-side), когда Shahed-136 сбит уроном
 * (HP упало до нуля в hurt()). НЕ постится при штатном подрыве о цель,
 * столкновении или failsafe — только «сбит».
 */
public class ShahedShotDownEvent extends Event {

    private final Shahed136Entity drone;
    private final DamageSource source;

    public ShahedShotDownEvent(Shahed136Entity drone, DamageSource source) {
        this.drone = drone;
        this.source = source;
    }

    public Shahed136Entity getDrone() {
        return drone;
    }

    public DamageSource getSource() {
        return source;
    }
}
