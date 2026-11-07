package com.yesmenn.creates.client;

import com.yesmenn.creates.YesmennCreates;
import com.yesmenn.creates.client.renderer.EnvoKiller3000Renderer;
import com.yesmenn.creates.registry.YMBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = YesmennCreates.MODID, value = Dist.CLIENT)
public final class YesmennCreatesClient {

    private YesmennCreatesClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event
    ) {
        event.registerBlockEntityRenderer(
                YMBlockEntities.ENVOKILLER3000.get(),
                EnvoKiller3000Renderer::new
        );
    }

    @SubscribeEvent
    public static void registerAdditionalModels(
            ModelEvent.RegisterAdditional event
    ) {
        event.register(
                EnvoKiller3000Renderer.ENVO_FLAME
        );
        event.register(
                EnvoKiller3000Renderer.ENVO_FLANSCH
        );
    }


}
