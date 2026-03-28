package ru.liko.wrbdrones.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.registry.ModItems;
import ru.liko.wrbdrones.registry.ModSounds;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class RebMiniEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Boolean> IS_ENABLED = SynchedEntityData.defineId(RebMiniEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(RebMiniEntity.class, EntityDataSerializers.FLOAT);
    private static final float MAX_HEALTH = 10.0F; // Максимальное здоровье мини-РЭБ (меньше чем у обычного)
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean wasOnGround = false;
    private boolean hasPlayedPlacementSound = false;

    public RebMiniEntity(EntityType<RebMiniEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(false);
        // По умолчанию РЭБ включен
        this.entityData.set(IS_ENABLED, true);
        // Устанавливаем начальное здоровье
        this.entityData.set(HEALTH, MAX_HEALTH);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(IS_ENABLED, true);
        this.entityData.define(HEALTH, MAX_HEALTH);
    }

    public boolean isEnabled() {
        return this.entityData.get(IS_ENABLED);
    }

    public void setEnabled(boolean enabled) {
        this.entityData.set(IS_ENABLED, enabled);
    }

    public float getHealth() {
        return this.entityData.get(HEALTH);
    }

    public void setHealth(float health) {
        this.entityData.set(HEALTH, Mth.clamp(health, 0.0F, this.getMaxHealth()));
    }

    public float getMaxHealth() {
        return MAX_HEALTH;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }
        
        if (!this.level().isClientSide() && this.getHealth() > 0.0F) {
            float newHealth = this.getHealth() - amount;
            this.setHealth(newHealth);
            
            if (this.getHealth() <= 0.0F) {
                // Уничтожаем РЭБ при достижении 0 HP
                this.discard();
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

    /**
     * Force render мини-РЭБ на любом расстоянии для всех игроков.
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            // Shift+ПКМ - поднять РЭБ
            if (!this.level().isClientSide()) {
                if (!player.getAbilities().instabuild) {
                    ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ModItems.REB_MINI.get()));
                }
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        } else {
            // Обычный ПКМ - включить/выключить
            if (!this.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                boolean newState = !this.isEnabled();
                this.setEnabled(newState);
                
                // Воспроизводим звук включения/выключения
                if (newState) {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
                            ModSounds.REB_TOGGLE_ON.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    serverPlayer.displayClientMessage(Component.translatable("wrbdrones.reb_mini.enabled"), true);
                } else {
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
                            ModSounds.REB_TOGGLE_OFF.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    serverPlayer.displayClientMessage(Component.translatable("wrbdrones.reb_mini.disabled"), true);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
    }

    @Override
    public void tick() {
        super.tick();

        // Проверяем, только что приземлились на землю
        boolean isOnGround = this.onGround();
        if (isOnGround && !wasOnGround && !hasPlayedPlacementSound && !this.level().isClientSide()) {
            // Воспроизводим звук размещения при приземлении (только один раз)
            playPlacementSound();
            hasPlayedPlacementSound = true;
        }
        wasOnGround = isOnGround;

        // Простая гравитация - только падение
        if (!isOnGround) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.04, 0));
            this.move(MoverType.SELF, this.getDeltaMovement());
        } else {
            // На земле - полная остановка
            this.setDeltaMovement(0, 0, 0);
        }
    }

    private void playPlacementSound() {
        // Случайно выбираем один из двух звуков размещения
        var sound = this.random.nextBoolean() 
                ? ModSounds.REB_PLACEMENT_01.get() 
                : ModSounds.REB_PLACEMENT_02.get();
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
                sound, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        if (pCompound.contains("IsEnabled")) {
            this.setEnabled(pCompound.getBoolean("IsEnabled"));
        }
        if (pCompound.contains("Health")) {
            this.setHealth(pCompound.getFloat("Health"));
        } else {
            this.setHealth(this.getMaxHealth());
        }
        // Если сущность загружается из сохранения, звук уже был воспроизведен
        hasPlayedPlacementSound = true;
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag pCompound) {
        pCompound.putBoolean("IsEnabled", this.isEnabled());
        pCompound.putFloat("Health", this.getHealth());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar data) {
        data.add(new AnimationController<>(this, "on_off_controller", 0, state -> {
            if (this.isEnabled()) {
                return state.setAndContinue(RawAnimation.begin().thenPlay("on"));
            } else {
                return state.setAndContinue(RawAnimation.begin().thenPlay("off"));
            }
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
