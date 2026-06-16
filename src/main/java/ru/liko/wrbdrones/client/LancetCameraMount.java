package ru.liko.wrbdrones.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4d;
import org.joml.Vector4d;
import ru.liko.wrbdrones.Wrbdrones;
import ru.liko.wrbdrones.entity.ZalaLancetEntity;

import java.io.IOException;

@OnlyIn(Dist.CLIENT)
public final class LancetCameraMount {
    private static final double MODEL_UNIT_TO_BLOCK = 1.0 / 16.0;
    private static final ResourceLocation LANCET_MODEL = Wrbdrones.id("geo/zala_lancet.geo.json");

    // Fallback only. The live mount is read from the "camera" bone in zala_lancet.geo.json.
    private static final Vec3 DEFAULT_CAMERA_BONE_PIVOT = new Vec3(-0.7, 8.2, -18.7);
    private static final Vec3 DEFAULT_CAMERA_EYE = new Vec3(-0.5, 8.1, -20.7);
    private static final CameraMount DEFAULT_CAMERA_MOUNT = new CameraMount(DEFAULT_CAMERA_BONE_PIVOT, DEFAULT_CAMERA_EYE);

    private static final double TP_CAMERA_X = 0.0;
    private static final double TP_CAMERA_Y = 1.2;
    private static final double TP_CAMERA_Z = 4.0;

    private static CameraMount cachedCameraMount;
    private static ResourceManager cachedResourceManager;
    private static boolean warnedCameraMountLoad;

    private LancetCameraMount() {
    }

    public static @NotNull Vec2 getCameraRotation(ZalaLancetEntity lancet, float partialTicks,
            @NotNull Player player, boolean isFirstPerson) {
        if (!isFirstPerson) {
            return new Vec2(lancet.getYaw(partialTicks), lancet.getBodyPitch(partialTicks));
        }
        return new Vec2(getCameraYaw(lancet, partialTicks), getCameraBonePitch(lancet, partialTicks, player));
    }

    public static Vec3 getCameraPosition(ZalaLancetEntity lancet, float partialTicks,
            @NotNull Player player, boolean isFirstPerson) {
        if (isFirstPerson) {
            Matrix4d transform = getLancetModelTransform(lancet, partialTicks);
            Vec3 localEye = getCameraBoneLocal(lancet, partialTicks, player);
            Vector4d pos = transform.transform(new Vector4d(localEye.x, localEye.y, localEye.z, 1));
            return new Vec3(pos.x, pos.y, pos.z);
        }

        Matrix4d transform = getLancetEntityTransform(lancet, partialTicks);
        Vector4d pos = transform.transform(new Vector4d(TP_CAMERA_X, TP_CAMERA_Y, TP_CAMERA_Z, 1));
        return com.atsuishio.superbwarfare.tools.CameraTool.getMaxZoom(transform, pos);
    }

    public static float getCameraBonePitch(ZalaLancetEntity lancet, float partialTicks, @NotNull Player player) {
        if (lancet.getLancetMode() == ZalaLancetEntity.MODE_RECON && lancet.isFreeCamera()) {
            return DroneInputHandler.getFreeCameraPitch();
        }
        // FPV-камера жёстко привязана к носу дрона: pitch = pitch корпуса.
        // Игнорируем player.xRot, чтобы камера синхронно наклонялась при пикировании
        // и не «уезжала» при входе в управление с произвольным направлением головы игрока.
        return Mth.clamp(lancet.getBodyPitch(partialTicks), -90.0f, 90.0f);
    }

    private static float getCameraYaw(ZalaLancetEntity lancet, float partialTicks) {
        if (lancet.getLancetMode() == ZalaLancetEntity.MODE_RECON && lancet.isFreeCamera()) {
            return DroneInputHandler.getFreeCameraYaw();
        }
        return lancet.getYaw(partialTicks);
    }

    private static Vec3 getCameraBoneLocal(ZalaLancetEntity lancet, float partialTicks, Player player) {
        CameraMount mount = getCameraMount();
        // Локальный поворот «карданчика» применяем только если камера действительно сдвинута
        // относительно носа (режим разведки со свободной камерой). В обычном режиме корпус
        // уже наклонён модельной матрицей — двойной поворот привёл бы к смещению глаза.
        float gimbalOffset = 0.0f;
        if (lancet.getLancetMode() == ZalaLancetEntity.MODE_RECON && lancet.isFreeCamera()) {
            gimbalOffset = DroneInputHandler.getFreeCameraPitch() - lancet.getBodyPitch(partialTicks);
        }
        double cameraRotX = Math.toRadians(-gimbalOffset);
        Vec3 rotatedEye = rotateAroundX(mount.eye(), mount.pivot(), cameraRotX);
        return toLocalBlocks(rotatedEye);
    }

    private static CameraMount getCameraMount() {
        Minecraft minecraft = Minecraft.getInstance();
        ResourceManager resourceManager = minecraft != null ? minecraft.getResourceManager() : null;
        if (cachedCameraMount != null && cachedResourceManager == resourceManager) {
            return cachedCameraMount;
        }

        cachedResourceManager = resourceManager;
        cachedCameraMount = resourceManager != null ? readCameraMount(resourceManager) : DEFAULT_CAMERA_MOUNT;
        return cachedCameraMount;
    }

