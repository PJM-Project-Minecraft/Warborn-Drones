package ru.liko.wrbdrones.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.item.AddonDroneItem;
import ru.liko.wrbdrones.item.RadioItem;
import ru.liko.wrbdrones.item.RebItem;
import ru.liko.wrbdrones.item.RebMiniItem;
import ru.liko.wrbdrones.item.Shahed136Item;

public final class ModItems {
        private ModItems() {
        }

        public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(BuiltInRegistries.ITEM,
                        Wrbdrones.MODID);

        public static final DeferredHolder<Item, Item> MAVIC_DRONE_WITH_DROP = REGISTRY.register(
                        "mavic_drone_with_drop",
                        () -> new AddonDroneItem(new Item.Properties().stacksTo(1),
                                        () -> AddonDroneItem.fromType(ModEntityTypes.MAVIC_DRONE_WITH_DROP),
                                        null));

        public static final DeferredHolder<Item, Item> MAVIC_DRONE_NO_DROP = REGISTRY.register("mavic_drone_no_drop",
                        () -> new AddonDroneItem(new Item.Properties().stacksTo(1),
                                        () -> AddonDroneItem.fromType(ModEntityTypes.MAVIC_DRONE_NO_DROP),
                                        null));

        public static final DeferredHolder<Item, Item> FPV_DRONE = REGISTRY.register("fpv_drone",
                        () -> new AddonDroneItem(new Item.Properties().stacksTo(1),
                                        () -> AddonDroneItem.fromType(ModEntityTypes.FPV_DRONE),
                                        null));

        public static final DeferredHolder<Item, Item> REB = REGISTRY.register("reb",
                        () -> new RebItem(new Item.Properties().stacksTo(16)));

        public static final DeferredHolder<Item, Item> REB_MINI = REGISTRY.register("reb_mini",
                        () -> new RebMiniItem(new Item.Properties().stacksTo(16)));

        public static final DeferredHolder<Item, Item> SHAHED136 = REGISTRY.register("shahed136",
                        () -> new Shahed136Item(new Item.Properties().stacksTo(1)));

        public static final DeferredHolder<Item, Item> RADIO = REGISTRY.register("radio",
                        () -> new RadioItem(new Item.Properties().stacksTo(1)));
}

