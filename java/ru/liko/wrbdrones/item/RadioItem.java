package ru.liko.wrbdrones.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.liko.wrbdrones.entity.Shahed136Entity;
import ru.liko.wrbdrones.network.ModNetworking;
import ru.liko.wrbdrones.network.OpenRadioScreenPacket;

import java.util.List;
import java.util.UUID;

public class RadioItem extends Item {

    public static final String TAG_LINKED = "Linked";
    public static final String TAG_LINKED_SHAHED_UUID = "LinkedShahedUUID";
    public static final String TAG_TARGET_X = "TargetX";
    public static final String TAG_TARGET_Y = "TargetY";
    public static final String TAG_TARGET_Z = "TargetZ";
    public static final String TAG_DRONE_X = "DroneX";
    public static final String TAG_DRONE_Y = "DroneY";
    public static final String TAG_DRONE_Z = "DroneZ";
    public static final String TAG_DRONE_LAUNCHED = "DroneLaunched";

    public RadioItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            CompoundTag tag = stack.getOrCreateTag();

            if (tag.getBoolean(TAG_LINKED)) {
                String shahedUuidStr = tag.getString(TAG_LINKED_SHAHED_UUID);
                if (!shahedUuidStr.isEmpty()) {
                    Shahed136Entity shahed = findShahed(level, shahedUuidStr);
                    if (shahed != null && !shahed.isLaunched()) {
                        ModNetworking.sendToPlayer(serverPlayer, new OpenRadioScreenPacket(
                            shahed.getId(),
                            (int) tag.getFloat(TAG_TARGET_X),
                            (int) tag.getFloat(TAG_TARGET_Y),
                            (int) tag.getFloat(TAG_TARGET_Z),
                            (int) shahed.getX(),
                            (int) shahed.getY(),
                            (int) shahed.getZ()
                        ));
                        return InteractionResultHolder.success(stack);
                    } else if (shahed == null) {
                        tag.putBoolean(TAG_LINKED, false);
                        tag.putString(TAG_LINKED_SHAHED_UUID, "");
                        player.displayClientMessage(
                            Component.translatable("item.wrbdrones.radio.shahed_not_found")
                                .withStyle(ChatFormatting.RED), true);
                    } else {
                        player.displayClientMessage(
                            Component.translatable("item.wrbdrones.radio.already_launched")
                                .withStyle(ChatFormatting.YELLOW), true);
                    }
                }
            } else {
                player.displayClientMessage(
                    Component.translatable("item.wrbdrones.radio.not_linked")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player,
                                                            @NotNull net.minecraft.world.entity.LivingEntity target,
                                                            @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public static InteractionResult onInteractWithShahed(Player player, Shahed136Entity shahed, ItemStack radioStack) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        CompoundTag tag = radioStack.getOrCreateTag();

        if (player.isShiftKeyDown()) {
            tag.putBoolean(TAG_LINKED, false);
            tag.putString(TAG_LINKED_SHAHED_UUID, "");
            shahed.setLinkedRadioUUID("");

            player.displayClientMessage(
                Component.translatable("item.wrbdrones.radio.unlinked")
                    .withStyle(ChatFormatting.YELLOW), true);
        } else {
            String shahedUuid = shahed.getStringUUID();
            tag.putBoolean(TAG_LINKED, true);
            tag.putString(TAG_LINKED_SHAHED_UUID, shahedUuid);
            tag.putFloat(TAG_TARGET_X, (float) player.getX());
            tag.putFloat(TAG_TARGET_Y, (float) player.getY());
            tag.putFloat(TAG_TARGET_Z, (float) player.getZ());

            shahed.setLinkedRadioUUID(radioStack.getOrCreateTag().hasUUID("RadioUUID") 
                ? radioStack.getOrCreateTag().getUUID("RadioUUID").toString() 
                : UUID.randomUUID().toString());

            player.displayClientMessage(
                Component.translatable("item.wrbdrones.radio.linked")
                    .withStyle(ChatFormatting.GREEN), true);
        }

        return InteractionResult.SUCCESS;
    }

    @Nullable
    private Shahed136Entity findShahed(Level level, String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(uuid);
                if (entity instanceof Shahed136Entity shahed) {
                    return shahed;
                }
            }
        } catch (IllegalArgumentException e) {
            // Invalid UUID
        }
        return null;
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        
        if (!level.isClientSide() && level.getGameTime() % 10 == 0) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.getBoolean(TAG_LINKED)) {
                String uuidStr = tag.getString(TAG_LINKED_SHAHED_UUID);
                Shahed136Entity shahed = findShahed(level, uuidStr);
                if (shahed != null) {
                    tag.putFloat(TAG_DRONE_X, (float) shahed.getX());
                    tag.putFloat(TAG_DRONE_Y, (float) shahed.getY());
                    tag.putFloat(TAG_DRONE_Z, (float) shahed.getZ());
                    tag.putBoolean(TAG_DRONE_LAUNCHED, shahed.isLaunched());
                    
                    if (shahed.isLaunched()) {
                        tag.putFloat(TAG_TARGET_X, (float) shahed.getTargetPos().x);
                        tag.putFloat(TAG_TARGET_Y, (float) shahed.getTargetPos().y);
                        tag.putFloat(TAG_TARGET_Z, (float) shahed.getTargetPos().z);
                    }
                } else {
                    // Auto-unlink if drone is dead or not found
                    tag.putBoolean(TAG_LINKED, false);
                    tag.putString(TAG_LINKED_SHAHED_UUID, "");
                    if (entity instanceof Player player) {
                         player.displayClientMessage(
                            Component.translatable("item.wrbdrones.radio.unlinked")
                                .withStyle(ChatFormatting.YELLOW), true);
                    }
                }
            }
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, 
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.getBoolean(TAG_LINKED)) {
            boolean launched = tag.getBoolean(TAG_DRONE_LAUNCHED);
            
            if (launched) {
                tooltip.add(Component.literal("§a● ЗАПУЩЕН").withStyle(ChatFormatting.GREEN));
            } else {
                tooltip.add(Component.literal("§e● ОЖИДАНИЕ").withStyle(ChatFormatting.YELLOW));
            }

            float droneX = tag.getFloat(TAG_DRONE_X);
            float droneY = tag.getFloat(TAG_DRONE_Y);
            float droneZ = tag.getFloat(TAG_DRONE_Z);
            
            tooltip.add(Component.literal("§7Дрон: §f" + (int)droneX + " " + (int)droneY + " " + (int)droneZ));

            float targetX = tag.getFloat(TAG_TARGET_X);
            float targetY = tag.getFloat(TAG_TARGET_Y);
            float targetZ = tag.getFloat(TAG_TARGET_Z);
            tooltip.add(Component.literal("§7Цель: §c" + (int)targetX + " " + (int)targetY + " " + (int)targetZ));
            
            if (launched) {
                double dx = targetX - droneX;
                double dy = targetY - droneY;
                double dz = targetZ - droneZ;
                double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                tooltip.add(Component.literal("§7До цели: §b" + (int)dist + " м"));
            }
        } else {
            tooltip.add(Component.literal("§8Не привязан"));
        }

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("§8ПКМ по Shahed - привязать"));
        tooltip.add(Component.literal("§8Shift+ПКМ - отвязать"));
        tooltip.add(Component.literal("§8ПКМ в воздух - открыть меню"));
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_LINKED);
    }
}
