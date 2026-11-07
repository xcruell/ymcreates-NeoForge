package com.yesmenn.creates.block.entity;

import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.utility.CreateLang;
import com.yesmenn.creates.YesmennCreates;
import com.yesmenn.creates.block.EnvoKiller3000Block;
import com.yesmenn.creates.registry.YMBlockEntities;
import com.yesmenn.creates.registry.YMParticles;
import com.yesmenn.creates.registry.YMTags;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;
import java.util.Random;

import static com.yesmenn.creates.block.EnvoKiller3000Block.POWERED;

@EventBusSubscriber(
        modid = YesmennCreates.MODID)
public class EnvoKiller3000BlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    protected IFluidHandler fluidCapability;
    public FluidTank tankCapacity;
    public boolean isBurning=false;
    public int burnTimer=0;
    private boolean hasPipe;

    public EnvoKiller3000BlockEntity(BlockPos pos, BlockState state) {
        super(YMBlockEntities.ENVOKILLER3000.get(), pos, state);
        tankCapacity = createInventory();
        fluidCapability = tankCapacity;
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                YMBlockEntities.ENVOKILLER3000.get(),
                (be, context) -> be.fluidCapability
        );
    }
    public boolean isLit() {
        return getBlockState().getValue(EnvoKiller3000Block.LIT);
    }

    public boolean hasPipe() {
        return hasPipe;
    }


    public void updatePipeState() {
        if (level == null || level.isClientSide) {
            return;
        }

        boolean newHasPipe = level
                .getBlockState(worldPosition.below())
                .is(YMTags.SUPPORTED_PIPES);

        if (hasPipe == newHasPipe) {
            return;
        }

        hasPipe = newHasPipe;
        setChanged();
        sendData();

        level.sendBlockUpdated(
                worldPosition,
                getBlockState(),
                getBlockState(),
                Block.UPDATE_CLIENTS
        );
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return createFluidTooltip(this, tooltip);
    }

