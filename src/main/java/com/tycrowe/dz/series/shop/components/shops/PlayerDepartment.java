package com.tycrowe.dz.series.shop.components.shops;

import com.tycrowe.dz.series.shop.DzShopPlugin;
import com.tycrowe.dz.series.shop.components.util.InventoryUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerDepartment {

    private final DzShopPlugin plugin;
    private final String departmentUUID;
    private final UUID playerUUID;
    private final File configFile;
    private final YamlConfiguration config;
    private final HashMap<String, PlayerShop> playerOwnedShops;

    private Inventory departmentMenu;

    private class DepartmentStoreMenuItem {
        private String shopName;
        private PlayerShop shop;
        private ArrayList<String> lore;

        public DepartmentStoreMenuItem(String shopName, PlayerShop shop, ArrayList<String> lore) {
            this.shopName = shopName;
            this.shop = shop;
            this.lore = lore;
        }

        public void addAsGuiItem() {
            departmentMenu.addItem(InventoryUtil.createGuiItem(Material.CHEST, shopName, lore));
        }

        public String getShopName() {
            return shopName;
        }

        public PlayerShop getShop() {
            return shop;
        }

        public ArrayList<String> getLore() {
            return lore;
        }
    }

    public PlayerDepartment(DzShopPlugin plugin, String departmentUUID, UUID playerUUID) {
        this.plugin = plugin;
        this.departmentUUID = departmentUUID;
        this.playerUUID = playerUUID;
        this.configFile = new File("%s%s/%s.yml".formatted(plugin.getDataFolder(), DzShopPlugin.playerPath, playerUUID));
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.playerOwnedShops = new HashMap<>();
    }

    public PlayerShop addShopToDepartment(PlayerShop shop) {
        if (!playerOwnedShops.containsKey(shop.getShopName())) {
            playerOwnedShops.put(shop.getShopName(), shop);
            this.departmentMenu = InventoryUtil.buildInventory(playerOwnedShops.size(), departmentUUID);
            // Add the gui item for this shop
            DepartmentStoreMenuItem item = new DepartmentStoreMenuItem(shop.getShopName(), shop, null);
            item.addAsGuiItem();
            save();
        }
        // Return the created shop.
        return playerOwnedShops.get(shop.getShopName());
    }

    /**
     * Saves the player's department to their configuration.
     */
    public void save() {
        if (playerOwnedShops.size() > 0) {
            DzShopPlugin.publishMessage("Saving %s's department.".formatted(playerUUID.toString()));
            // Iterate through each store, and serialize each.
            playerOwnedShops.forEach((shopName, playerShop) -> playerShop.save());
            saveFile();
        }
    }

    /**
     * Loads the player's department from the save file.
     */
    public void load() throws InventoryUtil.InventoryTooLargeException {
        DzShopPlugin.publishMessage("Loading %s's department.".formatted(playerUUID.toString()));
        ConfigurationSection shopSection = config.getConfigurationSection("shops");
        if (shopSection != null) {
            Set<String> shops = shopSection.getKeys(false);
            for (String shop : shops) {
                // Load the shop itself
                PlayerShop playerShop = PlayerShop.load(
                        this, shop, shopSection
                );
                //
                addShopToDepartment(playerShop);
                DzShopPlugin.publishMessage("Successfully added %s to %s's department.".formatted(shop, playerUUID.toString()));
            }
        }
    }

    /**
     * Simply prints out all key's values to the console. Meant for debugging only.
     */
    public void cat() {
        config.getKeys(true).forEach(k -> DzShopPlugin.publishMessage(k));
    }

    /**
     * Creates a new file for the player's department. Departments contain all shops for the player.
     * @return - The object itself (this) if the file was successfully created, else, null.
     */
    public boolean createFile() {
        try {
            if (configFile.createNewFile()) {
                DzShopPlugin.publishMessage("Created %s's department file.".formatted(playerUUID.toString()));
            }
            return saveFile();
        } catch (IOException e) {
            DzShopPlugin.publishMessage("Failed to create %s's department file.".formatted(playerUUID.toString()));
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Saves the department as a YAML configuration file. In the future, it may be worth trying to make this work
     * with something like a NoSQL solution. Until that need arises from my three users, this should be fine.
     * @return - if the saving of the file was successful.
     */
    public boolean saveFile() {
        try {
            config.save(configFile);
            return true;
        } catch (IOException e) {
            DzShopPlugin.publishMessage("Failed to save %s's department.".formatted(playerUUID.toString()));
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Gets the instance of the plugin, the spigot plugin.
     * @return instance of the plugin
     */
    public DzShopPlugin getPlugin() {
        return plugin;
    }

    /**
     * Gets the player associated to this department.
     * @return the player associated to this department.
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    /**
     * Gets the configuration file associated to this department, the actual file object.
     * @return the file object.
     */
    public File getConfigFile() {
        return configFile;
    }

    /**
     * Gets the YamlConfiguration instance of the above file.
     * @return the spigot/bukket YAML configuration.
     */
    public YamlConfiguration getConfig() {
        return config;
    }

    /**
     * Fetches all the player owned shops that belong to this player's department.
     * @return the hashmap of all player shops.
     */
    public HashMap<String, PlayerShop> getPlayerOwnedShops() {
        return playerOwnedShops;
    }

    /**
     * Get the inventory that represents the departments' menu.
     * @return the department menu represented as a Minecraft inventory.
     */
    public Inventory getDepartmentMenu() {
        return departmentMenu;
    }

    public String getDepartmentUUID() {
        return departmentUUID;
    }

    public boolean isShop(String name) {
        return playerOwnedShops.containsKey(name);
    }

    public List<String> getShopNames() {
        return playerOwnedShops.keySet().stream().toList();
    }
}
