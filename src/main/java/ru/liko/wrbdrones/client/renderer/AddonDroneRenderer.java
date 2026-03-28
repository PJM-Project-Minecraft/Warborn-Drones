package ru.liko.wrbdrones.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.client.model.AddonDroneModel;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.entity.FpvDroneEntity;
import ru.liko.wrbdrones.entity.MavicDroneWithDropEntity;
import com.atsuishio.superbwarfare.tools.NBTTool;

import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.GeoBone;

public class AddonDroneRenderer<T extends AddonDroneEntity> extends GeoEntityRenderer<T> {

    public AddonDroneRenderer(EntityRendererProvider.Context context) {
        super(context, new AddonDroneModel<>());
        this.shadowRadius = 0.25f;
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource bufferSource,
            float partialTick) {
        return RenderType.entityTranslucent(getTextureLocation(animatable));
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
            @NotNull MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYaw(partialTicks)));
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getBodyPitch(partialTicks)));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getRoll(partialTicks)));

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);

        poseStack.popPose();
    }

    @Override
    public void renderRecursively(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType,
            MultiBufferSource bufferSource,
            VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
            int color) {
        float red = (float) ((color >> 16) & 0xFF) / 255f;
        float green = (float) ((color >> 8) & 0xFF) / 255f;
        float blue = (float) (color & 0xFF) / 255f;
        float alpha = (float) ((color >> 24) & 0xFF) / 255f;
        String name = bone.getName();

        // Управление видимостью групп снарядов для ФПВ дрона
        if (animatable instanceof FpvDroneEntity) {
            // Проверяем группы снарядов: snarad_mortar, snarad_rpg-7tbg, snarad_rpg-7vm
            if (name.startsWith("snarad_")) {
                String currentAmmoType = getCurrentAmmoType(animatable);
                boolean shouldShow = false;

                if ("snarad_mortar".equals(name)) {
                    // Показываем только если текущий снаряд - минометный
                    shouldShow = "superbwarfare:mortar_shell".equals(currentAmmoType);
                } else if ("snarad_rpg-7tbg".equals(name)) {
                    // Показываем только если текущий снаряд - RPG-7 TBG
                    shouldShow = "superbwarfare:rpg_rocket_tbg".equals(currentAmmoType);
                } else if ("snarad_rpg-7vm".equals(name)) {
                    // Показываем только если текущий снаряд - RPG-7 VM
                    shouldShow = "superbwarfare:rpg_rocket_standard".equals(currentAmmoType);
                }

                // Показываем группу только если есть боеприпасы и тип совпадает
                boolean hasAmmo = hasAmmo(animatable);
                bone.setHidden(!hasAmmo || !shouldShow);
            }
        } else {
            // Для других дронов - старое поведение для кости "snarad"
            if ("snarad".equals(name)) {
                boolean hasAmmo = hasAmmo(animatable);
                bone.setHidden(!hasAmmo);
            }
        }

        // Управление видимостью гранат - применяется ТОЛЬКО для костей granade1 и
        // granade2
        if ("granade1".equals(name) || "granade2".equals(name)) {
            // Для мавика с модулем сброса
            if (animatable instanceof MavicDroneWithDropEntity) {
                // Проверяем, что это именно граната RGO
                boolean grenadeAttached = isGrenadeAttached(animatable);

                if (grenadeAttached) {
                    int ammo = getAmmoCount(animatable);

                    if ("granade1".equals(name)) {
                        // granade1 видна, если есть хотя бы 1 граната
                        bone.setHidden(ammo < 1);
                    } else if ("granade2".equals(name)) {
                        // granade2 видна, если есть 2 гранаты
                        bone.setHidden(ammo < 2);
                    }
                } else {
                    // Если это не граната RGO или нет боеприпасов, скрываем кости гранат
                    bone.setHidden(true);
                }
            } else {
                // Для всех остальных дронов (включая MavicDroneNoDropEntity и FpvDroneEntity)
                // скрываем гранаты
                bone.setHidden(true);
            }
        }

        // Анимация роторов
        if (!animatable.onGround() && isRotorBone(name)) {
            bone.setRotY((System.currentTimeMillis() % 36_000_000L) / 12f);
        }

        // Анимация камеры на основе pitch игрока
        // Применяем только к самой кости "camera", не к её дочерним костям
        if ("camera".equals(name)) {
            float cameraPitch = getControllerPitch(animatable, partialTick);
            // Применяем поворот камеры по оси X (pitch)
            // Ограничиваем угол поворота от -90 до 90 градусов
            float clampedPitch = Mth.clamp(cameraPitch, -90.0f, 90.0f);
            // Конвертируем в радианы и применяем к кости
            // Используем отрицательное значение, так как в GeckoLib поворот по X
            // инвертирован
            bone.setRotX((float) Math.toRadians(-clampedPitch));
        }

        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, color);
    }

    /**
     * Получает количество боеприпасов у дрона
     */
    private int getAmmoCount(T entity) {
        try {
            var ammoAccessor = ru.liko.wrbdrones.entity.AddonDroneEntity.getAmmoAccessor();
            if (ammoAccessor != null) {
                return entity.getEntityData().get(ammoAccessor);
            } else {
                // Пробуем прямой доступ через VehicleEntity.AMMO
                try {
                    Class<?> vehicleEntityClass = com.atsuishio.superbwarfare.entity.vehicle.DroneEntity.class
                            .getSuperclass();
                    if (vehicleEntityClass != null) {
                        java.lang.reflect.Field ammoField = vehicleEntityClass.getDeclaredField("AMMO");
                        ammoField.setAccessible(true);
                        net.minecraft.network.syncher.EntityDataAccessor<Integer> ammoAccessorDirect = (net.minecraft.network.syncher.EntityDataAccessor<Integer>) ammoField
                                .get(null);
                        if (ammoAccessorDirect != null) {
                            return entity.getEntityData().get(ammoAccessorDirect);
                        }
                    }
                } catch (Exception e2) {
                    try {
                        Class<?> vehicleEntityClass = Class
                                .forName("com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
                        java.lang.reflect.Field ammoField = vehicleEntityClass.getDeclaredField("AMMO");
                        ammoField.setAccessible(true);
                        net.minecraft.network.syncher.EntityDataAccessor<Integer> ammoAccessorDirect = (net.minecraft.network.syncher.EntityDataAccessor<Integer>) ammoField
                                .get(null);
                        if (ammoAccessorDirect != null) {
                            return entity.getEntityData().get(ammoAccessorDirect);
                        }
                    } catch (Exception e3) {
                        // Игнорируем ошибки
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        return 0;
    }

    /**
     * Проверяет, прикреплена ли граната к дрону
     * Использует DISPLAY_ENTITY для проверки на клиенте (синхронизируется через
     * EntityData)
     */
    private boolean isGrenadeAttached(T entity) {
        try {
            // Используем DISPLAY_ENTITY для проверки типа боеприпаса (синхронизируется
            // между клиентом и сервером)
            var displayEntityAccessor = ru.liko.wrbdrones.entity.AddonDroneEntity.getDisplayEntityAccessor();
            if (displayEntityAccessor != null) {
                String displayEntity = entity.getEntityData().get(displayEntityAccessor);
                if (displayEntity != null && !displayEntity.isEmpty()) {
                    // Проверяем, что это граната (SBW или WRB Explosives)
                    if (displayEntity.contains("rgo_grenade") || 
                        displayEntity.equals("warbornexplosives:grenade")) {
                        return true;
                    }
                }
            } else {
                // Fallback на currentItem (только на сервере)
                if (!entity.level().isClientSide()) {
                    initCurrentItemField();
                    if (CURRENT_ITEM_FIELD != null) {
                        com.atsuishio.superbwarfare.entity.vehicle.DroneEntity droneEntity = (com.atsuishio.superbwarfare.entity.vehicle.DroneEntity) entity;
                        net.minecraft.world.item.ItemStack currentItem = (net.minecraft.world.item.ItemStack) CURRENT_ITEM_FIELD
                                .get(droneEntity);

                        if (currentItem != null && !currentItem.isEmpty()) {
                            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                    .getKey(currentItem.getItem());
                            if (key != null) {
                                String keyStr = key.toString();
                                if ("superbwarfare:rgo_grenade".equals(keyStr) ||
                                    "warbornexplosives:rgo".equals(keyStr) ||
                                    "warbornexplosives:rgn".equals(keyStr)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        return false;
    }

    // Кэш для поля currentItem через рефлексию
    private static java.lang.reflect.Field CURRENT_ITEM_FIELD = null;
    private static boolean currentItemFieldInitialized = false;

    private static synchronized void initCurrentItemField() {
        if (currentItemFieldInitialized)
            return;

        try {
            CURRENT_ITEM_FIELD = com.atsuishio.superbwarfare.entity.vehicle.DroneEntity.class
                    .getDeclaredField("currentItem");
            CURRENT_ITEM_FIELD.setAccessible(true);
            currentItemFieldInitialized = true;
        } catch (Exception e) {
            currentItemFieldInitialized = true;
        }
    }

    // Кэш для DISPLAY_ENTITY через рефлексию
    private static java.lang.reflect.Field DISPLAY_ENTITY_FIELD = null;
    private static boolean displayEntityFieldInitialized = false;

    private static synchronized void initDisplayEntityField() {
        if (displayEntityFieldInitialized)
            return;

        try {
            DISPLAY_ENTITY_FIELD = com.atsuishio.superbwarfare.entity.vehicle.DroneEntity.class
                    .getDeclaredField("DISPLAY_ENTITY");
            DISPLAY_ENTITY_FIELD.setAccessible(true);
            displayEntityFieldInitialized = true;
        } catch (Exception e) {
            displayEntityFieldInitialized = true;
        }
    }

    /**
     * Получает тип текущего боеприпаса (ID предмета)
     * Возвращает пустую строку, если боеприпасов нет
     */
    private String getCurrentAmmoType(T entity) {
        try {
            // Используем currentItem для определения типа боеприпаса (основной способ)
            initCurrentItemField();
            if (CURRENT_ITEM_FIELD != null) {
                try {
                    com.atsuishio.superbwarfare.entity.vehicle.DroneEntity droneEntity = (com.atsuishio.superbwarfare.entity.vehicle.DroneEntity) entity;
                    net.minecraft.world.item.ItemStack currentItem = (net.minecraft.world.item.ItemStack) CURRENT_ITEM_FIELD
                            .get(droneEntity);

                    if (currentItem != null && !currentItem.isEmpty()) {
                        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(currentItem.getItem());
                        if (key != null) {
                            return key.toString();
                        }
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            }

            // Альтернативная проверка через DISPLAY_ENTITY (если currentItem недоступен)
            // DISPLAY_ENTITY обычно содержит тип сущности, а не ID предмета, но попробуем
            var displayEntityAccessor = ru.liko.wrbdrones.entity.AddonDroneEntity.getDisplayEntityAccessor();
            if (displayEntityAccessor != null) {
                String displayEntity = entity.getEntityData().get(displayEntityAccessor);
                if (displayEntity != null && !displayEntity.isEmpty()) {
                    // DISPLAY_ENTITY может содержать ID предмета в некоторых случаях
                    if (displayEntity.contains(":") && displayEntity.startsWith("superbwarfare:")) {
                        return displayEntity;
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем ошибки
        }
        return "";
    }

    /**
     * Проверяет, есть ли у дрона боеприпасы через поле currentItem и DISPLAY_ENTITY
     */
    private boolean hasAmmo(T entity) {
        // Инициализируем поля
        initCurrentItemField();
        initDisplayEntityField();

        // Проверяем через DISPLAY_ENTITY (более надежный способ)
        if (DISPLAY_ENTITY_FIELD != null) {
            try {
                net.minecraft.network.syncher.EntityDataAccessor<String> displayEntityAccessor = (net.minecraft.network.syncher.EntityDataAccessor<String>) DISPLAY_ENTITY_FIELD
                        .get(null);
                var data = entity.getEntityData();
                String displayEntity = data.get(displayEntityAccessor);
                if (displayEntity != null && !displayEntity.isEmpty()) {
                    return true; // Если DISPLAY_ENTITY не пустой, значит есть боеприпасы
                }
            } catch (Exception e) {
                // Если произошла ошибка, пробуем другой способ
            }
        }

        // Альтернативная проверка через currentItem
        if (CURRENT_ITEM_FIELD != null) {
            try {
                com.atsuishio.superbwarfare.entity.vehicle.DroneEntity droneEntity = (com.atsuishio.superbwarfare.entity.vehicle.DroneEntity) entity;
                net.minecraft.world.item.ItemStack currentItem = (net.minecraft.world.item.ItemStack) CURRENT_ITEM_FIELD
                        .get(droneEntity);

                // Если currentItem не пустой, значит есть боеприпасы
                return currentItem != null && !currentItem.isEmpty();
            } catch (Exception e) {
                // Если произошла ошибка, считаем что боеприпасов нет
            }
        }

        return false;
    }

    private static boolean isRotorBone(String name) {
        return "wingFL".equals(name) || "wingFR".equals(name) || "wingBL".equals(name) || "wingBR".equals(name)
                || "rotor_fl".equals(name) || "rotor_fr".equals(name) || "rotor_bl".equals(name)
                || "rotor_br".equals(name)
                || "rotor_left".equals(name) || "rotor_right".equals(name);
    }

    /**
     * Получает pitch контроллера дрона на клиенте
     * Возвращает pitch игрока, если он управляет дроном через монитор
     */
    private float getControllerPitch(T animatable, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) {
            return 0.0f;
        }

        // Проверяем, управляет ли игрок этим дроном через монитор
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() == com.atsuishio.superbwarfare.init.ModItems.MONITOR.get()) {
            String linkedDrone = NBTTool.getTag(mainHand).getString("LinkedDrone");
            boolean isUsing = NBTTool.getTag(mainHand).getBoolean("Using");
            boolean isLinked = NBTTool.getTag(mainHand).getBoolean("Linked");

            if (isUsing && isLinked && linkedDrone.equals(animatable.getStringUUID())) {
                // Возвращаем pitch игрока с интерполяцией
                return Mth.lerp(partialTick, player.xRotO, player.getXRot());
            }
        }

        // Также проверяем инвентарь на наличие монитора
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == com.atsuishio.superbwarfare.init.ModItems.MONITOR.get()) {
                String linkedDrone = NBTTool.getTag(stack).getString("LinkedDrone");
                boolean isUsing = NBTTool.getTag(stack).getBoolean("Using");
                boolean isLinked = NBTTool.getTag(stack).getBoolean("Linked");

                if (isUsing && isLinked && linkedDrone.equals(animatable.getStringUUID())) {
                    return Mth.lerp(partialTick, player.xRotO, player.getXRot());
                }
            }
        }

        return 0.0f;
    }
}
