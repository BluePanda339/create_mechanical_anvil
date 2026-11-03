package bluepanda.create_mechanical_anvil.block.mechanicalanvil;

import bluepanda.create_mechanical_anvil.CEIMABlockEntities;
import com.simibubi.create.AllFluids;
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
import net.neoforged.neoforge.fluids.FluidStack;
import plus.dragons.createenchantmentindustry.common.registry.CEIFluids;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MechanicalAnvilBlockEntity extends KineticBlockEntity implements Clearable, IHaveGoggleInformation {

    public ProcessingInventory inventory;
    protected SmartFluidTankBehaviour tank;
    private ItemStack combinedItem = ItemStack.EMPTY;

    public MechanicalAnvilBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inventory = new ProcessingInventory(inserted -> start()) {
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
                    if (context == null || context == Direction.DOWN) {
                        return be.tank.getPrimaryHandler();
                    }
                    return null;
                }
        );
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        tank = SmartFluidTankBehaviour.single(this, 250);
        tank.getPrimaryHandler().setValidator(stack -> stack.getFluid().isSame(CEIFluids.EXPERIENCE.get()));
        behaviours.add(tank);

        behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnels()
                .setInsertionHandler(this::tryInsertingFromSide));
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(getBlockPos()).inflate(.125f);
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

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();
        if (getSpeed() == 0)
            return;

        if (!combinedItem.isEmpty()) {
            spawnEventParticles(combinedItem);
            combinedItem = ItemStack.EMPTY;
            AllSoundEvents.SAW_ACTIVATE_STONE.playAt(level, worldPosition, 3, 1, true);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (getSpeed() == 0) return;

        // if the countdown hasn't started yet
        if (inventory.remainingTime == -1) {
            // if either slot is empty or if it has already combined the items
            if (inventory.getStackInSlot(0).isEmpty() || inventory.getStackInSlot(1).isEmpty() || inventory.appliedRecipe)
                return;
            start();
            return;
        }

        float processingSpeed = Mth.clamp(Math.abs(getSpeed()) / 32f, 1f, 128f);
        inventory.remainingTime -= processingSpeed;

        if (inventory.remainingTime <= 0 && !inventory.appliedRecipe) {// if the amount of time is less than or equal to 0 and the items haven't been combined
            if (level.isClientSide && !isVirtual()) return;// if client side and not in ponder

            combinedItem = inventory.getStackInSlot(0);
            combine();
            inventory.appliedRecipe = true;
            // recipe duration is only used for rendering
            inventory.recipeDuration = 20;
            inventory.remainingTime = inventory.recipeDuration;
            sendData();
            return;
        }
        // we get here if the remaining time is more than 0
        // or the items have been combined

        if (inventory.remainingTime > 0)
            return;// if the remaining time is more than 0
        // we get here if the remaining time is less than 0
        // which can't happen, so we get here only if the items have combined

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

    public void start() {
        // if there's no items in the block?
        // if client side? and not pondering?
        // if there's no item in either slot
        if (inventory.isEmpty() || (level.isClientSide && !isVirtual()) ||
                inventory.getStackInSlot(0).isEmpty() || inventory.getStackInSlot(1).isEmpty()) return;

        // start the countdown
        inventory.recipeDuration = 40;
        inventory.remainingTime = inventory.recipeDuration;
        inventory.appliedRecipe = false;
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
        super.write(compound, registries, clientPacket);
        if (clientPacket || !combinedItem.isEmpty()) {
            compound.put("PlayEvent", combinedItem.saveOptional(registries));
            combinedItem = ItemStack.EMPTY;
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        if (compound.contains("PlayEvent"))
            combinedItem = ItemStack.parseOptional(registries, compound.getCompound("PlayEvent"));
    }

    @Override
    public float calculateStressApplied() {
        return 8.0f;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return containedFluidTooltip(tooltip, isPlayerSneaking, tank.getPrimaryHandler());
    }

    private void combine() {
        ItemStack input = inventory.getStackInSlot(0);
        ItemStack input2 = inventory.getStackInSlot(1);

        // if either slot is missing item or if first slot cannot hold enchantments
        if (input.isEmpty() || input2.isEmpty() || !EnchantmentHelper.canStoreEnchantments(input)) return;

        int cost = 0;
        long accumulateCost = 0;

        ItemStack result = input.copy();

        accumulateCost += (long)(Integer)input.getOrDefault(DataComponents.REPAIR_COST, 0) + (long)(Integer)input2.getOrDefault(DataComponents.REPAIR_COST, 0);
        // if result can have durability and if the second slot can repair first slot
        if (result.isDamageableItem() && result.getItem().isValidRepairItem(input, input2)) {
            int repairAmount = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
            if (repairAmount <= 0) return;

            for (int i = 0; repairAmount > 0 && i < input2.getCount(); i++) {
                result.setDamageValue(result.getDamageValue() - repairAmount);
                cost++;
                repairAmount = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
            }
            // if result can't have durability or if the second slot couldn't repair first slot
        } else {
            // if second slot isn't an enchantment book
            if (!input2.has(DataComponents.STORED_ENCHANTMENTS)) {
                // if result differs from first slot
                if (!result.is(input2.getItem()) || !result.isDamageableItem()) return;

                // if result can have durability
                if (result.isDamageableItem()) {
                    int dur = input.getMaxDamage() - input.getDamageValue();
                    int dur2 = input2.getMaxDamage() - input2.getDamageValue();
                    // [100] * 12 / 100 = [12]
                    // [12] + [50] = [62]
                    // [100] - [62] = [38]
                    int newDur = result.getMaxDamage() - (dur + (dur2 + result.getMaxDamage() * 12 / 100));
                    // if results new dur[38] is less than results max dur[100] set new dur
                    if (newDur < result.getDamageValue()) {
                        result.setDamageValue(Math.max(0, newDur));
                        cost += 2;
                    }
                }
            }

            ItemEnchantments.Mutable resultsEnchantments = new ItemEnchantments.Mutable(
                    EnchantmentHelper.getEnchantmentsForCrafting(result));

            ItemEnchantments input2Enchantments = EnchantmentHelper.getEnchantmentsForCrafting(input2);
            boolean appliedEnchantment = false;
            boolean rejectedEnchantment = false;
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : input2Enchantments.entrySet()) {
                Holder<Enchantment> enchantmentHolder = entry.getKey();

                int lev = resultsEnchantments.getLevel(enchantmentHolder);
                int lev2 = entry.getIntValue();
                int newLev = lev == lev2 ? lev2 + 1 : Math.max(lev2, lev);

                Enchantment enchantment = enchantmentHolder.value();
                boolean compatible = input.supportsEnchantment(enchantmentHolder);
                for (Holder<Enchantment> holder1 : resultsEnchantments.keySet()) {
                    // if holder1 is a different enchantment from enchantmentHolder
                    // if enchants of enchantmentHolder and holder1 aren't compatible
                    if (!holder1.equals(enchantmentHolder) && !Enchantment.areCompatible(enchantmentHolder, holder1)) {
                        compatible = false;
                        cost++;
                    }
                }
                // if first slot can have enchantmentHolders enchants and
                // if holder1 is the same enchantment from enchantmentHolder
                // if enchants of enchantmentHolder and holder1 are compatible
                if (!compatible) {
                    rejectedEnchantment = true;
                } else {
                    appliedEnchantment = true;
                    resultsEnchantments.set(enchantmentHolder, Math.min(newLev, enchantment.getMaxLevel()));

                    int l3 = enchantment.getAnvilCost();
                    if (input2.has(DataComponents.STORED_ENCHANTMENTS)) l3 = Math.max(1, l3 / 2);

                    cost += l3 * newLev;

                    if (input.getCount() > 1) {
                        cost = 40;
                    }
                }
            }
            // if first slot can't have enchantmentHolders enchants and
            // if holder1 is different enchantment from enchantmentHolder
            // if enchants of enchantmentHolder and holder1 aren't compatible
            if (rejectedEnchantment && !appliedEnchantment) return;


            EnchantmentHelper.setEnchantments(result, resultsEnchantments.toImmutable());
        }
        if (cost <= 0) return;

        int levelCost = (int) Mth.clamp(accumulateCost + cost, 0L, 2147483647L);
        int pointCost;

        if (levelCost <= 16) pointCost = (levelCost * levelCost) + 6 * levelCost;
        else if (levelCost <= 31) pointCost = (int) (2.5 * (levelCost * levelCost) - 40.5 * levelCost + 360);
        else pointCost = (int) (4.5 * (levelCost * levelCost) - 162.5 * levelCost + 2220);

        FluidStack toFill = tank.getPrimaryHandler().getFluid();

        if (toFill.getAmount() >= pointCost) {
            toFill.shrink(pointCost);
            tank.getPrimaryHandler().setFluid(toFill);

            result.set(DataComponents.REPAIR_COST, (Math.max(input.getOrDefault(DataComponents.REPAIR_COST, 0), input2.getOrDefault(DataComponents.REPAIR_COST, 0)) * 2) + 1);

            inventory.setStackInSlot(3, result);
            input.shrink(1);
            input2.shrink(1);
        }
    }
}