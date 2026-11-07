package com.yesmenn.creates.client.ponder;

import com.yesmenn.creates.YesmennCreates;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class YesmennPonders implements PonderPlugin {

    @Override
    public String getModId() {
        return YesmennCreates.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        CheckValvePonder.register(helper);
        // NeuerBlockScenes.register(helper);   <- weitere Blöcke einfach hier ergänzen
    }
}
