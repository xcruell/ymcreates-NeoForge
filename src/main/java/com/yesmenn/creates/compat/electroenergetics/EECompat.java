package com.yesmenn.creates.compat.electroenergetics;

import com.yesmenn.creates.compat.electroenergetics.client.EEClient;
import com.yesmenn.creates.compat.electroenergetics.surfacewire.SurfaceWireData;
import com.yesmenn.creates.compat.electroenergetics.surfacewire.SurfaceWirePackets;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import com.yesmenn.creates.compat.electroenergetics.surfacewire.FlatWireConfig;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Create: Electro Energetics integration. Only called from {@code YMCompat} when EE is installed,
 * so classes from EE may be referenced freely inside this package (and nowhere else).
 */
public final class EECompat {
    private EECompat() {}

    public static void init(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, FlatWireConfig.SPEC, "yesmenncreates-flat-wires.toml");
        EEItems.ITEMS.register(modEventBus);
        EEDataComponents.DATA_COMPONENTS.register(modEventBus);
        modEventBus.addListener(SurfaceWirePackets::register);

        NeoForge.EVENT_BUS.addListener(SurfaceWireData::onAddToElectricGraph);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> syncWires(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> syncWires(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> syncWires(e.getEntity()));

        if (FMLEnvironment.dist.isClient()) {
            EEClient.init(modEventBus);
        }
    }

    private static void syncWires(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            SurfaceWireData.get(serverPlayer.serverLevel()).syncTo(serverPlayer);
        }
    }

    public static void addToCreativeTab(CreativeModeTab.Output output) {
        EEItems.addToCreativeTab(output);
    }
}
