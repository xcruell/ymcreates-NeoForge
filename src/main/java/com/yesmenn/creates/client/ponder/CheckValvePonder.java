package com.yesmenn.creates.client.ponder;

import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.yesmenn.creates.registry.YMBlocks;
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

public class CheckValvePonder {

    // Plugin Call
    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(YMBlocks.CHECK_VALVE.getId())
                .addStoryBoard("checkvalve/basics", CheckValvePonder::basics); // CAPPED LETTERS CRASHED THE GAME
    }

    // Szene start
    public static void basics(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("check_valve", "Using the Check Valve");

        scene.configureBasePlate(0, 0, 5); // Base for Ponder
        scene.world().showSection(util.select().layer(0), Direction.UP); // Baseplate
        scene.idle(30);
        scene.world().showSection(util.select().fromTo(4,1,3,0,2,4), Direction.SOUTH); // Build of Tanks
        scene.idle(30);
        scene.world().showSection(util.select().fromTo(4,1,2,3,1,2), Direction.EAST);
        scene.idle(20);
        scene.world().showSection(util.select().fromTo(1,1,0,1,1,2), Direction.WEST);
        scene.idle(20);
        scene.world().showSection(util.select().position(0,1,2), Direction.DOWN);
        scene.idle(30);
        scene.world().showSection(util.select().position(2,1,1), Direction.UP);
        scene.idle(20);

        // Positionen
        BlockPos valve  = util.grid().at(2, 1, 1);
        BlockPos before = util.grid().at(3, 1, 1);   // Infocube Eingang
        BlockPos after  = util.grid().at(1, 1, 1);   // Infocube Ausgang
        BlockPos pump   = util.grid().at(1, 1, 2);
        BlockPos source = util.grid().at(1, 1, 3);   // Tank, aus dem es fließt
        BlockPos target = util.grid().at(3, 1, 3);   // Tank, in den es fließt
        var drive = util.select().fromTo(0, 1, 2, 1, 1, 2);   // Zahnrad + Pumpe

        // Start: Quelltank ist gefüllt (VOR der Pumpe)
        fillTank(scene, source, 12000);
        scene.idle(10);

        // Ventil hervorheben
        scene.overlay().showOutline(PonderPalette.GREEN, new Object(), util.select().position(valve), 90);
        scene.overlay().showText(90)
                .text("The Check Valve only lets fluids pass in one direction")
                .colored(PonderPalette.GREEN)
                .attachKeyFrame()
                .pointAt(util.vector().topOf(valve))
                .placeNearTarget();
        scene.idle(90);
// UNDO BIS HIER!
        // Pumpe an, Wasser wird blockiert
        scene.addKeyframe();
        scene.world().setKineticSpeed(drive, 32);
        scene.idle(50);

        // Eingang
        scene.overlay().showOutline(PonderPalette.INPUT, new Object(), util.select().position(before), 70);
        scene.overlay().showText(70)
                .text("Fluids enter from the back")
                .colored(PonderPalette.INPUT)
                .pointAt(util.vector().topOf(before))
                .placeNearTarget();
        scene.idle(80);

        // Ausgang
        scene.overlay().showOutline(PonderPalette.OUTPUT, new Object(), util.select().position(after), 70);
        scene.overlay().showText(70)
                .text("and leave through the front")
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().topOf(after))
                .placeNearTarget();
        scene.idle(80);

        // Pumpe stoppen
        scene.world().setKineticSpeed(drive, 0);
        scene.idle(20);

// Ventil um 180° drehen
        scene.addKeyframe();
        scene.overlay().showOutline(PonderPalette.GREEN, new Object(), util.select().position(valve), 60);
        scene.overlay().showText(60)
                .text("Turn the valve around to reverse the flow direction")
                .colored(PonderPalette.GREEN)
                .pointAt(util.vector().topOf(valve))
                .placeNearTarget();
        scene.idle(30);
        scene.world().modifyBlock(valve,
                state -> state.setValue(BlockStateProperties.FACING,
                        state.getValue(BlockStateProperties.FACING).getOpposite()),
                true);
        scene.idle(40);

        // Pumpe in Gegenrichtung: Wasser fließt zurück
        scene.world().setKineticSpeed(drive, 32);
        scene.idle(30);

        // Umfüllen läuft im Hintergrund (600 Ticks), die Szene geht schon nach 60 Ticks weiter
        TankTransfer job = new TankTransfer(scene, source, target, 12000, 80, 600, 3);
        job.idle(60);

        // Platzhalter-Text, während der Tank weiter befüllt wird
        scene.addKeyframe();
        scene.overlay().showText(80)
                .text("Placeholder: now the fluid flows through the valve")
                .colored(PonderPalette.OUTPUT)
                .pointAt(util.vector().topOf(valve))
                .placeNearTarget();
        job.idle(90);
        scene.markAsFinished();
        // Warten, bis der Tank fertig befüllt ist, dann Pumpe stoppen
        job.finish();
        scene.idle(20);
        scene.world().setKineticSpeed(drive, 0);
        scene.idle(20);

        // scene.markAsFinished();
    }

    private static void fillTank(CreateSceneBuilder scene, BlockPos pos, int amount) {
        scene.world().modifyBlockEntity(pos, FluidTankBlockEntity.class,
                be -> be.getTankInventory().fill(new FluidStack(Fluids.WATER, amount),
                        IFluidHandler.FluidAction.EXECUTE));
    }

    private static void drainTank(CreateSceneBuilder scene, BlockPos pos, int amount) {
        scene.world().modifyBlockEntity(pos, FluidTankBlockEntity.class,
                be -> be.getTankInventory().drain(amount, IFluidHandler.FluidAction.EXECUTE));
    }

    // Füllt von "from" nach "to" um. Am Anfang schnell, am Ende langsam (Ease-out).
    // Läuft parallel zur Szene: job.idle(...) ersetzt scene.idle(...)
    private static class TankTransfer {
        private final CreateSceneBuilder scene;
        private final BlockPos from, to;
        private final int total, steps, duration;
        private final double power;
        private int stepIndex = 0;   // wie viele Schritte schon gelaufen sind
        private int elapsed = 0;     // Ticks seit dem Start
        private int moved = 0;       // bisher umgefüllte Menge

        TankTransfer(CreateSceneBuilder scene, BlockPos from, BlockPos to,
                     int total, int steps, int duration, double power) {
            this.scene = scene;
            this.from = from;
            this.to = to;
            this.total = total;
            this.steps = steps;
            this.duration = duration;
            this.power = power;
        }

        private boolean isDone() {
            return stepIndex >= steps;
        }

        private void doStep() {
            stepIndex++;
            double t = (double) stepIndex / steps;
            int reached = (int) Math.round(total * (1 - Math.pow(1 - t, power)));
            int amount = reached - moved;
            moved = reached;
            if (amount > 0) {
                drainTank(scene, from, amount);
                fillTank(scene, to, amount);
            }
        }

        // Ersatz für scene.idle(...): wartet und füllt dabei im Hintergrund weiter um
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

        // Wartet, bis die komplette Umfüllung durch ist
        void finish() {
            idle(Math.max(0, duration - elapsed));
            while (!isDone()) {
                doStep();
            }
        }
    }
}
