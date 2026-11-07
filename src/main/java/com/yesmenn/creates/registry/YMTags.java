package com.yesmenn.creates.registry;

import com.yesmenn.creates.YesmennCreates;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
public class YMTags {
    public static final TagKey<Fluid> ENVO_FLUIDS =
            TagKey.create(
                    Registries.FLUID,
                    ResourceLocation.fromNamespaceAndPath(
                            YesmennCreates.MODID,
                            "envo_fluids"
                    )
            );

    public static final TagKey<Block> SUPPORTED_PIPES =
            BlockTags.create(
                    ResourceLocation.fromNamespaceAndPath(
                            YesmennCreates.MODID,
                            "supported_pipes"
                    )
            );

}
