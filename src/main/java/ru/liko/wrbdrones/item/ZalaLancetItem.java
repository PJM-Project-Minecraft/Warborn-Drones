package ru.liko.wrbdrones.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import ru.liko.wrbdrones.entity.LancetLaunchPlatformEntity;
import ru.liko.wrbdrones.registry.ModEntityTypes;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public class ZalaLancetItem extends AddonDroneItem {
    public ZalaLancetItem(Item.Properties properties) {
        super(properties, () -> AddonDroneItem.fromType(ModEntityTypes.ZALA_LANCET), null);
    }

    public static InteractionResult placeOnPlatform(Player player, ItemStack stack, LancetLaunchPlatformEntity platform) {
        return platform.loadLancet(player, stack);
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        if (context.getLevel() instanceof ServerLevel serverLevel && context.getPlayer() != null
                && !context.getPlayer().isShiftKeyDown()) {
            LancetLaunchPlatformEntity platform = findNearbyPlatform(serverLevel, context.getClickedPos());
            if (platform != null) {
                return platform.loadLancet(context.getPlayer(), context.getItemInHand());
            }
        }

        return super.useOn(context);
    }

    private static LancetLaunchPlatformEntity findNearbyPlatform(ServerLevel level, BlockPos clickedPos) {
        AABB searchBox = new AABB(clickedPos).inflate(4.0, 3.0, 4.0);
        return level.getEntitiesOfClass(LancetLaunchPlatformEntity.class, searchBox).stream()
                .min(Comparator.comparingDouble(platform -> platform.distanceToSqr(
                        clickedPos.getX() + 0.5, clickedPos.getY() + 0.5, clickedPos.getZ() + 0.5)))
                .orElse(null);
    }
}
