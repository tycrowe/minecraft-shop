package com.tycrowe.dz.series.shop.components.shops.parts;

import com.tycrowe.dz.series.shop.components.shops.exceptions.InvalidTransactionException;
import com.tycrowe.dz.series.shop.components.util.InventoryUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class Transaction {
    // Uniquely generated string
    private String transactionUUID;

    // What the buyer is purchasing
    private Material buyingMaterial;
    private int buyingAmount;

    // What the seller is attempting get (shop owner = seller)
    private Material sellingMaterial;
    private int sellingAmount;

    // The stock of the item being sold for what's being bought.
    private int stock = 0;

    // The income amount of the sold units.
    private int income = 0;

    // Gui item text
    private ArrayList<String> lore;

    public Transaction() {
        this.lore = new ArrayList<>();
    }

    public Transaction(Material buyingMaterial, int buyingAmount, Material sellingMaterial, int sellingAmount) {
        this.buyingMaterial = buyingMaterial;
        this.buyingAmount = buyingAmount;
        this.sellingMaterial = sellingMaterial;
        this.sellingAmount = sellingAmount;
        this.lore = new ArrayList<>();
        // Now, set the transaction string to something easy to remember.
        this.transactionUUID = "%s-%s-[%s]".formatted(buyingMaterial.name(), sellingMaterial.name(), UUID.randomUUID().toString().substring(0, 3));
        setDefaultLore();
    }

    private String getBuyString() {
        return "§fBuying §6%s §a%s §ffor §6%s §a%s".formatted(buyingAmount, buyingMaterial.name(), sellingAmount, sellingMaterial.name());
    }

    public void addAsGuiItem(Inventory inventory) {
        inventory.addItem(InventoryUtil.createGuiItem(buyingMaterial, getBuyString(), lore));
    }

    public String getTransactionUUID() {
        return transactionUUID;
    }

    public void setTransactionUUID(String transactionUUID) {
        this.transactionUUID = transactionUUID;
    }

    public Material getBuyingMaterial() {
        return buyingMaterial;
    }

    public void setBuyingMaterial(Material buyingMaterial) {
        this.buyingMaterial = buyingMaterial;
    }

    public int getBuyingAmount() {
        return buyingAmount;
    }

    public void setBuyingAmount(int buyingAmount) {
        this.buyingAmount = buyingAmount;
    }

    public Material getSellingMaterial() {
        return sellingMaterial;
    }

    public void setSellingMaterial(Material sellingMaterial) {
        this.sellingMaterial = sellingMaterial;
    }

    public int getSellingAmount() {
        return sellingAmount;
    }

    public void setSellingAmount(int sellingAmount) {
        this.sellingAmount = sellingAmount;
    }

    public ArrayList<String> getLore() {
        return lore;
    }

    public void setLore(ArrayList<String> lore) {
        this.lore = lore;
    }

    public void setDefaultLore() {
        lore.add("§aClick §fto confirm purchase.");
        lore.add(transactionUUID);
    }

    public static Transaction deserialize(String transactionString) {
        Transaction transaction = new Transaction();
        String[] fields = transactionString.split(";");
        for (String field : fields) {
            String[] property = field.split("=");
            switch (property[0]) {
                case "transactionuuid" -> transaction.setTransactionUUID(property[1]);
                case "buyingmaterial" -> transaction.setBuyingMaterial(Material.valueOf(property[1]));
                case "buyingamount" -> transaction.setBuyingAmount(Integer.parseInt(property[1]));
                case "sellingmaterial" -> transaction.setSellingMaterial(Material.valueOf(property[1]));
                case "sellingamount" -> transaction.setSellingAmount(Integer.parseInt(property[1]));
            }
        }
        return transaction;
    }

    public String serialize() {
        return serialize(this);
    }

    public static String serialize(Transaction transaction) {
        return "transactionuuid=" + transaction.getTransactionUUID() + ';' +
                "buyingmaterial=" + transaction.getBuyingMaterial().name() + ';' +
                "buyingamount=" + transaction.getBuyingAmount() + ';' +
                "sellingmaterial=" + transaction.getSellingMaterial().name() + ';' +
                "sellingamount=" + transaction.getSellingAmount() + ';';
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public int addStock(int stock) {
        return this.stock += stock;
    }

    public int removeStock(int stock) {
        return this.stock -= stock;
    }

    public int purchase(Player customer) throws InvalidTransactionException {
        // Calculate how much of the buying material in the buyers inventory.
        int buyingMaterialCount = Arrays.stream(customer.getInventory().getContents())
                .filter(Objects::nonNull)
                .filter(stack -> stack.getType().equals(buyingMaterial)).mapToInt(ItemStack::getAmount).sum();

        // Does the shop have stock for the transaction?
        if (getStock() <= sellingAmount) {
            throw new InvalidTransactionException("&fShop is currently out of stock of item &a%s.".formatted(sellingMaterial.name()));
        }

        if (buyingMaterialCount < buyingAmount) {
            throw new InvalidTransactionException("&fCannot afford! Amount of &a%s&f in inventory: &a%s&f; amount needed: &a%s&f.".formatted(buyingMaterial.name(), buyingMaterialCount, getBuyingAmount()));
        }

        if (InventoryUtil.isCapableOfReceiving(customer.getInventory(), sellingMaterial, sellingAmount)) {
            // Clear the customer's inventory of the buying material
            for (ItemStack itemStack : customer.getInventory()) {
                if (itemStack != null && itemStack.isSimilar(new ItemStack(buyingMaterial, buyingAmount))) {
                    itemStack.setAmount(0);
                }
            }
            // Remove the set number of items from the player's inventory
            customer.getInventory().addItem(new ItemStack(sellingMaterial, sellingAmount));
            addIncome(buyingAmount);
            removeStock(sellingAmount);
            // Drop the amount of the item at the player's feet
            if ((buyingMaterialCount - buyingAmount) > 0 && buyingMaterialCount > 0) {
                customer.getWorld().dropItem(
                        customer.getLocation(),
                        new ItemStack(buyingMaterial, buyingMaterialCount - buyingAmount)
                );
            }
        }

        return buyingAmount;
    }

    public int addStockFromInventory(Inventory inventory, int depositAmount, boolean all) {
        int sellingMaterialCount = Arrays.stream(inventory.getContents())
                .filter(Objects::nonNull)
                .filter(stack -> stack.getType().equals(sellingMaterial)).mapToInt(ItemStack::getAmount).sum();
        if (all) {
            inventory.removeItem(new ItemStack(sellingMaterial, sellingMaterialCount));
            addStock(sellingMaterialCount);
            return sellingMaterialCount;
        } else if (sellingMaterialCount >= depositAmount) {
            inventory.removeItem(new ItemStack(sellingMaterial, depositAmount));
            addStock(depositAmount);
            return depositAmount;
        } else {
            return addStockFromInventory(inventory, depositAmount, true);
        }
    }

    public int removeStockToInventory(Inventory inventory, int withdrawalAmount, boolean all) {
        if (all) {
            inventory.addItem(new ItemStack(sellingMaterial, getStock()));
            int givenAmount = getStock();
            removeStock(givenAmount);
            return givenAmount;
        } else {
            if (getStock() >= withdrawalAmount) {
                inventory.addItem(new ItemStack(sellingMaterial, withdrawalAmount));
                removeStock(withdrawalAmount);
                return withdrawalAmount;
            } else {
                return removeStockToInventory(inventory, withdrawalAmount, true);
            }
        }
    }

    public int removeIncomeFromInventory(Inventory inventory, int withdrawalAmount, boolean all) {
        if (all) {
            inventory.addItem(new ItemStack(buyingMaterial, getIncome()));
            int givenAmount = getIncome();
            removeIncome(givenAmount);
            return givenAmount;
        } else {
            if (getIncome() >= withdrawalAmount) {
                //inventory.addItem(new ItemStack(buyingMaterial, withdrawalAmount));
                Player player = (Player) inventory.getHolder();
                if (player != null) {
                    player.getWorld().dropItem(player.getLocation(), new ItemStack(buyingMaterial, withdrawalAmount));
                    removeIncome(withdrawalAmount);
                    return withdrawalAmount;
                }
                return 0;
            } else {
                return removeIncomeFromInventory(inventory, withdrawalAmount, true);
            }
        }
    }

    public int getIncome() {
        return income;
    }

    public void setIncome(int income) {
        this.income = income;
    }

    public void addIncome(int income) {
        this.income += income;
    }

    public int removeIncome(int income) {
        return this.income -= income;
    }

}
