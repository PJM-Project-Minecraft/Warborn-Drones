package ru.liko.wrbdrones.registry;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.item.AddonDroneItem;
import ru.liko.wrbdrones.item.RadioItem;
import ru.liko.wrbdrones.item.RebItem;
import ru.liko.wrbdrones.item.RebMiniItem;
import ru.liko.wrbdrones.item.Shahed136Item;

public final class ModItems {
    private ModItems() {
    }

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Wrbdrones.MODID);

    public static final RegistryObject<Item> MAVIC_DRONE_WITH_DROP = ITEMS.register("mavic_drone_with_drop",
            () -> new AddonDroneItem(new Item.Properties().stacksTo(1),
                    () -> AddonDroneItem.fromType(ModEntityTypes.MAVIC_DRONE_WITH_DROP),
                    null));

    public static final RegistryObject<Item> MAVIC_DRONE_NO_DROP = ITEMS.register("mavic_drone_no_drop",
            () -> new AddonDroneItem(new Item.Properties().stacksTo(1),
                    () -> AddonDroneItem.fromType(ModEntityTypes.MAVIC_DRONE_NO_DROP),
                    null));

    public static final RegistryObject<Item> FPV_DRONE = ITEMS.register("fpv_drone",
            () -> new AddonDroneItem(new Item.Properties().stacksTo(1),
                    () -> AddonDroneItem.fromType(ModEntityTypes.FPV_DRONE),
                    null));

    public static final RegistryObject<Item> REB = ITEMS.register("reb",
            () -> new RebItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> REB_MINI = ITEMS.register("reb_mini",
            () -> new RebMiniItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> SHAHED136 = ITEMS.register("shahed136",
            () -> new Shahed136Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> RADIO = ITEMS.register("radio",
            () -> new RadioItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