//    public void setLit(boolean lit) {
//        if (level != null) {
//            BlockState newState = getBlockState()
//                    .setValue(EnvoKiller3000Block.LIT, lit);
//
//            level.setBlock(
//                    worldPosition,
//                    newState,
//                    3
//            );
//
//        }
//    }


    public boolean createFluidTooltip(BlockEntity be, List<Component> tooltip) {
        IFluidHandler tank = Capabilities.FluidHandler.BLOCK.getCapability(
                be.getLevel(), be.getBlockPos(), be.getBlockState(), be, null
        );

        if (tank == null || tank.getTanks() == 0)
            return false;

        boolean powered = getBlockState().getValue(EnvoKiller3000Block.POWERED);
        int burnRate = powered ? 1500 : 500;

        FluidStack fluid = tank.getFluidInTank(0);
        LangBuilder mb = CreateLang.translate("generic.unit.millibuckets");

        CreateLang.translate("goggles.envokiller3000")
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip);

        if (isLit()) {
            CreateLang.builder()
                    .add(CreateLang.translate("goggles.envokiller3000.status")
                            .style(ChatFormatting.GRAY))
                    .add(CreateLang.translate("goggles.envokiller3000.burning")
                            .style(ChatFormatting.RED))
                    .forGoggles(tooltip);
        } else {
            CreateLang.builder()
                    .add(CreateLang.translate("goggles.envokiller3000.status")
                            .style(ChatFormatting.GRAY))
                    .add(CreateLang.translate("goggles.envokiller3000.idle")
                            .style(ChatFormatting.GREEN))
                    .forGoggles(tooltip);
        }

        CreateLang.text("")
                .forGoggles(tooltip);


        CreateLang.builder()
                .add(CreateLang.translate("goggles.envokiller3000.fluidPrefix"))
                .add(CreateLang.fluidName(fluid)
                        .style(ChatFormatting.GOLD))
                .forGoggles(tooltip);

        CreateLang.builder()
                .text(" ")
                .add(CreateLang.number(fluid.getAmount()).add(mb)
                        .style(ChatFormatting.DARK_GREEN))
                .text(ChatFormatting.GRAY, " / ")
                .add(CreateLang.number(tank.getTankCapacity(0)).add(mb)
                        .style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip);

        CreateLang.text("")
                .forGoggles(tooltip);

        CreateLang.builder()
                .add(CreateLang.translate("goggles.envokiller3000.burnrate")
                        .style(ChatFormatting.GRAY))
                .add(CreateLang.number(burnRate)
                        .style(ChatFormatting.AQUA))
                .add(CreateLang.text(" mB/s")
                        .style(ChatFormatting.GRAY))
                .forGoggles(tooltip);

        return true;
    }


    protected SmartFluidTank createInventory() {
        return new SmartFluidTank(2500, this::onFluidStackChanged) {
            @Override
            public boolean isFluidValid(FluidStack stack) {
                return stack.is(YMTags.ENVO_FLUIDS);
            }
        };
    }

    // Fluid changes happen every tick while burning. Syncing each change to
    // clients (sendData) and marking the chunk dirty (setChanged, which also
    // notifies neighbours) every tick caused heavy lag, so it is throttled.
    private static final int SYNC_INTERVAL = 10;
    private boolean syncPending = false;
    private int syncCooldown = 0;

    protected void onFluidStackChanged(FluidStack newFluidStack) {
        if (level != null && !level.isClientSide) {
            syncPending = true;
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null) {
            return;
        }

        BlockState state = getBlockState();

        // Client: only visuals. LIT is synced from the server via the block state.
        if (level.isClientSide) {
            if (state.getValue(EnvoKiller3000Block.LIT)) {
                makeParticles(level, worldPosition, state);
            }
            return;
        }

        // Server: burn logic
        if (tankCapacity.getFluidAmount() > 0) {
            burnTimer = 120;
            tankCapacity.drain(state.getValue(POWERED) ? 75 : 25,
                    IFluidHandler.FluidAction.EXECUTE);
        } else if (burnTimer > 0) {
            burnTimer--;
        }

        isBurning = burnTimer > 0;

        // Only touch the block state when it actually changes
        // (triggers light updates and chunk re-renders).
        if (state.getValue(EnvoKiller3000Block.LIT) != isBurning) {
            level.setBlock(worldPosition,
                    state.setValue(EnvoKiller3000Block.LIT, isBurning),
                    Block.UPDATE_CLIENTS);
        }

        if (syncCooldown > 0) {
            syncCooldown--;
        }
        if (syncPending && syncCooldown == 0) {
            syncPending = false;
            syncCooldown = SYNC_INTERVAL;
            setChanged();
            sendData();
        }
    }


    public static void makeParticles(Level level, BlockPos pos, BlockState state) {
        Random random = Create.RANDOM;
        int partY = pos.getY() + 2;
        float offs = random.nextFloat(0.015F);
        float smokeYspeed = 0.05F + random.nextFloat(0.05F);

        if (state.getValue(POWERED)) {
            int smokeTimer = random.nextInt(5);
            float speedXZ = random.nextFloat(0.03F);
//            float speedX = random.nextFloat(0.03F);
            float randomx = random.nextFloat(1);
            float randomz = random.nextFloat(1);
            float speedY = 0.1F;
//            float speedYemb = 0.2F;


            if(smokeTimer==1) {
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, pos.getX() + randomx, partY + 2.5, pos.getZ() + randomz, 0.0D, smokeYspeed, 0.0D);
            }
//            if (level.random.nextFloat() < 0.3F) {
//                level.addParticle(YMParticles.EMBER.get(), pos.getX() + randomx, partY, pos.getZ() + randomz, 0.0, speedY, 0.0);
//            }
//            if (level.random.nextFloat() < 0.75F) {
//                double x = pos.getX() + level.random.nextFloat();
//                double z = pos.getZ() + level.random.nextFloat();
//
//
//                int count = level.random.nextInt(2) + 1;
//
//                for (int i = 0; i < count; i++) {
//                }
//            }
            if(smokeTimer==2) {
                level.addParticle(YMParticles.EMBER.get(), pos.getX() + randomx, partY + 2, pos.getZ() + randomz, 0.0, speedY, 0.0);
                level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.25, partY, pos.getZ() + 0.5 + offs, speedXZ, speedY, 0);
                level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.75, partY, pos.getZ() + 0.5 + offs, -speedXZ, speedY, 0);
                level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + offs, partY, pos.getZ() + 0.75, 0.0F, speedY, -speedXZ);
                level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + offs, partY, pos.getZ() + 0.25, 0.0F, speedY, speedXZ);

            }

        } else {
            int smokeTimer = random.nextInt(5);
            float speedZ = random.nextFloat(0.03F);
            float speedX = random.nextFloat(0.03F);
            float speedY = 0.075F;

            if(smokeTimer==0) {
                level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, pos.getX()  +random.nextFloat(1), partY + 2.5, pos.getZ() + random.nextFloat(1), 0.0D, smokeYspeed, 0.0D);
            }
            if(smokeTimer==2) {
                level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.25, partY, pos.getZ() + 0.5 + offs, speedX, speedY, 0);
                level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.75, partY, pos.getZ() + 0.5 + offs, -speedX, speedY, 0);
                level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + offs, partY, pos.getZ() + 0.75, 0, speedY, -speedZ);
                level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + offs, partY, pos.getZ() + 0.25, 0, speedY, speedZ);
            }
        }

    }


    @Override
    protected void read(
            CompoundTag compound,
            HolderLookup.Provider registries,
            boolean clientPacket
    ) {
        super.read(compound, registries, clientPacket);

        tankCapacity.readFromNBT(
                registries,
                compound.getCompound("FluidContent")
        );

        hasPipe = compound.getBoolean("HasPipe");
    }

    @Override
    public void write(
            CompoundTag compound,
            HolderLookup.Provider registries,
            boolean clientPacket
    ) {
        super.write(compound, registries, clientPacket);

        compound.put(
                "FluidContent",
                tankCapacity.writeToNBT(registries, new CompoundTag())
        );

        compound.putBoolean("HasPipe", hasPipe);
    }


    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

}