package com.yesmenn.creates.client.ponder;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.yesmenn.creates.block.EnvoKiller3000Block;
import com.yesmenn.creates.registry.YMBlocks;
import com.yesmenn.creates.registry.YMParticles;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EnvokillerPonder {

    // Plugin Call
    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(YMBlocks.ENVOKILLER3000.getId())
                .addStoryBoard("envokiller/envokiller", EnvokillerPonder::basics);
    }

    // Scene start
    public static void basics(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("envokiller", "ENVOKILLER3000");
        scene.removeShadow();
        scene.configureBasePlate(0, 0, 5); // Base for Ponder

        // CONFIG FOR ENVOKILLER incase for new burnrates
        final int BURN_RATE = 500; // without redstone
        final int POWERED_RATE = 1500; // with redstone
        final int LIT_WAIT = 200; // time
        final int POWERED_WAIT = 120;
        final int FUEL = 16000;

        // Actor positions
        BlockPos envo  = util.grid().at(2, 2, 1); // The Envokiller ofc
        BlockPos motor = util.grid().at(1, 1, 3); // Motor for Fade in
        BlockPos fwheel = util.grid().at(1, 1, 2); // Cogwheel for fade in
        BlockPos fpump = util.grid().at(2, 1, 2); // Pump for fade in
        BlockPos source = util.grid().at(2, 1, 3);   // Tank1
        var wheel = util.select().fromTo(1,1,2,1,1,3);   // Cogwheel and motor
        var pump =  util.select().position(2, 1, 2); // Pump
        var shaft =  util.select().position(0, 1, 4); // shaft
        var tank = util.select().fromTo(2,1,3,2,2,3);   // Tank

        // YesMenn! Intro
        introSpiral(scene, util, 5, true);

        scene.world().showSection(util.select().position(2,1,1), Direction.SOUTH); // Pipe
        scene.world().showSection(tank, Direction.NORTH);
        scene.idle(5);
        scene.world().showSection(util.select().position(fpump), Direction.WEST);
        scene.idle(5);
        scene.world().showSection(util.select().position(fwheel), Direction.SOUTH);
        scene.world().showSection(util.select().position(motor), Direction.NORTH);
        scene.idle(15);
        scene.world().showSection(util.select().position(envo), Direction.DOWN);
        scene.idle(20);

        //Scene 1
        scene.addKeyframe();
        scene.overlay().showText(80)
                .text("Envokiller is nice yes.")
                .colored(PonderPalette.WHITE)
                .pointAt(util.vector().centerOf(envo))
                .placeNearTarget();
        scene.idle(30);
        fillTank(scene, source, FUEL);
        scene.idle(60);
        scene.world().setKineticSpeed(wheel, 32);
        scene.world().setKineticSpeed(pump, -32);
        scene.world().setKineticSpeed(shaft, -32);

        scene.overlay().showText(80)
                .text("Envokiller needs some hot stuff!")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(envo))
                .placeNearTarget();
        scene.idle(30);

        // lit (fam) and start the burning job
        scene.world().modifyBlock(envo,
                state -> state.setValue(EnvoKiller3000Block.LIT, true),
                false);
        FuelBurn burn = new FuelBurn(scene, source, FUEL, BURN_RATE);
        scene.overlay().showText(240)
                .text("Burnrate without redstone")
                .colored(PonderPalette.WHITE)
                .pointAt(util.vector().centerOf(new BlockPos(2,2,3)))
                .placeNearTarget();
        burn.idle(120);

