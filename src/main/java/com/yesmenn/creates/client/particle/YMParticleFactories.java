package com.yesmenn.creates.client.particle;

import com.yesmenn.creates.YesmennCreates;
import com.yesmenn.creates.particle.EnvoEmberParticle;
import com.yesmenn.creates.registry.YMParticles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(
        modid = YesmennCreates.MODID,
        value = Dist.CLIENT
)
public final class YMParticleFactories {

    private YMParticleFactories() {}

    @SubscribeEvent
    public static void registerParticleProviders(
            RegisterParticleProvidersEvent event
    ) {
        event.registerSpriteSet(
                YMParticles.EMBER.get(),
                EnvoEmberParticle.Provider::new
        );
    }
}
