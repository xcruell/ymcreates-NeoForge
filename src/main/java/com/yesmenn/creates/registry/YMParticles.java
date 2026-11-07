package com.yesmenn.creates.registry;

import com.yesmenn.creates.YesmennCreates;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class YMParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(
                    Registries.PARTICLE_TYPE,
                    YesmennCreates.MODID
            );

    public static final Supplier<SimpleParticleType> EMBER =
            PARTICLE_TYPES.register(
                    "envo_ember",
                    () -> new SimpleParticleType(false)
            );

    private YMParticles() {}
}
