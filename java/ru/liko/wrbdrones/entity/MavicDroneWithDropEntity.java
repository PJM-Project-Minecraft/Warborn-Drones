package ru.liko.wrbdrones.entity;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.Wrbdrones;

import java.util.Set;

/**
 * Mavic дрон с модулем сброса - для разведки и сброса небоевых предметов
 * Разрешены: медицинские наборы, дымовые гранаты (RGO), мины BLU-43
 * Максимум 2 гранаты
 */
public class MavicDroneWithDropEntity extends AddonDroneEntity {

    private static final Set<String> ALLOWED_ATTACHMENTS = Set.of(
        "superbwarfare:medical_kit",
        "superbwarfare:rgo_grenade",
        "superbwarfare:blu_43_mine"
    );
    
    private static final int MAX_GRENADES = 2;

    public MavicDroneWithDropEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public ResourceLocation getModelResource() {
        return Wrbdrones.id("geo/mavic_drone_with_drop.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource() {
        return Wrbdrones.id("textures/entity/mavic_drone.png");
    }

    @Override
    protected Set<String> getAllowedAttachments() {
        return ALLOWED_ATTACHMENTS;
    }
    
    @Override
    @SuppressWarnings("null")
    public @NotNull InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        @SuppressWarnings("null")
        ItemStack stack = player.getItemInHand(hand);
        String itemId = getItemId(stack);
        
        // Специальная обработка для гранат - проверяем ограничение на сервере ДО вызова super
        if (!stack.isEmpty() && !player.isCrouching() && "superbwarfare:rgo_grenade".equals(itemId) && ALLOWED_ATTACHMENTS.contains(itemId)) {
            // Проверка должна работать только на сервере
            if (!this.level().isClientSide()) {
                var ammoAccessor = getAmmoAccessor();
                if (ammoAccessor != null) {
                    int currentAmmo = this.getEntityData().get(ammoAccessor);
                    
                    // Если уже есть 2 гранаты, не позволяем добавить больше
                    if (currentAmmo >= MAX_GRENADES) {
                        @SuppressWarnings("null")
                        @NotNull Component message = Component.translatable("tips.wrbdrones.drone.max_grenades_reached")
                                .withStyle(ChatFormatting.RED);
                        player.displayClientMessage(message, true);
                        return InteractionResult.sidedSuccess(false);
                    }
                }
            } else {
                // На клиенте просто проверяем, что не превышен лимит (для оптимизации)
                var ammoAccessor = getAmmoAccessor();
                if (ammoAccessor != null) {
                    int currentAmmo = this.getEntityData().get(ammoAccessor);
                    if (currentAmmo >= MAX_GRENADES) {
                        return InteractionResult.sidedSuccess(true);
                    }
                }
            }
        }
        
        // Вызываем родительский метод для обработки
        InteractionResult result = super.interact(player, hand);
        
        // После добавления гранаты ограничиваем AMMO и MAX_AMMO до 2 (только на сервере)
        if (!stack.isEmpty() && "superbwarfare:rgo_grenade".equals(itemId) && !this.level().isClientSide()) {
            var ammoAccessor = getAmmoAccessor();
            var maxAmmoAccessor = getMaxAmmoAccessor();
            
            if (ammoAccessor != null && maxAmmoAccessor != null) {
                int currentAmmo = this.getEntityData().get(ammoAccessor);
                int maxAmmo = this.getEntityData().get(maxAmmoAccessor);
                
                // Если AMMO больше 2, уменьшаем до 2 и возвращаем лишние гранаты игроку
                if (currentAmmo > MAX_GRENADES) {
                    int excess = currentAmmo - MAX_GRENADES;
                    this.getEntityData().set(ammoAccessor, MAX_GRENADES);
                    
                    // Возвращаем лишние гранаты игроку
                    for (int i = 0; i < excess; i++) {
                        if (!player.isCreative()) {
                            net.minecraftforge.items.ItemHandlerHelper.giveItemToPlayer(player, stack.copyWithCount(1));
                        }
                    }
                }
                
                // Всегда устанавливаем MAX_AMMO = 2 для гранат
                if (maxAmmo != MAX_GRENADES) {
                    this.getEntityData().set(maxAmmoAccessor, MAX_GRENADES);
                }
            }
        }
        
        return result;
    }
    
    @Override
    public void baseTick() {
        super.baseTick();
        
        // Постоянно ограничиваем гранаты до 2 на сервере
        if (!this.level().isClientSide()) {
            var ammoAccessor = getAmmoAccessor();
            var maxAmmoAccessor = getMaxAmmoAccessor();
            
            if (ammoAccessor != null && maxAmmoAccessor != null) {
                // Проверяем, что это граната
                try {
                    java.lang.reflect.Field currentItemField =
                            com.atsuishio.superbwarfare.entity.vehicle.DroneEntity.class.getDeclaredField("currentItem");
                    currentItemField.setAccessible(true);
                    ItemStack currentItem = (ItemStack) currentItemField.get(this);
                    
                    if (currentItem != null && !currentItem.isEmpty()) {
                        String itemId = getItemId(currentItem);
                        if ("superbwarfare:rgo_grenade".equals(itemId)) {
                            int currentAmmo = this.getEntityData().get(ammoAccessor);
                            int maxAmmo = this.getEntityData().get(maxAmmoAccessor);
                            
                            // Если AMMO больше 2, уменьшаем до 2
                            if (currentAmmo > MAX_GRENADES) {
                                this.getEntityData().set(ammoAccessor, MAX_GRENADES);
                            }
                            
                            // Всегда устанавливаем MAX_AMMO = 2 для гранат
                            if (maxAmmo != MAX_GRENADES) {
                                this.getEntityData().set(maxAmmoAccessor, MAX_GRENADES);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            }
        }
    }
}
