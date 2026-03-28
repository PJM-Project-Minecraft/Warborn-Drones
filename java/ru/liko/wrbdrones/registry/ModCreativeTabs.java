package ru.liko.wrbdrones.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import ru.liko.wrbdrones.Wrbdrones;

public final class ModCreativeTabs {
    private ModCreativeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Wrbdrones.MODID);

    public static final RegistryObject<CreativeModeTab> WRB_DRONES = CREATIVE_TABS.register("wrb_drones",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wrbdrones"))
                    .icon(() -> new ItemStack(ModItems.MAVIC_DRONE_WITH_DROP.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MAVIC_DRONE_WITH_DROP.get());
                        output.accept(ModItems.MAVIC_DRONE_NO_DROP.get());
                        output.accept(ModItems.FPV_DRONE.get());
                        output.accept(ModItems.SHAHED136.get());
                        output.accept(ModItems.RADIO.get());
                        output.accept(ModItems.REB.get());
                        output.accept(ModItems.REB_MINI.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_TABS.register(eventBus);
    }
}
