package ru.liko.wrbdrones.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.entity.Shahed136Entity;
import ru.liko.wrbdrones.registry.ModEntityTypes;

public class Shahed136Item extends Item {

    public Shahed136Item(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        
        if (!level.isClientSide()) {
            BlockPos pos = context.getClickedPos();
            Vec3 spawnPos = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            
            Shahed136Entity shahed = new Shahed136Entity(ModEntityTypes.SHAHED136.get(), level);
            shahed.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            
            if (context.getPlayer() != null) {
                shahed.setYRot(context.getPlayer().getYRot());
                shahed.setOwnerUUID(context.getPlayer().getUUID());
            }
            
            level.addFreshEntity(shahed);
            
            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
