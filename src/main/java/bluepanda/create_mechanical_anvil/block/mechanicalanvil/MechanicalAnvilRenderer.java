package bluepanda.create_mechanical_anvil.block.mechanicalanvil;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;

public class MechanicalAnvilRenderer extends SafeBlockEntityRenderer<MechanicalAnvilBlockEntity> {

    public MechanicalAnvilRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(MechanicalAnvilBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        renderItems(be, partialTicks, ms, buffer, light, overlay);

        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;

        renderShaft(be, ms, buffer, light, overlay);
    }

    protected void renderShaft(MechanicalAnvilBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light,
                               int overlay) {
        KineticBlockEntityRenderer.renderRotatingBuffer(be, getRotatedModel(be), ms,
                buffer.getBuffer(RenderType.solid()), light);
    }

    protected void renderItems(MechanicalAnvilBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.inventory.isEmpty())
            return;

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        float progress = 0f;
        float processingSpeed = Mth.clamp(Math.abs(be.getSpeed()) / 32f, 1f, 128f);
        // while block has rotation and the recipe duration has started
        if (be.inventory.recipeDuration > 0 && be.getSpeed() != 0) {
            // progress = 0 to 1 of how long till next tick * the speed / countdowns og number,
            // set to a 0 to 1
            progress = 1f - Mth.clamp((be.inventory.remainingTime - partialTicks * processingSpeed) / be.inventory.recipeDuration, 0f, 1f);
        }

        float fullCount = be.inventory.recipeDuration;
        float scale = .5f;
        float rotX = 0f;
        float rotY = 0f;
        float offsetY = 0;
        float offsetX = .5f;
        float offsetZ = .5f;

        // every tick countdown is remaining time[20-0] changed by speed[32(min speed)] * partial[0.5] = [16] - [20(just started)] = -4 clamped to 0
        float countdown = Mth.clamp(be.inventory.remainingTime - (partialTicks * processingSpeed), 0f, fullCount);
        // recipe applies if (inventory.remainingTime <= 0 && !inventory.appliedRecipe) and not if (level.isClientSide && !isVirtual())
        if (!be.inventory.appliedRecipe) {// while the items haven't combined
            offsetZ = (float) (Math.pow(Mth.clamp(1f - (Mth.clamp(countdown - (partialTicks * processingSpeed), 0f, fullCount) / fullCount), 0f, 1f), 3) * .5);
            if (progress > .5f) {
                scale *= (float) (1f - Math.pow((progress - .6f) / .4f, 3f));
            }
        } else {// after the items have combined

            if (progress < .5f) {
                scale *= (progress / .5f);
                rotX = 0f;
                rotY = (progress / .5f) * 270f;
                offsetZ = .5f;
            } else {
                float animationMath = Mth.clamp((progress - .8f) / .2f, 0f, 1f);

                scale = .5f;
                rotX = Mth.clamp((animationMath * 112f), 0, 90);
                rotY = 270;
                offsetY = itemRenderer.getModel(be.inventory.getStackInSlot(0), be.getLevel(), null, 0)
                        .isGui3d() ? 0 : Mth.clamp((animationMath * -.18f), -.15f, 0);
                offsetX = .5f - (animationMath * .5f);
            }
        }

        ms.pushPose();
        switch (be.getBlockState().getValue(MechanicalAnvilBlock.HORIZONTAL_FACING)) {
            case SOUTH -> {
                ms.mulPose(Axis.YP.rotationDegrees(90));
                ms.translate(-1, 0, 0);
            }
            case NORTH -> {
                ms.mulPose(Axis.YP.rotationDegrees(270));
                ms.translate(0, 0, -1);
            }
            case EAST -> {
                ms.mulPose(Axis.YP.rotationDegrees(180));
                ms.translate(-1, 0, -1);
            }
            default -> ms.mulPose(Axis.YP.rotationDegrees(0));

        }

        for (int i = 0; i < be.inventory.getSlots(); i++) {
            ItemStack stack = be.inventory.getStackInSlot(i);
            if (stack.isEmpty())
                continue;

            BakedModel outputModel = itemRenderer.getModel(stack, be.getLevel(), null, 0);
            boolean outputIs3D = outputModel.isGui3d();

            ms.pushPose();

            if (!be.inventory.appliedRecipe) {
                if (i == 0) offsetZ = (float) ((Math.pow(progress, 3)) * .5f);
                else if (i == 1) offsetZ = (float) (1f - (Math.pow(progress, 3) * .5f));
            }

            ms.translate(offsetX, offsetY, offsetZ);

            float scaled = (outputIs3D ? 1f : .75f) * scale;
            ms.translate(0, (outputIs3D ? 15f : 16f) / 16f + (((scale / .5f) - 1f) * (outputIs3D ? 0.125f : 0.1875f)), 0);

            ms.scale(scaled, scaled, scaled);
            ms.mulPose(Axis.YP.rotationDegrees(rotY));
            ms.mulPose(Axis.XP.rotationDegrees(rotX));

            itemRenderer.render(stack, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, outputModel);
            ms.popPose();
        }

        ms.popPose();
    }

    protected SuperByteBuffer getRotatedModel(KineticBlockEntity be) {
        BlockState state = be.getBlockState();
        if (state.getValue(FACING)
                .getAxis()
                .isHorizontal())
            return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF,
                    state.rotate(be.getLevel(), be.getBlockPos(), Rotation.CLOCKWISE_180));
        return CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, getRenderedBlockState(be));
    }

    protected BlockState getRenderedBlockState(KineticBlockEntity be) {
        return KineticBlockEntityRenderer.shaft(KineticBlockEntityRenderer.getRotationAxisOf(be));
    }

}