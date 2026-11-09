package com.yesmenn.creates.client.ponder;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.yesmenn.creates.registry.YMBlocks;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CheckValvePonder {

    // Plugin Call
    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(YMBlocks.CHECK_VALVE.getId())
                .addStoryBoard("checkvalve/basics", CheckValvePonder::basics); // CAPPED LETTERS CRASHED THE GAME
    }

    // Scene start
    public static void basics(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("check_valve", "Using the Check Valve");
        scene.configureBasePlate(0, 0, 5); // Base for Ponder
        // Pos
        BlockPos valve  = util.grid().at(2, 1, 1);
        //BlockPos after = util.grid().at(3, 1, 1);   // Infocube Pressure
        BlockPos before  = util.grid().at(1, 1, 1);   // Infocube Exit
        //BlockPos pump   = util.grid().at(1, 1, 2);
        BlockPos source = util.grid().at(1, 1, 3);   // Tank1
        BlockPos target = util.grid().at(3, 1, 3);   // Tank2
        var drive = util.select().position(0, 1, 2);   // Cogwheel
        var pump =  util.select().position(1, 1, 2); // Pump

        // YesMenn! Intro
        YMPonderAnim.introSpiral(scene, util, 5, false);
        scene.world().showSection(util.select().fromTo(4,1,3,0,2,4), Direction.NORTH); // Build of Tanks
        scene.idle(30);
        scene.world().showSection(util.select().fromTo(4,1,2,3,1,0), Direction.EAST); // Left Side
        scene.world().showSection(util.select().fromTo(1,1,0,1,1,2), Direction.WEST); // Right Side
        scene.idle(20);

        // Fill tank
        fillTank(scene, source, 12000);
        scene.world().showSection(util.select().position(0,1,2), Direction.DOWN);
        scene.idle(20);
        scene.world().showSection(util.select().position(2,1,1), Direction.SOUTH); // actor check_valve
        scene.idle(30);

        // Highlight the valve
        scene.overlay().showOutline(PonderPalette.GREEN, new Object(), util.select().position(valve), 90);
        scene.overlay().showText(90)
                .text("Isn't it obvious? Its a 1-Way valve.")
                .colored(PonderPalette.GREEN)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(valve))
                .placeNearTarget();
        scene.idle(90);

        // Pump animation start
        scene.addKeyframe();
        scene.world().setKineticSpeed(drive, 32);
        scene.world().setKineticSpeed(pump, -32);
        scene.idle(50);

        // Infobox Fluid Blocked
        scene.overlay().showOutline(PonderPalette.RED, new Object(), util.select().position(before), 80);
        scene.overlay().showText(100)
                .text("Water gogo here")
                .colored(PonderPalette.RED)
                .pointAt(util.vector().topOf(before))
                .placeNearTarget();
        scene.idle(80);

        // Turn off ze pump
        scene.world().setKineticSpeed(drive, 0);
        scene.world().setKineticSpeed(pump, 0);
        scene.idle(20);

        // Rotate the pump baby
        scene.addKeyframe();
        scene.overlay().showOutline(PonderPalette.GREEN, new Object(), util.select().position(valve), 58);
        scene.overlay().showText(60)
                .text("Turn the valve around to reverse the flow direction")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(valve))
                .placeNearTarget();
        scene.idle(20);

        // Is it wrenchable? YesMenn!
        scene.overlay().showControls(util.vector().topOf(valve), Pointing.DOWN, 40)
                .rightClick()
                .withItem(AllItems.WRENCH.asStack());
        scene.idle(50);

        var valveLink = scene.world().makeSectionIndependent(util.select().position(valve));
        scene.world().configureCenterOfRotation(valveLink, util.vector().centerOf(valve));
        scene.world().rotateSection(valveLink, 0, 180, 0, 20);   // 180 slow turn
        scene.idle(25);

        // Pumps turning on
        scene.world().setKineticSpeed(drive, 32);
        scene.world().setKineticSpeed(pump, -32);
        scene.idle(30);

        // Call job tank filling
        TankTransfer job = new TankTransfer(scene, source, target, 12000, 80, 600);
        job.idle(60);

        // Last Scene
        scene.addKeyframe();
        scene.overlay().showText(80)
                .text("Placeholder: now the fluid flows through the valve")
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().topOf(target))
                .placeNearTarget();
        job.idle(90);
        scene.markAsFinished();

        // wait for liquidtransfer to finish
        job.finish();
        scene.idle(5);
        scene.world().setKineticSpeed(drive, 0);
        scene.world().setKineticSpeed(pump, 0);
        scene.idle(20);

    }

    // -------------------- Basement --------------------


    // ----- Tanks -----
    private static void fillTank(CreateSceneBuilder scene, BlockPos pos, int amount) {
        scene.world().modifyBlockEntity(pos, FluidTankBlockEntity.class,
                be -> be.getTankInventory().fill(new FluidStack(Fluids.WATER, amount),
                        IFluidHandler.FluidAction.EXECUTE));
    }

    private static void drainTank(CreateSceneBuilder scene, BlockPos pos, int amount) {
        scene.world().modifyBlockEntity(pos, FluidTankBlockEntity.class,
                be -> be.getTankInventory().drain(amount, IFluidHandler.FluidAction.EXECUTE));
    }

    // ----- Liquid Transferjob and Jobcalling -----
    private static class TankTransfer {
        private final CreateSceneBuilder scene;
        private final BlockPos from, to;
        private final int total, steps, duration;
        private int stepIndex = 0;   // steps already made
        private int elapsed = 0;     // ticks since start
        private int moved = 0;       // already moved buckets

        TankTransfer(CreateSceneBuilder scene, BlockPos from, BlockPos to,
                     int total, int steps, int duration) {
            this.scene = scene;
            this.from = from;
            this.to = to;
            this.total = total;
            this.steps = steps;
            this.duration = duration;
        }

        private boolean isDone() {
            return stepIndex >= steps;
        }

        private void doStep() {
            stepIndex++;
            int reached = (int) Math.round((double) total * stepIndex / steps);   // linear
            int amount = reached - moved;
            moved = reached;
            if (amount > 0) {
                drainTank(scene, from, amount);
                fillTank(scene, to, amount);
            }
        }

        // Jobticks in the background for filling the tanks
        void idle(int ticks) {
            while (ticks > 0) {
                if (isDone()) {
                    scene.idle(ticks);
                    return;
                }
                int due = (int) Math.round((stepIndex + 1) * (double) duration / steps);
                int wait = due - elapsed;
                if (wait <= 0) {
                    doStep();
                    continue;
                }
                int slice = Math.min(ticks, wait);
                scene.idle(slice);
                ticks -= slice;
                elapsed += slice;
            }
        }

        // idle until the job is done
        void finish() {
            idle(Math.max(0, duration - elapsed));
            while (!isDone()) {
                doStep();
            }
        }
    }
}