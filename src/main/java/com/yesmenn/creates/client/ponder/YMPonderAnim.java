package com.yesmenn.creates.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YMPonderAnim {

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

    public static void introSpiral(CreateSceneBuilder scene, SceneBuildingUtil util, int size, boolean fromCenter) {
        List<BlockPos> order = spiral(size);
        if (fromCenter) Collections.reverse(order);

        for (BlockPos p : order) {
            scene.world().showSection(util.select().position(p), Direction.UP);
            scene.idle(1);
        }
        scene.idle(15);
    }
}
