package ru.liko.wrbdrones.util;

import com.atsuishio.superbwarfare.entity.OBBEntity;
import com.atsuishio.superbwarfare.tools.OBB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;

/**
 * Проверка пересечения OBB-хитбоксов машины (SBW) с коллизией блоков мира.
 * <p>
 * SBW-система OBB покрывает только попадания снарядов и поиск сущностей —
 * столкновения с блоками она не обрабатывает. Этот утиль замыкает пробел:
 * для каждого OBB строится AABB-обёртка по его восьми вершинам, из мира
 * забираются коллизионные формы блоков в этой обёртке, и каждая проверяется
 * точным тестом {@link OBB#isColliding(OBB, AABB)}. Так крыло, задевшее стену
 * в крене, засчитывается, а пролёт рядом — нет.
 * <p>
 * Утиль dist-нейтральный; вызывать имеет смысл на сервере после перемещения
 * сущности (и после {@code updateOBB()}, иначе боксы отстают на тик).
 */
public final class ObbBlockCollision {

    private ObbBlockCollision() {
    }

    /** Пересекается ли хоть один OBB сущности с коллизионной формой блока. */
    public static <T extends Entity & OBBEntity> boolean intersectsBlocks(T entity) {
        Level level = entity.level();
        for (OBB obb : entity.getOBBs()) {
            AABB envelope = envelope(obb);
            for (VoxelShape shape : level.getBlockCollisions(entity, envelope)) {
                for (AABB blockBox : shape.toAabbs()) {
                    if (OBB.isColliding(obb, blockBox)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** AABB-обёртка повёрнутого бокса по его восьми вершинам. */
    private static AABB envelope(OBB obb) {
        Vector3d[] vertices = obb.getVertices();
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (Vector3d v : vertices) {
            minX = Math.min(minX, v.x);
            minY = Math.min(minY, v.y);
            minZ = Math.min(minZ, v.z);
            maxX = Math.max(maxX, v.x);
            maxY = Math.max(maxY, v.y);
            maxZ = Math.max(maxZ, v.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
