package bluepanda.create_mechanical_anvil;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.simibubi.create.AllShapes.Builder;

import static net.minecraft.core.Direction.SOUTH;

public class CEIMAShapes {

    public static final VoxelShaper
    MECHANICAL_ANVIL = shape(2,0,2,14,12,14).add(0,7,3,16,13,13).forHorizontal(SOUTH),
    MECHANICAL_NONEXIST_ANVIL = shape(1,1,1,2,2,2).forHorizontal(SOUTH)

            ;

    private static Builder shape(VoxelShape shape) { return new Builder(shape); }

    private static Builder shape(double x1, double y1, double z1, double x2, double y2, double z2) {
        return shape(cuboid(x1, y1, z1, x2, y2, z2));
    }

    private static VoxelShape cuboid(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Block.box(x1, y1, z1, x2, y2, z2);
    }
}
