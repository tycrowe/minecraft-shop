package com.tycrowe.dz.series.shop.components.shops;

import com.tycrowe.dz.series.shop.DzShopPlugin;
import com.tycrowe.dz.series.shop.components.shops.exceptions.InvalidTransactionException;
import com.tycrowe.dz.series.shop.components.shops.parts.Transaction;
import com.tycrowe.dz.series.shop.components.util.InventoryUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.*;
import java.util.stream.Collectors;

public class PlayerShop implements EventListener {

    private final PlayerDepartment playerDepartment;
    private String shopName;
    private Set<String> shopTags;
    private String shopUUID;
    private Inventory shopInventory;
    private Location location;
    private ArrayList<Transaction> transactions;
    private boolean isAdminShop;
    private boolean isActive = false;

    public PlayerShop(PlayerDepartment playerDepartment) {
        this.playerDepartment = playerDepartment;
        this.transactions = new ArrayList<>();
    }

    public PlayerShop(PlayerDepartment playerDepartment, String shopName, String... tags) {
        this.playerDepartment = playerDepartment;
        this.transactions = new ArrayList<>();
        this.shopName = shopName;
        this.shopTags = new HashSet<>(Arrays.asList(tags));
        this.shopUUID = UUID.randomUUID().toString();
    }

    public void addTransaction(Material buyingMaterial, int buyingAmount, Material sellingMaterial, int sellingAmount) throws InventoryUtil.InventoryTooLargeException {
        Transaction transaction = new Transaction(buyingMaterial, buyingAmount, sellingMaterial, sellingAmount);
        addTransaction(transaction);
    }

    public void addTransaction(Transaction transaction) throws InventoryUtil.InventoryTooLargeException {
        transactions.add(transaction);
        // Rebuild shop with every new transaction, this ensures we're always expanding! TODO: Paging?
        rebuildShop(transactions.size());
    }

