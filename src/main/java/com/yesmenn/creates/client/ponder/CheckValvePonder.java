package com.yesmenn.creates.client.ponder;

import com.yesmenn.creates.registry.YMBlocks;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public class CheckValvePonder {

    // Wird vom Plugin aufgerufen
    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(YMBlocks.CHECK_VALVE.getId())
                .addStoryBoard("checkvalve/basics", CheckValvePonder::basics); // CAPPED LETTERS CRASHED THE GAME
    }

    // Die eigentliche Szene
    public static void basics(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("check_valve", "Using the Check Valve");
        scene.configureBasePlate(0, 0, 5);

        scene.world().showSection(util.select().layer(0), Direction.UP);          // Plattform
        scene.idle(50);
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);   // alles darauf
        scene.idle(20);

        BlockPos valve = util.grid().at(2, 1, 1);   // nur für den Textpfeil: echte Position eintragen

        scene.overlay().showText(120)
                .text("The Check Valve only lets fluids pass in one direction")
                .pointAt(util.vector().topOf(valve))
                .placeNearTarget();
        scene.idle(80);

        scene.markAsFinished();
    }
}


