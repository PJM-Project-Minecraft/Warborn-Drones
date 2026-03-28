package ru.liko.wrbdrones.entity;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import ru.liko.wrbdrones.Wrbdrones;

import java.util.Set;

/**
 * Mavic дрон без модуля сброса - только для разведки
 * Не может сбрасывать предметы, только наблюдение
 * Не может использовать боеприпасы
 */
public class MavicDroneNoDropEntity extends AddonDroneEntity {

    private static final Set<String> ALLOWED_ATTACHMENTS = Set.of(); // Пустой набор - не может использовать боеприпасы

    public MavicDroneNoDropEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public ResourceLocation getModelResource() {
        return Wrbdrones.id("geo/mavic_drone_no_drop.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource() {
        return Wrbdrones.id("textures/entity/mavic_drone.png");
    }

    @Override
    protected Set<String> getAllowedAttachments() {
        return ALLOWED_ATTACHMENTS;
    }

    @Override
    public void baseTick() {
        // Сохраняем значение fire перед вызовом super.baseTick()
        boolean hadFire = this.fire;
        
        // Временно отключаем fire, чтобы предотвратить сброс в super.baseTick()
        // Это предотвратит вызов droneDrop() в DroneEntity.baseTick()
        this.fire = false;
        
        // Вызываем базовый метод
        super.baseTick();
        
        // Если fire был установлен, просто сбрасываем его
        // AMMO не будет уменьшено, так как fire был отключен перед вызовом super.baseTick()
        if (hadFire) {
            this.fire = false;
        }
    }
}

