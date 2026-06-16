package ru.liko.wrbdrones.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.menu.DroneAssemblyMenu;
import ru.liko.wrbdrones.network.CancelDroneAssemblyPacket;
import ru.liko.wrbdrones.network.StartDroneAssemblyPacket;
import ru.liko.wrbdrones.recipe.DroneAssemblyIngredient;
import ru.liko.wrbdrones.recipe.DroneAssemblyRecipe;

@OnlyIn(Dist.CLIENT)
public class DroneAssemblyScreen extends AbstractContainerScreen<DroneAssemblyMenu> {
    // Ultra-Minimalist Military Palette
    private static final int BG_COLOR = 0xFF121212; // Almost black
    private static final int BORDER_COLOR = 0xFF3A3A3A; // Dark grey
    private static final int ACCENT_COLOR = 0xFF8BA673; // Muted military green
    private static final int HIGHLIGHT_BG = 0xFF1E1E1E; // Slightly lighter grey for selection
    
    private static final int TEXT_PRIMARY = 0xFFE0E0E0;
    private static final int TEXT_MUTED = 0xFF707070;
    private static final int TEXT_ERROR = 0xFFBA5C5C;
    private static final int TEXT_OK = 0xFF769B56;

    private static final int RECIPE_ROW_HEIGHT = 28;
    private static final int RECIPE_LIST_X = 24;
    private static final int RECIPE_LIST_Y = 32;
    private static final int RECIPE_LIST_W = 140;
    private static final int RECIPE_LIST_H = 196;
    
    private static final int OUTPUT_X = 196;
    private static final int OUTPUT_Y = 32;
    private static final int BUTTON_X = 196;
    private static final int BUTTON_Y = 202;
    private static final int BUTTON_W = 180;
    private static final int BUTTON_H = 26;

    private int selectedIndex;
    private boolean holding;
    private boolean startSent;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    
    private int recipeScrollOffset = 0;
    private int ingredientScrollOffset = 0;

    public DroneAssemblyScreen(DroneAssemblyMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 400;
        this.imageHeight = 260;
        this.inventoryLabelY = this.imageHeight + 100; // Hide inventory label
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        this.hoveredStack = ItemStack.EMPTY;
        int x = this.leftPos;
        int y = this.topPos;
        List<RecipeHolder<DroneAssemblyRecipe>> recipes = this.menu.getRecipes();
        this.selectedIndex = Mth.clamp(this.selectedIndex, 0, Math.max(0, recipes.size() - 1));

        // Ultra-minimal solid background
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, BG_COLOR);
        
        // Single sharp 1px border
        graphics.renderOutline(x, y, this.imageWidth, this.imageHeight, BORDER_COLOR);

        // Header Line (separates title)
        graphics.fill(x, y + 20, x + this.imageWidth, y + 21, BORDER_COLOR);
        graphics.drawString(this.font, this.title.getString().toUpperCase(), x + 24, y + 7, TEXT_PRIMARY, false);

