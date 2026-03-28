package ru.liko.wrbdrones;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import ru.liko.wrbdrones.config.ServerConfig;

@Mod.EventBusSubscriber(modid = Wrbdrones.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    private Config() {
    }

    private static final ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();
    
    static {
        ServerConfig.init(SERVER_BUILDER);
    }

    public static final ForgeConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
    }
}
