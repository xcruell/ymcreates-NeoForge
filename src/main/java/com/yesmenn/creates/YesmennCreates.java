package com.yesmenn.creates;

import com.yesmenn.creates.compat.YMCompat;
import com.yesmenn.creates.registry.*;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(YesmennCreates.MODID)
public class YesmennCreates {
    public static final String MODID = "yesmenncreates";
    public static final Logger LOGGER = LogUtils.getLogger();

    public YesmennCreates(IEventBus modEventBus, ModContainer modContainer) {
        YMBlocks.BLOCKS.register(modEventBus);
        YMBlockEntities.register(modEventBus);
        YMItems.ITEMS.register(modEventBus);
        YMCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
//        YMMenuTypes.MENUS.register(modEventBus);
        YMParticles.PARTICLE_TYPES.register(modEventBus);

        // optional mod integrations (only active when the mod is installed)
        YMCompat.init(modEventBus, modContainer);
    }
}