    public Inventory rebuildShop(int inventorySize) throws InventoryUtil.InventoryTooLargeException {
        if (inventorySize <= 57) {
            Inventory shopInventory = InventoryUtil.buildInventory(inventorySize, shopName);

            shopInventory.clear();
            isActive = true;
            for (Transaction transaction : transactions) {
                transaction.addAsGuiItem(shopInventory);
            }
            return shopInventory;
        } else {
            throw new InventoryUtil.InventoryTooLargeException("Unable to build inventory for shop, size exceeds maximum capacity allowed.");
        }
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public Set<String> getShopTags() {
        return shopTags;
    }

    public void setShopTags(Set<String> shopTags) {
        this.shopTags = shopTags;
    }

    public Inventory getShopInventory() {
        return shopInventory;
    }

    public void setShopInventory(Inventory shopInventory) {
        this.shopInventory = shopInventory;
    }

    public ArrayList<Transaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(ArrayList<Transaction> transactions) {
        this.transactions = transactions;
    }

    public boolean isAdminShop() {
        return isAdminShop;
    }

    public void setAdminShop(boolean adminShop) {
        isAdminShop = adminShop;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getShopUUID() {
        return shopUUID;
    }

    public void setShopUUID(String shopUUID) {
        this.shopUUID = shopUUID;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public PlayerDepartment getPlayerDepartment() {
        return playerDepartment;
    }

    public Transaction getTransactionById(String transactionUUID) {
        Optional<Transaction> promise = transactions
                .stream()
                .filter(transaction -> transaction.getTransactionUUID().equalsIgnoreCase(transactionUUID))
                .findFirst();
        return promise.orElse(null);
    }

    public List<String> getTransactionsUUIDs(String startsWith) {
        if (!startsWith.isEmpty()) {
            return transactions.stream()
                    .map(Transaction::getTransactionUUID)
                    .filter(transactionUUID -> transactionUUID.startsWith(startsWith))
                    .collect(Collectors.toList());
        } else {
            return transactions.stream()
                    .map(Transaction::getTransactionUUID)
                    .collect(Collectors.toList());
        }
    }

    public void save() {
        YamlConfiguration config = playerDepartment.getConfig();
        config.set("shops.%s.name".formatted(shopName), shopName);
        config.set("shops.%s.uuid".formatted(shopName), getShopUUID());
        config.set("shops.%s.location".formatted(shopName), getLocation());
        // Iterate through each transaction of the store, and serialize it.
        DzShopPlugin.publishMessage("Now saving %s transactions for %s".formatted(getTransactions().size(), shopName));
        getTransactions().forEach(transaction -> {
            config.set(
                    "shops.%s.transactions.%s.obj".formatted(shopName, transaction.getTransactionUUID()),
                    transaction.serialize()
            );
            config.set(
                    "shops.%s.transactions.%s.stock".formatted(shopName, transaction.getTransactionUUID()),
                    transaction.getStock()
            );
            config.set(
                    "shops.%s.transactions.%s.income".formatted(shopName, transaction.getTransactionUUID()),
                    transaction.getIncome()
            );
        });
        playerDepartment.saveFile();
    }

    public String getTransactionInfo(String transactionId) {
        Transaction transaction = getTransactionById(transactionId);
        return "--&f[&a%s &fStatus]--\nStock: &d%s\n&fIncome: &d%s".formatted(shopName, transaction.getStock(), transaction.getIncome());
    }

    public void addStock(Inventory inventory, String transactionId, int amount, boolean all) {
        InventoryHolder inventoryOwner = inventory.getHolder();
        Transaction transaction = getTransactionById(transactionId);
        if (transaction != null && inventoryOwner instanceof Player player) {
            int added = transaction.addStockFromInventory(inventory, amount, all);
            save();
            DzShopPlugin.publishMessage(
                    player,
                    "Deposited %s into the stock for transaction: %s - current stock: %s".formatted(
                            added, transaction.getTransactionUUID(), transaction.getStock()
                    )
            );
        }
    }

    public void removeStock(Inventory inventory, String transactionId, int amount, boolean all) {
        InventoryHolder inventoryOwner = inventory.getHolder();
        Transaction transaction = getTransactionById(transactionId);
        if (transaction != null && inventoryOwner instanceof Player player) {
            int removed = transaction.removeStockToInventory(inventory, amount, all);
            save();
            DzShopPlugin.publishMessage(
                    player,
                    "Withdrew %s from the stock of transaction: %s - leftover stock: %s".formatted(
                            removed, transaction.getTransactionUUID(), transaction.getStock()
                    )
            );
        }
    }

    public void removeIncome(Inventory inventory, String transactionId, int amount, boolean all) {
        InventoryHolder inventoryOwner = inventory.getHolder();
        Transaction transaction = getTransactionById(transactionId);
        if (transaction != null && inventoryOwner instanceof Player player) {
            int removed = transaction.removeIncomeFromInventory(inventory, amount, all);
            save();
            DzShopPlugin.publishMessage(
                    player,
                    "Withdrew %s from the income of transaction: %s - leftover income: %s".formatted(
                            removed, transaction.getTransactionUUID(), transaction.getIncome()
                    )
            );
        }
    }

    public void purchase(Player customer, String transactionId) {
        Transaction transaction = getTransactionById(transactionId);
        if (transaction != null && customer != null) {
            try {
                int buyingAmount = transaction.purchase(customer);
                DzShopPlugin.publishMessage(
                        customer,
                        "&fBought &a%s %s&f for &a%s %s&f!".formatted(
                                buyingAmount, transaction.getBuyingMaterial(),
                                transaction.getSellingAmount(), transaction.getSellingMaterial()
                        )
                );

            } catch (InvalidTransactionException ex) {
                DzShopPlugin.publishMessage(
                        customer,
                        "%s".formatted(ex.getMessage())
                );
            }
        }
    }

    public static PlayerShop load(PlayerDepartment playerDepartment, String shopName, ConfigurationSection section) throws InventoryUtil.InventoryTooLargeException {
        ArrayList<Transaction> transactions = new ArrayList<>();
        PlayerShop playerShop = new PlayerShop(playerDepartment);
        playerShop.setShopName(section.getString("%s.name".formatted(shopName)));
        playerShop.setShopUUID(section.getString("%s.uuid".formatted(shopName)));
        playerShop.setLocation(section.getLocation("%s.location".formatted(shopName)));
        playerShop.rebuildShop(9);
        ConfigurationSection transSection = section.getConfigurationSection("%s.transactions".formatted(shopName));
        if (transSection != null) {
            DzShopPlugin.publishMessage("Transactions found for %s!".formatted(shopName));
            Set<String> transactionIDs = transSection.getKeys(false);
            for (String transactionID : transactionIDs) {
                String transactionString = transSection.getString("%s.obj".formatted(transactionID));
                DzShopPlugin.publishMessage("Loading transaction [%s] for %s.".formatted(transactionID, playerShop.getShopName()));
                if (transactionString != null) {
                    Transaction transaction = Transaction.deserialize(transactionString);
                    transaction.setStock(transSection.getInt("%s.stock".formatted(transactionID)));
                    transaction.setIncome(transSection.getInt("%s.income".formatted(transactionID)));
                    transaction.setDefaultLore();
                    transactions.add(transaction);
                }
            }
        }

        // Load each transaction from memory.
        for (Transaction transaction : transactions) {
            playerShop.addTransaction(transaction);
        }

        return playerShop;
    }

}
