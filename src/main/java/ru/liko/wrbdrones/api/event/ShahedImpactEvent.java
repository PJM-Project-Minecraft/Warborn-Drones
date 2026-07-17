package ru.liko.wrbdrones.api.event;

import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import ru.liko.wrbdrones.entity.Shahed136Entity;

/**
 * Постится на NeoForge.EVENT_BUS (server-side), когда Shahed-136 детонировал,
 * достигнув отмеченной цели в терминальной фазе (контактный или неконтактный
 * взрыватель в {@link Shahed136Entity#checkTerminalDetonation}). Это сигнал
 * «дрон долетел до цели и подорвался».
 *
 * <p>НЕ постится при сбитии уроном (см. {@link ShahedShotDownEvent}), столкновении
 * с рельефом вдали от цели или failsafe — только штатный подрыв по цели.</p>
 */
public class ShahedImpactEvent extends Event {

    private final Shahed136Entity drone;
    private final Vec3 targetPos;
    private final Vec3 impactPos;

    public ShahedImpactEvent(Shahed136Entity drone, Vec3 targetPos, Vec3 impactPos) {
        this.drone = drone;
        this.targetPos = targetPos;
        this.impactPos = impactPos;
    }

    public Shahed136Entity getDrone() {
        return drone;
    }

    /** Отмеченная цель дрона (setTargetPos). */
    public Vec3 getTargetPos() {
        return targetPos;
    }

    /** Фактическая точка подрыва (позиция дрона в момент детонации). */
    public Vec3 getImpactPos() {
        return impactPos;
    }
}
