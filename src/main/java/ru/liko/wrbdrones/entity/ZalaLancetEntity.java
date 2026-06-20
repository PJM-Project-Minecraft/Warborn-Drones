package ru.liko.wrbdrones.entity;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.atsuishio.superbwarfare.tools.CustomExplosion;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.ParticleTool;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.entity.flight.AircraftProfile;
import ru.liko.wrbdrones.entity.flight.FixedWingDynamics;
import ru.liko.wrbdrones.entity.flight.FlightDemand;
import ru.liko.wrbdrones.registry.ModItems;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.Set;
import java.util.UUID;

/**
 * Игровая версия ZALA Lancet как самолётного барражирующего боеприпаса.
 * Физика намеренно аркадная: дрон держится в воздухе только за счёт скорости,
 * не зависает и не использует квадрокоптерную тягу SBW.
 */
public class ZalaLancetEntity extends AddonDroneEntity {

    private static final EntityDataAccessor<Boolean> STARTED = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> AIRSPEED = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THROTTLE = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> HAS_TARGET = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TERMINAL = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> TARGET_X = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Z = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> TARGET_ENTITY_UUID = SynchedEntityData
            .defineId(ZalaLancetEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> RUDDER_INPUT = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HAS_COURSE_COMMAND = SynchedEntityData
            .defineId(ZalaLancetEntity.class, EntityDataSerializers.BOOLEAN);
    // Курс задаётся точкой в мире (куда клиент спроецировал курсор по ЛКМ) — единый источник
    // правды и для полёта, и для HUD: рамка рисуется обратной проекцией этой точки на экран,
    // поэтому всегда стоит ровно там, куда целился игрок, и не «перепрыгивает».
    private static final EntityDataAccessor<Float> COURSE_TARGET_X = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> COURSE_TARGET_Y = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> COURSE_TARGET_Z = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LANCET_MODE = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SELECTED_TARGET_ID = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RECON_TARGET_X = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RECON_TARGET_Y = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RECON_TARGET_Z = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> FREE_CAMERA = SynchedEntityData.defineId(ZalaLancetEntity.class,
            EntityDataSerializers.BOOLEAN);

    public static final int MODE_COURSE = 0;
    public static final int MODE_RECON = 1;

    private static final float MIN_THROTTLE = 0.0f;
    private static final float DEFAULT_THROTTLE = 0.45f;
    private static final float MAX_THROTTLE = 1.0f;
    private static final float LAUNCH_SPEED_KMH = 50.0f;
    private static final float KMH_TO_BLOCKS_PER_TICK = 1.0f / 72.0f;
    private static final double MODEL_UNIT_TO_BLOCK = 1.0 / 16.0;
    private static final Vec3 LAUNCH_PLATFORM_DRONE_OFFSET = new Vec3(-2.5, 20.1732, -32.5603);
    private static final float MAX_MANUAL_ROLL = 55.0f;
    private static final float MAX_CRUISE_PITCH = 38.0f;
    private static final float MAX_TERMINAL_PITCH = 72.0f;
    private static final float MANUAL_PITCH_RATE = 1.35f;
    private static final float MANUAL_ROLL_RATE = 4.0f;
    private static final float RUDDER_YAW_RATE = 0.9f;
    private static final float BANK_YAW_FACTOR = 0.055f;
    private static final float AUTO_TURN_RATE = 2.0f;
    // Терминальная атака: темп разворота растёт по мере сближения, чтобы дрон реально
    // доворачивал на точечную цель, а не наматывал круги широким радиусом R = v/ω.
    private static final float TERMINAL_TURN_RATE = 5.0f;
    private static final float TERMINAL_TURN_RATE_CLOSE = 18.0f;
    private static final float TERMINAL_HOMING_RANGE = 40.0f;
    // На финальном участке гасим скорость до манёвренной (×cruise), иначе радиус разворота
    // раздувается и дрон не способен попасть в цель.
    private static final float TERMINAL_MANEUVER_SPEED_FACTOR = 1.45f;
    // Неконтактный взрыватель: взводится в этом радиусе и подрывает в точке наибольшего
    // сближения (когда дальность снова начала расти) — гарантия, что дрон не кружит вечно.
    private static final float PROXIMITY_ARM_RADIUS = 12.0f;
    private static final float PROXIMITY_CONTACT_RADIUS = 2.2f;
    // Замкнутая орбита разведки: упреждение по фазе берётся от фактического азимута дрона.
    private static final float LOITER_LOOKAHEAD = 0.4f;
    private static final float COURSE_MIN_TURN_RATE = 0.9f;
    private static final float COURSE_MAX_TURN_RATE = 3.8f;
    // При |yawDiff| ≥ этого значения (град.) разворот идёт на максимальном темпе.
    private static final float COURSE_FULL_TURN_YAW = 30.0f;
    private static final float COURSE_DIRECT_YAW_GAIN = 0.055f;
    private static final float COURSE_ROLL_GAIN = 2.25f;
    private static final float PITCH_RESPONSE = 1.35f;
    private static final float TERMINAL_PITCH_RESPONSE = 2.4f;
    private static final int SAFE_LAUNCH_TICKS = 12;
    private static final double COLLISION_RAY_SCALE = 1.35;

    // Энергетическая модель: airspeed += thrust(throttle) + gravityComponent − drag·v²
    // Калибровка: throttle=0 → idle ~80 км/ч, throttle=0.45 → cruise, throttle=1 → max.
    private static final float THRUST_IDLE = 0.0152f;
    private static final float THRUST_FULL = 0.05f;
    private static final float DRAG_COEF = 0.0132f;
    // Вертикальное ускорение от наклона: pitch вниз положительный, sin(pitch) даёт прирост.
    private static final float PITCH_GRAVITY = 0.11f;
    // Сглаживание входов (lerp-фактор за тик): ~4 тика выхода на сигнал.
    private static final float INPUT_SMOOTHING = 0.25f;
    // Падение подъёмной силы при сваливании: вертикальная скорость теряется при airspeed<stall.
    private static final float STALL_LIFT_LOSS = 0.12f;

    private static final Set<String> ALLOWED_ATTACHMENTS = Set.of();

    private float loiterDir = 1.0f;
    private double prevTargetDistSqr = -1.0;
    private int startedTicks;
    private float spawnX;
    private float spawnY;
    private float spawnZ;
    private float smoothedManualPitch;
    private float smoothedManualRoll;
    private float smoothedRudder;

    public ZalaLancetEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public static Vec3 getLaunchPlatformDronePosition(Vec3 platformPos, float yaw) {
        double localX = LAUNCH_PLATFORM_DRONE_OFFSET.x * MODEL_UNIT_TO_BLOCK;
        double localY = LAUNCH_PLATFORM_DRONE_OFFSET.y * MODEL_UNIT_TO_BLOCK;
        double localZ = LAUNCH_PLATFORM_DRONE_OFFSET.z * MODEL_UNIT_TO_BLOCK;
        double radians = Math.toRadians(-yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double worldX = localX * cos + localZ * sin;
        double worldZ = -localX * sin + localZ * cos;
        return platformPos.add(worldX, localY, worldZ);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STARTED, false);
        builder.define(AIRSPEED, 0.0f);
        builder.define(THROTTLE, DEFAULT_THROTTLE);
        builder.define(HAS_TARGET, false);
        builder.define(TERMINAL, false);
        builder.define(TARGET_X, 0.0f);
        builder.define(TARGET_Y, 64.0f);
        builder.define(TARGET_Z, 0.0f);
        builder.define(TARGET_ENTITY_UUID, "");
        builder.define(RUDDER_INPUT, 0);
        builder.define(HAS_COURSE_COMMAND, false);
        builder.define(COURSE_TARGET_X, 0.0f);
        builder.define(COURSE_TARGET_Y, 64.0f);
        builder.define(COURSE_TARGET_Z, 0.0f);
        builder.define(LANCET_MODE, MODE_COURSE);
        builder.define(SELECTED_TARGET_ID, -1);
        builder.define(RECON_TARGET_X, 0.0f);
        builder.define(RECON_TARGET_Y, 0.0f);
        builder.define(RECON_TARGET_Z, 0.0f);
        builder.define(FREE_CAMERA, false);
    }

    @Override
    public ResourceLocation getModelResource() {
        return Wrbdrones.id("geo/zala_lancet.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource() {
        return Wrbdrones.id("textures/entity/zala_lancet.png");
    }

    @Override
    protected Set<String> getAllowedAttachments() {
        return ALLOWED_ATTACHMENTS;
    }

    @Override
    public float getMaxHealth() {
        return 8.0f;
    }

    /**
     * Отключаем квадрокоптерную travel-физику SBW. Движение считаем в baseTick
     * после WRB-логики монитора и восстановления оператора.
     */
    @Override
    public void travel() {
    }

    @Override
    public void baseTick() {
        boolean hadFire = this.fire;
        this.fire = false;
        super.mouseInput(0.0, 0.0);
        super.baseTick();
        super.mouseInput(0.0, 0.0);
        this.fire = false;

        if (this.isRemoved()) {
            return;
        }

        if (this.level().isClientSide()) {
            clientFlightTick();
        } else {
            serverFlightTick(hadFire);
        }
    }

    private void clientFlightTick() {
        if (!isStarted()) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() > 0.0001) {
            this.move(MoverType.SELF, motion);
        }
        // Self-chunk режим: игрок не рядом с дроном, и штатная клиентская интерполяция
        // поворота SBW (handleClientSync) для контролёра не обновляет yaw/pitch — поэтому
        // клиентская ориентация Lancet застывала и камера (читающая getYaw/getBodyPitch)
        // не следила за манёврами. Драйвим ориентацию из синхронизированных серверных
        // углов (serverYaw/serverPitch synced), чтобы клиент совпадал с сервером.
        this.setYRot(this.getServerYaw());
        this.setXRot(this.getServerPitch());
        this.setBodyXRot(this.getXRot());
        this.setZRot(this.getRoll());
    }

    private void serverFlightTick(boolean hadFire) {
        if (hadFire && hasTarget()) {
            setTerminalAttack(true);
        }

        boolean controlled = isMonitorActivelyControlling();
        if (!isStarted()) {
            if (!controlled) {
                return;
            }
            startFlight();
        }

        startedTicks++;
        updateTrackedTargetPosition();

        if (this.isInWater()) {
            explode();
            return;
        }

        updateFixedWingFlight(controlled);

        if (this.isRemoved()) {
            return;
        }

        checkTerminalDetonation();
        if (this.isRemoved()) {
            return;
        }

        checkEntityImpact();
        if (this.isRemoved()) {
            return;
        }

        checkMaxDistance();
    }

    private void startFlight() {
        setStarted(true);
        startedTicks = 0;
        spawnX = (float) this.getX();
        spawnY = (float) this.getY();
        spawnZ = (float) this.getZ();

        this.setNoGravity(true);
        this.setXRot(-8.0f);
        this.setBodyXRot(this.getXRot());
        setThrottle(DEFAULT_THROTTLE);
        setAirspeed(getLaunchSpeed());
        this.setDeltaMovement(Vec3.directionFromRotation(this.getXRot(), this.getYRot()).scale(getAirspeed()));
        if (this.onGround()) {
            this.setPos(this.getX(), this.getY() + 0.7, this.getZ());
        }
        setReconTargetPos(this.position());
    }

    public void launchFromPlatform() {
        if (this.level().isClientSide() || isStarted()) {
            return;
        }
        startFlight();
    }

    @Override
    public void mouseInput(double x, double y) {
        super.mouseInput(0.0, 0.0);
    }

    /** Профиль аэродинамики Lancet из текущих констант и config-геттеров. */
    private AircraftProfile lancetProfile() {
        return new AircraftProfile(
                THRUST_IDLE, THRUST_FULL, DRAG_COEF, PITCH_GRAVITY,
                getStallSpeed(), getCruiseSpeed(), getDiveMaxSpeed(),
                STALL_LIFT_LOSS, BANK_YAW_FACTOR);
    }

    private void updateFixedWingFlight(boolean controlled) {
        float throttle = getThrottle();
        boolean autoThrottle = getLancetMode() == MODE_RECON && !isTerminalAttack();
        if (autoThrottle) {
            // В режиме разведки автопилот сам держит крейсерский газ — ручная регулировка
            // не имеет смысла на орбите и только сбивает радиус.
            throttle = Mth.lerp(0.08f, throttle, DEFAULT_THROTTLE);
        } else if (controlled) {
            if (this.downInputDown()) {
                throttle += 0.018f;
            }
            if (this.sprintInputDown()) {
                throttle -= 0.024f;
            }
        } else {
            throttle = Mth.lerp(0.02f, throttle, DEFAULT_THROTTLE * 0.85f);
        }
        setThrottle(Mth.clamp(throttle, MIN_THROTTLE, MAX_THROTTLE));
        setPower(getThrottle());

        // Энергетическая модель: тяга, гравитационная составляющая по тангажу и квадратичное сопротивление.
        // При горизонтальном полёте обеспечивает естественное равновесие airspeed на заданном throttle.
        // При пикировании скорость растёт за счёт sin(pitch); при наборе высоты — теряется через тот же член.
        float airspeed = FixedWingDynamics.integrateAirspeed(
                getAirspeed(), this.getXRot(), getThrottle(), lancetProfile());
        if (isTerminalAttack()) {
            // В терминальной атаке добавляем малый импульс для надёжного достижения пиковых значений.
            airspeed += 0.005f;
        }
        airspeed = Mth.clamp(airspeed, 0.0f, getDiveMaxSpeed());
        // На сближении в атаке гасим скорость до манёвренной: тугой радиус разворота R = v/ω
        // нужен, чтобы реально доворачивать на цель, а не наматывать вокруг неё круги.
        if (isTerminalAttack() && hasTarget()) {
            double dist = getTargetPos().distanceTo(this.position());
            if (dist < TERMINAL_HOMING_RANGE) {
                float maneuverCap = Math.min(getDiveMaxSpeed(), getCruiseSpeed() * TERMINAL_MANEUVER_SPEED_FACTOR);
                airspeed = Math.min(airspeed, maneuverCap);
            }
        }
        setAirspeed(airspeed);

        FlightDemand demand = computeFlightDemand(controlled);

        // Ручной WASD-стик убран: тангаж/крен полностью задаёт курсовой автопилот.
        // За игроком остаётся только курсор (направление по ЛКМ), руль довода и газ.
        float rawManualPitch = 0.0f;
        float rawManualRoll = 0.0f;
        int rawRudder = 0;
        if (controlled && getLancetMode() != MODE_RECON) {
            rawRudder = getRudderInput();
        }

        // Сглаживание входа (фильтр первого порядка) — снимает рывки джойстика.
        smoothedManualPitch = Mth.lerp(INPUT_SMOOTHING, smoothedManualPitch, rawManualPitch);
        smoothedManualRoll = Mth.lerp(INPUT_SMOOTHING, smoothedManualRoll, rawManualRoll);
        smoothedRudder = Mth.lerp(INPUT_SMOOTHING, smoothedRudder, rawRudder);

        float maxPitch = isTerminalAttack() ? MAX_TERMINAL_PITCH : MAX_CRUISE_PITCH;
        float requestedPitch = this.getXRot() + smoothedManualPitch * MANUAL_PITCH_RATE;
        if (demand.hasAutopilot()) {
            float pitchResponse = isTerminalAttack() ? TERMINAL_PITCH_RESPONSE : PITCH_RESPONSE;
            requestedPitch = Mth.approachDegrees(requestedPitch, demand.pitch(), pitchResponse);
        } else {
            // Нет автопилота (курс ещё не задан) — держим лёгкий набор/прямой полёт.
            requestedPitch = Mth.approachDegrees(requestedPitch, 4.0f, 0.45f);
        }

        // Сваливание: при airspeed<stall нос принудительно идёт вниз для разгона.
        float stallDeficit = FixedWingDynamics.stallDeficit(airspeed, getStallSpeed());
        if (stallDeficit > 0.0f) {
            requestedPitch = Mth.approachDegrees(requestedPitch, 22.0f, 1.0f + 1.5f * stallDeficit);
        }
        this.setXRot(Mth.clamp(requestedPitch, -maxPitch, maxPitch));
        this.setBodyXRot(this.getXRot());

        boolean courseMode = hasCourseCommand() && !hasTarget();
        // Орбитальный режим (разведка + облёт назначенной цели): разворот ведём чисто
        // координированным креном. Прямой довод по yaw тут НЕ применяем — иначе он
        // складывается с bankYaw, темп разворота задваивается (~×1.7), и дрон не
        // наматывает стабильный круг радиуса R, а рыскает и сваливается на меньший радиус.
        boolean loiterAutopilot = demand.hasAutopilot() && !courseMode && !isTerminalAttack();

        // Связь крена со скоростью разворота квадратично зависит от скорости (как у настоящего самолёта).
        float bankFactor = FixedWingDynamics.bankFactor(airspeed, lancetProfile());

        float autoYawChange;
        if (!demand.hasAutopilot()) {
            autoYawChange = 0.0f;
        } else if (courseMode) {
            autoYawChange = Mth.clamp(demand.yawDiff() * COURSE_DIRECT_YAW_GAIN,
                    -demand.turnRate(), demand.turnRate());
        } else if (loiterAutopilot) {
            // Разворот целиком возложен на крен ниже — прямого довода нет.
            autoYawChange = 0.0f;
        } else {
            // Терминальная атака — агрессивный прямой довод на точечную цель.
            autoYawChange = Mth.clamp(demand.yawDiff(), -demand.turnRate(), demand.turnRate());
        }

        float targetRoll;
        if (loiterAutopilot) {
            // Требуемый темп разворота переводим в крен через ту же связь roll→yaw:
            // bankYaw = roll·bankFactor ⇒ roll = требуемый_темп / bankFactor. Тогда фактический
            // темп разворота сходится к omega = v/R и радиус орбиты совпадает с заданным.
            float demandedYaw = Mth.clamp(demand.yawDiff(), -demand.turnRate(), demand.turnRate());
            targetRoll = Mth.clamp(demandedYaw / Math.max(bankFactor, 1.0e-4f),
                    -MAX_MANUAL_ROLL, MAX_MANUAL_ROLL);
        } else if (courseMode) {
            targetRoll = Mth.clamp(demand.yawDiff() * COURSE_ROLL_GAIN, -MAX_MANUAL_ROLL, MAX_MANUAL_ROLL);
        } else if (demand.hasAutopilot()) {
            targetRoll = Mth.clamp(autoYawChange * 14.0f, -MAX_MANUAL_ROLL, MAX_MANUAL_ROLL);
        } else {
            targetRoll = 0.0f;
        }
        if (controlled && Math.abs(smoothedManualRoll) > 0.01f) {
            targetRoll = smoothedManualRoll * MAX_MANUAL_ROLL;
        }
        // Скорость крена зависит от airspeed: на малых — вяло, на высоких — энергично.
        float rollRate = MANUAL_ROLL_RATE
                * Mth.clamp(airspeed / Math.max(getCruiseSpeed(), 0.01f), 0.4f, 1.4f);
        setZRot(Mth.approach(this.getRoll(), targetRoll, rollRate));

        float bankYaw = this.getRoll() * bankFactor;
        float rudderYaw = smoothedRudder * RUDDER_YAW_RATE;
        this.setYRot(this.getYRot() + bankYaw + rudderYaw + autoYawChange);

        Vec3 motion = FixedWingDynamics.assembleMotion(
                this.getXRot(), this.getYRot(), airspeed, stallDeficit, lancetProfile());

        if (startedTicks > SAFE_LAUNCH_TICKS && willHitBlock(motion)) {
            explode();
            return;
        }

        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);

        if (startedTicks > SAFE_LAUNCH_TICKS
                && (this.horizontalCollision || this.verticalCollision || this.onGround())) {
            explode();
        }
    }

