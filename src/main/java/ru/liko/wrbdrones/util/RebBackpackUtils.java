package ru.liko.wrbdrones.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Доступ к рюкзаку РЭБ из мода Warborn-Renewed через Curios.
 * Вынесено в отдельный класс, чтобы классы Curios не грузились, когда мода нет —
 * вызывать только под проверкой ModList (см. {@link RebUtils}).
 */
final class RebBackpackUtils {

    /**
     * Компонент есть только на рюкзаках РЭБ Warborn-Renewed, поэтому список id предметов не нужен.
     */
    private static final ResourceLocation REB_ENABLED = ResourceLocation.fromNamespaceAndPath("warbornrenewed",
            "reb_enabled");

    private RebBackpackUtils() {
    }

    /** true, если у игрока в слоте "back" надет включённый рюкзак РЭБ. */
    static boolean hasActiveBackpack(Player player) {
        var componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(REB_ENABLED);
        if (componentType == null)
            return false;

        var inventory = CuriosApi.getCuriosInventory(player).orElse(null);
        if (inventory == null)
            return false;

        var handler = inventory.getStacksHandler("back").orElse(null);
        if (handler == null)
            return false;

        var stacks = handler.getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            ItemStack stack = stacks.getStackInSlot(i);
            if (!stack.isEmpty() && Boolean.TRUE.equals(stack.get(componentType))) {
                return true;
            }
        }
        return false;
    }
}
