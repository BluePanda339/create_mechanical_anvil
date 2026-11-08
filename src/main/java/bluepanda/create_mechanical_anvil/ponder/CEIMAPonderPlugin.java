package bluepanda.create_mechanical_anvil.ponder;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import bluepanda.create_mechanical_anvil.CEIMA;

public class CEIMAPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CEIMA.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        CEIMAPonderScenes.register(helper);
    }
}
