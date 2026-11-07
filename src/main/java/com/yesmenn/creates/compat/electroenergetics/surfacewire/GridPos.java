package com.yesmenn.creates.compat.electroenergetics.surfacewire;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * A point of a flat wire in units of 1/32 block. A wire with an odd thickness (in pixels) has its points on
 * pixel centres (odd values), one with an even thickness on pixel borders (even values), so its square
 * cross-section always lines up with the pixel grid.
 */
public record GridPos(int x, int y, int z) {
    public static final int SCALE = 32;

    public static final Codec<GridPos> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(GridPos::x),
            Codec.INT.fieldOf("y").forGetter(GridPos::y),
            Codec.INT.fieldOf("z").forGetter(GridPos::z)
    ).apply(i, GridPos::new));

    public static final StreamCodec<ByteBuf, GridPos> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GridPos::x,
            ByteBufCodecs.VAR_INT, GridPos::y,
            ByteBufCodecs.VAR_INT, GridPos::z,
            GridPos::new
    );

    /** Snaps a world position to the grid of a wire with the given thickness in pixels. */
    public static GridPos snap(Vec3 pos, int thicknessPixels) {
        boolean odd = (thicknessPixels & 1) == 1;
        return new GridPos(snap(pos.x, odd), snap(pos.y, odd), snap(pos.z, odd));
    }

    private static int snap(double v, boolean odd) {
        return odd ? 2 * (int) Math.floor(v * 16) + 1 : 2 * (int) Math.round(v * 16);
    }

    public int get(Direction.Axis axis) {
        return axis.choose(x, y, z);
    }

    public GridPos with(Direction.Axis axis, int value) {
        return switch (axis) {
            case X -> new GridPos(value, y, z);
            case Y -> new GridPos(x, value, z);
            case Z -> new GridPos(x, y, value);
        };
    }

    public Vec3 toVec3() {
        return new Vec3(x / (double) SCALE, y / (double) SCALE, z / (double) SCALE);
    }

    public BlockPos blockPos() {
        return new BlockPos(Math.floorDiv(x, SCALE), Math.floorDiv(y, SCALE), Math.floorDiv(z, SCALE));
    }

    public int manhattan(GridPos other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
    }
}
