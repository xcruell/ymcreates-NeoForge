package com.yesmenn.creates.compat.electroenergetics.surfacewire;

import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.yesmenn.creates.compat.electroenergetics.EEDataComponents;
import com.yesmenn.creates.compat.electroenergetics.client.SurfaceWireClient;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Flat wire item. Right-click an EE connector (or the free end of a laid wire) to start, then right-click
 * a block: the wire is laid along the block surfaces up to that point and you continue from there.
 * Right-clicking an EE connector finishes the wire. Holding Ctrl lays the wire through the air.
 * Sneak + right-click cancels, or removes the wire you look at.
 */
public class SurfaceWireItem extends Item {
    /** How far (in 1/32 block) a click may be from a free wire end to continue from it. */
    private static final int END_SNAP = 4;

    private final FlatWireMaterial material;

    public SurfaceWireItem(Properties properties, FlatWireMaterial material) {
        super(properties);
        this.material = material;
    }

    public FlatWireMaterial getMaterial() {
        return material;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            return sneakUse(level, player, stack)
                    ? InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
                    : InteractionResultHolder.pass(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    /**
     * Runs before the clicked block reacts. Without this, blocks with their own interaction (e.g. EE's momentary
     * switch) would be operated instead of getting a wire. Only taken over when aiming at an EE node or while a
     * wire is being laid; otherwise blocks behave as usual.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        boolean laying = stack.has(EEDataComponents.WIRE_START.get());
        boolean atNode = SurfaceWirePlacement.resolve(context.getLevel(), context.getClickedPos(),
                context.getClickLocation(), context.getClickedFace(), material).node().isPresent();
        return laying || atNode ? useOn(context) : InteractionResult.PASS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            return sneakUse(level, player, stack) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
        }

        SurfaceWirePlacement.Target target = SurfaceWirePlacement.resolve(
                level, context.getClickedPos(), context.getClickLocation(), context.getClickedFace(), material);
        GridPos targetPos = target.node().isPresent() ? target.pos() : snapToFreeEnd(level, target.pos());
        WireStart start = stack.get(EEDataComponents.WIRE_START.get());

        if (start == null) {
            boolean freeEnd = target.node().isEmpty() && !targetPos.equals(target.pos());
            if (target.node().isEmpty() && !freeEnd) {
                message(player, "start_hint", ChatFormatting.YELLOW);
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide) {
                stack.set(EEDataComponents.WIRE_START.get(), new WireStart(targetPos, target.node(),
                        target.node().isEmpty() ? Optional.of(target.face()) : Optional.empty()));
                message(player, "started", ChatFormatting.GREEN);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        Planned planned = plan(level, player, start, target, targetPos);
        if (planned.points().size() < 2) {
            return InteractionResult.FAIL;
        }
        if (planned.problem() != null) {
            message(player, planned.problem(), ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        SurfaceWireData.get((ServerLevel) level).add(
                new SurfaceWire(0, material, planned.points(), start.node(), target.node(), planned.cost()));
        if (target.node().isPresent()) {
            stack.remove(EEDataComponents.WIRE_START.get());
            message(player, "connected", ChatFormatting.GREEN);
        } else {
            stack.set(EEDataComponents.WIRE_START.get(),
                    new WireStart(targetPos, Optional.empty(), Optional.of(target.face())));
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(planned.cost());
        }
        return InteractionResult.CONSUME;
    }

    /**
     * The wire that would be laid from {@code start} to {@code target}.
     *
     * @param problem translation key suffix of why it cannot be laid, or {@code null}
     */
    public record Planned(List<GridPos> points, double length, int cost, String problem) {}

    /** Used by the item and the client preview, so the preview is exactly what gets placed. */
    public static Planned plan(Level level, Player player, WireStart start, SurfaceWirePlacement.Target target,
                               GridPos targetPos) {
        ItemStack stack = player.getMainHandItem().getItem() instanceof SurfaceWireItem
                ? player.getMainHandItem() : player.getOffhandItem();
        FlatWireMaterial material = stack.getItem() instanceof SurfaceWireItem item ? item.material : FlatWireMaterial.COPPER;

        Set<BlockPos> exempt = SurfaceWirePlacement.enterableBlocks(level, start.node(), target.node());
        Direction surface = target.node().isEmpty() ? target.face() : start.face().orElse(Direction.UP);
        boolean airMode = isAirMode(player);
        List<GridPos> points = SurfaceWirePlacement.path(level, start.pos(), start.node().isPresent(),
                targetPos, target.node().isPresent(), surface, airMode, player.getLookAngle(), material, exempt,
                start.node().map(InWorldNode::sourcePos).orElse(null),
                target.node().map(InWorldNode::sourcePos).orElse(null));
        double length = SurfaceWire.length(points);
        int cost = SurfaceWirePlacement.itemCost(length);

        String problem = null;
        if (length > SurfaceWirePlacement.MAX_LENGTH_BLOCKS) {
            problem = "too_long";
        } else if (!SurfaceWirePlacement.isClear(level, points, exempt)) {
            problem = "blocked";
        } else if (!airMode && !SurfaceWirePlacement.isSupported(level, points, surface, material, exempt)) {
            problem = "unsupported";
        } else if (!player.getAbilities().instabuild && stack.getCount() < cost) {
            problem = "not_enough";
        }
        return new Planned(points, length, cost, problem);
    }

    private static boolean isAirMode(Player player) {
        return player.level().isClientSide ? SurfaceWireClient.isAirMode() : SurfaceWirePackets.isAirMode(player);
    }

    /** Sneak + right-click: remove the looked-at wire (client decides), otherwise cancel the current wire. */
    private static boolean sneakUse(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            int id = SurfaceWireClient.lookedAtWire(player);
            if (id >= 0) {
                SurfaceWireClient.requestRemove(id);
                return true;
            }
            return stack.has(EEDataComponents.WIRE_START.get());
        }
        if (stack.has(EEDataComponents.WIRE_START.get())) {
            stack.remove(EEDataComponents.WIRE_START.get());
            message(player, "cancelled", ChatFormatting.GRAY);
        }
        return true;
    }

    /** The free wire end close to {@code pos}, or {@code pos} itself. */
    public static GridPos snapToFreeEnd(Level level, GridPos pos) {
        Collection<SurfaceWire> wires = level.isClientSide
                ? SurfaceWireClient.wires()
                : SurfaceWireData.get((ServerLevel) level).wires();
        GridPos best = pos;
        int bestDistance = Integer.MAX_VALUE;
        for (SurfaceWire wire : wires) {
            for (GridPos end : List.of(wire.start(), wire.end())) {
                if (!wire.hasFreeEndAt(end)) {
                    continue;
                }
                int d = Math.max(Math.abs(end.x() - pos.x()), Math.max(Math.abs(end.y() - pos.y()), Math.abs(end.z() - pos.z())));
                if (d <= END_SNAP && d < bestDistance) {
                    best = end;
                    bestDistance = d;
                }
            }
        }
        return best;
    }

    private static void message(Player player, String key, ChatFormatting color) {
        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.translatable("message.yesmenncreates.flat_wire." + key)
                    .withStyle(color), true);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Tooltip
    // ------------------------------------------------------------------------------------------------

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (material.hidden()) {
            tooltip.add(Component.translatable("tooltip.yesmenncreates.flat_wire.invisible").withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.yesmenncreates.flat_wire.electric_stats").withStyle(ChatFormatting.GRAY));
        tooltip.add(stat("resistance", Units.resistancePerMeter(material.resistancePerBlock()), ChatFormatting.AQUA));
        if (material.insulated()) {
            tooltip.add(stat("max_voltage", Units.voltage(material.maxInsulationVoltage()), ChatFormatting.GOLD));
        }
        tooltip.add(stat("max_length", Component.translatable("tooltip.yesmenncreates.flat_wire.blocks",
                SurfaceWirePlacement.MAX_LENGTH_BLOCKS), ChatFormatting.YELLOW));

        tooltip.add(Component.empty());
        if (flag.hasShiftDown()) {
            for (int i = 1; i <= 5; i++) {
                tooltip.add(Component.translatable("tooltip.yesmenncreates.flat_wire.usage" + i).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.yesmenncreates.flat_wire.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component stat(String key, Component value, ChatFormatting color) {
        MutableComponent line = Component.literal(" ")
                .append(Component.translatable("tooltip.yesmenncreates.flat_wire." + key).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" "));
        return line.append(value.copy().withStyle(color));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(EEDataComponents.WIRE_START.get()) || super.isFoil(stack);
    }
}
