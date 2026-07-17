package ru.liko.wrbdrones.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class ServerConfig {

    // FPV Drone конфигурация
    public static ModConfigSpec.BooleanValue FPV_ENABLE_NOISE_EFFECT;
    public static ModConfigSpec.DoubleValue FPV_NOISE_MIN_DISTANCE;
    public static ModConfigSpec.DoubleValue FPV_NOISE_MAX_DISTANCE;
    public static ModConfigSpec.DoubleValue FPV_NOISE_INTENSITY_MULTIPLIER;
    public static ModConfigSpec.DoubleValue FPV_NOISE_ANIMATION_SPEED;
    public static ModConfigSpec.DoubleValue FPV_NOISE_CHANGE_SPEED;
    public static ModConfigSpec.DoubleValue FPV_NOISE_SCALE;
    public static ModConfigSpec.IntValue FPV_NOISE_TILE_SIZE;
    public static ModConfigSpec.DoubleValue FPV_MAX_DISTANCE;

    // Mavic Drone конфигурация
    public static ModConfigSpec.DoubleValue MAVIC_MAX_DISTANCE;
    public static ModConfigSpec.DoubleValue MAVIC_SIGNAL_LOSS_DISTANCE;

    // ZALA Lancet конфигурация
    public static ModConfigSpec.DoubleValue LANCET_MAX_DISTANCE;
    public static ModConfigSpec.DoubleValue LANCET_STALL_SPEED;
    public static ModConfigSpec.DoubleValue LANCET_CRUISE_SPEED;
    public static ModConfigSpec.DoubleValue LANCET_MAX_SPEED;
    public static ModConfigSpec.DoubleValue LANCET_DIVE_MAX_SPEED;
    public static ModConfigSpec.DoubleValue LANCET_LOITER_RADIUS;
    public static ModConfigSpec.DoubleValue LANCET_LOITER_ALTITUDE;
    public static ModConfigSpec.DoubleValue LANCET_EXPLOSION_DAMAGE;
    public static ModConfigSpec.DoubleValue LANCET_EXPLOSION_RADIUS;

    // REB конфигурация
    public static ModConfigSpec.DoubleValue REB_RADIUS;
    public static ModConfigSpec.DoubleValue REB_MINI_RADIUS;
    public static ModConfigSpec.DoubleValue REB_BACKPACK_RADIUS;
    public static ModConfigSpec.DoubleValue REB_JAMMING_CURVE_EXPONENT;
    public static ModConfigSpec.DoubleValue REB_JAMMING_MULTIPLIER;

    // Продвинутая модель сигнала (стены + высота)
    public static ModConfigSpec.BooleanValue SIGNAL_LOS_ENABLED;
    public static ModConfigSpec.DoubleValue WALL_HARD_PER_BLOCK;
    public static ModConfigSpec.DoubleValue WALL_SOFT_PER_BLOCK;
    public static ModConfigSpec.DoubleValue WALL_MAX_TOTAL_ATTENUATION;
    public static ModConfigSpec.IntValue WALL_MAX_RAY_BLOCKS;
    public static ModConfigSpec.IntValue SIGNAL_CACHE_TTL_TICKS;
    public static ModConfigSpec.BooleanValue SIGNAL_ALTITUDE_ENABLED;
    public static ModConfigSpec.DoubleValue ALT_PENALTY_FLOOR;
    public static ModConfigSpec.DoubleValue ALT_BONUS_CEIL;
    public static ModConfigSpec.DoubleValue ALT_MIN_MULTIPLIER;
    public static ModConfigSpec.BooleanValue SIGNAL_SERVER_CUTOFF_ENABLED;
    public static ModConfigSpec.IntValue SIGNAL_SERVER_CHECK_INTERVAL_TICKS;
    public static ModConfigSpec.DoubleValue SIGNAL_SERVER_CUTOFF_THRESHOLD;
    public static ModConfigSpec.BooleanValue SIGNAL_DESTROY_ON_ZERO_ENABLED;
    public static ModConfigSpec.DoubleValue SIGNAL_DESTROY_THRESHOLD;

    // Shahed 136 конфигурация
    public static ModConfigSpec.DoubleValue SHAHED136_MAX_DISTANCE;
    public static ModConfigSpec.DoubleValue SHAHED136_EXPLOSION_DAMAGE;
    public static ModConfigSpec.DoubleValue SHAHED136_EXPLOSION_RADIUS;
    public static ModConfigSpec.DoubleValue SHAHED136_MIN_SPEED_KMH;
    public static ModConfigSpec.DoubleValue SHAHED136_MAX_SPEED_KMH;
    public static ModConfigSpec.DoubleValue SHAHED136_MIN_ALTITUDE;
    public static ModConfigSpec.DoubleValue SHAHED136_MAX_ALTITUDE;
    public static ModConfigSpec.DoubleValue SHAHED136_CRUISE_ALTITUDE;

    // Автопилот: пропорциональное наведение, путевые точки, terrain-following
    public static ModConfigSpec.DoubleValue SHAHED136_NAV_CONSTANT;
    public static ModConfigSpec.DoubleValue SHAHED136_WAYPOINT_ADVANCE_RADIUS;
    public static ModConfigSpec.BooleanValue SHAHED136_TERRAIN_FOLLOW_ALLOWED;
    public static ModConfigSpec.DoubleValue SHAHED136_TERRAIN_FOLLOW_CLEARANCE;
    public static ModConfigSpec.DoubleValue SHAHED136_TERRAIN_FOLLOW_LOOKAHEAD;
    public static ModConfigSpec.IntValue SHAHED136_MAX_WAYPOINTS;

    // Звук дронов (дистанция слышимости)
    public static ModConfigSpec.DoubleValue SHAHED_SOUND_MAX_DISTANCE;
    public static ModConfigSpec.DoubleValue FPV_SOUND_MAX_DISTANCE;

    // Ускорение отправки чанков пилоту (борьба с пустыми чанками у быстрых дронов)
    public static ModConfigSpec.BooleanValue CHUNK_SEND_BOOST_ENABLED;
    public static ModConfigSpec.DoubleValue CHUNK_SEND_BOOST_RATE;
    public static ModConfigSpec.IntValue CHUNK_SEND_BOOST_BATCHES;
    public static ModConfigSpec.DoubleValue CHUNK_SEND_FLOOR_RATE;
    public static ModConfigSpec.IntValue DRONE_CHUNK_RADIUS;
    public static ModConfigSpec.IntValue DRONE_VIEW_RADIUS;

    public static void init(ModConfigSpec.Builder builder) {
        builder.push("fpv_drone");

        builder.comment("Enable noise interference effect based on FPV drone distance");
        FPV_ENABLE_NOISE_EFFECT = builder.define("enable_noise_effect", true);

        builder.comment("Minimum distance (blocks) where noise starts appearing for FPV drone");
        FPV_NOISE_MIN_DISTANCE = builder.defineInRange("noise_min_distance", 20.0, 0.0, 5000.0);

        builder.comment("Maximum distance (blocks) where noise reaches full intensity for FPV drone");
        FPV_NOISE_MAX_DISTANCE = builder.defineInRange("noise_max_distance", 2400.0, 0.0, 5000.0);

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
        FPV_MAX_DISTANCE = builder.defineInRange("max_distance", 2500.0, 50.0, 10000.0);

        builder.pop();

        builder.push("mavic_drone");

        builder.comment("Maximum distance (blocks) for Mavic drone signal loss");
        MAVIC_MAX_DISTANCE = builder.defineInRange("max_distance", 2500.0, 50.0, 10000.0);

        builder.comment("Distance (blocks) where Mavic drone starts losing signal");
        MAVIC_SIGNAL_LOSS_DISTANCE = builder.defineInRange("signal_loss_distance", 2000.0, 50.0, 10000.0);

        builder.pop();

        builder.push("lancet");

        builder.comment("Maximum distance (blocks) for ZALA Lancet signal and flight cleanup");
        LANCET_MAX_DISTANCE = builder.defineInRange("max_distance", 2500.0, 50.0, 10000.0);

        builder.comment("Minimum controllable airspeed in blocks/tick. Below this the Lancet stalls and sinks. 0.42 ~= 30 km/h.");
        LANCET_STALL_SPEED = builder.defineInRange("stall_speed", 0.42, 0.05, 5.0);

        builder.comment("Default cruise airspeed in blocks/tick. 1.53 ~= 110 km/h.");
        LANCET_CRUISE_SPEED = builder.defineInRange("cruise_speed", 1.53, 0.1, 8.0);

        builder.comment("Maximum level-flight airspeed in blocks/tick. 1.94 ~= 140 km/h.");
        LANCET_MAX_SPEED = builder.defineInRange("max_speed", 1.94, 0.2, 12.0);

        builder.comment("Maximum dive airspeed in blocks/tick. 4.17 ~= 300 km/h.");
        LANCET_DIVE_MAX_SPEED = builder.defineInRange("dive_max_speed", 4.17, 0.2, 12.0);

        builder.comment("Loiter orbit radius around the marked target, in blocks");
        LANCET_LOITER_RADIUS = builder.defineInRange("loiter_radius", 40.0, 8.0, 256.0);

        builder.comment("Loiter waypoint altitude above the marked target, in blocks");
        LANCET_LOITER_ALTITUDE = builder.defineInRange("loiter_altitude", 18.0, -64.0, 256.0);

        builder.comment("Built-in warhead explosion damage for ZALA Lancet");
        LANCET_EXPLOSION_DAMAGE = builder.defineInRange("explosion_damage", 800.0, 1.0, 2000.0);

        builder.comment("Built-in warhead explosion radius for ZALA Lancet");
        LANCET_EXPLOSION_RADIUS = builder.defineInRange("explosion_radius", 7.0, 1.0, 64.0);

        builder.pop();

        builder.push("reb");

        builder.comment("Radius of large REB (Radio Electronic Warfare) interference effect in blocks");
        REB_RADIUS = builder.defineInRange("reb_radius", 100.0, 10.0, 5000.0);

        builder.comment("Radius of mini REB (Radio Electronic Warfare) interference effect in blocks");
        REB_MINI_RADIUS = builder.defineInRange("reb_mini_radius", 50.0, 5.0, 1000.0);

        builder.comment("Radius of the wearable REB backpack from Warborn-Renewed, in blocks. Requires Curios.");
        REB_BACKPACK_RADIUS = builder.defineInRange("reb_backpack_radius", 30.0, 0.0, 1000.0);

        builder.comment("REB jamming curve exponent. 1.0 = linear (strong on edges), 2.0 = quadratic (legacy soft).");
        REB_JAMMING_CURVE_EXPONENT = builder.defineInRange("jamming_curve_exponent", 1.0, 0.5, 4.0);

        builder.comment("REB jamming strength multiplier. >1.0 makes REB more effective; capped at 1.0 jamming factor.");
        REB_JAMMING_MULTIPLIER = builder.defineInRange("jamming_multiplier", 1.5, 0.5, 3.0);

        builder.pop();

        builder.push("signal");

        builder.comment("Enable line-of-sight (wall) attenuation between operator and drone.");
        SIGNAL_LOS_ENABLED = builder.define("los_enabled", true);

        builder.comment("Signal loss per solid block thickness on the line of sight (0..1).");
        WALL_HARD_PER_BLOCK = builder.defineInRange("wall_hard_per_block", 0.05, 0.0, 1.0);

        builder.comment("Signal loss per soft obstacle block (#wrbdrones:soft_obstacles tag) on the line of sight (0..1).");
        WALL_SOFT_PER_BLOCK = builder.defineInRange("wall_soft_per_block", 0.01, 0.0, 1.0);

        builder.comment("Maximum total wall attenuation cap (0..1). Prevents infinitely long raycasts from snapping to zero instantly.");
        WALL_MAX_TOTAL_ATTENUATION = builder.defineInRange("wall_max_total_attenuation", 0.95, 0.0, 1.0);

        builder.comment("Maximum number of voxels to traverse along the line of sight ray.");
        WALL_MAX_RAY_BLOCKS = builder.defineInRange("wall_max_ray_blocks", 500, 16, 5000);

        builder.comment("How long a per-drone signal calculation is cached, in ticks (HUD renders 60+ FPS).");
        SIGNAL_CACHE_TTL_TICKS = builder.defineInRange("cache_ttl_ticks", 5, 1, 100);

        builder.comment("Enable altitude-based signal modifier (higher drone over operator = better signal).");
        SIGNAL_ALTITUDE_ENABLED = builder.define("altitude_enabled", true);

        builder.comment("Drone altitude (in blocks) below operator at which the signal multiplier reaches its minimum.");
        ALT_PENALTY_FLOOR = builder.defineInRange("altitude_penalty_floor", -20.0, -200.0, 0.0);

        builder.comment("Drone altitude (in blocks) above operator at which the signal multiplier reaches 1.0 (no further bonus).");
        ALT_BONUS_CEIL = builder.defineInRange("altitude_bonus_ceil", 30.0, 0.0, 200.0);

        builder.comment("Lowest signal multiplier when drone is at or below altitude_penalty_floor (0..1).");
        ALT_MIN_MULTIPLIER = builder.defineInRange("altitude_min_multiplier", 0.3, 0.0, 1.0);

        builder.comment("Enable authoritative server-side signal cutoff (forces signal loss if quality drops below threshold).");
        SIGNAL_SERVER_CUTOFF_ENABLED = builder.define("server_cutoff_enabled", true);

        builder.comment("How often (in server ticks) the server re-evaluates signal quality for active drones.");
        SIGNAL_SERVER_CHECK_INTERVAL_TICKS = builder.defineInRange("server_check_interval_ticks", 10, 1, 200);

        builder.comment("Server-side signal threshold below which the drone forcibly loses control (0..1).");
        SIGNAL_SERVER_CUTOFF_THRESHOLD = builder.defineInRange("server_cutoff_threshold", 0.01, 0.0, 0.5);

        builder.comment("If true, the drone self-destructs (kamikaze-style explosion) when signal drops to/below destroy_threshold.");
        SIGNAL_DESTROY_ON_ZERO_ENABLED = builder.define("destroy_on_signal_loss", true);

        builder.comment("Signal quality threshold below which the drone explodes (0..1). 0.0 = explode only at exact zero signal.");
        SIGNAL_DESTROY_THRESHOLD = builder.defineInRange("destroy_threshold", 0.0, 0.0, 0.5);

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

        builder.comment("Proportional navigation constant N for terminal homing (typical 3–4). "
                + "Higher = more aggressive lead/intercept curve, but can overshoot at low turn authority.");
        SHAHED136_NAV_CONSTANT = builder.defineInRange("nav_constant", 3.5, 1.0, 6.0);

        builder.comment("Radius (blocks, horizontal) within which an intermediate waypoint is considered reached "
                + "and the route advances to the next point. Final waypoint is never advanced past.");
        SHAHED136_WAYPOINT_ADVANCE_RADIUS = builder.defineInRange("waypoint_advance_radius", 40.0, 5.0, 200.0);

        builder.comment("Whether terrain-following (low-altitude ingress over terrain) may be enabled per launch. "
                + "If false, the per-launch toggle is forced off regardless of operator input.");
        SHAHED136_TERRAIN_FOLLOW_ALLOWED = builder.define("terrain_follow_allowed", true);

        builder.comment("Terrain-following clearance (blocks above ground) when the mode is active.");
        SHAHED136_TERRAIN_FOLLOW_CLEARANCE = builder.defineInRange("terrain_follow_clearance", 30.0, 5.0, 300.0);

        builder.comment("Terrain-following look-ahead distance (blocks) along the heading; samples terrain ahead "
                + "to avoid flying into a rising slope. 0 = sample only directly below.");
        SHAHED136_TERRAIN_FOLLOW_LOOKAHEAD = builder.defineInRange("terrain_follow_lookahead", 32.0, 0.0, 128.0);

        builder.comment("Maximum number of intermediate waypoints the operator may set per launch.");
        SHAHED136_MAX_WAYPOINTS = builder.defineInRange("max_waypoints", 6, 0, 16);

        builder.pop();

        builder.push("sound");

        builder.comment("Maximum distance (blocks) at which Shahed-136 engine sound can be heard. Set to 0 for no limit");
        SHAHED_SOUND_MAX_DISTANCE = builder.defineInRange("shahed_max_distance", 1000.0, 0.0, 5000.0);

        builder.comment("Maximum distance (blocks) at which FPV/Mavic drone engine sound can be heard (50-300, default 160)");
        FPV_SOUND_MAX_DISTANCE = builder.defineInRange("fpv_max_distance", 160.0, 50.0, 300.0);

        builder.pop();

        builder.push("chunk_sending");

        builder.comment(
                "Boost chunk-send throughput to a player while they pilot a drone via monitor.",
                "Fixes empty/void chunks that render ahead of fast drones (FPV, Lancet): they outrun",
                "the vanilla rate-limited chunk sender, whose speed is dictated by the client. Affects",
                "only the controlling player and only while actively piloting.");
        CHUNK_SEND_BOOST_ENABLED = builder.define("boost_enabled", true);

        builder.comment(
                "Принудительная скорость отправки чанков пилоту (ваниль: 9, безопасный максимум: 32).",
                "Значение 16 оставляет запас, не сериализуя до 1280 чанков/с на одного пилота.");
        CHUNK_SEND_BOOST_RATE = builder.defineInRange("boost_chunks_per_tick", 16.0, 1.0, 32.0);

        builder.comment(
                "Максимум неподтверждённых батчей чанков у пилота (ваниль: 10).",
                "Держите значение низким: каждый батч может содержать boost_chunks_per_tick полных чанков.");
        CHUNK_SEND_BOOST_BATCHES = builder.defineInRange("boost_max_batches", 4, 1, 8);

        builder.comment(
                "Нижний порог скорости отправки чанков ДЛЯ ВСЕХ игроков, не только пилотов дронов.",
                "Та же болезнь, что лечит boost, но у обычных игроков: темп диктует клиент",
                "(ChunkBatchSizeCalculator = 7мс / время обработки чанка), а меряет он время между",
                "пакетами batch-start и batch-finish в главном потоке — туда попадают кадры отрисовки.",
                "Просевший FPS => клиент занижает запрос (наблюдалось 0.35 чанка/тик при норме 9) =>",
                "сервер честно шлёт по капле => игрок летит в пустоту. Усреднение инерционное (вес",
                "старых сэмплов до 49), поэтому само не восстанавливается — помогает лишь перезаход,",
                "который обнуляет счётчик. Порог не даёт запросу упасть ниже указанного.",
                "Ориентир по каналу: 3 чанка/тик ~ 600 КиБ/с на летящего игрока, 5 ~ 1 МиБ/с.",
                "0 — выключить и вернуть чисто ванильное поведение.");
        CHUNK_SEND_FLOOR_RATE = builder.defineInRange("floor_chunks_per_tick", 3.0, 0.0, 32.0);

        builder.comment(
                "Безопасный предел радиуса FULL-only обзора дрона (кап поверх drone_view_radius).",
                "ENTITY_TICKING получает только центр дрона; чанки обзора генерируются, но не тикают.");
        DRONE_CHUNK_RADIUS = builder.defineInRange("drone_max_load_radius", 32, 2, 32);

        builder.comment(
                "Радиус обзора (в чанках) вокруг пилотируемого дрона, стримящийся пилоту.",
                "Пробивает серверный view-distance: пилот видит вокруг дрона этот радиус, даже если",
                "сервер стоит на 10. Режется запрошенным клиентским view-distance (нет смысла слать",
                "чанки, которые клиент не отрисует) и drone_max_load_radius.");
        DRONE_VIEW_RADIUS = builder.defineInRange("drone_view_radius", 20, 2, 32);

        builder.pop();
    }
}
