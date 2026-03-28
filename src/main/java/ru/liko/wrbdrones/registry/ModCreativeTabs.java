package ru.liko.wrbdrones.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.wrbdrones.Wrbdrones;

public final class ModCreativeTabs {
    private ModCreativeTabs() {
    }

    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, Wrbdrones.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WRB_DRONES = REGISTRY.register("wrb_drones",
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
}
