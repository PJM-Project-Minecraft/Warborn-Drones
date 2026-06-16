package ru.liko.wrbdrones.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.menu.DroneAssemblyMenu;

public final class ModMenus {
    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(BuiltInRegistries.MENU,
            Wrbdrones.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<DroneAssemblyMenu>> DRONE_ASSEMBLY = REGISTRY
            .register("drone_assembly", () -> IMenuTypeExtension.create(DroneAssemblyMenu::client));
}
