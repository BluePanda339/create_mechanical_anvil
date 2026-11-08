package bluepanda.create_mechanical_anvil;

import bluepanda.create_mechanical_anvil.block.mechanicalanvil.MechanicalAnvilBlockEntity;
import bluepanda.create_mechanical_anvil.ponder.CEIMAPonderPlugin;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.providers.ProviderType;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CEIMA.MOD_ID)
public class CEIMA {
    public static final String MOD_ID = "create_mechanical_anvil";
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID);

    public CEIMA(IEventBus eventBus) {
        REGISTRATE.registerEventListeners(eventBus);
        CEIMABlocks.register();
        CEIMABlockEntities.register();

        eventBus.addListener(MechanicalAnvilBlockEntity::registerCapabilities);
        eventBus.addListener(CEIMA::setup);
    }
    @SubscribeEvent
    public static void setup(final FMLClientSetupEvent event) { PonderIndex.addPlugin(new CEIMAPonderPlugin()); }

    public static LangBuilder lang() {
        return new LangBuilder(MOD_ID);
    }

    public static ResourceLocation asResource(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
}
