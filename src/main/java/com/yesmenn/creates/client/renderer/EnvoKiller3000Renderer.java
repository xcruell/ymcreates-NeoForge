
package com.yesmenn.creates.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yesmenn.creates.YesmennCreates;
import com.yesmenn.creates.block.EnvoKiller3000Block;
import com.yesmenn.creates.block.entity.EnvoKiller3000BlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

public class EnvoKiller3000Renderer
        implements BlockEntityRenderer<EnvoKiller3000BlockEntity> {

    public static final ModelResourceLocation ENVO_FLAME =
            ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(
                            YesmennCreates.MODID,
                            "envokiller3000_flame"
                    )
            );

    public static final ModelResourceLocation ENVO_FLANSCH =
            ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(
                            YesmennCreates.MODID,
                            "block/envokiller3000_flansch"
                    )
            );

    public EnvoKiller3000Renderer(
            BlockEntityRendererProvider.Context context
    ) {
    }

    @Override
    public void render(
            EnvoKiller3000BlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        ModelBlockRenderer modelRenderer =
                minecraft.getBlockRenderer().getModelRenderer();

        if (blockEntity.getBlockState().getValue(EnvoKiller3000Block.LIT)) {
            BakedModel flameModel =
                    minecraft.getModelManager().getModel(ENVO_FLAME);

            VertexConsumer flameBuffer =
                    bufferSource.getBuffer(RenderType.cutoutMipped());

            poseStack.pushPose();

            poseStack.translate(0.0F, 2.0F, 0.0F);

            modelRenderer.renderModel(
                    poseStack.last(),
                    flameBuffer,
                    null,
                    flameModel,
                    1.0F,
                    1.0F,
                    1.0F,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY
            );

            poseStack.popPose();
        }

        if (blockEntity.hasPipe()) {
            BakedModel pipeLipModel =
                    minecraft.getModelManager().getModel(ENVO_FLANSCH);

            VertexConsumer pipeBuffer =
                    bufferSource.getBuffer(RenderType.cutoutMipped());

            poseStack.pushPose();

            if (blockEntity.getLevel() != null) {
                modelRenderer.tesselateBlock(
                        blockEntity.getLevel(),
                        pipeLipModel,
                        blockEntity.getBlockState(),
                        blockEntity.getBlockPos(),
                        poseStack,
                        pipeBuffer,
                        false,
                        blockEntity.getLevel().getRandom(),
                        0L,
                        OverlayTexture.NO_OVERLAY
                );
            }

            poseStack.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(
            EnvoKiller3000BlockEntity blockEntity
    ) {
        BlockPos pos = blockEntity.getBlockPos();

        return new AABB(
                pos.getX(),
                pos.getY() - 1.0,
                pos.getZ(),
                pos.getX() + 1.0,
                pos.getY() + 4.0,
                pos.getZ() + 1.0
        );
    }

    @Override
    public boolean shouldRenderOffScreen(
            EnvoKiller3000BlockEntity blockEntity
    ) {
        // The custom render bounding box already covers the flame above the
        // block, so frustum culling works correctly without forcing global rendering.
        return false;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
