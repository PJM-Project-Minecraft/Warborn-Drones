package ru.liko.wrbdrones.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Декой (обманка) игрока - полная визуальная копия реального игрока.
 */
public class PlayerDecoyEntity extends LivingEntity {

    private static final EntityDataAccessor<String> OWNER_UUID = SynchedEntityData.defineId(PlayerDecoyEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(PlayerDecoyEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Byte> PLAYER_MODEL_PARTS = SynchedEntityData
            .defineId(PlayerDecoyEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> IS_CROUCHING = SynchedEntityData.defineId(PlayerDecoyEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_SITTING = SynchedEntityData.defineId(PlayerDecoyEntity.class,
            EntityDataSerializers.BOOLEAN);

    @Nullable
    private GameProfile cachedProfile;

    @Nullable
    private UUID cachedDecoyProfileId;

    @Nullable
    private UUID ownerUUID;

    private final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
    private final NonNullList<ItemStack> handItems = NonNullList.withSize(2, ItemStack.EMPTY);

    private float storedHealth = 20.0f;
    private int syncCooldown = 0;
    private boolean profileSent = false;

    public PlayerDecoyEntity(EntityType<? extends PlayerDecoyEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(false);
        this.noPhysics = false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, "");
        builder.define(OWNER_NAME, "Steve");
        builder.define(PLAYER_MODEL_PARTS, (byte) 0x7F);
        builder.define(IS_CROUCHING, false);
        builder.define(IS_SITTING, false);
    }

    public void initFromPlayer(ServerPlayer player) {
        this.ownerUUID = player.getUUID();
        this.entityData.set(OWNER_UUID, player.getStringUUID());
        this.entityData.set(OWNER_NAME, player.getName().getString());

        GameProfile profile = player.getGameProfile();
        this.cachedDecoyProfileId = createDecoyProfileId(player.getUUID());
        this.cachedProfile = copyGameProfile(profile, this.cachedDecoyProfileId);

        // Устанавливаем начальную позицию и вращение
        this.setPos(player.getX(), player.getY(), player.getZ());
        this.setYRot(player.getYRot());
        this.setXRot(player.getXRot());
        this.yHeadRot = player.yHeadRot;
        this.yBodyRot = player.yBodyRot;

        copyEquipment(player);
        copyPlayerState(player);

        this.storedHealth = player.getHealth();
        this.setHealth(player.getHealth());

        this.entityData.set(PLAYER_MODEL_PARTS, getPlayerModelCustomisation(player));
    }

    private static UUID createDecoyProfileId(UUID ownerId) {
        return UUID.nameUUIDFromBytes(("wrbdrones:decoy:" + ownerId).getBytes(StandardCharsets.UTF_8));
    }

    private GameProfile copyGameProfile(GameProfile original, UUID decoyProfileId) {
        GameProfile copy = new GameProfile(decoyProfileId, original.getName());

        Collection<Property> textures = original.getProperties().get("textures");
        for (Property property : textures) {
            // В NeoForge/Vanilla 1.20+ Property это record или имеет методы value(), signature()
            // Но конструктор тот же: name, value, signature
            copy.getProperties().put("textures", property);
        }

        return copy;
    }

    public void copyEquipment(Player player) {
        for (int i = 0; i < 4; i++) {
            EquipmentSlot slot = EquipmentSlot.values()[i + 2];
            this.armorItems.set(i, player.getItemBySlot(slot).copy());
        }

        this.handItems.set(0, player.getMainHandItem().copy());
        this.handItems.set(1, player.getOffhandItem().copy());
    }

    /**
     * Копирует состояние игрока (поза).
     * ВАЖНО: Не копирует позицию и вращение, чтобы декой оставался на месте.
     */
    public void copyPlayerState(Player player) {
        // Если игрок управляет дроном (сидит на дроне), декой должен сидеть
        if (player.getVehicle() instanceof AddonDroneEntity) {
             this.setDecoySitting(true);
             this.entityData.set(IS_CROUCHING, false);
             return;
        }

        // Если игрок просто присел (не на дроне)
        boolean crouching = player.isCrouching();
        this.entityData.set(IS_CROUCHING, crouching);
        
        if (crouching) {
            this.setPose(Pose.CROUCHING);
            this.setDecoySitting(false);
        } else {
            this.setPose(Pose.STANDING);
            this.setDecoySitting(false);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.isDecoySitting()) {
            this.setPose(Pose.SITTING);
        }

        if (!this.level().isClientSide()) {
            ServerLevel serverLevel = (ServerLevel) this.level();

            ServerPlayer owner = getOwnerPlayer(serverLevel);
            if (owner == null || owner.isRemoved()) {
                this.discard();
                return;
            }

            if (syncCooldown > 0) {
                syncCooldown--;
            }

            if (syncCooldown == 0) {
                copyEquipment(owner);
                copyPlayerState(owner);
                syncCooldown = 20;
            }

            if (!profileSent && serverLevel.getServer().getTickCount() % 5 == 0) {
                broadcastPlayerInfo(serverLevel);
                profileSent = true;
            }
        }
    }

    private static byte getPlayerModelCustomisation(Player player) {
        try {
            java.lang.reflect.Field field = Player.class.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            EntityDataAccessor<Byte> accessor = (EntityDataAccessor<Byte>) field.get(null);
            return player.getEntityData().get(accessor);
        } catch (Exception e) {
            return (byte) 0x7F;
        }
    }

    private void broadcastPlayerInfo(ServerLevel level) {
        if (cachedProfile == null)
            return;

        // Временно отключено из-за сложности создания пакета с фейковым профилем
        // Клиенты увидят скин Стива или Алекса, если не найдут UUID
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide()) {
            return false;
        }

        if (this.isInvulnerableTo(source)) {
            return false;
        }

        ServerLevel serverLevel = (ServerLevel) this.level();
        ServerPlayer owner = getOwnerPlayer(serverLevel);

        if (owner != null && !owner.isRemoved()) {
            DamageSource forwardedSource = source;
            if (!(source.getEntity() instanceof PlayerDecoyEntity)) {
                forwardedSource = new DamageSource(
                        source.typeHolder(),
                        source.getDirectEntity(),
                        this,
                        source.getSourcePosition());
            }
            boolean damaged = owner.hurt(forwardedSource, amount);

            if (damaged) {
                this.setHealth(owner.getHealth());
                this.storedHealth = owner.getHealth();

                this.hurtMarked = true;
                this.hurtTime = 10;
                this.hurtDuration = 10;
            }

            if (owner.isDeadOrDying()) {
                this.discard();
            }

            return damaged;
        }

        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            ServerLevel serverLevel = (ServerLevel) this.level();
            ServerPlayer owner = getOwnerPlayer(serverLevel);

            if (owner != null && !owner.isDeadOrDying()) {
                owner.hurt(source, Float.MAX_VALUE);
            }
        }

        super.die(source);
    }

    @Nullable
    private ServerPlayer getOwnerPlayer(ServerLevel level) {
        if (ownerUUID == null) {
            String uuidStr = this.entityData.get(OWNER_UUID);
            if (uuidStr.isEmpty())
                return null;
            try {
                ownerUUID = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        Player player = level.getPlayerByUUID(ownerUUID);
        return player instanceof ServerPlayer sp ? sp : null;
    }

    @Override
    public @NotNull Iterable<ItemStack> getArmorSlots() {
        return this.armorItems;
    }

    @Override
    public @NotNull ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND, OFFHAND -> this.handItems.get(slot.getIndex());
            case FEET, LEGS, CHEST, HEAD, BODY -> this.armorItems.get(slot.getIndex());
            default -> ItemStack.EMPTY; // Fallback
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, @NotNull ItemStack stack) {
        this.verifyEquippedItem(stack);
        switch (slot) {
            case MAINHAND, OFFHAND -> this.handItems.set(slot.getIndex(), stack);
            case FEET, LEGS, CHEST, HEAD, BODY -> this.armorItems.set(slot.getIndex(), stack);
        }
    }

    @Override
    public @NotNull HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.putString("OwnerUUID", this.entityData.get(OWNER_UUID));
        tag.putString("OwnerName", this.entityData.get(OWNER_NAME));
        tag.putByte("ModelParts", this.entityData.get(PLAYER_MODEL_PARTS));
        tag.putFloat("StoredHealth", this.storedHealth);

        ListTag armorTag = new ListTag();
        for (ItemStack stack : this.armorItems) {
            armorTag.add(stack.save(this.level().registryAccess(), new CompoundTag()));
        }
        tag.put("ArmorItems", armorTag);

        ListTag handTag = new ListTag();
        for (ItemStack stack : this.handItems) {
            handTag.add(stack.save(this.level().registryAccess(), new CompoundTag()));
        }
        tag.put("HandItems", handTag);

        if (cachedProfile != null) {
            CompoundTag profileTag = new CompoundTag();
            profileTag.putString("Id", cachedProfile.getId().toString());
            profileTag.putString("Name", cachedProfile.getName());

            Collection<Property> textures = cachedProfile.getProperties().get("textures");
            if (!textures.isEmpty()) {
                Property texture = textures.iterator().next();
                profileTag.putString("TextureValue", texture.value());
                if (texture.signature() != null) {
                    profileTag.putString("TextureSignature", texture.signature());
                }
            }
            tag.put("GameProfile", profileTag);
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("OwnerUUID")) {
            String uuid = tag.getString("OwnerUUID");
            this.entityData.set(OWNER_UUID, uuid);
            if (!uuid.isEmpty()) {
                try {
                    this.ownerUUID = UUID.fromString(uuid);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (tag.contains("OwnerName")) {
            this.entityData.set(OWNER_NAME, tag.getString("OwnerName"));
        }

        if (tag.contains("ModelParts")) {
            this.entityData.set(PLAYER_MODEL_PARTS, tag.getByte("ModelParts"));
        }

        if (tag.contains("StoredHealth")) {
            this.storedHealth = tag.getFloat("StoredHealth");
            this.setHealth(this.storedHealth);
        }

        if (tag.contains("ArmorItems")) {
            ListTag armorTag = tag.getList("ArmorItems", 10);
            for (int i = 0; i < Math.min(armorTag.size(), 4); i++) {
                this.armorItems.set(i, ItemStack.parseOptional(this.level().registryAccess(), armorTag.getCompound(i)));
            }
        }

        if (tag.contains("HandItems")) {
            ListTag handTag = tag.getList("HandItems", 10);
            for (int i = 0; i < Math.min(handTag.size(), 2); i++) {
                this.handItems.set(i, ItemStack.parseOptional(this.level().registryAccess(), handTag.getCompound(i)));
            }
        }

        if (tag.contains("GameProfile")) {
            CompoundTag profileTag = tag.getCompound("GameProfile");
            UUID profileId = UUID.fromString(profileTag.getString("Id"));
            String profileName = profileTag.getString("Name");

            this.cachedProfile = new GameProfile(profileId, profileName);

            if (profileTag.contains("TextureValue")) {
                String value = profileTag.getString("TextureValue");
                String signature = profileTag.contains("TextureSignature")
                        ? profileTag.getString("TextureSignature")
                        : null;
                this.cachedProfile.getProperties().put("textures",
                        new Property("textures", value, signature));
            }
        }
    }

    public GameProfile getGameProfile() {
        if (cachedProfile != null) {
            return cachedProfile;
        }

        String uuid = this.entityData.get(OWNER_UUID);
        String name = this.entityData.get(OWNER_NAME);

        if (!uuid.isEmpty()) {
            try {
                UUID ownerId = UUID.fromString(uuid);
                return new GameProfile(createDecoyProfileId(ownerId), name);
            } catch (IllegalArgumentException ignored) {
            }
        }

        return new GameProfile(UUID.randomUUID(), name);
    }

    public byte getPlayerModelParts() {
        return this.entityData.get(PLAYER_MODEL_PARTS);
    }

    public boolean isDecoyCrouching() {
        return this.entityData.get(IS_CROUCHING);
    }

    public boolean isDecoySitting() {
        return this.entityData.get(IS_SITTING);
    }

    public void setDecoySitting(boolean sitting) {
        this.entityData.set(IS_SITTING, sitting);
        if (sitting) {
            this.setPose(Pose.SITTING);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (!this.level().isClientSide() && cachedProfile != null) {
            ServerLevel level = (ServerLevel) this.level();
            for (ServerPlayer player : level.players()) {
                player.connection.send(new ClientboundPlayerInfoRemovePacket(
                        List.of(cachedProfile.getId())));
            }
        }
        super.remove(reason);
    }

    @Nullable
    public UUID getOwnerUUID() {
        if (ownerUUID != null) {
            return ownerUUID;
        }
        // На клиенте читаем из синхронизированных данных
        String uuidStr = this.entityData.get(OWNER_UUID);
        if (!uuidStr.isEmpty()) {
            try {
                ownerUUID = UUID.fromString(uuidStr);
                return ownerUUID;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    public void syncHealthFromOwner(float health) {
        this.setHealth(health);
        this.storedHealth = health;
    }
}
