package com.yesmenn.creates.registry;

import com.yesmenn.creates.YesmennCreates;
import com.yesmenn.creates.block.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class YMBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(YesmennCreates.MODID);

    public static final DeferredBlock<EnvoKiller3000Block> ENVOKILLER3000 =
            BLOCKS.registerBlock(
                    "envokiller3000",
                    EnvoKiller3000Block::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .sound(SoundType.COPPER)
                            .strength(2.0F)
                            .noOcclusion()
            );

    public static final DeferredBlock<CheckValveBlock> CHECK_VALVE =
            BLOCKS.registerBlock(
                    "check_valve",
                    CheckValveBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .sound(SoundType.COPPER)
                            .strength(3.0f, 8.0f)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
            );


}