package com.yesmenn.creates.compat.electroenergetics.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Client setup of the EE integration. Only loaded on the client and only when EE is installed. */
public final class EEClient {
    private EEClient() {}

    public static void init(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.addListener(SurfaceWireClient::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(SurfaceWireClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(SurfaceWireClient::onClientTick);
    }
}
