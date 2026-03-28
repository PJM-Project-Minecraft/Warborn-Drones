package ru.liko.wrbdrones.client;

import com.atsuishio.superbwarfare.init.ModItems;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.config.ServerConfig;
import ru.liko.wrbdrones.entity.AddonDroneEntity;
import ru.liko.wrbdrones.entity.MavicDroneNoDropEntity;
import ru.liko.wrbdrones.entity.MavicDroneWithDropEntity;
import ru.liko.wrbdrones.util.RebUtils;
import com.google.gson.JsonSyntaxException;
import org.joml.Matrix4f;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

@Mod.EventBusSubscriber(modid = Wrbdrones.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DronePostChainHandler {

    @SuppressWarnings("deprecation")
    private static final ResourceLocation SHADER_LOC = new ResourceLocation(Wrbdrones.MODID, "shaders/post/fpv_post.json");
    
    private static PostChain fpvPostChain;
    private static Field passesFieldCache;
    private static int lastChainWidth = -1;
    private static int lastChainHeight = -1;
    private static boolean inFpvMode = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (inFpvMode) {
                disableFpv();
            }
            return;
        }

        // Cleanup if no drone is active
        if (getActiveDrone(mc.player) == null) {
            if (inFpvMode) {
                disableFpv();
            }
        }
    }

    /**
     * Renders the FPV shader effect BEFORE any GUI overlays are drawn.
     * This ensures the shader only affects the 3D world view, not the HUD elements.
     */
    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        AddonDroneEntity drone = getActiveDrone(mc.player);
        if (drone == null) {
            if (inFpvMode) {
                disableFpv();
            }
            return;
        }
        
        if (!inFpvMode) {
            inFpvMode = true;
            ensureFpvChain(mc);
        }
        
        if (fpvPostChain != null) {
            resizeFpvChainIfNeeded(mc);
            
            float signal = calculateSignalQuality(mc.player, drone);
            updateShaderUniforms(signal);
            
            // Backup GL state
            Matrix4f backupProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
            var backupSorting = RenderSystem.getVertexSorting();
            
            float partialTick = event.getPartialTick();
            try {
                fpvPostChain.process(partialTick);
            } catch (Exception e) {
                Wrbdrones.LOGGER.warn("Failed to process FPV shader", e);
                disableFpv();
            }
            
            // Re-bind the main framebuffer for GUI rendering
            mc.getMainRenderTarget().bindWrite(true);
            
            // Restore Projection Matrix which is crucial for subsequent HUD rendering
            RenderSystem.setProjectionMatrix(backupProjection, backupSorting);
            
            // Restore standard state for HUD
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static AddonDroneEntity getActiveDrone(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.MONITOR.get())) return null;
        if (!stack.getOrCreateTag().getBoolean("Using")) return null;
        if (!stack.getOrCreateTag().getBoolean("Linked")) return null;
        
        String droneId = stack.getOrCreateTag().getString("LinkedDrone");
        if (droneId == null || droneId.isEmpty()) return null;

        var entity = EntityFindUtil.findDrone(player.level(), droneId);
        if (entity instanceof AddonDroneEntity addonDrone) {
            return addonDrone;
        }
        return null;
    }
    
    private static float calculateSignalQuality(Player player, AddonDroneEntity drone) {
        Vec3 operatorPos = player.position();
        Vec3 avatarPos = drone.getOperatorPosition();
        if (avatarPos != null) {
            operatorPos = avatarPos;
        }
        
        double distance = operatorPos.distanceTo(drone.position());
        double rebJammingFactor = RebUtils.getRebFactor(drone);
        
        double maxDistance;
        
        if (drone instanceof MavicDroneWithDropEntity || drone instanceof MavicDroneNoDropEntity) {
            maxDistance = ServerConfig.MAVIC_MAX_DISTANCE.get();
        } else {
            maxDistance = ServerConfig.FPV_MAX_DISTANCE.get();
        }
        
        double signalPercent = Math.max(0.0, 1.0 - (distance / maxDistance));
        signalPercent = signalPercent * signalPercent; // Quadratic falloff
        signalPercent = signalPercent * (1.0 - rebJammingFactor);
        
        return (float) Math.max(0.0, Math.min(1.0, signalPercent));
    }

    private static void ensureFpvChain(Minecraft mc) {
        if (fpvPostChain != null) return;
        try {
            fpvPostChain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), SHADER_LOC);
            lastChainWidth = mc.getWindow().getWidth();
            lastChainHeight = mc.getWindow().getHeight();
            fpvPostChain.resize(lastChainWidth, lastChainHeight);
            passesFieldCache = null;
        } catch (IOException e) {
            Wrbdrones.LOGGER.warn("Failed to load FPV shader", e);
            fpvPostChain = null;
        } catch (JsonSyntaxException e) {
            Wrbdrones.LOGGER.warn("Failed to parse FPV shader", e);
            fpvPostChain = null;
        }
    }

    private static void resizeFpvChainIfNeeded(Minecraft mc) {
        if (fpvPostChain == null) return;
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w != lastChainWidth || h != lastChainHeight) {
            lastChainWidth = w;
            lastChainHeight = h;
            fpvPostChain.resize(w, h);
        }
    }

    private static void disableFpv() {
        inFpvMode = false;
        if (fpvPostChain != null) {
            fpvPostChain.close();
            fpvPostChain = null;
        }
        passesFieldCache = null;
    }

    @SuppressWarnings("unchecked")
    private static void updateShaderUniforms(float signalQuality) {
        if (fpvPostChain == null) return;
        
        try {
            // Reflection hack to access passes if needed (standard PostChain doesn't expose easy uniform access for all passes)
            if (passesFieldCache == null) {
                for (Field f : PostChain.class.getDeclaredFields()) {
                    if (List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object obj = f.get(fpvPostChain);
                        if (obj instanceof List<?> list && !list.isEmpty()) {
                            if (list.get(0) instanceof PostPass) {
                                passesFieldCache = f;
                                break;
                            }
                        }
                    }
                }
            }

            if (passesFieldCache != null) {
                List<PostPass> passes = (List<PostPass>) passesFieldCache.get(fpvPostChain);
                // Use system time for smooth animation independent of tick rate
                float time = (float) ((System.currentTimeMillis() % 3600000L) / 1000.0);
                
                for (PostPass pass : passes) {
                    var effect = pass.getEffect();
                    var signalU = effect.getUniform("SignalQuality");
                    if (signalU != null) signalU.set(signalQuality);
                    
                    var timeU = effect.getUniform("Time");
                    if (timeU != null) timeU.set(time);
                }
            }
        } catch (Exception e) {
            // Ignore reflection errors to prevent spam
        }
    }
}
