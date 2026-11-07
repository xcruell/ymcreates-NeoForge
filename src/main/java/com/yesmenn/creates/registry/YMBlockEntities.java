package com.yesmenn.creates.registry;

import com.yesmenn.creates.YesmennCreates;
import com.yesmenn.creates.block.entity.CheckValveBlockEntity;
import com.yesmenn.creates.block.entity.EnvoKiller3000BlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class YMBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(
                    Registries.BLOCK_ENTITY_TYPE,
                    YesmennCreates.MODID
            );

    public static final DeferredHolder<
            BlockEntityType<?>,
            BlockEntityType<EnvoKiller3000BlockEntity>
            > ENVOKILLER3000 =
            BLOCK_ENTITIES.register(
                    "envokiller3000",
                    () -> BlockEntityType.Builder.of(
                            EnvoKiller3000BlockEntity::new,
                            YMBlocks.ENVOKILLER3000.get()
                    ).build(null)
            );

    public static final DeferredHolder<
            BlockEntityType<?>,
            BlockEntityType<CheckValveBlockEntity>
            > CHECK_VALVE =
            BLOCK_ENTITIES.register(
                    "check_valve",
                    () -> BlockEntityType.Builder.of(
                            CheckValveBlockEntity::new,
                            YMBlocks.CHECK_VALVE.get()
                    ).build(null)
            );

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}