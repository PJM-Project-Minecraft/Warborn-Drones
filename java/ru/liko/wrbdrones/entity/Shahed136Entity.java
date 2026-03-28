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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.util.ChunkLoadManager;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.registry.ModSounds;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import ru.liko.wrbdrones.item.RadioItem;
import ru.liko.wrbdrones.registry.ModItems;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class Shahed136Entity extends Entity implements GeoEntity {

    private static final EntityDataAccessor<Boolean> LAUNCHED = SynchedEntityData.defineId(Shahed136Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> TARGET_X = SynchedEntityData.defineId(Shahed136Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(Shahed136Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Z = SynchedEntityData.defineId(Shahed136Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(Shahed136Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> LINKED_RADIO_UUID = SynchedEntityData.defineId(Shahed136Entity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> SET_SPEED = SynchedEntityData.defineId(Shahed136Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SET_ALTITUDE = SynchedEntityData.defineId(Shahed136Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> EVASIVE_MODE = SynchedEntityData.defineId(Shahed136Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(Shahed136Entity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final float MAX_HEALTH = 20.0f;
    private static final float TURN_SPEED = 1.5f; // Reduced for smoother turns
    private static final float MAX_BANK_ANGLE = 30.0f; // Reduced max bank
    private static final float ACCELERATION = 0.05f;
    private static final float ROLL_SMOOTHING = 2.0f; // Max roll change per tick
    
    public float oRoll;

    private int launchTicks = 0;
    private boolean hasPlayedStartSound = false;

    @Nullable
    private UUID ownerUUID;

    public Shahed136Entity(EntityType<? extends Shahed136Entity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noCulling = true;
        this.setMaxUpStep(0.0f);
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(1.5f, 0.5f);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LAUNCHED, false);
        this.entityData.define(TARGET_X, 0.0f);
        this.entityData.define(TARGET_Y, 64.0f);
        this.entityData.define(TARGET_Z, 0.0f);
        this.entityData.define(HEALTH, MAX_HEALTH);
        this.entityData.define(LINKED_RADIO_UUID, "");
        this.entityData.define(SET_SPEED, 0.5f);
        this.entityData.define(SET_ALTITUDE, 100.0f);
        this.entityData.define(EVASIVE_MODE, false);
        this.entityData.define(ROLL, 0.0f);
    }

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
            this.entityData.get(TARGET_Z)
        );
    }

    public void setTargetPos(double x, double y, double z) {
        this.entityData.set(TARGET_X, (float) x);
        this.entityData.set(TARGET_Y, (float) y);
        this.entityData.set(TARGET_Z, (float) z);
    }

    public void setSetSpeed(float speed) {
        this.entityData.set(SET_SPEED, speed);
    }

    public float getSetSpeed() {
        return this.entityData.get(SET_SPEED);
    }

    public void setSetAltitude(float altitude) {
        this.entityData.set(SET_ALTITUDE, altitude);
    }

    public float getSetAltitude() {
        return this.entityData.get(SET_ALTITUDE);
    }

    public void setEvasiveMode(boolean evasive) {
        this.entityData.set(EVASIVE_MODE, evasive);
    }

    public boolean isEvasiveMode() {
        return this.entityData.get(EVASIVE_MODE);
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
        this.entityData.set(HEALTH, Mth.clamp(health, 0.0f, MAX_HEALTH));
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

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }

        if (!this.level().isClientSide() && this.getHealth() > 0.0f) {
            float newHealth = this.getHealth() - amount;
            this.setHealth(newHealth);

            if (this.getHealth() <= 0.0f) {
                explode();
            }

            return true;
        }

        return false;
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
            spawnSmokeTrail();

            if (!this.level().isClientSide()) {
                updateFlight();
            } else {
                // Клиентское предсказание - применяем текущую velocity для плавного движения
                Vec3 motion = this.getDeltaMovement();
                if (motion.lengthSqr() > 0.001) {
                    this.move(MoverType.SELF, motion);
                }
            }
            
            if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
                spawnEngineParticles(serverLevel);
                handleChunkLoading(serverLevel);
            }

            // Failsafe: Check physical block intersection (brute force) - только на сервере
            if (!this.level().isClientSide() && launchTicks > 20) {
                AABB box = this.getBoundingBox().inflate(0.1);
                for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ), Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
                    if (!this.level().getBlockState(pos).getCollisionShape(this.level(), pos).isEmpty()) {
                        explode();
                        break;
                    }
                }
            }

            // Failsafe 3: Check if stuck (low speed) - только на сервере
            if (!this.level().isClientSide() && launchTicks > 40 && this.getDeltaMovement().lengthSqr() < 0.05) {
                explode();
            }

            // Failsafe 4: Check if position hasn't changed despite being launched (stuck) - только на сервере
            if (!this.level().isClientSide() && launchTicks > 40 && this.position().distanceToSqr(this.xo, this.yo, this.zo) < 0.01) {
                explode();
            }

        } else {
            if (!this.onGround()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0, -0.04, 0));
                this.move(MoverType.SELF, this.getDeltaMovement());
            } else {
                this.setDeltaMovement(Vec3.ZERO);
            }
        }

        if (this.horizontalCollision || this.verticalCollision || (this.onGround() && isLaunched())) {
            if (!this.level().isClientSide() && isLaunched() && launchTicks > 20) {
                explode();
            }
        }

        // Explode if stuck (very low speed while launched)
        if (!this.level().isClientSide() && isLaunched() && launchTicks > 20 && this.getDeltaMovement().lengthSqr() < 0.1) {
            explode();
        }

        if (this.isInWater()) {
            if (!this.level().isClientSide()) {
                explode();
            }
        }

        checkMaxDistance();
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
                ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ModItems.SHAHED136.get()));
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }

        return InteractionResult.PASS;
    }

    private void updateFlight() {
        Vec3 currentPos = this.position();
        Vec3 targetPos = getTargetPos();

        double distXZ = Math.sqrt(currentPos.distanceToSqr(targetPos.x, currentPos.y, targetPos.z));
        double desiredY = targetPos.y;

        // Dynamic maneuvering parameters
        float currentTurnSpeed = TURN_SPEED;
        float maxPitch = 45.0f;

        // Terminal phase logic
        if (distXZ < 100) {
            // Increase agility when close
            currentTurnSpeed = 5.0f; 
            maxPitch = 85.0f; // Allow steep dive
        }

        // Cruise logic - maintain altitude until close
        if (distXZ > 150) { 
            desiredY = getSetAltitude();
        }

        double dx = targetPos.x - currentPos.x;
        double dy = desiredY - currentPos.y;
        double dz = targetPos.z - currentPos.z;

        // Yaw (Heading)
        float desiredYaw = (float)(Mth.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;

        // Evasive Maneuvers - Use GameTime for client-server sync
        if (isEvasiveMode() && distXZ > 100) {
            long time = this.level().getGameTime();
            desiredYaw += (float) Math.sin(time * 0.05) * 20.0f;
            desiredY += Math.cos(time * 0.03) * 5.0;
            dy = desiredY - currentPos.y;
        }

        float yawDiff = Mth.wrapDegrees(desiredYaw - this.getYRot());
        float yawChange = Mth.clamp(yawDiff, -currentTurnSpeed, currentTurnSpeed);
        float newYaw = this.getYRot() + yawChange;

        this.setYRot(newYaw);

        // Roll (Banking)
        float targetRoll = -yawChange * 15.0f; // Bank angle based on turn intensity
        targetRoll = Mth.clamp(targetRoll, -MAX_BANK_ANGLE, MAX_BANK_ANGLE);

        // Smooth roll transition
        float currentRoll = getRoll();
        float rollDiff = targetRoll - currentRoll;
        float rollChange = Mth.clamp(rollDiff, -ROLL_SMOOTHING, ROLL_SMOOTHING);
        setRoll(currentRoll + rollChange);

        // Pitch
        double dist3D = Math.sqrt(distXZ * distXZ + dy * dy);
        float desiredPitch = (float)(-(Mth.atan2(dy, distXZ) * (180D / Math.PI)));

        // Clamp pitch to avoid extreme dives/climbs unless very close
        if (distXZ > 20) {
             desiredPitch = Mth.clamp(desiredPitch, -maxPitch, maxPitch);
        } else {
             // Close range: allow full pitch range
             desiredPitch = Mth.clamp(desiredPitch, -90.0f, 90.0f);
        }

        float pitchDiff = Mth.wrapDegrees(desiredPitch - this.getXRot());
        float pitchChange = Mth.clamp(pitchDiff, -currentTurnSpeed, currentTurnSpeed);
        this.setXRot(this.getXRot() + pitchChange);

        // Movement
        float targetSpeed = getSetSpeed();
        float currentSpeed = (float) this.getDeltaMovement().length();
        float newSpeed = currentSpeed + Mth.clamp(targetSpeed - currentSpeed, -ACCELERATION, ACCELERATION);
        
        Vec3 motion = Vec3.directionFromRotation(this.getXRot(), this.getYRot()).scale(newSpeed);

            // Raytrace ahead to prevent clipping/stuck
        if (launchTicks > 20) {
            Vec3 start = this.position();
            Vec3 end = start.add(motion.scale(1.5)); // Check slightly further
            HitResult result = this.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (result.getType() != HitResult.Type.MISS) {
                this.setPos(result.getLocation());
                explode();
                return;
            }
        }

        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);

        // Immediate collision check after movement
        if (launchTicks > 20 && (this.horizontalCollision || this.verticalCollision || this.onGround())) {
            explode();
        }
    }


    private void spawnEngineParticles(ServerLevel serverLevel) {
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
    }

    private void spawnSmokeTrail() {
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
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
    }

    private void playStartSound() {
        if (!this.level().isClientSide()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.SHAHED136_START.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        }
    }

    private void handleChunkLoading(ServerLevel serverLevel) {
        if (!isLaunched()) return;
        
        // Используем новый API с системой тикетов
        // Радиус 4 обеспечивает загрузку чанков вокруг дрона и впереди по направлению движения
        ChunkLoadManager.ensureChunksLoaded(serverLevel, this.getId(), this.chunkPosition(), 4);
    }

    private void unloadChunks(ServerLevel serverLevel) {
        // Освобождаем тикет загрузки чанков
        ChunkLoadManager.releaseChunks(serverLevel, this.getId());
    }

    private void checkMaxDistance() {
        if (!this.level().isClientSide() && isLaunched()) {
            double maxDistance = ServerConfig.SHAHED136_MAX_DISTANCE.get();
            Vec3 startPos = new Vec3(
                this.entityData.get(TARGET_X),
                this.entityData.get(TARGET_Y),
                this.entityData.get(TARGET_Z)
            );

            if (this.position().distanceTo(startPos) > maxDistance * 2) {
                explode();
            }
        }
    }

    public void explode() {
        if (this.level().isClientSide() || this.isRemoved()) {
            return;
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            unloadChunks(serverLevel);

            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                ModSounds.SHAHED136_ENGINE_END.get(), SoundSource.HOSTILE, 2.0f, 1.0f);

            float damage = (float) (double) ServerConfig.SHAHED136_EXPLOSION_DAMAGE.get();
            float radius = (float) (double) ServerConfig.SHAHED136_EXPLOSION_RADIUS.get();

            new CustomExplosion.Builder(this)
                .damageSource(ModDamageTypes.causeCustomExplosionDamage(this.level().registryAccess(), this, null))
                .damage(damage)
                .radius(radius)
                .damageMultiplier(2.0f)
                .withParticleType(ParticleTool.ParticleType.GIANT)
                .explode();

            this.discard();
        }
    }

    public float getCurrentSpeed() {
        return (float) this.getDeltaMovement().length();
    }

    public void launch() {
        if (!isLaunched()) {
            setLaunched(true);
            launchTicks = 0;

            // Add spread to target (25 blocks)
            RandomSource random = this.level().getRandom();
            float spread = 25.0f;
            float dx = (random.nextFloat() * 2.0f - 1.0f) * spread;
            float dz = (random.nextFloat() * 2.0f - 1.0f) * spread;
            
            this.entityData.set(TARGET_X, this.entityData.get(TARGET_X) + dx);
            this.entityData.set(TARGET_Z, this.entityData.get(TARGET_Z) + dz);

            Vec3 forward = Vec3.directionFromRotation(0, this.getYRot());
            this.setDeltaMovement(forward.scale(0.1)); // Start slow
        }
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            unloadChunks(serverLevel);
        }
        super.remove(reason);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.contains("Launched")) {
            setLaunched(tag.getBoolean("Launched"));
        }
        if (tag.contains("TargetX")) {
            this.entityData.set(TARGET_X, tag.getFloat("TargetX"));
        }
        if (tag.contains("TargetY")) {
            this.entityData.set(TARGET_Y, tag.getFloat("TargetY"));
        }
        if (tag.contains("TargetZ")) {
            this.entityData.set(TARGET_Z, tag.getFloat("TargetZ"));
        }
        if (tag.contains("Health")) {
            setHealth(tag.getFloat("Health"));
        }
        if (tag.contains("LinkedRadioUUID")) {
            setLinkedRadioUUID(tag.getString("LinkedRadioUUID"));
        }
        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        }
        if (tag.contains("SetSpeed")) {
            setSetSpeed(tag.getFloat("SetSpeed"));
        }
        if (tag.contains("SetAltitude")) {
            setSetAltitude(tag.getFloat("SetAltitude"));
        }
        if (tag.contains("EvasiveMode")) {
            setEvasiveMode(tag.getBoolean("EvasiveMode"));
        }
        launchTicks = tag.getInt("LaunchTicks");
        hasPlayedStartSound = tag.getBoolean("HasPlayedStartSound");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putBoolean("Launched", isLaunched());
        tag.putFloat("TargetX", this.entityData.get(TARGET_X));
        tag.putFloat("TargetY", this.entityData.get(TARGET_Y));
        tag.putFloat("TargetZ", this.entityData.get(TARGET_Z));
        tag.putFloat("Health", getHealth());
        tag.putString("LinkedRadioUUID", getLinkedRadioUUID());
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
        tag.putInt("LaunchTicks", launchTicks);
        tag.putBoolean("HasPlayedStartSound", hasPlayedStartSound);
        tag.putFloat("SetSpeed", getSetSpeed());
        tag.putFloat("SetAltitude", getSetAltitude());
        tag.putBoolean("EvasiveMode", isEvasiveMode());
    }

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
