package ru.liko.wrbdrones.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.item.ZalaLancetItem;
import ru.liko.wrbdrones.network.OpenLancetPlatformScreenPacket;
import ru.liko.wrbdrones.registry.ModEntityTypes;
import ru.liko.wrbdrones.registry.ModItems;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class LancetLaunchPlatformEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Boolean> HAS_LANCET = SynchedEntityData
            .defineId(LancetLaunchPlatformEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> LANCET_UUID = SynchedEntityData
            .defineId(LancetLaunchPlatformEntity.class, EntityDataSerializers.STRING);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LancetLaunchPlatformEntity(EntityType<? extends LancetLaunchPlatformEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HAS_LANCET, false);
        builder.define(LANCET_UUID, "");
    }

    @Override
    public void tick() {
        super.tick();
        this.setNoGravity(true);
        this.setDeltaMovement(0.0, 0.0, 0.0);
        this.move(MoverType.SELF, this.getDeltaMovement());

        if (!this.level().isClientSide()) {
            keepLoadedLancetOnRail();
        }
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
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() && stack.is(ModItems.ZALA_LANCET.get())) {
            return ZalaLancetItem.placeOnPlatform(player, stack, this);
        }

        if (!player.isShiftKeyDown() && hasLoadedLancet()
                && stack.is(com.atsuishio.superbwarfare.init.ModItems.MONITOR.get())) {
            ZalaLancetEntity lancet = getLoadedLancet();
            if (lancet != null && !lancet.isRemoved()) {
                return lancet.interact(player, hand);
            }
        }

        if (!player.isShiftKeyDown() && stack.isEmpty()) {
            if (!this.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer,
                        new OpenLancetPlatformScreenPacket(this.getId(), hasLoadedLancet()));
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }

        if (player.isShiftKeyDown() && stack.isEmpty()) {
            if (!this.level().isClientSide()) {
                unloadLancetToInventory(player);
                if (!player.getAbilities().instabuild) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.LANCET_LAUNCH_PLATFORM.get()));
                }
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compound) {
        if (compound.contains("HasLancet")) {
            this.entityData.set(HAS_LANCET, compound.getBoolean("HasLancet"));
        }
        if (compound.contains("LancetUuid")) {
            this.entityData.set(LANCET_UUID, compound.getString("LancetUuid"));
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compound) {
        compound.putBoolean("HasLancet", hasLoadedLancet());
        compound.putString("LancetUuid", this.entityData.get(LANCET_UUID));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public boolean hasLoadedLancet() {
        return this.entityData.get(HAS_LANCET);
    }

    public InteractionResult loadLancet(Player player, ItemStack stack) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (hasLoadedLancet()) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.wrbdrones.lancet_platform.loaded"), true);
            }
            return InteractionResult.CONSUME;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }

        ZalaLancetEntity lancet = new ZalaLancetEntity(ModEntityTypes.ZALA_LANCET.get(), level);
        positionLancetOnRail(lancet);
        lancet.applySpawnLoadout();
        level.addFreshEntity(lancet);

        this.entityData.set(HAS_LANCET, true);
        this.entityData.set(LANCET_UUID, lancet.getStringUUID());

        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.wrbdrones.lancet_platform.loaded"), true);
        }
        return InteractionResult.CONSUME;
    }

    public InteractionResult launchLoadedLancet(Player player) {
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ZalaLancetEntity lancet = getLoadedLancet();
        if (lancet == null || lancet.isRemoved()) {
            clearLoadedLancet();
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.wrbdrones.lancet_platform.empty"), true);
            }
            return InteractionResult.CONSUME;
        }

        positionLancetOnRail(lancet);
        lancet.launchFromPlatform();
        clearLoadedLancet();
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.wrbdrones.lancet_platform.launched"), true);
        }
        return InteractionResult.CONSUME;
    }

    private void unloadLancetToInventory(Player player) {
        ZalaLancetEntity lancet = getLoadedLancet();
        if (lancet != null && !lancet.isRemoved() && !lancet.isStarted()) {
            if (!player.getAbilities().instabuild) {
                player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.ZALA_LANCET.get()));
            }
            lancet.discard();
        }
        clearLoadedLancet();
    }

    private void keepLoadedLancetOnRail() {
        if (!hasLoadedLancet()) {
            return;
        }

        ZalaLancetEntity lancet = getLoadedLancet();
        if (lancet == null || lancet.isRemoved()) {
            clearLoadedLancet();
            return;
        }
        if (lancet.isStarted()) {
            clearLoadedLancet();
            return;
        }

        positionLancetOnRail(lancet);
    }

    private void positionLancetOnRail(ZalaLancetEntity lancet) {
        var pos = ZalaLancetEntity.getLaunchPlatformDronePosition(this.position(), this.getYRot());
        lancet.setPos(pos.x, pos.y, pos.z);
        lancet.setYRot(this.getYRot());
        lancet.yRotO = this.getYRot();
        lancet.setXRot(0.0f);
        lancet.xRotO = 0.0f;
        lancet.setDeltaMovement(0.0, 0.0, 0.0);
    }

    private ZalaLancetEntity getLoadedLancet() {
        UUID uuid = getLoadedLancetUuid();
        if (uuid == null || !(this.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity entity = level.getEntity(uuid);
        return entity instanceof ZalaLancetEntity lancet ? lancet : null;
    }

    private UUID getLoadedLancetUuid() {
        String raw = this.entityData.get(LANCET_UUID);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void clearLoadedLancet() {
        this.entityData.set(HAS_LANCET, false);
        this.entityData.set(LANCET_UUID, "");
    }
}
