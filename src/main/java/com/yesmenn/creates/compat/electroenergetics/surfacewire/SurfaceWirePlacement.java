package com.yesmenn.creates.compat.electroenergetics.surfacewire;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Placement rules shared by the client preview and the server, so both compute exactly the same wire.
 * <p>
 * Normal mode: the wire runs on the surface ("plane") between the two ends as an L - along the longer of the
 * two in-plane axes first, so the 90° corner follows the crosshair. An end at an EE node is reached with a
 * straight piece perpendicular to the surface (down from / up to the node).
 * <p>
 * Air mode (Ctrl held): the wire first moves perpendicular to the target surface onto the target's level and
 * then runs as an L at that level, so it can span through the air.
 */
public final class SurfaceWirePlacement {
    public static final int MAX_LENGTH_BLOCKS = 64;
    /** How close (in blocks) a click must be to an EE node to snap to it. */
    private static final float NODE_RADIUS = 0.4f;
    /** How far (in blocks) we look below a node for the surface when both ends are nodes. */
    private static final int SURFACE_SEARCH_BLOCKS = 4;

    private SurfaceWirePlacement() {}

    /** Where a click ends up: an EE node, or a point on the clicked surface. */
    public record Target(GridPos pos, Optional<InWorldNode> node, Direction face) {}

    public static Target resolve(Level level, BlockPos clickedPos, Vec3 hit, Direction face, FlatWireMaterial material) {
        int thickness = material.thicknessPixels();
        InWorldNode node = null;
        try {
            node = InWorldNode.closestNode(level, hit, NODE_RADIUS, clickedPos);
        } catch (RuntimeException ignored) {
            // not an EE block
        }
        if (node != null) {
            Vec3 nodePos = node.getPosition(level);
            if (nodePos != null) {
                // the wire lies against the outside of the model on the clicked side and ends level with the node:
                // go from the node to the model's surface on that side, then out by half the wire thickness.
                // Moving out before snapping also keeps rounding from pushing the wire into the model on the
                // negative sides. For a full block the node is on the face, so only the half thickness is added.
                double out = distanceToSurface(level, node.sourcePos(), nodePos, face) + thickness / 32.0;
                Vec3 point = nodePos.add(Vec3.atLowerCornerOf(face.getNormal()).scale(out));
                return new Target(GridPos.snap(point, thickness), Optional.of(node), face);
            }
        }
        // lift the wire's centre by half its thickness so it lies on the surface
        Vec3 onSurface = hit.add(Vec3.atLowerCornerOf(face.getNormal()).scale(thickness / 32.0));
        return new Target(GridPos.snap(onSurface, thickness), Optional.empty(), face);
    }

