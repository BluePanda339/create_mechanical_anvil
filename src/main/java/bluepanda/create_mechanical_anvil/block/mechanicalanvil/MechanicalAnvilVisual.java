package bluepanda.create_mechanical_anvil.block.mechanicalanvil;

import com.simibubi.create.content.kinetics.base.ShaftVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;

public class MechanicalAnvilVisual extends ShaftVisual<MechanicalAnvilBlockEntity>  {

    public MechanicalAnvilVisual(VisualizationContext context, MechanicalAnvilBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
    }

}
