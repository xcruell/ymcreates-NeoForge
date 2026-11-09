package com.yesmenn.creates.client.ponder;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class YMPonderAnim {

    // Default outro column height: Y 0 to 5
    private static final int OUTRO_HEIGHT = 6;

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

    // ----- Basecall YesMenn Outro -----
    // Same spiral order, but each step is a whole column from Y 0 to height-1
    private static List<Selection> spiralOut(SceneBuildingUtil util, int size, int height) {
        List<Selection> out = new ArrayList<>();
        for (BlockPos p : spiral(size))
            out.add(util.select().fromTo(p.getX(), 0, p.getZ(), p.getX(), height - 1, p.getZ()));
        return out;
    }

    public static void introSpiral(CreateSceneBuilder scene, SceneBuildingUtil util, int size, boolean fromCenter) {
        List<BlockPos> order = spiral(size);
        if (fromCenter) Collections.reverse(order);

        for (BlockPos p : order) {
            scene.world().showSection(util.select().position(p), Direction.UP);
            scene.idle(1);
        }
        scene.idle(15);
    }

    public static void introSpiralRandom(CreateSceneBuilder scene, SceneBuildingUtil util, int size) {
        int randomInt = Create.RANDOM.nextInt(2);

        switch (randomInt) {
            case 0 -> introSpiral(scene, util, size, false);
            case 1 -> introSpiral(scene, util, size, true);
            default -> { }
        }

    }

    // Outro with whole columns, default height (Y 0 to 5)
    public static void outroSpiralFull(CreateSceneBuilder scene, SceneBuildingUtil util, int size, boolean fromCenter) {
        outroSpiralFull(scene, util, size, OUTRO_HEIGHT, fromCenter);
    }

    // Outro with whole columns from Y 0 to height-1
    public static void outroSpiralFull(CreateSceneBuilder scene, SceneBuildingUtil util, int size, int height, boolean fromCenter) {
        List<Selection> order = spiralOut(util, size, height);
        if (!fromCenter) Collections.reverse(order);

        for (Selection column : order) {
            scene.world().hideSection(column, Direction.DOWN);
            scene.idle(1);
        }
        scene.idle(15);
    }

    public static void outroSpiral(CreateSceneBuilder scene, SceneBuildingUtil util, int size, boolean fromCenter) {
        List<BlockPos> order = spiral(size);
        if (!fromCenter) Collections.reverse(order);

        for (BlockPos p : order) {
            scene.world().hideSection(util.select().position(p), Direction.DOWN);
            scene.idle(1);
        }
        scene.idle(15);
    }

    public static void outroSpiralRandom(CreateSceneBuilder scene, SceneBuildingUtil util, int size) {
        int randomInt = Create.RANDOM.nextInt(2);

        switch (randomInt) {
            case 0 -> outroSpiral(scene, util, size, false);
            case 1 -> outroSpiral(scene, util, size, true);
            default -> { }
        }

    }


}
