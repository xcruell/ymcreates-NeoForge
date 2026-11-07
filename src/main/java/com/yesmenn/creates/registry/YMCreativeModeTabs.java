
package com.yesmenn.creates.registry;

import com.yesmenn.creates.YesmennCreates;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YMCreativeModeTabs {

    public static final ResourceKey<CreativeModeTab> YM_DECOR_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath(YesmennCreates.MODID, "yes_menn")
    );

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, YesmennCreates.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> YM_DECOR =
            CREATIVE_MODE_TABS.register("yes_menn", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ymdecoradditions"))
                    .icon(() -> YMItems.ENVOKILLER3000.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(YMBlocks.ENVOKILLER3000.get());
                        output.accept(YMBlocks.CHECK_VALVE.get());
                    })
                    .build());

    private YMCreativeModeTabs() {
    }

}
