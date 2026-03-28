package ru.liko.wrbdrones;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.liko.wrbdrones.Config;
import ru.liko.wrbdrones.entity.PlayerDecoyEntity;
import ru.liko.wrbdrones.event.DroneControlDamageHandler;
import ru.liko.wrbdrones.network.ModNetworking;
import ru.liko.wrbdrones.registry.ModCreativeTabs;
import ru.liko.wrbdrones.registry.ModEntityTypes;
import ru.liko.wrbdrones.registry.ModItems;
import ru.liko.wrbdrones.registry.ModSounds;
import software.bernie.geckolib.GeckoLib;

@Mod(Wrbdrones.MODID)
public class Wrbdrones {

    public static final String MODID = "wrbdrones";
    public static final Logger LOGGER = LoggerFactory.getLogger(Wrbdrones.class);

    @SuppressWarnings("deprecation")
    public Wrbdrones() {
        GeckoLib.initialize();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(modEventBus);
        ModEntityTypes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModSounds.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerEntityAttributes);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(DroneControlDamageHandler.class);

        Config.register();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.debug("WRBDrones common setup initialised");
        event.enqueueWork(ModNetworking::init);
    }

    private void registerEntityAttributes(final EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.PLAYER_DECOY.get(), PlayerDecoyEntity.createAttributes().build());
    }

    @SuppressWarnings("deprecation")
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