    private FlightDemand computeFlightDemand(boolean controlled) {
        if (getLancetMode() == MODE_RECON && !isTerminalAttack()) {
            return computeLoiterDemand(getReconTargetPos(), false);
        }

        if (hasCourseCommand() && !hasTarget()) {
            // Курс — это точка в мире: yaw/pitch берём из геометрии «дрон → точка».
            Vec3 delta = getCourseTargetPos().subtract(this.position());
            double horizontal = Math.max(0.001, Math.sqrt(delta.x * delta.x + delta.z * delta.z));
            float desiredYaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
            float yawDiff = Mth.wrapDegrees(desiredYaw - this.getYRot());
            float desiredPitch = (float) (-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
            desiredPitch = Mth.clamp(desiredPitch, -MAX_CRUISE_PITCH, MAX_CRUISE_PITCH);
            // Темп разворота растёт с величиной рассогласования по курсу.
            float turnRate = Mth.lerp(Mth.clamp(Math.abs(yawDiff) / COURSE_FULL_TURN_YAW, 0.0f, 1.0f),
                    COURSE_MIN_TURN_RATE, COURSE_MAX_TURN_RATE);
            return new FlightDemand(true, desiredPitch, yawDiff, turnRate);
        }

        if (!hasTarget()) {
            return FlightDemand.manual();
        }

        Vec3 target = getTargetPos();
        if (!isTerminalAttack()) {
            return computeLoiterDemand(target, !controlled);
        }

        Vec3 delta = target.subtract(this.position());
        double horizontal = Math.max(0.001, Math.sqrt(delta.x * delta.x + delta.z * delta.z));
        float desiredYaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        float yawDiff = Mth.wrapDegrees(desiredYaw - this.getYRot());
        float desiredPitch = (float) (-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        desiredPitch = Mth.clamp(desiredPitch, -MAX_TERMINAL_PITCH, MAX_TERMINAL_PITCH);
        // Чем ближе цель, тем выше темп доворота — радиус разворота сжимается под точечную цель.
        float dist = (float) Math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z);
        float closeFactor = Mth.clamp(1.0f - dist / TERMINAL_HOMING_RANGE, 0.0f, 1.0f);
        float turnRate = Mth.lerp(closeFactor, TERMINAL_TURN_RATE, TERMINAL_TURN_RATE_CLOSE);
        return new FlightDemand(true, desiredPitch, yawDiff, turnRate);
    }

    /**
     * Орбитальный автопилот с замкнутой обратной связью: фаза орбиты берётся не из открытого
     * интегратора (который рассинхронизируется с реальным положением и даёт рысканье), а из
     * фактического азимута дрона относительно центра. Точку прицеливания ставим на упреждение
     * {@link #LOITER_LOOKAHEAD} вперёд по орбите — дрон сам стабильно наматывает круг радиуса R.
     * R ∝ v² (координированный разворот с фиксированным креном), ω = v/R.
     */
    private FlightDemand computeLoiterDemand(Vec3 target, boolean reducedAuthority) {
        double baseRadius = ServerConfig.LANCET_LOITER_RADIUS.get();
        double altitude = ServerConfig.LANCET_LOITER_ALTITUDE.get();
        float cruise = Math.max(0.1f, getCruiseSpeed());
        float v = Math.max(getStallSpeed() * 0.8f, getAirspeed());
        double speedFrac = v / cruise;
        // R масштабируется как v²/cruise² с ограничением, чтобы не вырождалось на крайних скоростях.
        double radius = baseRadius * Mth.clamp(speedFrac * speedFrac, 0.5, 4.0);
        double omega = v / radius;

        // Фактический азимут дрона относительно центра орбиты (замкнутая связь).
        Vec3 rel = this.position().subtract(target);
        double currentAngle = Mth.atan2(rel.z, rel.x);
        float aimAngle = (float) (currentAngle + loiterDir * (LOITER_LOOKAHEAD + omega));
        double loiterY = target.y + altitude;
        Vec3 aimPoint = new Vec3(
                target.x + Math.cos(aimAngle) * radius,
                loiterY,
                target.z + Math.sin(aimAngle) * radius);

        Vec3 delta = aimPoint.subtract(this.position());
        double horizontal = Math.max(0.001, Math.sqrt(delta.x * delta.x + delta.z * delta.z));
        float desiredYaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        float yawDiff = Mth.wrapDegrees(desiredYaw - this.getYRot());
        float desiredPitch = (float) (-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        desiredPitch = Mth.clamp(desiredPitch, -MAX_CRUISE_PITCH, MAX_CRUISE_PITCH);
        // Темп разворота согласован с ω + базовый минимум, чтобы автопилот мог парировать снос.
        float turnRate = (float) Math.max(AUTO_TURN_RATE * 0.6, omega * Mth.RAD_TO_DEG);
        if (reducedAuthority) {
            turnRate *= 0.85f;
        }
        return new FlightDemand(true, desiredPitch, yawDiff, turnRate);
    }

    /**
     * Подрыв в атаке: контактный взрыватель при касании цели и неконтактный — в точке
     * наибольшего сближения (когда дальность снова начала расти после взвода). Это гарантирует,
     * что дрон не способен бесконечно кружить вокруг цели, не взрываясь.
     */
    private void checkTerminalDetonation() {
        if (!isTerminalAttack() || !hasTarget() || startedTicks <= SAFE_LAUNCH_TICKS) {
            prevTargetDistSqr = -1.0;
            return;
        }
        double distSqr = getTargetPos().distanceToSqr(this.position());
        if (distSqr <= PROXIMITY_CONTACT_RADIUS * PROXIMITY_CONTACT_RADIUS) {
            explode();
            return;
        }
        if (distSqr <= PROXIMITY_ARM_RADIUS * PROXIMITY_ARM_RADIUS
                && prevTargetDistSqr >= 0.0 && distSqr > prevTargetDistSqr) {
            // Взрыватель взведён и дальность пошла на увеличение — прошли точку сближения.
            explode();
            return;
        }
        prevTargetDistSqr = distSqr;
    }

    /**
     * Задаёт направление орбиты (CW/CCW) так, чтобы заход совпал с текущим вектором скорости —
     * вход в круг получается плавным, без резкого разворота.
     */
    private void seedLoiterDirection(Vec3 center) {
        Vec3 rel = this.position().subtract(center);
        Vec3 forward = Vec3.directionFromRotation(0.0f, this.getYRot());
        // Знак векторного произведения (rel × forward) по Y задаёт сторону облёта.
        double cross = rel.x * forward.z - rel.z * forward.x;
        loiterDir = cross >= 0.0 ? 1.0f : -1.0f;
    }

    private boolean willHitBlock(Vec3 motion) {
        Vec3 start = this.position();
        Vec3 end = start.add(motion.scale(COLLISION_RAY_SCALE));
        HitResult result = this.level()
                .clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (result.getType() == HitResult.Type.MISS) {
            return false;
        }
        this.setPos(result.getLocation());
        return true;
    }

    private void checkEntityImpact() {
        if (startedTicks <= SAFE_LAUNCH_TICKS) {
            return;
        }
        AABB box = this.getBoundingBox().inflate(0.25);
        for (Entity entity : this.level().getEntities(this, box, this::canImpactEntity)) {
            explode();
            return;
        }
    }

    private boolean canImpactEntity(Entity entity) {
        if (entity == null || entity == this || entity.isSpectator() || !entity.isAlive()) {
            return false;
        }
        if (entity instanceof ItemEntity || entity instanceof Projectile) {
            return false;
        }
        Entity controller = getController();
        return controller == null || entity != controller;
    }

    private boolean isMonitorActivelyControlling() {
        Entity controller = getController();
        if (!(controller instanceof ServerPlayer player)) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(com.atsuishio.superbwarfare.init.ModItems.MONITOR.get())) {
            return false;
        }
        var tag = com.atsuishio.superbwarfare.tools.NBTTool.getTag(stack);
        return tag.getBoolean(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED)
                && tag.getBoolean("Using")
                && this.getStringUUID().equals(tag.getString(com.atsuishio.superbwarfare.item.misc.MonitorItem.LINKED_DRONE));
    }

    private void updateTrackedTargetPosition() {
        UUID targetUuid = getTargetEntityUuid();
        if (targetUuid == null) {
            return;
        }
        Entity target = EntityFindUtil.findEntity(this.level(), targetUuid.toString());
        if (target == null || target.isRemoved() || !target.isAlive()) {
            clearTarget();
            return;
        }
        Vec3 pos = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
        setTargetPos(pos);
    }

    public void setPointTarget(Vec3 target) {
        clearCourseCommand();
        setTargetPos(target);
        this.entityData.set(TARGET_ENTITY_UUID, "");
        this.entityData.set(HAS_TARGET, true);
        this.entityData.set(TERMINAL, false);
        seedLoiterDirection(target);
    }

    public void setEntityTarget(UUID targetUuid, Vec3 fallbackPos) {
        clearCourseCommand();
        setTargetPos(fallbackPos);
        this.entityData.set(TARGET_ENTITY_UUID, targetUuid.toString());
        this.entityData.set(HAS_TARGET, true);
        this.entityData.set(TERMINAL, false);
        seedLoiterDirection(fallbackPos);
    }

    public void clearTarget() {
        this.entityData.set(HAS_TARGET, false);
        this.entityData.set(TERMINAL, false);
        this.entityData.set(TARGET_ENTITY_UUID, "");
    }

    /**
     * Задаёт курс по точке в мире (клиент проецирует курсор по ЛКМ в эту точку).
     * Дрон правит yaw/pitch так, чтобы лететь на неё; HUD рисует рамку обратной
     * проекцией той же точки — маркер стоит ровно там, куда целился игрок.
     */
    public void setCourseCommand(Vec3 worldPoint) {
        this.entityData.set(COURSE_TARGET_X, (float) worldPoint.x);
        this.entityData.set(COURSE_TARGET_Y, (float) worldPoint.y);
        this.entityData.set(COURSE_TARGET_Z, (float) worldPoint.z);
        this.entityData.set(HAS_COURSE_COMMAND, true);
        clearTarget();
    }

    public Vec3 getCourseTargetPos() {
        return new Vec3(this.entityData.get(COURSE_TARGET_X),
                this.entityData.get(COURSE_TARGET_Y),
                this.entityData.get(COURSE_TARGET_Z));
    }

    public void clearCourseCommand() {
        this.entityData.set(HAS_COURSE_COMMAND, false);
    }

    public void setLancetMode(int mode) {
        int previous = this.entityData.get(LANCET_MODE);
        this.entityData.set(LANCET_MODE, mode);
        if (mode == MODE_RECON) {
            // Цель ставим впереди по курсу на ~базовый радиус — дрон сразу заходит на орбиту,
            // а не пытается «вылететь» из самого себя как из центра окружности.
            double baseRadius = ServerConfig.LANCET_LOITER_RADIUS.get();
            Vec3 forward = Vec3.directionFromRotation(0.0f, this.getYRot());
            Vec3 reconTarget = this.position().add(forward.scale(baseRadius));
            setReconTargetPos(reconTarget);
            // Направление облёта выбираем под текущий вектор скорости — заход без рывка.
            if (previous != MODE_RECON) {
                seedLoiterDirection(reconTarget);
            }
            clearTarget();
            clearCourseCommand();
        } else {
            this.entityData.set(FREE_CAMERA, false);
        }
    }

    public int getLancetMode() {
        return this.entityData.get(LANCET_MODE);
    }

    public void setFreeCamera(boolean freeCamera) {
        this.entityData.set(FREE_CAMERA, freeCamera);
    }

    public boolean isFreeCamera() {
        return this.entityData.get(FREE_CAMERA);
    }

    public void setSelectedTargetId(int id) {
        this.entityData.set(SELECTED_TARGET_ID, id);
    }

    public int getSelectedTargetId() {
        return this.entityData.get(SELECTED_TARGET_ID);
    }

    public void setReconTargetPos(Vec3 pos) {
        this.entityData.set(RECON_TARGET_X, (float) pos.x);
        this.entityData.set(RECON_TARGET_Y, (float) pos.y);
        this.entityData.set(RECON_TARGET_Z, (float) pos.z);
    }

    public Vec3 getReconTargetPos() {
        return new Vec3(this.entityData.get(RECON_TARGET_X), this.entityData.get(RECON_TARGET_Y), this.entityData.get(RECON_TARGET_Z));
    }

    public void setTerminalAttack(boolean terminal) {
        if (terminal && !hasTarget()) {
            return;
        }
        this.entityData.set(TERMINAL, terminal);
        // Сбрасываем взрыватель: счёт сближения начнётся заново с этого захода.
        prevTargetDistSqr = -1.0;
    }

    public void setRudderInput(boolean left, boolean right) {
        int value = 0;
        if (left) {
            value--;
        }
        if (right) {
            value++;
        }
        this.entityData.set(RUDDER_INPUT, Mth.clamp(value, -1, 1));
    }

    public boolean hasTarget() {
        return this.entityData.get(HAS_TARGET);
    }

    public boolean isTerminalAttack() {
        return this.entityData.get(TERMINAL);
    }

    public boolean isStarted() {
        return this.entityData.get(STARTED);
    }

    public boolean hasCourseCommand() {
        return this.entityData.get(HAS_COURSE_COMMAND);
    }

    public float getAirspeed() {
        return this.entityData.get(AIRSPEED);
    }

    public float getThrottle() {
        return this.entityData.get(THROTTLE);
    }

    public Vec3 getTargetPos() {
        return new Vec3(this.entityData.get(TARGET_X), this.entityData.get(TARGET_Y), this.entityData.get(TARGET_Z));
    }

    public int getRudderInput() {
        return this.entityData.get(RUDDER_INPUT);
    }

    @Nullable
    public UUID getTargetEntityUuid() {
        String raw = this.entityData.get(TARGET_ENTITY_UUID);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void setStarted(boolean started) {
        this.entityData.set(STARTED, started);
    }

    private void setAirspeed(float airspeed) {
        this.entityData.set(AIRSPEED, airspeed);
    }

    private void setThrottle(float throttle) {
        this.entityData.set(THROTTLE, throttle);
    }

    private void setTargetPos(Vec3 target) {
        this.entityData.set(TARGET_X, (float) target.x);
        this.entityData.set(TARGET_Y, (float) target.y);
        this.entityData.set(TARGET_Z, (float) target.z);
    }

    private float getStallSpeed() {
        return ServerConfig.LANCET_STALL_SPEED.get().floatValue();
    }

    private float getCruiseSpeed() {
        return ServerConfig.LANCET_CRUISE_SPEED.get().floatValue();
    }

    private float getMaxSpeed() {
        return ServerConfig.LANCET_MAX_SPEED.get().floatValue();
    }

    private float getDiveMaxSpeed() {
        return ServerConfig.LANCET_DIVE_MAX_SPEED.get().floatValue();
    }

    private float getLaunchSpeed() {
        return LAUNCH_SPEED_KMH * KMH_TO_BLOCKS_PER_TICK;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public @org.jetbrains.annotations.Nullable Vec2 getCameraRotation(float partialTicks, @NotNull Player player, boolean zoom, boolean isFirstPerson) {
        return ru.liko.wrbdrones.client.LancetCameraMount.getCameraRotation(this, partialTicks, player, isFirstPerson);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public Vec3 getCameraPosition(float partialTicks, @NotNull Player player, boolean zoom, boolean isFirstPerson) {
        return ru.liko.wrbdrones.client.LancetCameraMount.getCameraPosition(this, partialTicks, player, isFirstPerson);
    }

    public void explode() {
        if (this.level().isClientSide() || this.isRemoved()) {
            return;
        }

        Entity attacker = getController();
        if (attacker != null) {
            this.entityData.set(com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity.LAST_ATTACKER_UUID,
                    attacker.getStringUUID());
        }

        float damage = ServerConfig.LANCET_EXPLOSION_DAMAGE.get().floatValue();
        float radius = ServerConfig.LANCET_EXPLOSION_RADIUS.get().floatValue();
        float vehicleMultiplier = ServerConfig.LANCET_VEHICLE_DAMAGE_MULTIPLIER.get().floatValue();
        DamageSource damageSource = ModDamageTypes.causeCustomExplosionDamage(this.level().registryAccess(), this, attacker);

        new CustomExplosion.Builder(this)
                .damageSource(damageSource)
                .damage(damage)
                .radius(radius)
                .damageMultiplier(vehicleMultiplier)
                .withParticleType(ParticleTool.ParticleType.MEDIUM)
                .explode();

        this.discard();
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);
        if (!this.level().isClientSide() && this.getHealth() <= 0.0f && !this.isRemoved()) {
            explode();
        }
        return result;
    }

    private void checkMaxDistance() {
        double maxDistance = ServerConfig.LANCET_MAX_DISTANCE.get();
        if (maxDistance <= 0.0) {
            return;
        }
        Vec3 spawn = new Vec3(spawnX, spawnY, spawnZ);
        if (spawn.equals(Vec3.ZERO)) {
            return;
        }
        if (this.position().distanceToSqr(spawn) > maxDistance * maxDistance) {
            explode();
        }
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        return new ItemStack(ModItems.ZALA_LANCET.get());
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("LancetStarted", isStarted());
        compound.putFloat("LancetAirspeed", getAirspeed());
        compound.putFloat("LancetThrottle", getThrottle());
        compound.putBoolean("LancetHasTarget", hasTarget());
        compound.putBoolean("LancetTerminal", isTerminalAttack());
        compound.putFloat("LancetTargetX", this.entityData.get(TARGET_X));
        compound.putFloat("LancetTargetY", this.entityData.get(TARGET_Y));
        compound.putFloat("LancetTargetZ", this.entityData.get(TARGET_Z));
        compound.putString("LancetTargetEntity", this.entityData.get(TARGET_ENTITY_UUID));
        compound.putBoolean("LancetHasCourseCommand", hasCourseCommand());
        compound.putFloat("LancetCourseX", this.entityData.get(COURSE_TARGET_X));
        compound.putFloat("LancetCourseY", this.entityData.get(COURSE_TARGET_Y));
        compound.putFloat("LancetCourseZ", this.entityData.get(COURSE_TARGET_Z));
        compound.putFloat("LancetLoiterDir", loiterDir);
        compound.putInt("LancetStartedTicks", startedTicks);
        compound.putFloat("LancetSpawnX", spawnX);
        compound.putFloat("LancetSpawnY", spawnY);
        compound.putFloat("LancetSpawnZ", spawnZ);
        compound.putInt("LancetMode", getLancetMode());
        compound.putBoolean("LancetFreeCamera", isFreeCamera());
        compound.putInt("LancetSelectedTarget", getSelectedTargetId());
        compound.putFloat("LancetReconX", this.entityData.get(RECON_TARGET_X));
        compound.putFloat("LancetReconY", this.entityData.get(RECON_TARGET_Y));
        compound.putFloat("LancetReconZ", this.entityData.get(RECON_TARGET_Z));
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("LancetStarted")) setStarted(compound.getBoolean("LancetStarted"));
        if (compound.contains("LancetAirspeed")) setAirspeed(compound.getFloat("LancetAirspeed"));
        if (compound.contains("LancetThrottle")) setThrottle(compound.getFloat("LancetThrottle"));
        if (compound.contains("LancetHasTarget")) this.entityData.set(HAS_TARGET, compound.getBoolean("LancetHasTarget"));
        if (compound.contains("LancetTerminal")) this.entityData.set(TERMINAL, compound.getBoolean("LancetTerminal"));
        if (compound.contains("LancetTargetX")) this.entityData.set(TARGET_X, compound.getFloat("LancetTargetX"));
        if (compound.contains("LancetTargetY")) this.entityData.set(TARGET_Y, compound.getFloat("LancetTargetY"));
        if (compound.contains("LancetTargetZ")) this.entityData.set(TARGET_Z, compound.getFloat("LancetTargetZ"));
        if (compound.contains("LancetTargetEntity")) this.entityData.set(TARGET_ENTITY_UUID, compound.getString("LancetTargetEntity"));
        if (compound.contains("LancetHasCourseCommand")) this.entityData.set(HAS_COURSE_COMMAND, compound.getBoolean("LancetHasCourseCommand"));
        if (compound.contains("LancetCourseX")) this.entityData.set(COURSE_TARGET_X, compound.getFloat("LancetCourseX"));
        if (compound.contains("LancetCourseY")) this.entityData.set(COURSE_TARGET_Y, compound.getFloat("LancetCourseY"));
        if (compound.contains("LancetCourseZ")) this.entityData.set(COURSE_TARGET_Z, compound.getFloat("LancetCourseZ"));
        if (compound.contains("LancetLoiterDir")) loiterDir = compound.getFloat("LancetLoiterDir");
        if (compound.contains("LancetStartedTicks")) startedTicks = compound.getInt("LancetStartedTicks");
        if (compound.contains("LancetSpawnX")) spawnX = compound.getFloat("LancetSpawnX");
        if (compound.contains("LancetSpawnY")) spawnY = compound.getFloat("LancetSpawnY");
        if (compound.contains("LancetSpawnZ")) spawnZ = compound.getFloat("LancetSpawnZ");
        if (compound.contains("LancetMode")) setLancetMode(compound.getInt("LancetMode"));
        if (compound.contains("LancetFreeCamera")) setFreeCamera(compound.getBoolean("LancetFreeCamera"));
        if (compound.contains("LancetSelectedTarget")) setSelectedTargetId(compound.getInt("LancetSelectedTarget"));
        if (compound.contains("LancetReconX")) this.entityData.set(RECON_TARGET_X, compound.getFloat("LancetReconX"));
        if (compound.contains("LancetReconY")) this.entityData.set(RECON_TARGET_Y, compound.getFloat("LancetReconY"));
        if (compound.contains("LancetReconZ")) this.entityData.set(RECON_TARGET_Z, compound.getFloat("LancetReconZ"));
    }

}
