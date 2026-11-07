package com.yesmenn.creates.compat.electroenergetics;

import com.yesmenn.creates.YesmennCreates;
import com.yesmenn.creates.compat.electroenergetics.surfacewire.WireStart;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components of the EE integration. */
public final class EEDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, YesmennCreates.MODID);

    /** Start point of the flat wire currently being laid. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WireStart>> WIRE_START =
            DATA_COMPONENTS.registerComponentType("wire_start", builder -> builder
                    .persistent(WireStart.CODEC)
                    .networkSynchronized(WireStart.STREAM_CODEC));

    private EEDataComponents() {}
}