//      ------------ Redstone scene ------------
        scene.addKeyframe();

        scene.overlay().showOutlineWithText(util.select().position(envo), 80)
                .text("Envokiller with redstone is lit, fam!")
                .colored(PonderPalette.RED)
                .placeNearTarget();

        var ember = scene.effects().simpleParticleEmitter(
                YMParticles.EMBER.get(),      // register of ze envokiller awesome particles
                new Vec3(0.07, 0.05, 0));             // particle movement
        scene.effects().emitParticles(
                util.vector().topOf(envo).add(0, 1.0, 0),   // particle spawnpoint
                ember,
                0.1F,      // Particles per Tick
                80);    // how many ticks

        burn.idle(LIT_WAIT - 60);

        scene.addLazyKeyframe();
        // POWERED - Burnrate
        scene.world().modifyBlock(envo,
                state -> state
                        .setValue(EnvoKiller3000Block.POWERED, true)
                        .setValue(EnvoKiller3000Block.LIT, true),
                false);
        burn.setRate(POWERED_RATE);
        scene.overlay().showText(80)
                .text("Brunrate with Redstone")
                .colored(PonderPalette.WHITE)
                .pointAt(util.vector().centerOf(new BlockPos(2,2,3)))
                .placeNearTarget();

        //get envokillers particles
        scene.effects().emitParticles(
                util.vector().topOf(envo).add(0, 0.07, 0),   // particle spawnpoint
                ember,
                1,      // Particles per Tick
                POWERED_WAIT);    // how many ticks
        burn.idle(POWERED_WAIT);
        scene.idle(30);

        scene.markAsFinished();

        // ------------ Outro scene ------------
        burn.finish();   // wait for tank to be empty
        scene.idle(50);
        scene.world().modifyBlock(envo, // turn the burner off
                state -> state
                        .setValue(EnvoKiller3000Block.LIT, false)
                        .setValue(EnvoKiller3000Block.POWERED, false),
                false);
        scene.idle(30);
        scene.world().setKineticSpeed(wheel, 0);
        scene.world().setKineticSpeed(pump, 0);
        scene.world().setKineticSpeed(shaft, 0);
    }

    // -------------------- Basement --------------------

    // ----- Basecall YesMenn Intro -----
    private static List<BlockPos> spiral(int size) {
        List<BlockPos> out = new ArrayList<>();
        int minX = 0, maxX = size - 1, minZ = 0, maxZ = size - 1;
        while (minX <= maxX && minZ <= maxZ) {
            for (int x = minX; x <= maxX; x++) out.add(new BlockPos(x, 0, minZ));
            for (int z = minZ + 1; z <= maxZ; z++) out.add(new BlockPos(maxX, 0, z));
            if (minZ < maxZ)
                for (int x = maxX - 1; x >= minX; x--) out.add(new BlockPos(x, 0, maxZ));
            if (minX < maxX)
                for (int z = maxZ - 1; z > minZ; z--) out.add(new BlockPos(minX, 0, z));
            minX++; maxX--; minZ++; maxZ--;
        }
        return out;
    }

    private static void introSpiral(CreateSceneBuilder scene, SceneBuildingUtil util, int size, boolean fromCenter) {
        List<BlockPos> order = spiral(size);
        if (fromCenter) Collections.reverse(order);

        for (BlockPos p : order) {
            scene.world().showSection(util.select().position(p), Direction.UP);
            scene.idle(1);
        }
        scene.idle(15);
    }

    // ----- Basecall Tank filling -----
    private static void fillTank(CreateSceneBuilder scene, BlockPos pos, int amount) {
        scene.world().modifyBlockEntity(pos, FluidTankBlockEntity.class,
                be -> be.getTankInventory().fill(new FluidStack(Fluids.LAVA, amount),
                        IFluidHandler.FluidAction.EXECUTE));
    }

    private static void drainTank(CreateSceneBuilder scene, BlockPos pos, int amount) {
        scene.world().modifyBlockEntity(pos, FluidTankBlockEntity.class,
                be -> be.getTankInventory().drain(amount, IFluidHandler.FluidAction.EXECUTE));
    }

    // ----- Fuelburner -----
    private static class FuelBurn {
        private static final int STEP = 2;     // skip all 2 ticks
        private final CreateSceneBuilder scene;
        private final BlockPos pos;
        private int remaining;
        private double perTick;                // mB per Tick
        private double carry = 0;              // dont change

        FuelBurn(CreateSceneBuilder scene, BlockPos pos, int content, int mbPerSecond) {
            this.scene = scene;
            this.pos = pos;
            this.remaining = content;
            this.perTick = mbPerSecond / 20.0;
        }

        // increase the fuel rate
        void setRate(int mbPerSecond) {
            this.perTick = mbPerSecond / 20.0;
        }

        // REPLACE for SCENE to do a job
        void idle(int ticks) {
            while (ticks > 0 && remaining > 0) {
                int slice = Math.min(ticks, STEP);
                scene.idle(slice);
                ticks -= slice;
                carry += perTick * slice;
                int amount = Math.min((int) carry, remaining);
                if (amount > 0) {
                    drainTank(scene, pos, amount);
                    remaining -= amount;
                    carry -= amount;
                }
            }
            if (ticks > 0) scene.idle(ticks);   // Tank is empty, wait for ticks
        }

        // wait until the tank is empty
        void finish() {
            if (perTick <= 0) return;
            while (remaining > 0) {
                idle(STEP);
            }
        }
    }
}