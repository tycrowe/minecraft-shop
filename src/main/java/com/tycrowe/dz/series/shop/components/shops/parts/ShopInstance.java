package com.tycrowe.dz.series.shop.components.shops.parts;

import com.tycrowe.dz.series.shop.components.shops.PlayerDepartment;
import com.tycrowe.dz.series.shop.components.shops.PlayerShop;
import com.tycrowe.dz.series.shop.components.util.InventoryUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class ShopInstance {

    private final Player customer;
    private final PlayerDepartment playerDepartment;
    private final PlayerShop playerShop;
    private final Inventory shopInventory;
    private boolean isOpen = false;

    public ShopInstance(Player customer, PlayerDepartment playerDepartment, PlayerShop playerShop) throws InventoryUtil.InventoryTooLargeException {
        this.customer = customer;
        this.playerDepartment = playerDepartment;
        this.playerShop = playerShop;
        this.shopInventory = playerShop.rebuildShop(playerShop.getTransactions().size() - 1);
    }

    public Player getCustomer() {
        return customer;
    }

    public PlayerDepartment getPlayerDepartment() {
        return playerDepartment;
    }

    public PlayerShop getPlayerShop() {
        return playerShop;
    }

    public Inventory getShopInventory() {
        return shopInventory;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        isOpen = open;
    }

    public void openInventory() {
        if (!isOpen && shopInventory != null) {
            customer.openInventory(shopInventory);
            isOpen = true;
        }
    }

    public void closeInventory() {
        if (isOpen && shopInventory != null) {
            shopInventory.clear(); // Dispose?
            playerShop.save();
            isOpen = false;
        }
    }
}
