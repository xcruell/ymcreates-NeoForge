package com.yesmenn.creates.compat.electroenergetics.surfacewire;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/**
 * Item data: where the wire currently being laid starts (an EE node or the free end of a laid wire).
 *
 * @param face for a free end: the face of the surface the wire lies on there
 */
public record WireStart(GridPos pos, Optional<InWorldNode> node, Optional<Direction> face) {
    public static final Codec<WireStart> CODEC = RecordCodecBuilder.create(i -> i.group(
            GridPos.CODEC.fieldOf("pos").forGetter(WireStart::pos),
            InWorldNode.CODEC.optionalFieldOf("node").forGetter(WireStart::node),
            Direction.CODEC.optionalFieldOf("face").forGetter(WireStart::face)
    ).apply(i, WireStart::new));

    public static final StreamCodec<ByteBuf, WireStart> STREAM_CODEC = StreamCodec.composite(
            GridPos.STREAM_CODEC, WireStart::pos,
            ByteBufCodecs.optional(InWorldNode.STREAM_CODEC), WireStart::node,
            ByteBufCodecs.optional(Direction.STREAM_CODEC), WireStart::face,
            WireStart::new
    );
}
