package com.yesmenn.creates.compat.electroenergetics.surfacewire;

import com.yesmenn.creates.YesmennCreates;
import com.yesmenn.creates.compat.electroenergetics.EEItems;
import com.yesmenn.creates.compat.electroenergetics.client.SurfaceWireClient;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Network packets of the flat wires. */
public final class SurfaceWirePackets {
    private static final double MAX_REMOVE_DISTANCE = 8.0;

    private SurfaceWirePackets() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(YesmennCreates.MODID, name));
    }

    /** Server -> client: all wires of the player's level. */
    public record Sync(List<SurfaceWire> wires) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = payloadType("surface_wire_sync");
        public static final StreamCodec<ByteBuf, Sync> STREAM_CODEC =
                SurfaceWire.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Sync::new, Sync::wires);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    /** Server -> client: a wire was laid. */
    public record Add(SurfaceWire wire) implements CustomPacketPayload {
        public static final Type<Add> TYPE = payloadType("surface_wire_add");
        public static final StreamCodec<ByteBuf, Add> STREAM_CODEC = SurfaceWire.STREAM_CODEC.map(Add::new, Add::wire);

        @Override
        public Type<Add> type() {
            return TYPE;
        }
    }

    /** Server -> client: a wire was removed. */
    public record Remove(int id) implements CustomPacketPayload {
        public static final Type<Remove> TYPE = payloadType("surface_wire_remove");
        public static final StreamCodec<ByteBuf, Remove> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Remove::new, Remove::id);

        @Override
        public Type<Remove> type() {
            return TYPE;
        }
    }

    /** Client -> server: the player wants to remove the wire they are looking at. */
    public record RemoveRequest(int id) implements CustomPacketPayload {
        public static final Type<RemoveRequest> TYPE = payloadType("surface_wire_remove_request");
        public static final StreamCodec<ByteBuf, RemoveRequest> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(RemoveRequest::new, RemoveRequest::id);

        @Override
        public Type<RemoveRequest> type() {
            return TYPE;
        }
    }

    /** Client -> server: whether the player holds Ctrl (lay the wire through the air). */
    public record AirMode(boolean enabled) implements CustomPacketPayload {
        public static final Type<AirMode> TYPE = payloadType("surface_wire_air_mode");
        public static final StreamCodec<ByteBuf, AirMode> STREAM_CODEC = ByteBufCodecs.BOOL.map(AirMode::new, AirMode::enabled);

        @Override
        public Type<AirMode> type() {
            return TYPE;
        }
    }

    private static final Set<UUID> AIR_MODE = ConcurrentHashMap.newKeySet();

    public static boolean isAirMode(Player player) {
        return AIR_MODE.contains(player.getUUID());
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(Sync.TYPE, Sync.STREAM_CODEC, (p, c) -> SurfaceWireClient.handleSync(p.wires()));
        registrar.playToClient(Add.TYPE, Add.STREAM_CODEC, (p, c) -> SurfaceWireClient.handleAdd(p.wire()));
        registrar.playToClient(Remove.TYPE, Remove.STREAM_CODEC, (p, c) -> SurfaceWireClient.handleRemove(p.id()));
        registrar.playToServer(RemoveRequest.TYPE, RemoveRequest.STREAM_CODEC, SurfaceWirePackets::handleRemoveRequest);
        registrar.playToServer(AirMode.TYPE, AirMode.STREAM_CODEC, (packet, context) -> {
            if (packet.enabled()) {
                AIR_MODE.add(context.player().getUUID());
            } else {
                AIR_MODE.remove(context.player().getUUID());
            }
        });
    }

    private static void handleRemoveRequest(RemoveRequest packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        SurfaceWireData data = SurfaceWireData.get(level);
        SurfaceWire wire = data.get(packet.id());
        if (wire == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        if (wire.bounds().distanceToSqr(eye) > MAX_REMOVE_DISTANCE * MAX_REMOVE_DISTANCE) {
            return;
        }
        data.remove(wire.id());
        if (!player.getAbilities().instabuild) {
            ItemStack drop = new ItemStack(EEItems.forMaterial(wire.material()), wire.items());
            Block.popResource(level, BlockPos.containing(wire.bounds().getCenter()), drop);
        }
    }
}
