package ru.liko.wrbdrones;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.liko.wrbdrones.network.ModNetworking;
import ru.liko.wrbdrones.registry.ModBlockEntities;
import ru.liko.wrbdrones.registry.ModBlocks;
import ru.liko.wrbdrones.registry.ModCreativeTabs;
import ru.liko.wrbdrones.registry.ModEntityTypes;
import ru.liko.wrbdrones.registry.ModItems;
import ru.liko.wrbdrones.registry.ModMenus;
import ru.liko.wrbdrones.registry.ModRecipes;
import ru.liko.wrbdrones.registry.ModSounds;

@Mod(Wrbdrones.MODID)
public class Wrbdrones {

    public static final String MODID = "wrbdrones";
    public static final Logger LOGGER = LoggerFactory.getLogger(Wrbdrones.class);

    public Wrbdrones(IEventBus bus, ModContainer container) {
        // Регистрация конфигурации
        container.registerConfig(ModConfig.Type.SERVER, Config.SERVER_SPEC);

        // Регистрация реестров
        ModBlocks.REGISTRY.register(bus);
        ModBlockEntities.REGISTRY.register(bus);
        ModItems.REGISTRY.register(bus);
        ModEntityTypes.REGISTRY.register(bus);
        ModMenus.REGISTRY.register(bus);
        ModRecipes.SERIALIZERS.register(bus);
        ModRecipes.TYPES.register(bus);
        ModCreativeTabs.REGISTRY.register(bus);
        ModSounds.REGISTRY.register(bus);

        // Подписка на события mod bus
        bus.addListener(this::commonSetup);
        bus.addListener(ModNetworking::register);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.debug("WRBDrones common setup initialised");
    }

    public static ResourceLocation loc(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public static ResourceLocation id(String path) {
        return loc(path);
    }
}
