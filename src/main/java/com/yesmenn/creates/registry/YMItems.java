package com.yesmenn.creates.registry;

import com.yesmenn.creates.YesmennCreates;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YMItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(YesmennCreates.MODID);

    public static final DeferredItem<BlockItem> ENVOKILLER3000 =
            ITEMS.register(
                    "envokiller3000",
                    () -> new BlockItem(
                            YMBlocks.ENVOKILLER3000.get(),
                            new Item.Properties()
                    )
            );

    public static final DeferredItem<BlockItem> CHECK_VALVE =
            ITEMS.register(
                    "check_valve",
                    () -> new BlockItem(
                            YMBlocks.CHECK_VALVE.get(),
                            new Item.Properties()
                    )
            );

    private YMItems() {
    }
}