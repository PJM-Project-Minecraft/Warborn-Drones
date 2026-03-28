package ru.liko.wrbdrones.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig {

    // FPV Drone конфигурация
    public static ForgeConfigSpec.BooleanValue FPV_ENABLE_NOISE_EFFECT;
    public static ForgeConfigSpec.DoubleValue FPV_NOISE_MIN_DISTANCE;
    public static ForgeConfigSpec.DoubleValue FPV_NOISE_MAX_DISTANCE;
    public static ForgeConfigSpec.DoubleValue FPV_NOISE_INTENSITY_MULTIPLIER;
    public static ForgeConfigSpec.DoubleValue FPV_NOISE_ANIMATION_SPEED;
    public static ForgeConfigSpec.DoubleValue FPV_NOISE_CHANGE_SPEED;
    public static ForgeConfigSpec.DoubleValue FPV_NOISE_SCALE;
    public static ForgeConfigSpec.IntValue FPV_NOISE_TILE_SIZE;
    public static ForgeConfigSpec.DoubleValue FPV_MAX_DISTANCE;

    // Mavic Drone конфигурация
    public static ForgeConfigSpec.DoubleValue MAVIC_MAX_DISTANCE;
    public static ForgeConfigSpec.DoubleValue MAVIC_SIGNAL_LOSS_DISTANCE;

    // REB конфигурация
    public static ForgeConfigSpec.DoubleValue REB_RADIUS;
    public static ForgeConfigSpec.DoubleValue REB_MINI_RADIUS;

    // Shahed 136 конфигурация
    public static ForgeConfigSpec.DoubleValue SHAHED136_MAX_DISTANCE;
    public static ForgeConfigSpec.DoubleValue SHAHED136_EXPLOSION_DAMAGE;
    public static ForgeConfigSpec.DoubleValue SHAHED136_EXPLOSION_RADIUS;
    public static ForgeConfigSpec.DoubleValue SHAHED136_MIN_SPEED_KMH;
    public static ForgeConfigSpec.DoubleValue SHAHED136_MAX_SPEED_KMH;
    public static ForgeConfigSpec.DoubleValue SHAHED136_MIN_ALTITUDE;
    public static ForgeConfigSpec.DoubleValue SHAHED136_MAX_ALTITUDE;
    public static ForgeConfigSpec.DoubleValue SHAHED136_CRUISE_ALTITUDE;

    public static void init(ForgeConfigSpec.Builder builder) {
        builder.push("fpv_drone");

        builder.comment("Enable noise interference effect based on FPV drone distance");
        FPV_ENABLE_NOISE_EFFECT = builder.define("enable_noise_effect", true);

        builder.comment("Minimum distance (blocks) where noise starts appearing for FPV drone");
        FPV_NOISE_MIN_DISTANCE = builder.defineInRange("noise_min_distance", 20.0, 0.0, 1000.0);

        builder.comment("Maximum distance (blocks) where noise reaches full intensity for FPV drone");
        FPV_NOISE_MAX_DISTANCE = builder.defineInRange("noise_max_distance", 100.0, 0.0, 1000.0);

        builder.comment("Multiplier for noise intensity for FPV drone (0.0 - 2.0)");
        FPV_NOISE_INTENSITY_MULTIPLIER = builder.defineInRange("noise_intensity_multiplier", 1.0, 0.0, 2.0);

        builder.comment("Animation speed for noise effect for FPV drone (higher = faster)");
        FPV_NOISE_ANIMATION_SPEED = builder.defineInRange("noise_animation_speed", 50.0, 1.0, 500.0);

        builder.comment("Speed of noise pattern change for FPV drone (higher = faster pattern change, 0.1 - 10.0)");
        FPV_NOISE_CHANGE_SPEED = builder.defineInRange("noise_change_speed", 0.5, 0.1, 10.0);

        builder.comment("Scale of noise pattern for FPV drone (higher = finer grain, 0.5 - 5.0)");
        FPV_NOISE_SCALE = builder.defineInRange("noise_scale", 1.0, 0.5, 5.0);

        builder.comment("Tile size for noise texture for FPV drone (pixels)");
        FPV_NOISE_TILE_SIZE = builder.defineInRange("noise_tile_size", 64, 16, 256);

        builder.comment("Maximum distance (blocks) for FPV drone signal loss");
        FPV_MAX_DISTANCE = builder.defineInRange("max_distance", 150.0, 50.0, 500.0);

        builder.pop();

        builder.push("mavic_drone");

        builder.comment("Maximum distance (blocks) for Mavic drone signal loss");
        MAVIC_MAX_DISTANCE = builder.defineInRange("max_distance", 200.0, 50.0, 500.0);

        builder.comment("Distance (blocks) where Mavic drone starts losing signal");
        MAVIC_SIGNAL_LOSS_DISTANCE = builder.defineInRange("signal_loss_distance", 150.0, 50.0, 500.0);

        builder.pop();

        builder.push("reb");

        builder.comment("Radius of large REB (Radio Electronic Warfare) interference effect in blocks");
        REB_RADIUS = builder.defineInRange("reb_radius", 100.0, 10.0, 500.0);

        builder.comment("Radius of mini REB (Radio Electronic Warfare) interference effect in blocks");
        REB_MINI_RADIUS = builder.defineInRange("reb_mini_radius", 50.0, 5.0, 100.0);

        builder.pop();

        builder.push("shahed136");

        builder.comment("Maximum flight distance (blocks) for Shahed 136 from spawn point");
        SHAHED136_MAX_DISTANCE = builder.defineInRange("max_distance", 5000.0, 500.0, 30000.0);

        builder.comment("Explosion damage for Shahed 136");
        SHAHED136_EXPLOSION_DAMAGE = builder.defineInRange("explosion_damage", 300.0, 10.0, 1000.0);

        builder.comment("Explosion radius (blocks) for Shahed 136");
        SHAHED136_EXPLOSION_RADIUS = builder.defineInRange("explosion_radius", 15.0, 2.0, 30.0);

        builder.comment("Minimum speed (km/h) for Shahed 136 input validation");
        SHAHED136_MIN_SPEED_KMH = builder.defineInRange("min_speed_kmh", 150.0, 50.0, 500.0);

        builder.comment("Maximum speed (km/h) for Shahed 136 input validation");
        SHAHED136_MAX_SPEED_KMH = builder.defineInRange("max_speed_kmh", 200.0, 100.0, 1000.0);

        builder.comment("Minimum altitude (blocks) for Shahed 136 input validation");
        SHAHED136_MIN_ALTITUDE = builder.defineInRange("min_altitude", -100.0, -200.0, 320.0);

        builder.comment("Maximum altitude (blocks) for Shahed 136 input validation");
        SHAHED136_MAX_ALTITUDE = builder.defineInRange("max_altitude", 1000.0, 100.0, 5000.0);

        builder.comment("Default Cruise altitude (blocks above target) for Shahed 136");
        SHAHED136_CRUISE_ALTITUDE = builder.defineInRange("cruise_altitude", 80.0, -200.0, 5000.0);

        builder.pop();
    }
}

