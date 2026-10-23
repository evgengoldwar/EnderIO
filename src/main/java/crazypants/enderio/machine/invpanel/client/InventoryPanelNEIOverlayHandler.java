package crazypants.enderio.machine.invpanel.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import codechicken.nei.OffsetPositioner;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.DefaultOverlayRenderer;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IStackPositioner;
import codechicken.nei.recipe.GuiOverlayButton.ItemOverlayState;
import codechicken.nei.recipe.IRecipeHandler;
import crazypants.enderio.machine.invpanel.GuiInventoryPanel;
import crazypants.enderio.machine.invpanel.InventoryPanelContainer;
import crazypants.enderio.machine.invpanel.StoredCraftingRecipe;

public class InventoryPanelNEIOverlayHandler implements IOverlayHandler {

    public static final int NEI_OFFSET_X = 25;
    public static final int NEI_OFFSET_Y = 6;

    private static final int CRAFTING_GRID_OFFSET_X = InventoryPanelContainer.CRAFTING_GRID_X - NEI_OFFSET_X;
    private static final int CRAFTING_GRID_OFFSET_Y = InventoryPanelContainer.CRAFTING_GRID_Y - NEI_OFFSET_Y;

    public static final IStackPositioner positioner = new OffsetPositioner(
            CRAFTING_GRID_OFFSET_X,
            CRAFTING_GRID_OFFSET_Y);

    @Override
    public void overlayRecipe(GuiContainer gui, IRecipeHandler recipe, int recipeIndex, boolean shift) {
        GuiInventoryPanel guiInvPanel = (GuiInventoryPanel) gui;
        List<PositionedStack> ingredients = recipe.getIngredientStacks(recipeIndex);

        if (shift) {
            shift = guiInvPanel.getContainer().clearCraftingGrid();
        }

        ItemStack[][] slots = mapSlots(ingredients, guiInvPanel.getContainer());
        if (slots != null) {
            CraftingHelperNEI helper = new CraftingHelperNEI(slots);
            helper.overlayRenderer = new DefaultOverlayRenderer(ingredients, positioner);
            guiInvPanel.setCraftingHelper(helper);
            if (shift) {
                helper.refill(guiInvPanel, 64);
            }
        } else {
            guiInvPanel.setCraftingHelper(null);
        }
    }

    /**
     * Marks the ingredients NEI shows as present or missing. Unlike the default implementation this also counts the
     * items stored in the inventory panel network, the same sources the panel uses when it fills the crafting grid.
     */
    @Override
    public List<ItemOverlayState> presenceOverlay(GuiContainer gui, IRecipeHandler recipe, int recipeIndex) {
        GuiInventoryPanel guiInvPanel = (GuiInventoryPanel) gui;
        InventoryPanelContainer c = guiInvPanel.getContainer();

        Map<Item, List<ItemStack>> available = new HashMap<>();
        addSlots(available, c.getCraftingGridSlots());
        addSlots(available, c.getReturnAreaSlots());
        addSlots(available, c.getPlayerInventorySlots());
        addSlots(available, c.getPlayerHotbarSlots());
        InventoryDatabaseClient db = guiInvPanel.getDatabase();
        if (db != null) {
            List<ItemEntry> entries = new ArrayList<>();
            db.getItems(entries);
            for (ItemEntry entry : entries) {
                if (entry.getCount() > 0) {
                    addStack(available, entry.makeItemStack());
                }
            }
        }

        List<ItemOverlayState> result = new ArrayList<>();
        for (PositionedStack ingredient : recipe.getIngredientStacks(recipeIndex)) {
            ItemStack used = findIngredient(available, ingredient);
            if (used != null) {
                used.stackSize--;
            }
            result.add(new ItemOverlayState(ingredient, used != null));
        }
        return result;
    }

    private static void addSlots(Map<Item, List<ItemStack>> available, Collection<Slot> slots) {
        for (Slot slot : slots) {
            ItemStack stack = slot.getStack();
            if (stack != null && stack.stackSize > 0) {
                addStack(available, stack.copy());
            }
        }
    }

    private static void addStack(Map<Item, List<ItemStack>> available, ItemStack stack) {
        if (stack.getItem() == null) {
            return;
        }
        List<ItemStack> list = available.get(stack.getItem());
        if (list == null) {
            list = new ArrayList<>();
            available.put(stack.getItem(), list);
        }
        list.add(stack);
    }

    private static ItemStack findIngredient(Map<Item, List<ItemStack>> available, PositionedStack ingredient) {
        for (ItemStack permutation : ingredient.items) {
            if (permutation == null) {
                continue;
            }
            List<ItemStack> list = available.get(permutation.getItem());
            if (list == null) {
                continue;
            }
            for (ItemStack stack : list) {
                if (stack.stackSize > 0 && (ingredient.contains(stack)
                        || StoredCraftingRecipe.isIngredientCompatible(permutation, stack))) {
                    return stack;
                }
            }
        }
        return null;
    }

    private ItemStack[][] mapSlots(List<PositionedStack> ingredients, InventoryPanelContainer c) {
        ItemStack[][] slots = new ItemStack[9][];
        List<Slot> craftingGrid = c.getCraftingGridSlots();
        int found = 0;
        for (PositionedStack pstack : ingredients) {
            for (int idx = 0; idx < 9; idx++) {
                Slot slot = craftingGrid.get(idx);
                if (slot.xDisplayPosition == pstack.relx + CRAFTING_GRID_OFFSET_X
                        && slot.yDisplayPosition == pstack.rely + CRAFTING_GRID_OFFSET_Y) {
                    slots[idx] = pstack.items;
                    found++;
                    break;
                }
            }
        }
        if (found != ingredients.size()) {
            return null;
        }
        return slots;
    }
}
