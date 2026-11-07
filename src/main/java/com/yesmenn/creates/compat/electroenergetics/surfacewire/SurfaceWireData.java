package com.yesmenn.creates.compat.electroenergetics.surfacewire;

import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.Node;
import com.george_vi.electroenergetics.simulation.CircuitBuilder;
import com.george_vi.electroenergetics.simulation.WrappedIndexedNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.mojang.serialization.Codec;
import com.yesmenn.creates.YesmennCreates;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All flat wires of one level, saved with the level and mirrored to the clients.
 * <p>
 * Electrically every wire is one resistor (length x resistance per block) between its two ends. An end at an
 * EE node uses that node, free ends at the same point share an internal node. Wire branches that end
 * without reaching a node are left out. The resistors are added to EE's circuit every simulation tick via
 * {@link AddToElectricGraphEvent} and only recomputed when wires change.
 */
public class SurfaceWireData extends SavedData {
    private static final String NAME = YesmennCreates.MODID + "_surface_wires";
    private static final String JUNCTION_OWNER = YesmennCreates.MODID + ":surface_wire";
    private static final double MIN_RESISTANCE = 1e-6;
    private static final Codec<List<SurfaceWire>> LIST_CODEC = SurfaceWire.CODEC.listOf();

    private final Int2ObjectMap<SurfaceWire> wires = new Int2ObjectLinkedOpenHashMap<>();
    private int nextId = 1;
    private ServerLevel level;

    private List<Bridge> bridges = List.of();
    private boolean bridgesDirty = true;
    private int nextJunctionId = 0;

