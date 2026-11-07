package com.yesmenn.creates.compat.electroenergetics.surfacewire;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * One laid flat wire: an axis aligned polyline on block surfaces. Each end is either attached to an EE node
 * (connector / device) or free; free ends at the same point are connected to each other.
 *
 * @param items number of wire items used, dropped again when the wire is removed
 */
public record SurfaceWire(int id, FlatWireMaterial material, List<GridPos> points,
                          Optional<InWorldNode> startNode, Optional<InWorldNode> endNode, int items) {

    public static final Codec<SurfaceWire> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(SurfaceWire::id),
            FlatWireMaterial.CODEC.fieldOf("material").forGetter(SurfaceWire::material),
            GridPos.CODEC.listOf().fieldOf("points").forGetter(SurfaceWire::points),
            InWorldNode.CODEC.optionalFieldOf("start_node").forGetter(SurfaceWire::startNode),
            InWorldNode.CODEC.optionalFieldOf("end_node").forGetter(SurfaceWire::endNode),
            Codec.INT.fieldOf("items").forGetter(SurfaceWire::items)
    ).apply(i, SurfaceWire::new));

    public static final StreamCodec<ByteBuf, SurfaceWire> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SurfaceWire::id,
            ByteBufCodecs.VAR_INT.map(o -> FlatWireMaterial.values()[o], Enum::ordinal), SurfaceWire::material,
            GridPos.STREAM_CODEC.apply(ByteBufCodecs.list()), SurfaceWire::points,
            ByteBufCodecs.optional(InWorldNode.STREAM_CODEC), SurfaceWire::startNode,
            ByteBufCodecs.optional(InWorldNode.STREAM_CODEC), SurfaceWire::endNode,
            ByteBufCodecs.VAR_INT, SurfaceWire::items,
            SurfaceWire::new
    );

    public SurfaceWire {
        points = List.copyOf(points);
    }

    public SurfaceWire withId(int newId) {
        return new SurfaceWire(newId, material, points, startNode, endNode, items);
    }

    public GridPos start() {
        return points.getFirst();
    }

    public GridPos end() {
        return points.getLast();
    }

    public double lengthInBlocks() {
        return length(points);
    }

    public static double length(List<GridPos> points) {
        int length = 0;
        for (int i = 1; i < points.size(); i++) {
            length += points.get(i - 1).manhattan(points.get(i));
        }
        return length / (double) GridPos.SCALE;
    }

    public boolean hasFreeEndAt(GridPos pos) {
        return (startNode.isEmpty() && start().equals(pos)) || (endNode.isEmpty() && end().equals(pos));
    }

    /** Bounds of the wire including its thickness. */
    public AABB bounds() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (GridPos p : points) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            minZ = Math.min(minZ, p.z());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
            maxZ = Math.max(maxZ, p.z());
        }
        double s = GridPos.SCALE;
        int h = material.halfThickness();
        return new AABB((minX - h) / s, (minY - h) / s, (minZ - h) / s, (maxX + h) / s, (maxY + h) / s, (maxZ + h) / s);
    }

    /** Bounds of the straight piece from {@code a} to {@code b} for a wire of half thickness {@code h}. */
    public static AABB segmentBounds(GridPos a, GridPos b, int h, double inflate) {
        double s = GridPos.SCALE;
        return new AABB(
                (Math.min(a.x(), b.x()) - h) / s, (Math.min(a.y(), b.y()) - h) / s, (Math.min(a.z(), b.z()) - h) / s,
                (Math.max(a.x(), b.x()) + h) / s, (Math.max(a.y(), b.y()) + h) / s, (Math.max(a.z(), b.z()) + h) / s
        ).inflate(inflate);
    }
}
