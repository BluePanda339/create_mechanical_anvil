package bluepanda.create_mechanical_anvil.block.mechanicalanvil;

import bluepanda.create_mechanical_anvil.CEIMABlockEntities;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.List;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MechanicalAnvilBlockEntity extends KineticBlockEntity implements Clearable, IHaveGoggleInformation {

    public ProcessingInventory inventory;
    private int recipeIndex;
    private ItemStack playEvent = ItemStack.EMPTY;

    protected SmartFluidTankBehaviour tank;

    public MechanicalAnvilBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inventory = new ProcessingInventory(this::start) {
            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                ItemStack insertItem = super.insertItem(slot, stack, simulate);
                if ((slot == 0 || slot == 1) && !(insertItem.getCount() == stack.getCount() && ItemStack.isSameItem(insertItem, stack)))
                    callback.accept(getStackInSlot(slot));
                return insertItem;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return (slot == 0 || slot == 1) && getStackInSlot(slot).isEmpty();
            }
        }.withSlotLimit(true);
        inventory.remainingTime = -1;
        recipeIndex = 0;
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CEIMABlockEntities.MECHANICAL_ANVIL.get(),
                (be, context) -> be.inventory
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                CEIMABlockEntities.MECHANICAL_ANVIL.get(),
                (be, context) -> {
                    if (context == null || context == Direction.DOWN)
                        return be.tank.getPrimaryHandler();
                    return null;
                }
        );
    }

    @Override
    protected AABB createRenderBoundingBox() { return new AABB(getBlockPos()).inflate(.125f); }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        tank = SmartFluidTankBehaviour.single(this, 1000);
        behaviours.add(tank);

        behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnels()
                .setInsertionHandler(this::tryInsertingFromSide));
    }

    private ItemStack tryInsertingFromSide(TransportedItemStack transportedStack, Direction side, boolean simulate) {
        Direction facing = getBlockState().getValue(MechanicalAnvilBlock.HORIZONTAL_FACING);
        int slot;

        if (side == facing.getCounterClockWise())
            slot = 0;
        else if (side == facing.getClockWise())
            slot = 1;
        else
            return transportedStack.stack;

        if (!inventory.getStackInSlot(slot).isEmpty())
            return transportedStack.stack;

        ItemStack inserted = transportedStack.stack;
        ItemStack remainder = inventory.insertItem(slot, inserted, simulate);
        if (!simulate && remainder.getCount() != inserted.getCount()) {
            notifyUpdate();
            setChanged();
        }
        return remainder;
    }

    public @Nullable IFluidHandler getFluidHandler(@Nullable Direction side) {
        if (side == Direction.DOWN || side == null)
            return tank.getPrimaryHandler();
        return null;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();
        if (getSpeed() == 0)
            return;

        if (!playEvent.isEmpty()) {
            spawnEventParticles(playEvent);
            playEvent = ItemStack.EMPTY;
            AllSoundEvents.SAW_ACTIVATE_STONE.playAt(level, worldPosition, 3, 1, true);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (getSpeed() == 0) return;

        if (inventory.remainingTime == -1) {
            if (inventory.getStackInSlot(0).isEmpty() || inventory.getStackInSlot(1).isEmpty() || inventory.appliedRecipe)
                return;
            start(inventory.getStackInSlot(0));
            return;
        }

        float processingSpeed = Mth.clamp(Math.abs(getSpeed()) / 32f, 1f, 128f);
        inventory.remainingTime -= processingSpeed;

        if (inventory.remainingTime <= 0 && !inventory.appliedRecipe) {
            if (level.isClientSide && !isVirtual()) return;

            playEvent = inventory.getStackInSlot(0);
            combine();
            inventory.appliedRecipe = true;
            inventory.recipeDuration = 20;
            inventory.remainingTime = inventory.recipeDuration;
            sendData();
            return;
        }

        if (inventory.remainingTime > 0)
            return;
        inventory.remainingTime = 0;

        Direction facing = getBlockState().getValue(MechanicalAnvilBlock.HORIZONTAL_FACING);
        // cardinal Dir to Vec
        Vec3 moveItemTo = new Vec3(facing.getStepX(), 0, facing.getStepZ());

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            ItemStack tryExportingToBeltFunnel = getBehaviour(DirectBeltInputBehaviour.TYPE)
                    .tryExportingToBeltFunnel(stack, facing, false);
            if (tryExportingToBeltFunnel != null) {
                if (tryExportingToBeltFunnel.getCount() != stack.getCount()) {
                    inventory.setStackInSlot(slot, tryExportingToBeltFunnel);
                    notifyUpdate();
                    return;
                }
                if (!tryExportingToBeltFunnel.isEmpty()) return;
            }
        }

        BlockPos nextPos = worldPosition.offset(BlockPos.containing(moveItemTo));
        DirectBeltInputBehaviour behaviour = BlockEntityBehaviour.get(level, nextPos, DirectBeltInputBehaviour.TYPE);
        if (behaviour != null) {
            boolean changed = false;
            if (!behaviour.canInsertFromSide(facing)) return;
            if (level.isClientSide && !isVirtual()) return;

            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                ItemStack remainder = behaviour.handleInsertion(stack, facing, false);
                if (ItemStack.matches(remainder, stack)) continue;

                inventory.setStackInSlot(slot, remainder);
                changed = true;
            }
            if (changed) {
                setChanged();
                sendData();
            }
            return;
        }

        Vec3 outPos = VecHelper.getCenterOf(worldPosition).add(moveItemTo.scale(.5f).add(0, .5, 0));
        Vec3 outMotion = moveItemTo.scale(.0625).add(0, .125, 0);

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            ItemEntity entityIn = new ItemEntity(level, outPos.x, outPos.y, outPos.z, stack);
            entityIn.setDeltaMovement(outMotion);
            level.addFreshEntity(entityIn);
        }
        inventory.clear();
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        inventory.remainingTime = -1;
        sendData();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        invalidateCapabilities();
    }

    @Override
    public void clearContent() {
        inventory.clear();
    }

    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, inventory);
    }

    protected void spawnEventParticles(ItemStack stack) {
        if (stack.isEmpty()) return;

        ParticleOptions particleData;
        if (stack.getItem() instanceof BlockItem)
            particleData = new BlockParticleOption(ParticleTypes.BLOCK, ((BlockItem) stack.getItem()).getBlock()
                    .defaultBlockState());
        else
            particleData = new ItemParticleOption(ParticleTypes.ITEM, stack);

        RandomSource r = level.random;
        Vec3 v = VecHelper.getCenterOf(this.worldPosition)
                .add(0, 5 / 16f, 0);
        for (int i = 0; i < 10; i++) {
            Vec3 m = VecHelper.offsetRandomly(new Vec3(0, 0.25f, 0), r, .125f);
            level.addParticle(particleData, v.x, v.y, v.z, m.x, m.y, m.z);
        }
    }

    public void insertItem(ItemEntity entity) {
        if (!entity.isAlive() || level.isClientSide) return;

        int slot = inventory.getStackInSlot(0).isEmpty() ? 0 : 1;

        if (!inventory.getStackInSlot(slot).isEmpty()) return;

        ItemStack remainder = inventory.insertItem(slot, entity.getItem().copy(), false);

        if (remainder.isEmpty())
            entity.discard();
        else
            entity.setItem(remainder);
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put("Inventory", inventory.serializeNBT(registries));
        compound.putInt("RecipeIndex", recipeIndex);
        super.write(compound, registries, clientPacket);
        if (clientPacket || !playEvent.isEmpty()) {
            compound.put("PlayEvent", playEvent.saveOptional(registries));
            playEvent = ItemStack.EMPTY;
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        recipeIndex = compound.getInt("RecipeIndex");
        if (compound.contains("PlayEvent"))
            playEvent = ItemStack.parseOptional(registries, compound.getCompound("PlayEvent"));
    }

    @Override
    public float calculateStressApplied() {
        return 8.0f;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return containedFluidTooltip(tooltip, isPlayerSneaking, tank.getPrimaryHandler());
    }

    private List<RecipeHolder<? extends Recipe<?>>> getRecipes() {
        return Collections.emptyList();
    }

    public void start(ItemStack inserted) {
        if (inventory.isEmpty() || (level.isClientSide && !isVirtual()) ||
                inventory.getStackInSlot(0).isEmpty() || inventory.getStackInSlot(1).isEmpty()) return;

        List<RecipeHolder<? extends Recipe<?>>> recipes = getRecipes();

        if (recipes.isEmpty()) {
            inventory.remainingTime = inventory.recipeDuration = 20;
            inventory.appliedRecipe = false;
            sendData();
            return;
        }
        inventory.remainingTime = 200;
        inventory.recipeDuration = inventory.remainingTime;
        inventory.appliedRecipe = false;
        sendData();
    }

    private void combine() {
        ItemStack input1 = inventory.getStackInSlot(0);
        ItemStack input2 = inventory.getStackInSlot(1);

        if (input1.isEmpty() || input2.isEmpty() || !EnchantmentHelper.canStoreEnchantments(input1)) return;

            ItemStack result = input1.copy();
            ItemEnchantments.Mutable input1Enchantments = new ItemEnchantments.Mutable(
                    EnchantmentHelper.getEnchantmentsForCrafting(result));
            boolean input2is = input2.has(DataComponents.STORED_ENCHANTMENTS);

            if (result.isDamageableItem() && result.getItem().isValidRepairItem(input1, input2)) {
                int repair = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                if (repair <= 0) return;

                int j3;
                for (j3 = 0; repair > 0 && j3 < input2.getCount(); ++j3) {
                    result.setDamageValue(result.getDamageValue() - repair);
                    repair = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
                }
                input2.shrink(1);
            } else {
                if (!input2is && (!result.is(input2.getItem()) || !result.isDamageableItem())) return;

                if (result.isDamageableItem() && !input2is) {
                    int dur1 = input1.getMaxDamage() - input1.getDamageValue();
                    int dur2 = input2.getMaxDamage() - input2.getDamageValue();
                    int dur = result.getMaxDamage() - (dur1 + (dur2 + result.getMaxDamage() * 12 / 100));
                    if (dur < 0) dur = 0;
                    if (dur < result.getDamageValue()) {
                        result.setDamageValue(dur);
                    }
                }

                ItemEnchantments itemEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(input2);

                boolean supportsEnchantment = false;
                boolean flag3 = false;
                for (Object2IntMap.Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    int lev1 = input1Enchantments.getLevel(holder);
                    int lev2 = entry.getIntValue();
                    int lev = lev1 == lev2 ? lev2 + 1 : Math.max(lev2, lev1);
                    Enchantment enchantment = holder.value();

                    supportsEnchantment = input1.supportsEnchantment(holder);
                    for (Holder<Enchantment> holder1 : input1Enchantments.keySet()) {
                        if (!holder1.equals(holder) && !Enchantment.areCompatible(holder, holder1))
                            supportsEnchantment = false;
                    }

                    if (supportsEnchantment) {
                        if (lev > enchantment.getMaxLevel())
                            lev = enchantment.getMaxLevel();

                        input1Enchantments.set(holder, lev);
                    } else flag3 = true;
                }
                if (flag3 && !supportsEnchantment) return;
                EnchantmentHelper.setEnchantments(result, input1Enchantments.toImmutable());

                input2.shrink(1);
            }

            int o1c = input1.getOrDefault(DataComponents.REPAIR_COST, 0);
            int o2c = input2.getOrDefault(DataComponents.REPAIR_COST, 0);
            result.set(DataComponents.REPAIR_COST, (Math.max(o1c, o2c) * 2) + 1);

            inventory.setStackInSlot(0, result);
            if (input2.isEmpty()) inventory.setStackInSlot(1, ItemStack.EMPTY);
            else inventory.setStackInSlot(1, input2);

    }
}