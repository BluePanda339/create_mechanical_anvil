package bluepanda.create_mechanical_anvil.ponder;

import bluepanda.create_mechanical_anvil.CEIMABlocks;
import bluepanda.create_mechanical_anvil.ponder.scene.MechanicalAnvilPonderScenes;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CEIMAPonderScenes {
    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> HELPER = helper.withKeyFunction(RegistryEntry::getId);

        HELPER.forComponents(CEIMABlocks.MECHANICAL_ANVIL)
                .addStoryBoard("mechanical_anvil_combine", MechanicalAnvilPonderScenes::intro);
    }
}
