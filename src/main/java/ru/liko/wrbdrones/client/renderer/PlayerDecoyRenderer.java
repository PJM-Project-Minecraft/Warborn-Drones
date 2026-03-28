package ru.liko.wrbdrones.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.entity.PlayerDecoyEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Рендерер для декоя игрока.
 * Отображает декой как полноценную модель игрока с скином, бронёй и предметами
 * в руках.
 */
public class PlayerDecoyRenderer extends LivingEntityRenderer<PlayerDecoyEntity, PlayerModel<PlayerDecoyEntity>> {

    private static final Map<UUID, ResourceLocation> SKIN_CACHE = new ConcurrentHashMap<>();

    public PlayerDecoyRenderer(EntityRendererProvider.Context context) {
        this(context, false);
    }

    public PlayerDecoyRenderer(EntityRendererProvider.Context context, boolean slim) {
        super(context, new PlayerModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim),
                0.5F);

        this.addLayer(new HumanoidArmorLayer<>(this,
                new net.minecraft.client.model.HumanoidArmorModel<>(
                        context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                new net.minecraft.client.model.HumanoidArmorModel<>(
                        context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new ArrowLayer<>(context, this));
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new ElytraLayer<>(this, context.getModelSet()));
        this.addLayer(new SpinAttackEffectLayer<>(this, context.getModelSet()));
        this.addLayer(new BeeStingerLayer<>(this));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull PlayerDecoyEntity entity) {
        // Используем UUID владельца для получения скина
        UUID ownerUuid = entity.getOwnerUUID();
        if (ownerUuid == null) {
            return DefaultPlayerSkin.get(UUID.randomUUID()).texture();
        }

        // Проверяем кэш
        ResourceLocation cached = SKIN_CACHE.get(ownerUuid);
        if (cached != null) {
            return cached;
        }

        // Пробуем получить скин через PlayerInfo (если игрок на сервере)
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(ownerUuid);
            if (playerInfo != null) {
                ResourceLocation skin = playerInfo.getSkin().texture();
                SKIN_CACHE.put(ownerUuid, skin);
                return skin;
            }
        }

        // Возвращаем дефолтный скин на основе UUID владельца
        PlayerSkin defaultSkin = DefaultPlayerSkin.get(ownerUuid);
        return defaultSkin.texture();
    }

    public static void clearSkinCache(UUID uuid) {
        SKIN_CACHE.remove(uuid);
    }

    public static void clearAllSkinCache() {
        SKIN_CACHE.clear();
    }

    @Override
    public void render(@NotNull PlayerDecoyEntity entity, float entityYaw, float partialTicks,
            @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        setModelProperties(entity);
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private void setModelProperties(PlayerDecoyEntity entity) {
        PlayerModel<PlayerDecoyEntity> model = this.getModel();

        model.setAllVisible(true);

        byte modelParts = entity.getPlayerModelParts();
        model.hat.visible = (modelParts & 0x01) != 0;
        model.jacket.visible = (modelParts & 0x02) != 0;
        model.leftPants.visible = (modelParts & 0x04) != 0;
        model.rightPants.visible = (modelParts & 0x08) != 0;
        model.leftSleeve.visible = (modelParts & 0x10) != 0;
        model.rightSleeve.visible = (modelParts & 0x20) != 0;

        model.crouching = entity.isDecoyCrouching();
        model.riding = entity.isDecoySitting();

        model.leftArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;

        if (!entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).isEmpty()) {
            model.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
        }
        if (!entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND).isEmpty()) {
            model.leftArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
        }
    }

    @Override
    protected boolean shouldShowName(@NotNull PlayerDecoyEntity entity) {
        return false;
    }
}
