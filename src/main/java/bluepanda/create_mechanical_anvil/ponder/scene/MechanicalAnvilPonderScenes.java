package bluepanda.create_mechanical_anvil.ponder.scene;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.phys.Vec3;

public class MechanicalAnvilPonderScenes {

    public static void intro(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("mechanical_anvil_combine", "Combining items with the Mechanical Anvil");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);

        BlockPos shaftPos = util.grid().at(2, 1, 3);
        scene.world().setBlock(shaftPos, AllBlocks.SHAFT.getDefaultState()
                .setValue(ShaftBlock.AXIS, Direction.Axis.Z), false);

        BlockPos anvilPos = util.grid().at(2,1,2);
        Selection anvilSelect = util.select().position(anvilPos);

        scene.idle(5);
        scene.world().showSection(util.select().fromTo(2, 1, 3, 2, 1, 5), Direction.DOWN);
        scene.idle(10);
        scene.effects().rotationDirectionIndicator(shaftPos);
        scene.world().showSection(anvilSelect, Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70).attachKeyFrame()
                .text("Mechanical Anvils can repair or combine items, and apply enchantments")
                .pointAt(util.vector().blockSurface(anvilPos, Direction.UP)).placeNearTarget();
        scene.idle(65);

        Holder<Enchantment> efficiency = Minecraft.getInstance().level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);

        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack mat = new ItemStack(Items.DIAMOND);
        ItemStack result = tool.copy();
        result.enchant(efficiency, 4);

        Vec3 toolSpawnPos = util.vector().centerOf(anvilPos.above().west());
        ElementLink<EntityElement> toolEntity = scene.world().createItemEntity(toolSpawnPos, util.vector().of(0,0,0), tool);
        scene.idle(12);

        scene.overlay().showControls(toolSpawnPos, Pointing.DOWN, 20).withItem(tool);
        scene.idle(10);

        scene.overlay().showText(60)
                .attachKeyFrame()
                .text("Dropping in items first fills the item to process")
                .pointAt(util.vector().blockSurface(anvilPos, Direction.WEST))
                .placeNearTarget();
        scene.idle(55);

        Vec3 matSpawnPos = util.vector().centerOf(anvilPos.above().east());
        ElementLink<EntityElement> matEntity = scene.world().createItemEntity(matSpawnPos, util.vector().of(0,0,0), mat);
        scene.idle(12);

        scene.overlay().showControls(matSpawnPos, Pointing.DOWN, 20).withItem(mat);
        scene.idle(10);

        scene.overlay().showText(40)
                .attachKeyFrame()
                .text("Then fills the material to apply")
                .pointAt(util.vector().blockSurface(anvilPos, Direction.EAST))
                .placeNearTarget();
        scene.idle(45);

        scene.world().modifyEntity(toolEntity, e -> e.setDeltaMovement(util.vector().of(0.05,0.2,0)));
        scene.world().modifyEntity(matEntity, e -> e.setDeltaMovement(util.vector().of(-0.05,0.2,0)));
        scene.idle(12);

        scene.world().modifyEntity(toolEntity, Entity::discard);
        scene.world().createItemOnBeltLike(anvilPos, Direction.WEST, tool);

        scene.world().modifyEntity(matEntity, Entity::discard);
        scene.world().createItemOnBeltLike(anvilPos, Direction.EAST, mat);
        scene.idle(80);

        toolEntity = scene.world().createItemEntity(util.vector().topOf(anvilPos)
                .add(0, -.1, -0.5), util.vector().of(0,0.18,-0.05), result);
        scene.idle(10);
        scene.overlay().showControls(toolSpawnPos.add(2, 0, 0), Pointing.DOWN, 20).withItem(result);
        scene.idle(30);
        scene.idle(70);
    }
}
