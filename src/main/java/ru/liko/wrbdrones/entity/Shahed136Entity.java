package ru.liko.wrbdrones.entity;

import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.atsuishio.superbwarfare.tools.CustomExplosion;
import com.atsuishio.superbwarfare.tools.ParticleTool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.item.RadioItem;
import ru.liko.wrbdrones.network.ShahedExplodePacket;
import ru.liko.wrbdrones.registry.ModItems;
import ru.liko.wrbdrones.registry.ModSounds;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Shahed136Entity extends Entity implements GeoEntity {

    // ── Synched Data ────────────────────────────────────────────────

    private static final EntityDataAccessor<Boolean> LAUNCHED = SynchedEntityData.defineId(Shahed136Entity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> TARGET_X = SynchedEntityData.defineId(Shahed136Entity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(Shahed136Entity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Z = SynchedEntityData.defineId(Shahed136Entity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(Shahed136Entity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> LINKED_RADIO_UUID = SynchedEntityData
            .defineId(Shahed136Entity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SET_SPEED = SynchedEntityData.defineId(Shahed136Entity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SET_ALTITUDE = SynchedEntityData.defineId(Shahed136Entity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> EVASIVE_MODE = SynchedEntityData.defineId(Shahed136Entity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(Shahed136Entity.class,
            EntityDataSerializers.FLOAT);

    // ── Flight Constants ────────────────────────────────────────────

    private static final float MAX_HEALTH = 20.0f;
    private static final float TURN_SPEED = 1.5f;
    private static final float TERMINAL_TURN_SPEED = 5.0f;
    private static final float MAX_BANK_ANGLE = 30.0f;
    private static final float ACCELERATION = 0.05f;
    private static final float DIVE_ACCELERATION = 0.15f;
    private static final float DIVE_SPEED_MULTIPLIER = 2.5f;
    private static final float ROLL_SMOOTHING = 2.0f;
    private static final float ROLL_FROM_YAW_FACTOR = 15.0f;
    private static final float CRUISE_MAX_PITCH = 45.0f;
    private static final float TERMINAL_MAX_PITCH = 85.0f;
    private static final float FULL_PITCH_RANGE = 90.0f;
    private static final double TERMINAL_PHASE_DISTANCE = 100.0;
    private static final double CRUISE_PHASE_DISTANCE = 150.0;
    private static final double CLOSE_RANGE_DISTANCE = 20.0;
    private static final float EVASIVE_YAW_AMPLITUDE = 20.0f;
    private static final double EVASIVE_ALTITUDE_AMPLITUDE = 5.0;
    private static final float LAUNCH_INITIAL_SPEED = 0.1f;
    private static final double GRAVITY = 0.04;
    private static final double RAYTRACE_SCALE = 1.5;
    private static final double CHUNK_PRELOAD_DISTANCE = 32.0;

    // ── Failsafe Constants ──────────────────────────────────────────

    private static final int FAILSAFE_DELAY_TICKS = 20;
    private static final int STUCK_CHECK_DELAY_TICKS = 40;
    private static final double MIN_SPEED_SQR = 0.05;
    private static final double MIN_MOVEMENT_SQR = 0.01;
    private static final double BLOCK_CHECK_INFLATE = 0.0;

    // ── Instance Fields ─────────────────────────────────────────────

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public float oRoll;
    private int launchTicks = 0;
    private boolean hasPlayedStartSound = false;
    private float spawnX, spawnY, spawnZ;

    @Nullable
    private UUID ownerUUID;

    @Nullable
    private List<ChunkPos> loadedChunks = null;

    // ── Constructor & Base Overrides ────────────────────────────────

    public Shahed136Entity(EntityType<? extends Shahed136Entity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noCulling = true;
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(1.5f, 0.5f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LAUNCHED, false);
        builder.define(TARGET_X, 0.0f);
        builder.define(TARGET_Y, 64.0f);
        builder.define(TARGET_Z, 0.0f);
        builder.define(HEALTH, MAX_HEALTH);
        builder.define(LINKED_RADIO_UUID, "");
        builder.define(SET_SPEED, 0.5f);
        builder.define(SET_ALTITUDE, 100.0f);
        builder.define(EVASIVE_MODE, false);
        builder.define(ROLL, 0.0f);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public boolean isControlledByLocalInstance() {
        return false;
    }

    // ── Synched Data Getters/Setters ────────────────────────────────

    public boolean isLaunched() {
        return this.entityData.get(LAUNCHED);
    }

    public void setLaunched(boolean launched) {
        this.entityData.set(LAUNCHED, launched);
    }

    public Vec3 getTargetPos() {
        return new Vec3(
                this.entityData.get(TARGET_X),
                this.entityData.get(TARGET_Y),
                this.entityData.get(TARGET_Z));
    }

    public void setTargetPos(double x, double y, double z) {
        this.entityData.set(TARGET_X, (float) x);
        this.entityData.set(TARGET_Y, (float) y);
        this.entityData.set(TARGET_Z, (float) z);
    }

    public float getSetSpeed() {
        return this.entityData.get(SET_SPEED);
    }

    public void setSetSpeed(float speed) {
        this.entityData.set(SET_SPEED, speed);
    }

    public float getSetAltitude() {
        return this.entityData.get(SET_ALTITUDE);
    }

    public void setSetAltitude(float altitude) {
        this.entityData.set(SET_ALTITUDE, altitude);
    }

    public boolean isEvasiveMode() {
        return this.entityData.get(EVASIVE_MODE);
    }

    public void setEvasiveMode(boolean evasive) {
        this.entityData.set(EVASIVE_MODE, evasive);
    }

    public float getRoll() {
        return this.entityData.get(ROLL);
    }

    public float getRoll(float partialTick) {
        return Mth.lerp(partialTick, this.oRoll, this.getRoll());
    }

    public void setRoll(float roll) {
        this.entityData.set(ROLL, roll);
    }

    public float getHealth() {
        return this.entityData.get(HEALTH);
    }

    public void setHealth(float health) {
        this.entityData.set(HEALTH, Mth.clamp(health, 0.0f, getMaxHealth()));
    }

    public float getMaxHealth() {
        return MAX_HEALTH;
    }

    public String getLinkedRadioUUID() {
        return this.entityData.get(LINKED_RADIO_UUID);
    }

    public void setLinkedRadioUUID(String uuid) {
        this.entityData.set(LINKED_RADIO_UUID, uuid);
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.ownerUUID = uuid;
    }

    public float getCurrentSpeed() {
        return (float) this.getDeltaMovement().length();
    }

    // ── Lifecycle: tick, launch, hurt, interact, explode, remove ────

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }

        if (!this.level().isClientSide() && this.getHealth() > 0.0f) {
            this.setHealth(this.getHealth() - amount);
            if (this.getHealth() <= 0.0f) {
                explode();
            }
            return true;
        }

        return false;
    }

    @Override
    public void tick() {
        this.oRoll = this.getRoll();
        super.tick();

        if (isLaunched()) {
            launchTicks++;
            if (!hasPlayedStartSound && launchTicks == 1) {
                playStartSound();
                hasPlayedStartSound = true;
            }

            if (this.level().isClientSide()) {
                clientTickLaunched();
            } else {
                serverTickLaunched();
            }
        } else {
            tickIdle();
        }

        if (this.isRemoved()) return;

        if (this.horizontalCollision || this.verticalCollision || (this.onGround() && isLaunched())) {
            if (!this.level().isClientSide() && isLaunched() && launchTicks > FAILSAFE_DELAY_TICKS) {
                explode();
            }
        }
        if (this.isRemoved()) return;

        if (this.isInWater() && !this.level().isClientSide()) {
            explode();
        }
        if (this.isRemoved()) return;

        if (!this.level().isClientSide()) {
            checkMaxDistance();
        }
    }

    private void clientTickLaunched() {
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() > 0.001) {
            this.move(MoverType.SELF, motion);
        }
    }

    private void serverTickLaunched() {
        ServerLevel serverLevel = (ServerLevel) this.level();

        updateFlight();
        if (this.isRemoved()) return;

        spawnFlightParticles(serverLevel);
        handleChunkLoading(serverLevel);

        if (launchTicks > FAILSAFE_DELAY_TICKS && checkBlockIntersection()) {
            explode();
            return;
        }

        if (launchTicks > STUCK_CHECK_DELAY_TICKS) {
            if (this.getDeltaMovement().lengthSqr() < MIN_SPEED_SQR) {
                explode();
                return;
            }
            if (this.position().distanceToSqr(this.xo, this.yo, this.zo) < MIN_MOVEMENT_SQR) {
                explode();
            }
        }
    }

    private void tickIdle() {
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -GRAVITY, 0));
            this.move(MoverType.SELF, this.getDeltaMovement());
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    private boolean checkBlockIntersection() {
        AABB box = this.getBoundingBox().inflate(BLOCK_CHECK_INFLATE);
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            if (!this.level().getBlockState(pos).getCollisionShape(this.level(), pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if (isLaunched()) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);

        if (stack.is(ModItems.RADIO.get())) {
            return RadioItem.onInteractWithShahed(player, this, stack);
        }

        if (player.isShiftKeyDown() && stack.isEmpty()) {
            if (!this.level().isClientSide()) {
                player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.SHAHED136.get()));
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }

        return InteractionResult.PASS;
    }

    public void launch() {
        if (isLaunched()) return;

        setLaunched(true);
        launchTicks = 0;

        this.spawnX = (float) this.getX();
        this.spawnY = (float) this.getY();
        this.spawnZ = (float) this.getZ();

        Vec3 forward = Vec3.directionFromRotation(0, this.getYRot());
        this.setDeltaMovement(forward.scale(LAUNCH_INITIAL_SPEED));
    }

    public void explode() {
        if (this.level().isClientSide() || this.isRemoved()) return;

        if (this.level() instanceof ServerLevel serverLevel) {
            unloadChunks(serverLevel);

            float damage = ServerConfig.SHAHED136_EXPLOSION_DAMAGE.get().floatValue();
            float radius = ServerConfig.SHAHED136_EXPLOSION_RADIUS.get().floatValue();

            new CustomExplosion.Builder(this)
                    .damageSource(ModDamageTypes.causeCustomExplosionDamage(this.level().registryAccess(), this, null))
                    .damage(damage)
                    .radius(radius)
                    .damageMultiplier(2.0f)
                    .withParticleType(ParticleTool.ParticleType.GIANT)
                    .explode();

            // Обрыв звука двигателя на клиентах выполняется централизованно в remove(),
            // которое вызывается отсюда через discard().
            this.discard();
        }
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            unloadChunks(serverLevel);

            // Единая точка выхода для ЛЮБОГО удаления дрона (взрыв, /kill, выгрузка):
            // гарантированно обрываем звук двигателя на всех клиентах, которые отслеживают
            // сущность (а значит — проигрывают её звук), независимо от расстояния до игрока.
            // Раньше пакет слался только из explode() и лишь игрокам в радиусе 1100 блоков,
            // из-за чего после взрыва звук «висел» (экстраполяция в ShahedSoundHandler), если
            // игрок был дальше либо дрон удалялся иным путём, минуя explode().
            // ВАЖНО: рассылка до super.remove(reason) — пока трекинг ещё содержит сущность.
            final ShahedExplodePacket packet = new ShahedExplodePacket(
                    this.getUUID().getMostSignificantBits(),
                    this.getUUID().getLeastSignificantBits());
            PacketDistributor.sendToPlayersTrackingEntity(this, packet);
        }
        super.remove(reason);
    }

    // ── Flight Logic ────────────────────────────────────────────────

    private void updateFlight() {
        Vec3 currentPos = this.position();
        Vec3 targetPos = getTargetPos();

        double distXZ = Math.sqrt(currentPos.distanceToSqr(targetPos.x, currentPos.y, targetPos.z));
        double desiredY = targetPos.y;

        float currentTurnSpeed = TURN_SPEED;
        float maxPitch = CRUISE_MAX_PITCH;

        if (distXZ < TERMINAL_PHASE_DISTANCE) {
            currentTurnSpeed = TERMINAL_TURN_SPEED;
            maxPitch = TERMINAL_MAX_PITCH;
        }

        if (distXZ > CRUISE_PHASE_DISTANCE) {
            desiredY = getSetAltitude();
        }

        double dx = targetPos.x - currentPos.x;
        double dy = desiredY - currentPos.y;
        double dz = targetPos.z - currentPos.z;

        float desiredYaw = (float) (Mth.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;

        if (isEvasiveMode() && distXZ > TERMINAL_PHASE_DISTANCE) {
            long time = this.level().getGameTime();
            desiredYaw += (float) Math.sin(time * 0.05) * EVASIVE_YAW_AMPLITUDE;
            desiredY += Math.cos(time * 0.03) * EVASIVE_ALTITUDE_AMPLITUDE;
            dy = desiredY - currentPos.y;
        }

        float yawDiff = Mth.wrapDegrees(desiredYaw - this.getYRot());
        float yawChange = Mth.clamp(yawDiff, -currentTurnSpeed, currentTurnSpeed);
        this.setYRot(this.getYRot() + yawChange);

        float targetRoll = -yawChange * ROLL_FROM_YAW_FACTOR;
        targetRoll = Mth.clamp(targetRoll, -MAX_BANK_ANGLE, MAX_BANK_ANGLE);
        float rollDiff = targetRoll - getRoll();
        float rollChange = Mth.clamp(rollDiff, -ROLL_SMOOTHING, ROLL_SMOOTHING);
        setRoll(getRoll() + rollChange);

        float desiredPitch = (float) (-(Mth.atan2(dy, distXZ) * (180D / Math.PI)));
        if (distXZ > CLOSE_RANGE_DISTANCE) {
            desiredPitch = Mth.clamp(desiredPitch, -maxPitch, maxPitch);
        } else {
            desiredPitch = Mth.clamp(desiredPitch, -FULL_PITCH_RANGE, FULL_PITCH_RANGE);
        }

        float pitchDiff = Mth.wrapDegrees(desiredPitch - this.getXRot());
        float pitchChange = Mth.clamp(pitchDiff, -currentTurnSpeed, currentTurnSpeed);
        this.setXRot(this.getXRot() + pitchChange);

        float targetSpeed = getSetSpeed();
        boolean isDiving = distXZ < TERMINAL_PHASE_DISTANCE && dy < 0;
        if (isDiving) {
            float diveIntensity = Mth.clamp(this.getXRot() / TERMINAL_MAX_PITCH, 0.0f, 1.0f);
            targetSpeed *= Mth.lerp(1.0f, DIVE_SPEED_MULTIPLIER, diveIntensity);
            targetSpeed = Math.max(targetSpeed, (float) this.getDeltaMovement().length());
        }

        float currentSpeed = (float) this.getDeltaMovement().length();
        float accel = isDiving ? DIVE_ACCELERATION : ACCELERATION;
        float newSpeed = currentSpeed + Mth.clamp(targetSpeed - currentSpeed, -accel, accel);

        Vec3 motion = Vec3.directionFromRotation(this.getXRot(), this.getYRot()).scale(newSpeed);

        if (launchTicks > FAILSAFE_DELAY_TICKS) {
            Vec3 start = this.position();
            Vec3 end = start.add(motion.scale(RAYTRACE_SCALE));
            HitResult result = this.level()
                    .clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (result.getType() != HitResult.Type.MISS) {
                this.setPos(result.getLocation());
                explode();
                return;
            }
        }

        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);

        if (launchTicks > FAILSAFE_DELAY_TICKS
                && (this.horizontalCollision || this.verticalCollision || this.onGround())) {
            explode();
        }
    }

    // ── Particles & Sounds ──────────────────────────────────────────

    private void spawnFlightParticles(ServerLevel serverLevel) {
        if (launchTicks % 2 == 0) {
            Vec3 backOffset = Vec3.directionFromRotation(this.getXRot(), this.getYRot()).scale(-1.5);
            Vec3 particlePos = this.position().add(backOffset);

            ParticleTool.sendParticle(serverLevel, ParticleTypes.SMOKE,
                    particlePos.x, particlePos.y, particlePos.z,
                    2, 0.1, 0.1, 0.1, 0.01, true);

            if (launchTicks % 4 == 0) {
                ParticleTool.sendParticle(serverLevel, ParticleTypes.FLAME,
                        particlePos.x, particlePos.y, particlePos.z,
                        1, 0.05, 0.05, 0.05, 0.001, true);
            }
        }

        if (launchTicks % 3 == 0) {
            Vec3 backOffset = Vec3.directionFromRotation(this.getXRot(), this.getYRot()).scale(-2.0);
            Vec3 particlePos = this.position().add(backOffset);

            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    particlePos.x, particlePos.y, particlePos.z,
                    1, 0.1, 0.1, 0.1, 0.001);
        }

        if (launchTicks % 5 == 0) {
            Vec3 backOffset = Vec3.directionFromRotation(this.getXRot(), this.getYRot()).scale(-2.5);
            Vec3 particlePos = this.position().add(backOffset);

            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    particlePos.x, particlePos.y, particlePos.z,
                    1, 0.2, 0.2, 0.2, 0.005);
        }
    }

    private void playStartSound() {
        if (!this.level().isClientSide()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    ModSounds.SHAHED136_START.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        }
    }

    // ── Chunk Loading ───────────────────────────────────────────────

    private void handleChunkLoading(ServerLevel serverLevel) {
        if (!isLaunched()) return;

        ChunkPos currentChunk = this.chunkPosition();

        boolean needsUpdate = loadedChunks == null
                || loadedChunks.isEmpty()
                || !loadedChunks.contains(currentChunk);

        if (needsUpdate) {
            unloadChunks(serverLevel);

            loadedChunks = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkPos cp = new ChunkPos(currentChunk.x + dx, currentChunk.z + dz);
                    serverLevel.setChunkForced(cp.x, cp.z, true);
                    loadedChunks.add(cp);
                }
            }

            Vec3 motion = this.getDeltaMovement();
            if (motion.lengthSqr() > 0.01) {
                Vec3 ahead = this.position().add(motion.normalize().scale(CHUNK_PRELOAD_DISTANCE));
                ChunkPos aheadChunk = new ChunkPos(Mth.floor(ahead.x) >> 4, Mth.floor(ahead.z) >> 4);
                if (!loadedChunks.contains(aheadChunk)) {
                    serverLevel.setChunkForced(aheadChunk.x, aheadChunk.z, true);
                    loadedChunks.add(aheadChunk);
                }
            }
        }
    }

    private void unloadChunks(ServerLevel serverLevel) {
        if (loadedChunks != null) {
            for (ChunkPos cp : loadedChunks) {
                serverLevel.setChunkForced(cp.x, cp.z, false);
            }
            loadedChunks = null;
        }
    }

    private void checkMaxDistance() {
        if (!isLaunched()) return;

        double maxDistance = ServerConfig.SHAHED136_MAX_DISTANCE.get();
        double maxDistanceSqr = maxDistance * maxDistance;
        Vec3 spawnPos = new Vec3(this.spawnX, this.spawnY, this.spawnZ);

        if (this.position().distanceToSqr(spawnPos) > maxDistanceSqr) {
            explode();
        }
    }

    // ── NBT ─────────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.contains("Launched")) setLaunched(tag.getBoolean("Launched"));
        if (tag.contains("TargetX")) this.entityData.set(TARGET_X, tag.getFloat("TargetX"));
        if (tag.contains("TargetY")) this.entityData.set(TARGET_Y, tag.getFloat("TargetY"));
        if (tag.contains("TargetZ")) this.entityData.set(TARGET_Z, tag.getFloat("TargetZ"));
        if (tag.contains("Health")) setHealth(tag.getFloat("Health"));
        if (tag.contains("LinkedRadioUUID")) setLinkedRadioUUID(tag.getString("LinkedRadioUUID"));
        if (tag.hasUUID("OwnerUUID")) ownerUUID = tag.getUUID("OwnerUUID");
        if (tag.contains("SetSpeed")) setSetSpeed(tag.getFloat("SetSpeed"));
        if (tag.contains("SetAltitude")) setSetAltitude(tag.getFloat("SetAltitude"));
        if (tag.contains("EvasiveMode")) setEvasiveMode(tag.getBoolean("EvasiveMode"));
        if (tag.contains("SpawnX")) this.spawnX = tag.getFloat("SpawnX");
        if (tag.contains("SpawnY")) this.spawnY = tag.getFloat("SpawnY");
        if (tag.contains("SpawnZ")) this.spawnZ = tag.getFloat("SpawnZ");
        if (tag.contains("LaunchTicks")) launchTicks = tag.getInt("LaunchTicks");
        if (tag.contains("HasPlayedStartSound")) hasPlayedStartSound = tag.getBoolean("HasPlayedStartSound");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putBoolean("Launched", isLaunched());
        tag.putFloat("TargetX", this.entityData.get(TARGET_X));
        tag.putFloat("TargetY", this.entityData.get(TARGET_Y));
        tag.putFloat("TargetZ", this.entityData.get(TARGET_Z));
        tag.putFloat("Health", getHealth());
        tag.putString("LinkedRadioUUID", getLinkedRadioUUID());
        if (ownerUUID != null) tag.putUUID("OwnerUUID", ownerUUID);
        tag.putInt("LaunchTicks", launchTicks);
        tag.putBoolean("HasPlayedStartSound", hasPlayedStartSound);
        tag.putFloat("SetSpeed", getSetSpeed());
        tag.putFloat("SetAltitude", getSetAltitude());
        tag.putBoolean("EvasiveMode", isEvasiveMode());
        tag.putFloat("SpawnX", this.spawnX);
        tag.putFloat("SpawnY", this.spawnY);
        tag.putFloat("SpawnZ", this.spawnZ);
    }

    // ── GeckoLib ────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "propeller_controller", 0, state -> {
            if (isLaunched()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("fly"));
            } else {
                return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
            }
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
