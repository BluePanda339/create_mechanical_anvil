package bluepanda.create_mechanical_anvil.block.mechanicalanvil;

import bluepanda.create_mechanical_anvil.CEIMABlockEntities;
import bluepanda.create_mechanical_anvil.CEIMAIcons;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.createmod.catnip.lang.Lang;
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
import net.neoforged.neoforge.items.IItemHandler;
import plus.dragons.createenchantmentindustry.common.registry.CEIFluids;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MechanicalAnvilBlockEntity extends KineticBlockEntity implements Clearable, IHaveGoggleInformation {

    public ProcessingInventory inventory;
    protected SmartFluidTankBehaviour tank;
    private ItemStack combinedItem = ItemStack.EMPTY;
    protected ScrollOptionBehaviour<InputMode> inputMode;
    // wherever items are being inserted we need to use 0 first then 1
    // whenever items are being processed we need to use getItemsSlot

    public MechanicalAnvilBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inventory = new ProcessingInventory(inserted -> start()) {

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                // slot is 0 or 1, stack is something, sim is if in ponder
                ItemStack insertItem = super.insertItem(slot, stack, simulate);
                // if slot isn't correct and if uhh? isn't stacks count and if insert item isn't the same as stack
                if ((slot == 0 || slot == 1) && !(insertItem.getCount() == stack.getCount()
                        && ItemStack.isSameItem(insertItem, stack)))
                    callback.accept(getStackInSlot(slot));
                return insertItem;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return !appliedRecipe && (slot == 0 || slot == 1) && getStackInSlot(slot).isEmpty();
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

        inputMode = new ScrollOptionBehaviour<>(InputMode.class,
                CreateLang.translateDirect("mechanical_anvil.input_mode"), this,
                new CenteredSideValueBoxTransform(((state, d) -> d == Direction.UP)) {
            @Override
            protected Vec3 getSouthLocation() {
                Vec3 vec;
                float z = 12.5f;
                switch (getBlockState().getValue(MechanicalAnvilBlock.HORIZONTAL_FACING)) {
                    case EAST -> vec = VecHelper.voxelSpace(8, 4, z);
                    case SOUTH -> vec = VecHelper.voxelSpace(4, 8, z);
                    case WEST -> vec = VecHelper.voxelSpace(8, 12, z);
                    default -> vec = VecHelper.voxelSpace(12, 8, z);
                }
                return vec;
            }
        });
        behaviours.add(inputMode);

        behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnels()
                .setInsertionHandler(this::tryInsertingFromSide));
    }

    private ItemStack tryInsertingFromSide(TransportedItemStack transportedStack, Direction side, boolean simulate) {

        Direction facing = getBlockState().getValue(MechanicalAnvilBlock.HORIZONTAL_FACING);
        int slot;

        // input into the left side if coming from the left
        if (side == facing.getCounterClockWise())
            slot = 0;
        // input into the right side if coming from the right
        else if (side == facing.getClockWise())
            slot = 1;
        else
            return transportedStack.stack;
        // if coming from left or right and not empty
        if (!inventory.getStackInSlot(slot).isEmpty())
            return transportedStack.stack;

        ItemStack inserted = transportedStack.stack;
        ItemStack remainder = inventory.insertItem(slot, inserted, simulate);
        // if not ponder and if the uhh is not the attempt to inserts count?
        if (!simulate && remainder.getCount() != inserted.getCount()) {
            notifyUpdate();
            setChanged();
        }
        return remainder;
    }

    @Override
    public void tick() {
        super.tick();
        if (getSpeed() == 0) return;

        // if the countdown hasn't started yet
        if (inventory.remainingTime == -1) {
            // if slot 0 or 1 is empty or
            // if already combined the items
            if (inventory.getStackInSlot(0).isEmpty() || inventory.getStackInSlot(1).isEmpty() || inventory.appliedRecipe)
                return;
            start();
            return;
        }

        float processingSpeed = Mth.clamp(Math.abs(getSpeed()) / 32f, 1f, 128f);
        inventory.remainingTime -= processingSpeed;

        if (inventory.remainingTime <= 0 && !inventory.appliedRecipe) {// if the amount of time is less than or equal to 0 and the items haven't been combined
            if (level.isClientSide && !isVirtual()) return;// if client side and not in ponder

            AnvilResult result = calculateCombination();
            if (result.valid()) {
                if (!applyCombine(result)) {
                    combinedItem = ItemStack.EMPTY;
                    inventory.remainingTime = -1;
                    inventory.recipeDuration = 0;
                    sendData();
                    return;
                }
                boolean applied = applyCombine(result);

                System.out.println("After applyCombine: slot0="
                        + inventory.getStackInSlot(0) + ", slot1="
                        + inventory.getStackInSlot(1));
                combinedItem = result.result.copy();
            }
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
        if (inventory.isEmpty()) {
            inventory.remainingTime = -1;
            inventory.recipeDuration = 0;
            inventory.appliedRecipe = false;
            sendData();
            return;
        }

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
        // if there isn't an item in slot 0 or 1
        if (inventory.remainingTime != -1 || (level.isClientSide && !isVirtual())) return;
        if (inventory.getStackInSlot(0).isEmpty() || inventory.getStackInSlot(1).isEmpty()) return;
        AnvilResult result = calculateCombination();
        if (result.valid() && result.expCost() > tank.getPrimaryHandler().getFluid().getAmount()) return;

        // start the countdown
        inventory.recipeDuration = 40;
        inventory.remainingTime = inventory.recipeDuration;
        inventory.appliedRecipe = false;
        sendData();
    }

    private boolean applyCombine(AnvilResult anvilResult) {
        if (!anvilResult.valid()) return false;

        FluidStack fluid = tank.getPrimaryHandler().getFluid();
        if (fluid.getAmount() < anvilResult.expCost()) return false;

        ItemStack input = inventory.getStackInSlot(getProcessItemsSlot()).copy();
        ItemStack material = inventory.getStackInSlot(getMaterialItemsSlot()).copy();

        if (input.isEmpty() || material.isEmpty()) return false;

        int processSlot = getProcessItemsSlot();
        int materialSlot = getMaterialItemsSlot();

        input.shrink(1);
        material.shrink(1);

        inventory.setStackInSlot(processSlot, input);
        inventory.setStackInSlot(materialSlot, material);

        fluid.shrink(anvilResult.expCost());
        tank.getPrimaryHandler().setFluid(fluid);

        inventory.setStackInSlot(3, anvilResult.result());

        setChanged();
        sendData();

        return true;
    }

    private record AnvilResult(ItemStack result, int expCost, boolean valid) {
        private static AnvilResult invalid() {
            return new AnvilResult(ItemStack.EMPTY, 0, false);
        }
    }

    private AnvilResult calculateCombination() {
        ItemStack input = inventory.getStackInSlot(getProcessItemsSlot());
        ItemStack material = inventory.getStackInSlot(getMaterialItemsSlot());

        // if either slot is missing item or if first slot cannot hold enchantments
        if (input.isEmpty() || material.isEmpty() || !EnchantmentHelper.canStoreEnchantments(input)) return AnvilResult.invalid();
        ItemStack result = input.copy();

        int levelsCost = 0;
        long accumulateCost = (long)input.getOrDefault(DataComponents.REPAIR_COST, 0) + (long) material.getOrDefault(DataComponents.REPAIR_COST, 0);

        // if result can have dur and if result can uhh material can repair input
        if (result.isDamageableItem() && result.getItem().isValidRepairItem(input, material)) {
            int repairAmount = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
            if (repairAmount <= 0) return AnvilResult.invalid();

            result.setDamageValue(result.getDamageValue() - repairAmount);
            levelsCost++;
        } else {

            if (!material.has(DataComponents.STORED_ENCHANTMENTS)) {// if second slot isn't an enchantment book
                // if result differs from first slot
                if (!result.is(material.getItem()) || !result.isDamageableItem()) return AnvilResult.invalid();

                int dur = input.getMaxDamage() - input.getDamageValue();
                int dur2 = material.getMaxDamage() - material.getDamageValue();
                // [100] * 12 / 100 = [12]
                // [12] + [50] = [62]
                // [100] - [62] = [38]
                int newDur = result.getMaxDamage() - (dur + (dur2 + result.getMaxDamage() * 12 / 100));
                // if results new dur[38] is less than results max dur[100] set new dur
                if (newDur < result.getDamageValue()) {
                    result.setDamageValue(Math.max(0, newDur));
                    levelsCost += 2;
                }
            }

            ItemEnchantments.Mutable resultsEnchantments = new ItemEnchantments.Mutable(
                    EnchantmentHelper.getEnchantmentsForCrafting(result));

            ItemEnchantments materialEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(material);
            boolean appliedEnchantment = false;
            boolean rejectedEnchantment = false;
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : materialEnchantments.entrySet()) {
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
                        levelsCost++;
                    }
                }
                // if first slot can have enchantmentHolders enchants and
                // if holder1 is the same enchantment from enchantmentHolder
                // if enchants of enchantmentHolder and holder1 are compatible
                if (!compatible) {
                    rejectedEnchantment = true;
                    continue;
                }
                appliedEnchantment = true;
                resultsEnchantments.set(enchantmentHolder, Math.min(newLev, enchantment.getMaxLevel()));

                int l3 = enchantment.getAnvilCost();
                if (material.has(DataComponents.STORED_ENCHANTMENTS)) l3 = Math.max(1, l3 / 2);
                levelsCost += l3 * newLev;
                if (input.getCount() > 1) levelsCost = 40;
            }
            // if first slot can't have enchantmentHolders enchants and
            // if holder1 is different enchantment from enchantmentHolder
            // if enchants of enchantmentHolder and holder1 aren't compatible
            if (rejectedEnchantment && !appliedEnchantment) return AnvilResult.invalid();

            EnchantmentHelper.setEnchantments(result, resultsEnchantments.toImmutable());
        }
        if (levelsCost <= 0) return AnvilResult.invalid();
        levelsCost = (int) Mth.clamp(accumulateCost + levelsCost, 0L, Integer.MAX_VALUE);
        int expCost;
        if (levelsCost <= 16) expCost = (levelsCost * levelsCost) + 6 * levelsCost;
        else if (levelsCost <= 31) expCost = (int) (2.5 * (levelsCost * levelsCost) - 40.5 * levelsCost + 360);
        else expCost = (int) (4.5 * (levelsCost * levelsCost) - 162.5 * levelsCost + 2220);

        result.set(DataComponents.REPAIR_COST,
                (Math.max(
                        input.getOrDefault(DataComponents.REPAIR_COST, 0),
                        material.getOrDefault(DataComponents.REPAIR_COST, 0)
                ) * 2) + 1
        );
        return new AnvilResult(result, expCost, true);
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(getBlockPos()).inflate(.125f);
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
    public void invalidate() {
        super.invalidate();
        invalidateCapabilities();
    }

    @Override
    public void clearContent() {
        inventory.clear();
        inventory.remainingTime = -1;
        inventory.recipeDuration = 0;
        inventory.appliedRecipe = false;
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

        // always input into left slot first then right slot
        int slot = inventory.getStackInSlot(0).isEmpty() ? 0 : 1;
        // if the slot isn't empty that means the second slot has items
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

    // if it's default then the process slot is 0 and the material slot is 1
    // if it'sn't default then the process slot is 1 and the material slot is 0
    public int getProcessItemsSlot() { return inputMode.get() == InputMode.DEFAULT ? 0 : 1; }
    public int getMaterialItemsSlot() { return inputMode.get() == InputMode.DEFAULT ? 1 : 0; }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return containedFluidTooltip(tooltip, isPlayerSneaking, tank.getPrimaryHandler());
    }

    public static enum InputMode implements INamedIconOptions {
        DEFAULT(CEIMAIcons.I_TOOL),
        FLIPPED(CEIMAIcons.I_BOOK)
        ;
        private final CEIMAIcons icon;
        InputMode(CEIMAIcons icon) {
            this.icon = icon;
        }

        @Override
        public CEIMAIcons getIcon() { return icon; }

        @Override
        public String getTranslationKey() { return "create.mechanical_anvil.input_mode." + Lang.asId(name()); }
    }
}
