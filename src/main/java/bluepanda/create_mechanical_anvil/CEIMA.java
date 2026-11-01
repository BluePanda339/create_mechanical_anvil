package bluepanda.create_mechanical_anvil;

import com.simibubi.create.foundation.data.CreateRegistrate;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CEIMA.MOD_ID)
public class CEIMA {
    public static final String MOD_ID = "create_mechanical_anvil";
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create("create_mechanical_anvil");

    public CEIMA(IEventBus eventBus) {
        REGISTRATE.registerEventListeners(eventBus);
        CEIMABlocks.register();
        CEIMABlockEntities.register();
    }
}
