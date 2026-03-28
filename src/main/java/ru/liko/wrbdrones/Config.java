package ru.liko.wrbdrones;

import net.neoforged.neoforge.common.ModConfigSpec;
import ru.liko.wrbdrones.config.ServerConfig;

public final class Config {
    private Config() {
    }

    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    static {
        ServerConfig.init(SERVER_BUILDER);
    }

    public static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();
}
