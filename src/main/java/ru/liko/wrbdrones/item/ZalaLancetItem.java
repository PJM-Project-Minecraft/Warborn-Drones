package ru.liko.wrbdrones.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import ru.liko.wrbdrones.entity.LancetLaunchPlatformEntity;
import ru.liko.wrbdrones.registry.ModEntityTypes;

public class ZalaLancetItem extends AddonDroneItem {
    public ZalaLancetItem(Item.Properties properties) {
        super(properties, () -> AddonDroneItem.fromType(ModEntityTypes.ZALA_LANCET), null);
    }

    /** Загрузка на рельс идёт через клик по самой платформе — {@link LancetLaunchPlatformEntity#interact}. */
    public static InteractionResult placeOnPlatform(Player player, ItemStack stack, LancetLaunchPlatformEntity platform) {
        return platform.loadLancet(player, stack);
    }
}
