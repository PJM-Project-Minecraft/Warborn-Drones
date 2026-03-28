package ru.liko.wrbdrones.entity;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import ru.liko.wrbdrones.Wrbdrones;

import java.util.Set;

/**
 * FPV дрон - только для боевых боеприпасов
 * Разрешены: RPG ракеты (standard, tbg) и минометные снаряды
 */
public class FpvDroneEntity extends AddonDroneEntity {

    private static final Set<String> ALLOWED_ATTACHMENTS = Set.of(
        "superbwarfare:rpg_rocket_standard",
        "superbwarfare:rpg_rocket_tbg",
        "superbwarfare:mortar_shell"
    );

    public FpvDroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public ResourceLocation getModelResource() {
        return Wrbdrones.id("geo/fpv_drone.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource() {
        return Wrbdrones.id("textures/entity/fpv_drone.png");
    }

    @Override
    protected Set<String> getAllowedAttachments() {
        return ALLOWED_ATTACHMENTS;
    }
}