    /**
     * Distance (in blocks) from {@code point} inside the block's outline shape to where that shape ends in
     * direction {@code dir}; 0 if the point is not inside the shape (e.g. on the face of a full block).
     */
    private static double distanceToSurface(BlockGetter level, BlockPos pos, Vec3 point, Direction dir) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        Direction.Axis axis = dir.getAxis();
        double c = point.get(axis);
        double distance = 0;
        for (AABB box : shape.toAabbs()) {
            AABB b = box.move(pos);
            boolean contains = true;
            for (Direction.Axis other : Direction.Axis.values()) {
                double v = point.get(other);
                if (v < b.min(other) - 1e-6 || v > b.max(other) + 1e-6) {
                    contains = false;
                }
            }
            if (contains) {
                double exit = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? b.max(axis) - c : c - b.min(axis);
                distance = Math.max(distance, exit);
            }
        }
        return distance;
    }

    /**
     * @param surface face of the surface the wire runs on (UP = floor)
     * @param look    view direction of the player: the L starts along the in-plane axis you look along
     * @param exempt  connector blocks at the ends (ignored when looking for the surface)
     * @param startBlock block of the start node, or null
     * @param endBlock   block of the end node, or null
     */
    public static List<GridPos> path(BlockGetter level, GridPos start, boolean startIsNode, GridPos end, boolean endIsNode,
                                     Direction surface, boolean airMode, Vec3 look, FlatWireMaterial material,
                                     Set<BlockPos> exempt, BlockPos startBlock, BlockPos endBlock) {
        Direction.Axis normal = surface.getAxis();
        int plane;
        if (airMode || !endIsNode) {
            plane = end.get(normal);
        } else if (!startIsNode) {
            plane = start.get(normal);
        } else {
            plane = findSurface(level, start, surface, material, exempt);
        }

        List<GridPos> points = new ArrayList<>(5);
        points.add(start);
        GridPos current = start.with(normal, plane);
        addIfMoved(points, current);

        // L on the plane: first along the in-plane axis the player looks along
        Direction.Axis first = null;
        Direction.Axis second = null;
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == normal) {
                continue;
            }
            if (first == null) {
                first = axis;
            } else {
                second = axis;
            }
        }
        if (Math.abs(look.get(second)) > Math.abs(look.get(first))) {
            Direction.Axis swap = first;
            first = second;
            second = swap;
        }
        current = current.with(first, end.get(first));
        addIfMoved(points, current);
        current = current.with(second, end.get(second));
        addIfMoved(points, current);

        addIfMoved(points, end);

        return points;
    }

    public static boolean isFullBlock(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }

    private static void addIfMoved(List<GridPos> points, GridPos pos) {
        if (!points.getLast().equals(pos)) {
            points.add(pos);
        }
    }

    /**
     * Grid coordinate (along the surface normal) at which the wire would lie on the first surface below
     * {@code from}. Falls back to the coordinate of {@code from}.
     */
    private static int findSurface(BlockGetter level, GridPos from, Direction surface, FlatWireMaterial material,
                                   Set<BlockPos> exempt) {
        Direction.Axis axis = surface.getAxis();
        int sign = surface.getAxisDirection().getStep();
        boolean odd = (material.thicknessPixels() & 1) == 1;
        GridPos probe = from;
        for (int i = 0; i < SURFACE_SEARCH_BLOCKS * 16; i++) {
            probe = probe.with(axis, probe.get(axis) - 2 * sign);
            if (isBlocked(level, probe, exempt)) {
                int surfaceCoord = probe.get(axis) + sign * (odd ? 1 : 2);
                return surfaceCoord + sign * material.halfThickness();
            }
        }
        return from.get(axis);
    }

    /**
     * Blocks of the end nodes the wire may enter: only connectors and other blocks that are not a full cube.
     * A full block device (e.g. a battery) is connected at its face.
     */
    public static Set<BlockPos> enterableBlocks(BlockGetter level, Optional<InWorldNode> a, Optional<InWorldNode> b) {
        Set<BlockPos> blocks = new java.util.HashSet<>(2);
        for (Optional<InWorldNode> node : List.of(a, b)) {
            node.ifPresent(n -> {
                BlockPos pos = n.sourcePos();
                if (!isFullBlock(level, pos)) {
                    blocks.add(pos);
                }
            });
        }
        return blocks;
    }

    /**
     * Whether every piece of the wire that runs on the surface plane actually lies on a block. Pieces that
     * leave the plane (towards a node) are not checked.
     */
    public static boolean isSupported(BlockGetter level, List<GridPos> points, Direction surface,
                                      FlatWireMaterial material, Set<BlockPos> exempt) {
        Direction.Axis axis = surface.getAxis();
        int below = -surface.getAxisDirection().getStep() * (material.halfThickness() + 1);
        for (int i = 1; i < points.size(); i++) {
            GridPos a = points.get(i - 1);
            GridPos b = points.get(i);
            if (a.get(axis) != b.get(axis)) {
                continue; // perpendicular piece to / from a node
            }
            int steps = a.manhattan(b) / 2;
            int dx = Integer.signum(b.x() - a.x()) * 2;
            int dy = Integer.signum(b.y() - a.y()) * 2;
            int dz = Integer.signum(b.z() - a.z()) * 2;
            for (int k = 0; k <= steps; k++) {
                GridPos p = new GridPos(a.x() + dx * k, a.y() + dy * k, a.z() + dz * k);
                if (exempt.contains(p.blockPos())) {
                    continue;
                }
                if (!isBlocked(level, p.with(axis, p.get(axis) + below), Set.of())) {
                    return false;
                }
            }
        }
        return true;
    }

    public static int itemCost(double lengthInBlocks) {
        return Math.max(1, (int) Math.ceil(lengthInBlocks - 1e-6));
    }

    /**
     * Whether the wire does not pass through any block. Blocks in {@code exempt} (the blocks of the EE nodes
     * the wire attaches to) are ignored.
     */
    public static boolean isClear(BlockGetter level, List<GridPos> points, Set<BlockPos> exempt) {
        for (int i = 1; i < points.size(); i++) {
            GridPos a = points.get(i - 1);
            GridPos b = points.get(i);
            int steps = a.manhattan(b) / 2;
            int dx = Integer.signum(b.x() - a.x()) * 2;
            int dy = Integer.signum(b.y() - a.y()) * 2;
            int dz = Integer.signum(b.z() - a.z()) * 2;
            for (int k = 0; k <= steps; k++) {
                GridPos p = new GridPos(a.x() + dx * k, a.y() + dy * k, a.z() + dz * k);
                if (isBlocked(level, p, exempt)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isBlocked(BlockGetter level, GridPos p, Set<BlockPos> exempt) {
        BlockPos pos = p.blockPos();
        if (exempt.contains(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        Vec3 local = p.toVec3().subtract(pos.getX(), pos.getY(), pos.getZ());
        for (AABB box : shape.toAabbs()) {
            if (box.contains(local)) {
                return true;
            }
        }
        return false;
    }
}
