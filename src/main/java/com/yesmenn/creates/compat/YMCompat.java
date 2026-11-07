package com.yesmenn.creates.compat;

import com.yesmenn.creates.YesmennCreates;
import com.yesmenn.creates.compat.electroenergetics.EECompat;

import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;

/**
 * Entry point for optional mod integrations.
 * <p>
 * Code that touches classes of an optional mod must live in its own package under {@code compat}
 * and may only be reached through the {@link #isLoaded} guard below, so the classes are never
 * loaded when that mod is missing.
 */
public final class YMCompat {
    public static final String ELECTRO_ENERGETICS = "electroenergetics";

    private YMCompat() {}

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        if (isLoaded(ELECTRO_ENERGETICS)) {
            YesmennCreates.LOGGER.info("Create: Electro Energetics found, loading compat content");
            EECompat.init(modEventBus, modContainer);
        }
    }

    /** Adds the items of all installed integrations to the creative tab. */
    public static void addToCreativeTab(CreativeModeTab.Output output) {
        if (isLoaded(ELECTRO_ENERGETICS)) {
            EECompat.addToCreativeTab(output);
        }
    }
}
