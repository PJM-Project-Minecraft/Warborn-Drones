package ru.liko.wrbdrones.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;
import ru.liko.wrbdrones.Wrbdrones;

@EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LancetKeyMappings {
    private static final String CATEGORY = "key.categories.wrbdrones";

    public static final KeyMapping YAW_LEFT = new KeyMapping(
            "key.wrbdrones.lancet_yaw_left",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Q,
            CATEGORY);

    public static final KeyMapping YAW_RIGHT = new KeyMapping(
            "key.wrbdrones.lancet_yaw_right",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E,
            CATEGORY);

    public static final KeyMapping TARGET = new KeyMapping(
            "key.wrbdrones.lancet_target",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            CATEGORY);

    public static final KeyMapping SWITCH_MODE = new KeyMapping(
            "key.wrbdrones.lancet_switch_mode",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY);

    public static final KeyMapping FREE_CAMERA = new KeyMapping(
            "key.wrbdrones.lancet_free_camera",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    public static final KeyMapping CYCLE_TARGET_LEFT = new KeyMapping(
            "key.wrbdrones.lancet_cycle_target_left",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT,
            CATEGORY);

    public static final KeyMapping CYCLE_TARGET_RIGHT = new KeyMapping(
            "key.wrbdrones.lancet_cycle_target_right",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT,
            CATEGORY);

    public static final KeyMapping THERMAL_VISION = new KeyMapping(
            "key.wrbdrones.lancet_thermal_vision",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY);

    private LancetKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(YAW_LEFT);
        event.register(YAW_RIGHT);
        event.register(TARGET);
        event.register(SWITCH_MODE);
        event.register(FREE_CAMERA);
        event.register(CYCLE_TARGET_LEFT);
        event.register(CYCLE_TARGET_RIGHT);
        event.register(THERMAL_VISION);
    }
}
