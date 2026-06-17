package ru.liko.wrbdrones.mixin.client;

import com.atsuishio.superbwarfare.entity.vehicle.DroneEntity;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.NBTTool;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.wrbdrones.client.LancetCameraMount;
import ru.liko.wrbdrones.client.DroneInputHandler;
import ru.liko.wrbdrones.entity.FpvDroneEntity;
import ru.liko.wrbdrones.entity.ZalaLancetEntity;

/**
 * Перехватывает настройку камеры для дронов WRBDrones.
 * Приоритет ниже числа SBW CameraMixin (1000): Mixin применяет такие перехваты
 * раньше, поэтому cancel() не даёт SBW поставить свой жёсткий мониторный оффсет.
 */
@Mixin(value = Camera.class, priority = 500)
public abstract class CameraMixinWRB {

    @Shadow
    @Deprecated
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    /**
     * Перехват Camera.setup() — если монитор смотрит на наш ZalaLancetEntity,
     * считаем позицию камеры напрямую через WRB-маунт, а не через SBW first-person
     * ветку с жёстким квадрокоптерным оффсетом.
     */
    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FFF)V", ordinal = 0),
            method = "setup",
            cancellable = true)
    private void wrbdrones$onSetup(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTicks, CallbackInfo info) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (!stack.is(com.atsuishio.superbwarfare.init.ModItems.MONITOR.get())) return;

        var tag = NBTTool.getTag(stack);
        if (!tag.getBoolean("Using") || !tag.getBoolean("Linked")) return;

        DroneEntity drone = EntityFindUtil.findDrone(player.level(), tag.getString("LinkedDrone"));
        if (drone == null) return;

        // Ветка FPV: self-chunk режим — игрок остаётся дома, камера ставится в нос дрона.
        // Используем те же методы, что SBW CameraMixin применяет для стандартных дронов:
        // getYaw(float) и getPitch(float) — интерполированные углы тела дрона из VehicleEntity.
        // getEyePosition(float) — стандартный Entity-метод с интерполяцией позиции и eye-высотой.
        if (drone instanceof FpvDroneEntity fpv) {
            Vec3 eye = fpv.getEyePosition(partialTicks);
            float yaw   = fpv.getYaw(partialTicks);
            float pitch = fpv.getPitch(partialTicks);
            setRotation(yaw, pitch);
            setPosition(eye.x, eye.y, eye.z);
            info.cancel();
            return;
        }

        if (!(drone instanceof ZalaLancetEntity lancetDrone)) return;

        // Наш дрон — обрабатываем камеру самостоятельно
        boolean isFirstPerson = mc.options.getCameraType() == CameraType.FIRST_PERSON
                || mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK;

        Vec2 rotation = LancetCameraMount.getCameraRotation(lancetDrone, partialTicks, player, isFirstPerson);
        Vec3 position = LancetCameraMount.getCameraPosition(lancetDrone, partialTicks, player, isFirstPerson);

        setRotation(rotation.x, rotation.y);
        setPosition(position.x, position.y, position.z);
        info.cancel();
    }

    /**
     * Свободная камера Ланцета (режим разведки): подмена yaw.
     */
    @ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float wrbdrones$modifyYaw(float yaw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && DroneInputHandler.isFreeCameraActive(mc.player)) {
            return DroneInputHandler.getFreeCameraYaw();
        }
        return yaw;
    }

    /**
     * Свободная камера Ланцета (режим разведки): подмена pitch.
     */
    @ModifyVariable(method = "setRotation(FF)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float wrbdrones$modifyPitch(float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && DroneInputHandler.isFreeCameraActive(mc.player)) {
            return DroneInputHandler.getFreeCameraPitch();
        }
        return pitch;
    }
}
