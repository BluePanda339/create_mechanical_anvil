package bluepanda.create_mechanical_anvil;

import bluepanda.create_mechanical_anvil.block.mechanicalanvil.MechanicalAnvilBlock;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import static bluepanda.create_mechanical_anvil.CEIMA.REGISTRATE;
import static com.simibubi.create.foundation.data.ModelGen.customItemModel;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;

public class CEIMABlocks {
    static {
        REGISTRATE.setCreativeTab(AllCreativeModeTabs.BASE_CREATIVE_TAB);
    }

    public static final BlockEntry<MechanicalAnvilBlock> MECHANICAL_ANVIL = REGISTRATE.block("mechanical_anvil", MechanicalAnvilBlock::new)
            .initialProperties(SharedProperties::copperMetal)
            .transform(pickaxeOnly())
            .addLayer(() -> RenderType::cutoutMipped)
            .blockstate(BlockStateGen.horizontalBlockProvider(false))
            .item()
            .transform(customItemModel())
            .register();

    public static void register() {}
}