    public static SurfaceWireData get(ServerLevel level) {
        SurfaceWireData data = level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SurfaceWireData::new, SurfaceWireData::load, null), NAME);
        data.level = level;
        return data;
    }

    private static SurfaceWireData load(CompoundTag tag, HolderLookup.Provider registries) {
        SurfaceWireData data = new SurfaceWireData();
        data.nextId = tag.getInt("NextId");
        LIST_CODEC.parse(NbtOps.INSTANCE, tag.get("Wires"))
                .resultOrPartial(error -> YesmennCreates.LOGGER.error("Could not load flat wires: {}", error))
                .ifPresent(list -> list.forEach(wire -> data.wires.put(wire.id(), wire)));
        data.nextId = Math.max(data.nextId, data.wires.keySet().intStream().max().orElse(0) + 1);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextId", nextId);
        LIST_CODEC.encodeStart(NbtOps.INSTANCE, new ArrayList<>(wires.values()))
                .resultOrPartial(error -> YesmennCreates.LOGGER.error("Could not save flat wires: {}", error))
                .ifPresent(list -> tag.put("Wires", list));
        return tag;
    }

    public Collection<SurfaceWire> wires() {
        return wires.values();
    }

    public SurfaceWire get(int id) {
        return wires.get(id);
    }

    public boolean hasFreeEndAt(GridPos pos) {
        for (SurfaceWire wire : wires.values()) {
            if (wire.hasFreeEndAt(pos)) {
                return true;
            }
        }
        return false;
    }

    public SurfaceWire add(SurfaceWire wire) {
        SurfaceWire added = wire.withId(nextId++);
        wires.put(added.id(), added);
        changed();
        PacketDistributor.sendToPlayersInDimension(level, new SurfaceWirePackets.Add(added));
        return added;
    }

    public SurfaceWire remove(int id) {
        SurfaceWire removed = wires.remove(id);
        if (removed != null) {
            changed();
            PacketDistributor.sendToPlayersInDimension(level, new SurfaceWirePackets.Remove(id));
        }
        return removed;
    }

    public void syncTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SurfaceWirePackets.Sync(new ArrayList<>(wires.values())));
    }

    private void changed() {
        setDirty();
        bridgesDirty = true;
    }

    // ------------------------------------------------------------------------------------------------
    // Electrical
    // ------------------------------------------------------------------------------------------------

    public static void onAddToElectricGraph(AddToElectricGraphEvent event) {
        SurfaceWireData data = get(event.level);
        if (data.wires.isEmpty()) {
            return;
        }
        if (data.bridgesDirty) {
            data.bridges = data.computeBridges();
            data.bridgesDirty = false;
        }
        data.addTo(event.builder, event.sd);
    }

    private record Edge(Object a, Object b, double resistance) {}

    private record Bridge(Node a, Node b, double resistance) {}

    private List<Bridge> computeBridges() {
        // vertices: InWorldNode (attached end) or GridPos (free end)
        List<Edge> edges = new ArrayList<>();
        Map<Object, Integer> degree = new HashMap<>();
        for (SurfaceWire wire : wires.values()) {
            Object a = wire.startNode().<Object>map(n -> n).orElse(wire.start());
            Object b = wire.endNode().<Object>map(n -> n).orElse(wire.end());
            if (a.equals(b)) {
                continue;
            }
            edges.add(new Edge(a, b, wire.lengthInBlocks() * wire.material().resistancePerBlock()));
            degree.merge(a, 1, Integer::sum);
            degree.merge(b, 1, Integer::sum);
        }

        // drop branches that end in a free end: they carry no current
        ArrayDeque<Object> deadEnds = new ArrayDeque<>();
        degree.forEach((vertex, d) -> {
            if (vertex instanceof GridPos && d <= 1) {
                deadEnds.add(vertex);
            }
        });
        Set<Object> removed = new java.util.HashSet<>();
        while (!deadEnds.isEmpty()) {
            Object vertex = deadEnds.poll();
            if (!removed.add(vertex)) {
                continue;
            }
            for (Iterator<Edge> it = edges.iterator(); it.hasNext(); ) {
                Edge edge = it.next();
                Object other = edge.a().equals(vertex) ? edge.b() : edge.b().equals(vertex) ? edge.a() : null;
                if (other == null) {
                    continue;
                }
                it.remove();
                int d = degree.merge(other, -1, Integer::sum);
                if (other instanceof GridPos && d <= 1) {
                    deadEnds.add(other);
                }
            }
        }

        Map<Object, Node> nodes = new HashMap<>();
        Map<Set<Node>, Double> conductances = new HashMap<>();
        for (Edge edge : edges) {
            Node a = nodes.computeIfAbsent(edge.a(), this::toNode);
            Node b = nodes.computeIfAbsent(edge.b(), this::toNode);
            conductances.merge(Set.of(a, b), 1.0 / Math.max(edge.resistance(), MIN_RESISTANCE), Double::sum);
        }
        List<Bridge> result = new ArrayList<>(conductances.size());
        conductances.forEach((pair, conductance) -> {
            Iterator<Node> it = pair.iterator();
            result.add(new Bridge(it.next(), it.next(), 1.0 / conductance));
        });
        return result;
    }

    private Node toNode(Object vertex) {
        return vertex instanceof InWorldNode node ? node : new AttachedNode(nextJunctionId++, JUNCTION_OWNER);
    }

    private void addTo(CircuitBuilder builder, InfrastructureSavedData sd) {
        for (Bridge bridge : bridges) {
            if (!isPresent(bridge.a(), sd) || !isPresent(bridge.b(), sd)) {
                continue; // connector was removed
            }
            ElectricalProperties properties = ElectricalProperties.resistor(bridge.resistance());
            WrappedIndexedNode a = builder.getNode(bridge.a());
            WrappedIndexedNode b = builder.getNode(bridge.b());
            if (a != null && b != null) {
                // never overwrite an existing connection, put ours in parallel to a plain resistor
                ElectricalProperties existing = builder.getConnectionProperties(a, b);
                if (existing != null) {
                    if (!existing.isSimpleResistor()) {
                        continue;
                    }
                    properties = ElectricalProperties.resistor(
                            1.0 / (1.0 / existing.resistance() + 1.0 / bridge.resistance()));
                }
            }
            builder.connect(bridge.a(), bridge.b(), properties);
        }
    }

    private static boolean isPresent(Node node, InfrastructureSavedData sd) {
        return !(node instanceof InWorldNode inWorldNode) || sd.hasNode(inWorldNode);
    }
}
