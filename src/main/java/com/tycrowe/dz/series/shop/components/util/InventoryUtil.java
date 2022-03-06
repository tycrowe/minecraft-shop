package com.tycrowe.dz.series.shop.components.util;

import com.google.common.base.Preconditions;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class InventoryUtil {

    public static class InventoryTooLargeException extends Exception {

        public InventoryTooLargeException(String message) {
            super("Inventory cannot exceed size of 57 items.");
        }

    }

    public static Inventory buildInventory(int inventorySize, String inventoryTitle) {
        if (inventorySize <= 0) inventorySize = 9;
        if (inventorySize % 9 != 0)
            inventorySize = (int) (Math.ceil(inventorySize / 9f) * 9);
        return Bukkit.createInventory(null, inventorySize, inventoryTitle);
    }

    public static ItemStack createGuiItem(final Material material, final String name, final ArrayList<String> lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            // Set the lore of the item
            if (lore != null) {
                ArrayList<String> components = new ArrayList<>(lore);
                meta.setLore(components);
            }

            item.setItemMeta(meta);
            return item;
        }

        return null;
    }

    public static boolean hasItem(Inventory inventory, Material material, int amount) {
        return inventory.all(material).size() >= amount;
    }

    /**
     * Determines if the target inventory is capable of receiving the amount of the item.
     * Does this by basically creating a "dummy" inventory that will be garbage collected when finished.
     * @param inventory - The inventory to check the space on
     * @param material - The material to verify
     * @param amount - The amount of the material to verify
     * @return if the inventory can hold the given amount.
     */
    public static boolean isCapableOfReceiving(Inventory inventory, Material material, int amount) {
        if (inventory.getHolder() instanceof Player) {
            Inventory dumbInventory = Bukkit.createInventory(null, 36);
            for (ItemStack itemStack : inventory.getContents()) {
                if (itemStack != null) {
                    dumbInventory.addItem(itemStack);
                }
            }
            // Attempt to add the amount of item to the inventory
            int beforeReceivingMaterialCount = Arrays.stream(dumbInventory.getContents())
                    .filter(Objects::nonNull)
                    .filter(stack -> stack.getType().equals(material)).mapToInt(ItemStack::getAmount).sum();
            dumbInventory.addItem(new ItemStack(material, amount));
            int afterReceivingMaterialCount = Arrays.stream(dumbInventory.getContents())
                    .filter(Objects::nonNull)
                    .filter(stack -> stack.getType().equals(material)).mapToInt(ItemStack::getAmount).sum();
            return (afterReceivingMaterialCount - beforeReceivingMaterialCount) == amount;
        }
        return false;
    }

    public static void removeItems(Inventory inventory, ItemStack item, int toRemove) {
        Preconditions.checkNotNull(inventory);
        Preconditions.checkNotNull(item);
        Preconditions.checkArgument(toRemove > 0);
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack loopItem = inventory.getItem(i);
            if (loopItem == null || !item.isSimilar(loopItem)) {
                continue;
            }
            if (toRemove <= 0) {
                return;
            }
            if (toRemove < loopItem.getAmount()) {
                loopItem.setAmount(loopItem.getAmount() - toRemove);
                return;
            }
            inventory.clear(i);
            toRemove -= loopItem.getAmount();
        }
    }

}