        this.drawRecipeList(graphics, recipes, mouseX, mouseY);
        this.drawRecipeDetail(graphics, recipes, mouseX, mouseY);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!this.hoveredStack.isEmpty()) {
            graphics.renderTooltip(this.font, this.hoveredStack, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        // Disabled default labels for clean minimalist look
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<RecipeHolder<DroneAssemblyRecipe>> recipes = this.menu.getRecipes();
            int localX = (int) mouseX - this.leftPos;
            int localY = (int) mouseY - this.topPos;
            int clickedRecipe = this.recipeIndexAt(localX, localY, recipes.size());
            if (clickedRecipe >= 0) {
                this.selectedIndex = clickedRecipe;
                this.ingredientScrollOffset = 0; // reset scroll on recipe change
                this.cancelAssembly();
                return true;
            }

            RecipeHolder<DroneAssemblyRecipe> selected = this.selectedRecipe(recipes);
            if (selected != null && this.inside(localX, localY, BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H)
                    && this.canStart(selected.value())) {
                PacketDistributor.sendToServer(new StartDroneAssemblyPacket(this.menu.containerId, selected.id()));
                this.holding = true;
                this.startSent = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.holding) {
            this.cancelAssembly();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;

        // Recipe list scrolling
        if (this.inside(localX, localY, RECIPE_LIST_X, RECIPE_LIST_Y, RECIPE_LIST_W, RECIPE_LIST_H)) {
            List<RecipeHolder<DroneAssemblyRecipe>> recipes = this.menu.getRecipes();
            int maxScroll = Math.max(0, recipes.size() - RECIPE_LIST_H / RECIPE_ROW_HEIGHT);
            this.recipeScrollOffset = Mth.clamp(this.recipeScrollOffset - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        
        // Ingredients list scrolling
        int ingAreaY = OUTPUT_Y + 68; // divY(44) + 12 + 12
        int ingAreaH = BUTTON_Y - ingAreaY - 4;
        if (this.inside(localX, localY, OUTPUT_X, ingAreaY, this.imageWidth - OUTPUT_X, ingAreaH)) {
            RecipeHolder<DroneAssemblyRecipe> selected = this.selectedRecipe(this.menu.getRecipes());
            if (selected != null) {
                int cols = 3;
                int rows = (selected.value().ingredients().size() + cols - 1) / cols;
                int visibleRows = ingAreaH / 28;
                int maxScroll = Math.max(0, rows - visibleRows);
                this.ingredientScrollOffset = Mth.clamp(this.ingredientScrollOffset - (int) Math.signum(scrollY), 0, maxScroll);
                return true;
            }
        }
        
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        this.cancelAssembly();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawRecipeList(GuiGraphics graphics, List<RecipeHolder<DroneAssemblyRecipe>> recipes, int mouseX,
            int mouseY) {
        int x = this.leftPos + RECIPE_LIST_X;
        int y = this.topPos + RECIPE_LIST_Y;
        
        // Solid border for the list
        graphics.renderOutline(x, y, RECIPE_LIST_W, RECIPE_LIST_H, BORDER_COLOR);

        int maxVisible = RECIPE_LIST_H / RECIPE_ROW_HEIGHT;
        
        graphics.enableScissor(x, y, x + RECIPE_LIST_W, y + RECIPE_LIST_H);
        for (int i = 0; i < recipes.size(); i++) {
            if (i < this.recipeScrollOffset || i >= this.recipeScrollOffset + maxVisible + 1) continue;
            
            RecipeHolder<DroneAssemblyRecipe> recipe = recipes.get(i);
            int rowY = y + (i - this.recipeScrollOffset) * RECIPE_ROW_HEIGHT;
            boolean selected = i == this.selectedIndex;
            boolean hovered = this.inside(mouseX, mouseY, x, rowY, RECIPE_LIST_W, RECIPE_ROW_HEIGHT);
            
            if (selected) {
                graphics.fill(x + 1, rowY, x + RECIPE_LIST_W - 1, rowY + RECIPE_ROW_HEIGHT, HIGHLIGHT_BG);
                graphics.fill(x, rowY, x + 2, rowY + RECIPE_ROW_HEIGHT, ACCENT_COLOR); // Minimal green indicator
            } else if (hovered) {
                graphics.fill(x + 1, rowY, x + RECIPE_LIST_W - 1, rowY + RECIPE_ROW_HEIGHT, 0xFF181818);
            }

            ItemStack result = recipe.value().getResultItem(this.minecraft.level.registryAccess()).copy();
            
            // Clean unbordered slot
            graphics.renderItem(result, x + 8, rowY + (RECIPE_ROW_HEIGHT - 16) / 2);
            
            if (this.inside(mouseX, mouseY, x, rowY, RECIPE_LIST_W, RECIPE_ROW_HEIGHT)) {
                if (this.inside(mouseX, mouseY, x + 8, rowY + (RECIPE_ROW_HEIGHT - 16) / 2, 16, 16)) {
                     this.hoveredStack = result;
                }
            }

            Component name = result.getHoverName();
            graphics.drawString(this.font, this.trim(name, 100), x + 32, rowY + (RECIPE_ROW_HEIGHT - 8) / 2, selected ? TEXT_PRIMARY : TEXT_MUTED, false);
            
            // Minimal separator line
            graphics.fill(x + 1, rowY + RECIPE_ROW_HEIGHT - 1, x + RECIPE_LIST_W - 1, rowY + RECIPE_ROW_HEIGHT, BORDER_COLOR);
        }
        graphics.disableScissor();
        
        // Draw Scrollbar
        int maxScroll = Math.max(0, recipes.size() - maxVisible);
        if (maxScroll > 0) {
            int scrollBarH = Math.max(20, RECIPE_LIST_H * maxVisible / Math.max(1, recipes.size()));
            int scrollBarY = y + (int) ((RECIPE_LIST_H - scrollBarH) * (this.recipeScrollOffset / (float) maxScroll));
            graphics.fill(x + RECIPE_LIST_W - 2, scrollBarY, x + RECIPE_LIST_W, scrollBarY + scrollBarH, ACCENT_COLOR);
        }
    }

    private void drawRecipeDetail(GuiGraphics graphics, List<RecipeHolder<DroneAssemblyRecipe>> recipes, int mouseX,
            int mouseY) {
        RecipeHolder<DroneAssemblyRecipe> selected = this.selectedRecipe(recipes);
        if (selected == null) {
            graphics.drawString(this.font, "NO SCHEMATIC SELECTED", this.leftPos + OUTPUT_X, this.topPos + OUTPUT_Y, TEXT_MUTED, false);
            return;
        }

        DroneAssemblyRecipe recipe = selected.value();
        ItemStack result = recipe.getResultItem(this.minecraft.level.registryAccess()).copy();
        int outputX = this.leftPos + OUTPUT_X;
        int outputY = this.topPos + OUTPUT_Y;
        
        // Large minimal item display
        graphics.renderOutline(outputX, outputY, 32, 32, BORDER_COLOR);
        
        graphics.pose().pushPose();
        graphics.pose().translate(outputX + 8, outputY + 8, 0);
        graphics.renderItem(result, 0, 0);
        graphics.pose().popPose();
        
        if (this.inside(mouseX, mouseY, outputX, outputY, 32, 32)) {
            this.hoveredStack = result;
        }

        // Details next to it
        graphics.drawString(this.font, this.trim(result.getHoverName(), 140).getString().toUpperCase(), outputX + 44, outputY + 6, TEXT_PRIMARY, false);
        Component time = Component.literal(String.format("TIME: %.1f SEC", recipe.assemblyTicks() / 20.0f));
        graphics.drawString(this.font, time, outputX + 44, outputY + 18, TEXT_MUTED, false);

        // Divider
        int divY = outputY + 44;
        graphics.fill(outputX, divY, this.leftPos + this.imageWidth - 24, divY + 1, BORDER_COLOR);
        
        int matY = divY + 12;
        graphics.drawString(this.font, "REQUIREMENTS", outputX, matY, TEXT_MUTED, false);
        
        int ingAreaY = matY + 12;
        int ingAreaH = (this.topPos + BUTTON_Y) - ingAreaY - 4;
        
        graphics.enableScissor(outputX, ingAreaY, this.leftPos + this.imageWidth, ingAreaY + ingAreaH);
        this.drawIngredients(graphics, recipe.ingredients(), outputX, ingAreaY + 4, ingAreaH, mouseX, mouseY);
        graphics.disableScissor();
        
        this.drawAssemblyButton(graphics, recipe, mouseX, mouseY);
    }

    private void drawIngredients(GuiGraphics graphics, List<DroneAssemblyIngredient> ingredients, int x, int y, int ingAreaH,
            int mouseX, int mouseY) {
            
        int cols = 3;
        int spacingX = 58;
        int spacingY = 28;
        
        for (int i = 0; i < ingredients.size(); i++) {
            DroneAssemblyIngredient ingredient = ingredients.get(i);
            int col = i % cols;
            int row = i / cols;
            
            if (row < this.ingredientScrollOffset) continue;
            
            int rowX = x + col * spacingX;
            int rowY = y + (row - this.ingredientScrollOffset) * spacingY;
            
            ItemStack stack = this.iconFor(ingredient);
            graphics.renderOutline(rowX, rowY, 20, 20, BORDER_COLOR);

            if (!stack.isEmpty()) {
                graphics.renderItem(stack, rowX + 2, rowY + 2);
                if (this.inside(mouseX, mouseY, rowX, rowY, 20, 20)) {
                    this.hoveredStack = stack;
                }
            }

            int owned = this.menu.countIngredient(ingredient);
            int needed = ingredient.count();
            int color = owned >= needed ? TEXT_OK : TEXT_ERROR;
            Component count = Component.literal(owned + "/" + needed);
            
            // Draw scaled text to fit neatly
            graphics.pose().pushPose();
            graphics.pose().translate(rowX + 22, rowY + 6, 0);
            graphics.pose().scale(0.85f, 0.85f, 1.0f);
            graphics.drawString(this.font, count, 0, 0, color, false);
            graphics.pose().popPose();
        }
        
        // Draw Scrollbar for ingredients
        int rows = (ingredients.size() + cols - 1) / cols;
        int visibleRows = ingAreaH / spacingY;
        int maxScroll = Math.max(0, rows - visibleRows);
        
        if (maxScroll > 0) {
            int scrollBarH = Math.max(16, ingAreaH * visibleRows / rows);
            int scrollBarY = y - 4 + (int) ((ingAreaH - scrollBarH) * (this.ingredientScrollOffset / (float) maxScroll));
            graphics.fill(this.leftPos + this.imageWidth - 26, scrollBarY, this.leftPos + this.imageWidth - 24, scrollBarY + scrollBarH, BORDER_COLOR);
        }
    }

    private void drawAssemblyButton(GuiGraphics graphics, DroneAssemblyRecipe recipe, int mouseX, int mouseY) {
        int x = this.leftPos + BUTTON_X;
        int y = this.topPos + BUTTON_Y;
        boolean canStart = this.canStart(recipe);
        boolean hovered = this.inside(mouseX, mouseY, x, y, BUTTON_W, BUTTON_H);
        
        int bg = canStart ? (hovered ? HIGHLIGHT_BG : BG_COLOR) : BG_COLOR;
        int border = canStart ? (hovered ? ACCENT_COLOR : BORDER_COLOR) : BORDER_COLOR;
        
        if (this.holding && canStart) {
            bg = 0xFF2A2A2A;
        }
        
        graphics.fill(x, y, x + BUTTON_W, y + BUTTON_H, bg);
        graphics.renderOutline(x, y, BUTTON_W, BUTTON_H, border);

        float progress = this.menu.getProgressFraction();
        if (progress > 0.0f) {
            int w = Mth.floor((BUTTON_W - 2) * progress);
            graphics.fill(x + 1, y + 1, x + 1 + w, y + BUTTON_H - 1, 0xFF2A3620); // Muted green loading bg
            if (w > 0 && w < BUTTON_W - 2) {
                graphics.fill(x + w, y + 1, x + w + 1, y + BUTTON_H - 1, ACCENT_COLOR); // Leading sharp edge
            }
        }

        Component label = canStart
                ? Component.translatable("screen.wrbdrones.dronetable.assemble")
                : Component.translatable("screen.wrbdrones.dronetable.missing");
        
        int textColor = canStart ? (hovered ? ACCENT_COLOR : TEXT_PRIMARY) : TEXT_ERROR;
        
        // Draw string uppercase for minimal military feel
        graphics.drawCenteredString(this.font, label.getString().toUpperCase(), x + BUTTON_W / 2, y + 9, textColor);
    }

    private int recipeIndexAt(int localX, int localY, int recipeCount) {
        if (!this.inside(localX, localY, RECIPE_LIST_X, RECIPE_LIST_Y, RECIPE_LIST_W, RECIPE_LIST_H)) {
            return -1;
        }
        int index = (localY - RECIPE_LIST_Y) / RECIPE_ROW_HEIGHT + this.recipeScrollOffset;
        return index >= 0 && index < recipeCount ? index : -1;
    }

    private RecipeHolder<DroneAssemblyRecipe> selectedRecipe(List<RecipeHolder<DroneAssemblyRecipe>> recipes) {
        if (recipes.isEmpty() || this.selectedIndex < 0 || this.selectedIndex >= recipes.size()) {
            return null;
        }
        return recipes.get(this.selectedIndex);
    }

    private boolean canStart(DroneAssemblyRecipe recipe) {
        return this.minecraft.player != null
                && (this.minecraft.player.getAbilities().instabuild || this.menu.hasMaterials(recipe));
    }

    private void cancelAssembly() {
        if (this.startSent) {
            PacketDistributor.sendToServer(new CancelDroneAssemblyPacket(this.menu.containerId));
        }
        this.holding = false;
        this.startSent = false;
    }

    private ItemStack iconFor(DroneAssemblyIngredient ingredient) {
        ItemStack[] stacks = ingredient.ingredient().getItems();
        return stacks.length == 0 ? ItemStack.EMPTY : stacks[0].copy();
    }

    private Component trim(Component component, int width) {
        if (this.font.width(component) <= width) {
            return component;
        }

        String text = component.getString();
        List<String> chars = new ArrayList<>();
        text.codePoints().forEach(codePoint -> chars.add(new String(Character.toChars(codePoint))));
        while (!chars.isEmpty() && this.font.width(String.join("", chars) + "...") > width) {
            chars.remove(chars.size() - 1);
        }
        return Component.literal(String.join("", chars) + "...");
    }

    private boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }
}
