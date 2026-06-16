package ru.liko.wrbdrones.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.liko.wrbdrones.recipe.DroneAssemblyIngredient;
import ru.liko.wrbdrones.recipe.DroneAssemblyRecipe;
import ru.liko.wrbdrones.registry.ModBlocks;
import ru.liko.wrbdrones.registry.ModMenus;
import ru.liko.wrbdrones.registry.ModRecipes;

public class DroneAssemblyMenu extends AbstractContainerMenu {
    private final Inventory playerInventory;
    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private final DataSlot progress = DataSlot.standalone();
    private final DataSlot maxProgress = DataSlot.standalone();

    @Nullable
    private ResourceLocation activeRecipeId;
    private boolean assembling;

    public static DroneAssemblyMenu client(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf == null ? BlockPos.ZERO : buf.readBlockPos();
        return new DroneAssemblyMenu(containerId, inventory, ContainerLevelAccess.create(inventory.player.level(), pos),
                pos);
    }

    public DroneAssemblyMenu(int containerId, Inventory inventory, ContainerLevelAccess access, BlockPos pos) {
        super(ModMenus.DRONE_ASSEMBLY.get(), containerId);
        this.playerInventory = inventory;
        this.access = access;
        this.pos = pos;
        this.addDataSlot(this.progress);
        this.addDataSlot(this.maxProgress);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.isAlive() && this.access.evaluate((level, blockPos) -> level.getBlockState(blockPos)
                .is(ModBlocks.DRONETABLE.get()) && player.distanceToSqr(blockPos.getX() + 0.5,
                        blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64.0, true);
    }

    @Override
    public void broadcastChanges() {
        if (this.playerInventory.player instanceof ServerPlayer serverPlayer) {
            this.tickAssembly(serverPlayer);
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(@NotNull Player player) {
        this.cancelAssembly();
        super.removed(player);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public int getProgress() {
        return this.progress.get();
    }

    public int getMaxProgress() {
        return this.maxProgress.get();
    }

    public float getProgressFraction() {
        int max = this.getMaxProgress();
        return max <= 0 ? 0.0f : Math.min(1.0f, this.getProgress() / (float) max);
    }

    public List<RecipeHolder<DroneAssemblyRecipe>> getRecipes() {
        return this.playerInventory.player.level().getRecipeManager()
                .getAllRecipesFor(ModRecipes.DRONE_ASSEMBLY_TYPE.get()).stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList();
    }

    public int countIngredient(DroneAssemblyIngredient ingredient) {
        int count = 0;
        for (ItemStack stack : this.inventoryStacks()) {
            if (!stack.isEmpty() && ingredient.ingredient().test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public boolean hasMaterials(DroneAssemblyRecipe recipe) {
        return this.canConsume(recipe, true);
    }

    public void startAssembly(ResourceLocation recipeId, ServerPlayer player) {
        if (!this.stillValid(player)) {
            return;
        }

        Optional<RecipeHolder<DroneAssemblyRecipe>> recipe = this.getRecipe(recipeId, player.level().getRecipeManager());
        if (recipe.isEmpty()) {
            this.cancelAssembly();
            return;
        }

        if (!player.getAbilities().instabuild && !this.hasMaterials(recipe.get().value())) {
            this.cancelAssembly();
            return;
        }

        if (this.assembling && recipeId.equals(this.activeRecipeId)) {
            return;
        }

        this.activeRecipeId = recipeId;
        this.assembling = true;
        this.progress.set(0);
        this.maxProgress.set(recipe.get().value().assemblyTicks());
    }

    public void cancelAssembly() {
        this.activeRecipeId = null;
        this.assembling = false;
        this.progress.set(0);
        this.maxProgress.set(0);
    }

    private void tickAssembly(ServerPlayer player) {
        if (!this.assembling || this.activeRecipeId == null) {
            return;
        }

        Optional<RecipeHolder<DroneAssemblyRecipe>> recipe = this.getRecipe(this.activeRecipeId,
                player.level().getRecipeManager());
        if (recipe.isEmpty() || !this.stillValid(player)) {
            this.cancelAssembly();
            return;
        }

        DroneAssemblyRecipe assemblyRecipe = recipe.get().value();
        this.maxProgress.set(assemblyRecipe.assemblyTicks());
        this.progress.set(this.progress.get() + 1);

        if (this.progress.get() >= this.maxProgress.get()) {
            this.finishAssembly(player, assemblyRecipe);
        }
    }

    private void finishAssembly(ServerPlayer player, DroneAssemblyRecipe recipe) {
        if (!player.getAbilities().instabuild && !this.consumeMaterials(recipe)) {
            this.cancelAssembly();
            return;
        }

        ItemStack result = recipe.getResultItem(player.level().registryAccess()).copy();
        this.cancelAssembly();
        player.getInventory().placeItemBackInInventory(result);
        player.inventoryMenu.broadcastFullState();
    }

    private Optional<RecipeHolder<DroneAssemblyRecipe>> getRecipe(ResourceLocation id, RecipeManager recipeManager) {
        return recipeManager.byKey(id)
                .filter(holder -> holder.value() instanceof DroneAssemblyRecipe)
                .map(holder -> (RecipeHolder<DroneAssemblyRecipe>) holder);
    }

    private boolean consumeMaterials(DroneAssemblyRecipe recipe) {
        if (!this.canConsume(recipe, true)) {
            return false;
        }
        return this.canConsume(recipe, false);
    }

    private boolean canConsume(DroneAssemblyRecipe recipe, boolean simulate) {
        List<ItemStack> stacks = this.inventoryStacks();
        List<ItemStack> working = simulate ? stacks.stream().map(ItemStack::copy).toList() : stacks;

        for (DroneAssemblyIngredient ingredient : recipe.ingredients()) {
            int remaining = ingredient.count();
            for (ItemStack stack : working) {
                if (remaining <= 0) {
                    break;
                }
                if (!stack.isEmpty() && ingredient.ingredient().test(stack)) {
                    int taken = Math.min(remaining, stack.getCount());
                    if (!simulate) {
                        stack.shrink(taken);
                    } else {
                        stack.setCount(stack.getCount() - taken);
                    }
                    remaining -= taken;
                }
            }

            if (remaining > 0) {
                return false;
            }
        }

        return true;
    }

    private List<ItemStack> inventoryStacks() {
        List<ItemStack> stacks = new ArrayList<>(this.playerInventory.items.size() + this.playerInventory.offhand.size());
        stacks.addAll(this.playerInventory.items);
        stacks.addAll(this.playerInventory.offhand);
        return stacks;
    }
}