    private static CameraMount readCameraMount(ResourceManager resourceManager) {
        var resource = resourceManager.getResource(LANCET_MODEL);
        if (resource.isEmpty()) {
            warnCameraMountLoad("Cannot find " + LANCET_MODEL + "; using the built-in Lancet camera fallback.", null);
            return DEFAULT_CAMERA_MOUNT;
        }

        try (var reader = resource.get().openAsReader()) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (!rootElement.isJsonObject()) {
                warnCameraMountLoad("Invalid " + LANCET_MODEL + ": root is not an object.", null);
                return DEFAULT_CAMERA_MOUNT;
            }

            CameraMount mount = findCameraMount(rootElement.getAsJsonObject());
            if (mount != null) {
                return mount;
            }

            warnCameraMountLoad("Cannot find a camera bone mount in " + LANCET_MODEL + "; using the built-in Lancet camera fallback.", null);
        } catch (IOException | IllegalStateException | ClassCastException e) {
            warnCameraMountLoad("Failed to read Lancet camera bone from " + LANCET_MODEL + "; using the built-in fallback.", e);
        }

        return DEFAULT_CAMERA_MOUNT;
    }

    private static CameraMount findCameraMount(JsonObject root) {
        JsonArray geometries = getArray(root, "minecraft:geometry");
        if (geometries == null) {
            return null;
        }

        for (JsonElement geometryElement : geometries) {
            if (!geometryElement.isJsonObject()) {
                continue;
            }

            JsonArray bones = getArray(geometryElement.getAsJsonObject(), "bones");
            if (bones == null) {
                continue;
            }

            for (JsonElement boneElement : bones) {
                if (!boneElement.isJsonObject()) {
                    continue;
                }

                JsonObject bone = boneElement.getAsJsonObject();
                JsonElement name = bone.get("name");
                if (name != null && name.isJsonPrimitive() && "camera".equals(name.getAsString())) {
                    JsonArray pivot = getArray(bone, "pivot");
                    Vec3 pivotPoint = pivot != null ? readVec3(pivot) : DEFAULT_CAMERA_BONE_PIVOT;
                    Vec3 eyePoint = findCameraEye(bone);
                    return new CameraMount(pivotPoint, eyePoint != null ? eyePoint : pivotPoint);
                }
            }
        }

        return null;
    }

    private static Vec3 findCameraEye(JsonObject cameraBone) {
        JsonArray cubes = getArray(cameraBone, "cubes");
        if (cubes == null || cubes.isEmpty()) {
            return null;
        }

        for (JsonElement cubeElement : cubes) {
            if (!cubeElement.isJsonObject()) {
                continue;
            }

            JsonObject cube = cubeElement.getAsJsonObject();
            JsonArray originArray = getArray(cube, "origin");
            JsonArray sizeArray = getArray(cube, "size");
            if (originArray == null || sizeArray == null) {
                continue;
            }

            Vec3 origin = readVec3(originArray);
            Vec3 size = readVec3(sizeArray);
            if (origin == null || size == null) {
                continue;
            }

            double frontZ = Math.min(origin.z, origin.z + size.z);
            return new Vec3(origin.x + size.x * 0.5, origin.y + size.y * 0.5, frontZ);
        }

        return null;
    }

    private static JsonArray getArray(JsonObject object, String member) {
        JsonElement element = object.get(member);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static Vec3 readVec3(JsonArray array) {
        if (array.size() < 3) {
            return null;
        }
        return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    private static Vec3 rotateAroundX(Vec3 point, Vec3 pivot, double angle) {
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double y = point.y - pivot.y;
        double z = point.z - pivot.z;
        return new Vec3(point.x, pivot.y + y * cos - z * sin, pivot.z + y * sin + z * cos);
    }

    private static Vec3 toLocalBlocks(Vec3 modelPivot) {
        // GeckoLib's Bedrock loader mirrors the model X axis for this asset.
        return new Vec3(
                -modelPivot.x * MODEL_UNIT_TO_BLOCK,
                modelPivot.y * MODEL_UNIT_TO_BLOCK,
                modelPivot.z * MODEL_UNIT_TO_BLOCK);
    }

    private static void warnCameraMountLoad(String message, Exception exception) {
        if (warnedCameraMountLoad) {
            return;
        }

        warnedCameraMountLoad = true;
        if (exception == null) {
            Wrbdrones.LOGGER.warn(message);
        } else {
            Wrbdrones.LOGGER.warn(message, exception);
        }
    }

    private static Matrix4d getLancetEntityTransform(ZalaLancetEntity lancet, float partialTicks) {
        Matrix4d transform = new Matrix4d();
        transform.translate(
                Mth.lerp(partialTicks, lancet.xo, lancet.getX()),
                Mth.lerp(partialTicks, lancet.yo, lancet.getY()),
                Mth.lerp(partialTicks, lancet.zo, lancet.getZ()));
        transform.rotate(Axis.YP.rotationDegrees(-lancet.getYaw(partialTicks)));
        transform.rotate(Axis.XP.rotationDegrees(lancet.getBodyPitch(partialTicks)));
        transform.rotate(Axis.ZP.rotationDegrees(lancet.getRoll(partialTicks)));
        return transform;
    }

    private static Matrix4d getLancetModelTransform(ZalaLancetEntity lancet, float partialTicks) {
        Matrix4d transform = getLancetEntityTransform(lancet, partialTicks);
        transform.rotate(Axis.YP.rotationDegrees(180.0f));
        transform.translate(0.0, 0.01, 0.0);
        return transform;
    }

    private record CameraMount(Vec3 pivot, Vec3 eye) {
    }
}
