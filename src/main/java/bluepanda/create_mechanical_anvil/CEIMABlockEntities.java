package bluepanda.create_mechanical_anvil;

import bluepanda.create_mechanical_anvil.block.mechanicalanvil.MechanicalAnvilBlockEntity;
import bluepanda.create_mechanical_anvil.block.mechanicalanvil.MechanicalAnvilRenderer;
import com.simibubi.create.content.kinetics.base.ShaftVisual;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import static bluepanda.create_mechanical_anvil.CEIMA.REGISTRATE;

public class CEIMABlockEntities {
    public static final BlockEntityEntry<MechanicalAnvilBlockEntity> MECHANICAL_ANVIL = REGISTRATE
            .blockEntity("mechanical_anvil", MechanicalAnvilBlockEntity::new)
            .visual(() -> ShaftVisual::new)
            .validBlocks(CEIMABlocks.MECHANICAL_ANVIL)
            .renderer(() -> MechanicalAnvilRenderer::new)
            .register();

    public static void register() {}
}
