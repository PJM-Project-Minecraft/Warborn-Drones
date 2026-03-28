package ru.liko.wrbdrones.client;

import com.atsuishio.superbwarfare.event.ClientEventHandler;
import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.atsuishio.superbwarfare.tools.ItemNBTTool;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.network.ModNetworking;

@Mod.EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DroneInputHandler {
    
    @SubscribeEvent
    public static void onMouseButtonPressed(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        
        // Обрабатываем только правую кнопку мыши (кнопка 1)
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        
        Player player = mc.player;
        ItemStack stack = player.getMainHandItem();
        
        // Проверяем, что игрок держит монитор и использует его для управления дроном
        if (!stack.is(ModItems.MONITOR.get())) {
            return;
        }
        
        if (!ItemNBTTool.getBoolean(stack, "Linked", false)) {
            return;
        }
        
        if (!stack.getOrCreateTag().getBoolean("Using")) {
            return;
        }
        
        // Проверяем, что дрон существует и игрок управляет им
        String droneId = stack.getOrCreateTag().getString("LinkedDrone");
        if (droneId == null || droneId.isEmpty() || droneId.equals("none")) {
            return;
        }
        
        var drone = EntityFindUtil.findDrone(player.level(), droneId);
        if (!(drone instanceof AddonDroneEntity)) {
            return;
        }
        
        // Проверяем, что игрок сидит на дроне
        if (player.getVehicle() != drone) {
            return;
        }
        
        // Обновляем флаг "Using" на клиенте для немедленного обновления UI
        stack.getOrCreateTag().putBoolean("Using", false);
        
        // Восстанавливаем камеру в режим первого лица (или сохраненный режим)
        if (ClientEventHandler.lastCameraType != null) {
            mc.options.setCameraType(ClientEventHandler.lastCameraType);
        } else {
            // Если сохраненный режим не найден, устанавливаем первое лицо
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        
        // Отправляем пакет на сервер для выхода из управления дроном
        // Это работает даже когда игрок в режиме спектатора
        ModNetworking.requestExitDroneControl();
        
        // Отменяем событие, чтобы предотвратить другие взаимодействия
        event.setCanceled(true);
    }
}

