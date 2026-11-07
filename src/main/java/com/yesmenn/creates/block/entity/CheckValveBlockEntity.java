package com.yesmenn.creates.block.entity;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity.StraightPipeFluidTransportBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.yesmenn.creates.block.CheckValveBlock;
import com.yesmenn.creates.registry.YMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Rückschlagventil: verhält sich wie ein gerades Create-Rohr, lässt Fluid aber nur
 * von der Einlass-Seite (FACING.getOpposite()) zur Auslass-Seite (FACING) fließen.
 *
 * Create bestimmt die Flussrichtung über den Druck, den Pumpen auf die einzelnen
 * Rohr-Seiten verteilen ("inbound" = Fluid fließt über diese Seite IN das Rohr,
 * "outbound" = Fluid fließt über diese Seite HERAUS). Wir verwerfen einfach jeden
 * Druck, der einen Fluss in Sperrrichtung erzeugen würde – damit kann dort nie ein
 * Flow entstehen, egal ob die Pumpe drückt oder saugt.
 */
public class CheckValveBlockEntity extends SmartBlockEntity {

    public CheckValveBlockEntity(BlockPos pos, BlockState state) {
        super(YMBlockEntities.CHECK_VALVE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new CheckValvePipeBehaviour(this));
        registerAwardables(behaviours, FluidPropagator.getSharedTriggers());
    }

    public static class CheckValvePipeBehaviour extends StraightPipeFluidTransportBehaviour {

        public CheckValvePipeBehaviour(SmartBlockEntity be) {
            super(be);
        }

        /** Nur entlang der Ventilachse gibt es Anschlüsse – wie bei einem geraden Rohr. */
        @Override
        public boolean canHaveFlowToward(BlockState state, Direction direction) {
            return state.getBlock() instanceof CheckValveBlock
                    && state.getValue(CheckValveBlock.FACING).getAxis() == direction.getAxis();
        }

        /** Druck in Sperrrichtung wird ignoriert. */
        @Override
        public void addPressure(Direction side, boolean inbound, float pressure) {
            if (!isAllowedFlow(blockEntity.getBlockState(), side, inbound))
                return;
            super.addPressure(side, inbound, pressure);
        }

        /** Zusätzliche Absicherung: Fluid darf nur über den Einlass hereingezogen werden. */
        @Override
        public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
            if (!(state.getBlock() instanceof CheckValveBlock))
                return false;
            if (direction != CheckValveBlock.getInlet(state))
                return false;
            return super.canPullFluidFrom(fluid, state, direction);
        }

        private static boolean isAllowedFlow(BlockState state, Direction side, boolean inbound) {
            if (!(state.getBlock() instanceof CheckValveBlock))
                return false;
            if (side == CheckValveBlock.getInlet(state))
                return inbound;   // am Einlass darf Fluid nur hinein
            if (side == CheckValveBlock.getOutlet(state))
                return !inbound;  // am Auslass darf Fluid nur hinaus
            return false;
        }
    }
}
