package ru.liko.wrbdrones.mixin;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Выбирает миксины по наличию Moonrise. Moonrise переписывает ванильный player chunk
 * loading ({@code @Overwrite} на {@code ChunkMap.updateChunkTracking} и
 * {@code getPlayerViewDistance}) — ванильный {@link ChunkMapPilotAnchorMixin} не может
 * туда заинжектиться и валит сервер FATAL'ом при старте. С Moonrise вместо него
 * применяются {@code Moonrise*}-миксины, реализующие тот же якорь вида пилота через
 * {@code RegionizedPlayerChunkLoader} и {@code NearbyPlayers}.
 */
public final class WrbdronesMixinPlugin implements IMixinConfigPlugin {

    private static final boolean MOONRISE =
            FMLLoader.getLoadingModList().getModFileById("moonrise") != null;

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (simpleName.startsWith("Moonrise")) {
            return MOONRISE;
        }
        if (simpleName.equals("ChunkMapPilotAnchorMixin")) {
            return !MOONRISE;
        }
        return true;
    }

    @Override
    public void onLoad(final String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass,
                         final String mixinClassName, final IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass,
                          final String mixinClassName, final IMixinInfo mixinInfo) {
    }
}
