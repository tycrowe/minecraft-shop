package com.tycrowe.dz.series.shop.components;

import com.tycrowe.dz.series.shop.DzShopPlugin;
import com.tycrowe.dz.series.shop.components.shops.PlayerDepartment;
import com.tycrowe.dz.series.shop.components.shops.PlayerShop;
import com.tycrowe.dz.series.shop.components.shops.parts.ShopInstance;
import com.tycrowe.dz.series.shop.components.util.InventoryUtil;
import org.bukkit.*;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class DepartmentManager implements CommandExecutor, Listener, TabCompleter {

    private final DzShopPlugin plugin;
    private final HashMap<String, ArrayList<Sign>> departmentSigns;
    private final HashMap<String, PlayerDepartment> departments;
    private final HashMap<UUID, String> departmentPlayer;
    private final HashMap<UUID, ShopInstance> instances;

    // Command useful
    private final List<String> materials = Arrays.stream(Material.values()).map(Material::name).toList();

    public DepartmentManager(DzShopPlugin plugin) {
        this.plugin = plugin;
        this.departments = new HashMap<>();
        this.departmentSigns = new HashMap<>();
        this.departmentPlayer = new HashMap<>();
        this.instances = new HashMap<>();
    }



    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (commandSender instanceof Player sender) {
            switch (s.toLowerCase(Locale.ROOT)) {
                case "shop" -> {
                    String shopName = strings[0];
                    PlayerShop playerShop = getShop(sender.getUniqueId(), shopName);
                    if (playerShop != null) {
                        String action = strings[1].toLowerCase(Locale.ROOT);
                        switch (action) {
                            case "buy" -> {
                                if (strings.length > 7) return false;
                                int buyingAmount, sellingAmount;
                                Material buyingMaterial, sellingMaterial;
                                try {
                                    buyingAmount = Integer.parseInt(strings[2]);
                                    sellingAmount = Integer.parseInt(strings[5]);
                                } catch (NumberFormatException ex) {
                                    DzShopPlugin.publishMessage(commandSender, "Failed to parse buying/selling amounts, make sure they're integers!");
                                    return false;
                                }
                                try {
                                    buyingMaterial = Material.matchMaterial(strings[3].toUpperCase(Locale.ROOT));
                                    sellingMaterial = Material.matchMaterial(strings[6].toUpperCase(Locale.ROOT));
                                } catch (IllegalArgumentException ex) {
                                    DzShopPlugin.publishMessage(commandSender, "Failed to parse buying/selling materials, make sure they're in proper format!");
                                    return false;
                                }

                                // Add the transaction
                                try {
                                    playerShop.addTransaction(
                                            buyingMaterial,
                                            buyingAmount,
                                            sellingMaterial,
                                            sellingAmount
                                    );
                                } catch (InventoryUtil.InventoryTooLargeException e) {
                                    e.printStackTrace();
                                    DzShopPlugin.publishMessage(commandSender, "&cUnable to rebuild shop, too many transactions?");
                                    return false;
                                }
                                DzShopPlugin.publishMessage(commandSender, "&rShop [&c%s&r] is now buying &a%s&r &d%s&r for &a%s&r &d%s&r!"
                                        .formatted(shopName, buyingAmount, buyingMaterial.name(), sellingAmount, sellingMaterial.name())
                                );
                                playerShop.getPlayerDepartment().save();

                                return true;

                            }
                            case "stock", "income" -> {
                                // Manages the stock of transactions in the shop's catalog
                                //shop <uuid|name> stock <transactionUUID> <status|s|deposit|d|withdrawal|w> <*|#>
                                // Grab the fields
                                if (strings.length >= 4) {
                                    String stockOrIncome = strings[1];
                                    String transactionId, stockOption;
                                    transactionId = strings[2];
                                    stockOption = strings[3];
                                    // Fetch the transaction
                                    if (!transactionId.isEmpty()) {
                                        switch (stockOption.toLowerCase(Locale.ROOT)) {
                                            case "status", "s" -> {
                                                DzShopPlugin.publishMessage(
                                                        commandSender,
                                                        playerShop.getTransactionInfo(transactionId)
                                                );
                                                return true;
                                            }
                                            case "deposit", "d" -> {
                                                if (stockOrIncome.equals("stock")) {
                                                    playerShop.addStock(
                                                            sender.getInventory(),
                                                            transactionId,
                                                            Integer.parseInt(strings[4]),
                                                            false
                                                    );
                                                }
                                                return true;
                                            }
                                            case "withdrawal", "w" -> {
                                                if (stockOrIncome.equals("stock")) {
                                                    playerShop.removeStock(
                                                            sender.getInventory(),
                                                            transactionId,
                                                            Integer.parseInt(strings[4]),
                                                            false
                                                    );
                                                } else {
                                                    playerShop.removeIncome(
                                                            sender.getInventory(),
                                                            transactionId,
                                                            Integer.parseInt(strings[4]),
                                                            false
                                                    );
                                                }
                                                return true;
                                            }
                                        }
                                    }
                                } else {
                                    DzShopPlugin.publishMessage(commandSender, "Invalid usage: /shop stock <transaction-id> <option> [#]");
                                }
                            }
                        }
                    } else {
                        DzShopPlugin.publishMessage(commandSender, "No shop found given the name: %s!".formatted(shopName));
                    }
                }
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        if (commandSender instanceof Player sender) {
            switch (s.toLowerCase(Locale.ROOT)) {
                case "shop" -> {
                    if (strings.length == 1) {
                        return departments.get(departmentPlayer.get(sender.getUniqueId())).getShopNames();
                    } else if (strings.length == 2) {
                        return List.of("buy", "stock", "income");
                    } else {
                        PlayerShop playerShop = departments
                                .get(departmentPlayer.get(sender.getUniqueId()))
                                .getPlayerOwnedShops()
                                .get(strings[0]);
                        if (playerShop != null) {
                            // buy, stock, list, etc.
                            switch (strings[1].toLowerCase(Locale.ROOT)) {
                                case "buy" -> {
                                    //shop <uuid|name> buy <number> <item> for <number> <item> <catalog-id>
                                    if (strings.length == 4) {
                                        String partialSearch = strings[3].toUpperCase();
                                        return materials.stream().filter(str -> str.startsWith(partialSearch)).toList();
                                    } else if (strings.length == 5) {
                                        return List.of("for");
                                    } else if (strings.length == 7) {
                                        String partialSearch = strings[6].toUpperCase();
                                        return materials.stream().filter(str -> str.startsWith(partialSearch)).toList();
                                    } else {
                                        return List.of();
                                    }
                                }
                                case "stock", "income" -> {
                                    //shop <uuid|name> stock <transactionUUID> <status|s|deposit|d|withdrawal|w>
                                    if (strings.length == 3) {
                                        return playerShop.getTransactionsUUIDs(strings[2]);
                                    } else if (strings.length == 4 && strings[1].equalsIgnoreCase("stock")) {
                                        return List.of("status", "s", "deposit", "d", "withdrawal", "w");
                                    } else if (strings.length == 4 && strings[1].equalsIgnoreCase("income")) {
                                        return List.of("status", "s", "withdrawal", "w");
                                    } else if (strings.length == 5) {
                                        String subAction = strings[4]; // status, deposit or withdrawal
                                        switch (subAction) {
                                            case "deposit", "d", "withdrawal", "w" -> {
                                                return List.of("*");
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            return List.of("Unable to find shop by name of: %s".formatted(strings[0]));
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Helper method to shorten a player name down to something acceptable for a sign.
     *
     * @param player -   The player object to fetch the name from.
     * @return -   A string that can fit on a Minecraft sign.
     */
    private String getShortPlayerName(Player player) {
        if (player.getName().length() > 15) {
            return player.getName().substring(0, 15);
        }
        return player.getName();
    }

    public void loadDepartments() {
        ConfigurationSection departmentSection = plugin.getConfig().getConfigurationSection("directory");
        if (departmentSection != null) {
            Set<String> keys = departmentSection.getKeys(false);
            for (String key : keys) {
                try {
                    String uuidString = departmentSection.getString("%s.player.uuid".formatted(key));
                    String nameString = departmentSection.getString("%s.player.name".formatted(key));
                    boolean isActive = departmentSection.getBoolean("%s.active".formatted(key));
                    if (isActive) {
                        if (uuidString != null) {
                            PlayerDepartment pd = new PlayerDepartment(
                                    plugin,
                                    key,
                                    UUID.fromString(uuidString)
                            );
                            // Loads, builds and initializes all the shops associated with hte department.
                            pd.load();

                            // Update our memory references
                            departmentPlayer.put(UUID.fromString(uuidString), pd.getDepartmentUUID());
                            departments.put(pd.getDepartmentUUID(), pd);

                            // Load the signs
                            departmentSigns.put(pd.getDepartmentUUID(), new ArrayList<>());
                            loadSignShopsOfDepartment(pd.getDepartmentUUID());

                            DzShopPlugin.publishMessage("Loaded department %s for player: %s".formatted(pd.getDepartmentUUID(), nameString));
                        }
                    } else {
                        DzShopPlugin.publishMessage("Department disabled for player %s, skipping.".formatted(nameString));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private PlayerDepartment createDepartment(UUID playerUUID, final String departmentId) {
        if (!departmentPlayer.containsKey(playerUUID) && !departments.containsKey(departmentId)) {
            Player player = plugin.getServer().getPlayer(playerUUID);
            if (player != null) {
                DzShopPlugin.publishMessage("Department for %s not in memory, creating...".formatted(player.getName()));
                PlayerDepartment pd = new PlayerDepartment(plugin, UUID.randomUUID().toString().substring(0, 10), playerUUID);
                // Attempt to create the departments (player's) configuration.
                if (pd.createFile()) {
                    // Now, save a list of this department to the directory. This will be used to load departments.
                    plugin.getConfig().set("directory.%s.player.uuid".formatted(pd.getDepartmentUUID()), player.getUniqueId().toString());
                    plugin.getConfig().set("directory.%s.player.name".formatted(pd.getDepartmentUUID()), player.getName());
                    plugin.getConfig().set("directory.%s.shop_count".formatted(pd.getDepartmentUUID()), 0); // will change later.
                    plugin.getConfig().set("directory.%s.known_signs".formatted(pd.getDepartmentUUID()), new ArrayList<Map<String, Object>>());
                    plugin.getConfig().set("directory.%s.active".formatted(pd.getDepartmentUUID()), true);
                    plugin.saveConfig();
                } else {
                    return null;
                }

                // Update our memory references
                departmentPlayer.put(playerUUID, pd.getDepartmentUUID());
                departments.put(pd.getDepartmentUUID(), pd);
                departmentSigns.put(pd.getDepartmentUUID(), new ArrayList<>());

                return pd;
            }
        }
        return null;
    }

    public PlayerDepartment getDepartment(UUID playerUUID, String departmentId) {
        // Attempt to find by player uuid first
        if (playerUUID != null && departmentPlayer.containsKey(playerUUID)) {
            return departments.get(departmentPlayer.get(playerUUID));
        } else if (departmentId != null && departments.containsKey(departmentId)) {
            return departments.get(departmentId);
        } else {
            return createDepartment(playerUUID, departmentId);
        }
    }

    private void addShopToDepartmentDirectory(final String departmentId) {
        // Get the amount of shops
        int shopsCount = plugin.getConfig().getInt("directory.%s.shop_count".formatted(departmentId));
        plugin.getConfig().set("directory.%s.shop_count".formatted(departmentId), shopsCount + 1);
        plugin.saveConfig();
    }

    /**
     * Adds the location of a shop's sign to the main listing. If the sign is destroyed, then it will be removed as a store. [At that location]
     *
     * @param departmentId the department id that the sign is being created for.
     * @param sign         the sign object itself.
     */
    private void addSignShopToDepartmentDirectory(final String departmentId, final Sign sign) {
        List<Map<String, Object>> locations = new ArrayList<>();
        // Update the memory.
        ArrayList<Sign> signs = departmentSigns.get(departmentId);
        signs.add(sign);
        saveSignShopToDepartmentDirectory(departmentId, locations, signs);
    }

    private void removeSignShopToDepartmentDirectory(final String departmentId, final Sign sign) {
        List<Map<String, Object>> locations = new ArrayList<>();
        // Update the memory.
        ArrayList<Sign> signs = departmentSigns.get(departmentId);
        signs.remove(sign);
        saveSignShopToDepartmentDirectory(departmentId, locations, signs);
    }

    private void saveSignShopToDepartmentDirectory(final String departmentId, final List<Map<String, Object>> locations, final ArrayList<Sign> signs) {
        departmentSigns.put(departmentId, signs);
        for (Sign sign1 : signs) {
            locations.add(sign1.getLocation().serialize());
        }

        plugin.getConfig().set("directory.%s.known_signs".formatted(departmentId), new ArrayList<>());
        plugin.saveConfig();
        plugin.getConfig().set("directory.%s.known_signs".formatted(departmentId), locations);
        plugin.saveConfig();
    }

    /**
     * Loads the signs of a given department.
     *
     * @param departmentId the department id to load the signs into memory.
     */
    @SuppressWarnings("unchecked")
    private void loadSignShopsOfDepartment(final String departmentId) {

        List<Map<String, Object>> locations = (List<Map<String, Object>>) plugin.getConfig().getList(
                "directory.%s.known_signs".formatted(departmentId)
        );

        if (locations != null) {
            ArrayList<Sign> signs = departmentSigns.get(departmentId);
            departmentSigns.put(departmentId, new ArrayList<>());
            for (Map<String, Object> serializedLocation : locations) {
                Location location = Location.deserialize(serializedLocation);
                if (location.getBlock().getType().name().contains("SIGN")) {
                    signs.add((Sign) location.getBlock().getState());
                }
            }
            departmentSigns.put(departmentId, signs);
        }
    }

    private void addShopToDepartment(Player player, SignChangeEvent sign) {
        try {
            PlayerDepartment pd = getDepartment(player.getUniqueId(), null);
            /*sign.line(
                    0,
                    Component.text("[", TextColor.color(Color.BLACK.getRed(), Color.BLACK.getGreen(), Color.BLACK.getBlue()))
                            .append(Component.text("SHOP", TextColor.color(Color.GREEN.getRed(), Color.GREEN.getGreen(), Color.GREEN.getBlue())))
                            .append(Component.text("]", TextColor.color(Color.BLACK.getRed(), Color.BLACK.getGreen(), Color.BLACK.getBlue())))
            );
            sign.line(
                    2,
                    Component.text(String.format("%s", getShortPlayerName(player)), TextColor.color(Color.PURPLE.getRed(), Color.PURPLE.getGreen(), Color.PURPLE.getBlue()))
            );
            sign.line(
                    3,
                    Component.text(String.format("%s", pd.getDepartmentUUID()), TextColor.color(Color.GREEN.getRed(), Color.GREEN.getGreen(), Color.GREEN.getBlue()))
            );*/
            sign.setLine(0, "%s[%sSHOP%s]".formatted(ChatColor.WHITE, ChatColor.GREEN, ChatColor.WHITE));
            sign.setLine(2, "%s%s".formatted(ChatColor.LIGHT_PURPLE, getShortPlayerName(player)));
            sign.setLine(3, "%s%s".formatted(ChatColor.GREEN, pd.getDepartmentUUID()));
            String cleanShopName = ChatColor.stripColor(sign.getLine(1));
            if (cleanShopName != null) {
                cleanShopName = cleanShopName.replace(" ", "-");
                sign.setLine(1, "%s%s".formatted(ChatColor.WHITE, cleanShopName));
                if (!pd.isShop(cleanShopName)) {
                    PlayerShop playerShop = new PlayerShop(pd, cleanShopName);
                    pd.addShopToDepartment(playerShop);
                    addShopToDepartmentDirectory(pd.getDepartmentUUID());
                } else {
                    DzShopPlugin.publishMessage("Shop already exists, activating this sign shop.");
                }
                addSignShopToDepartmentDirectory(pd.getDepartmentUUID(), (Sign) sign.getBlock().getState());
                DzShopPlugin.publishMessage("Successfully registered new shop for %s".formatted(player.getName()));
            }
        } catch (Exception ex) {
            DzShopPlugin.publishMessage("Failed to register new shop for %s".formatted(player.getName()));
        }
    }

    /**
     * Gets a player's shop by department id and shop name
     */
    private PlayerShop getShop(Sign sign) {
        PlayerDepartment playerDepartment;
        try {
            String shopName = ChatColor.stripColor(sign.getLine(1));
            String departmentId = ChatColor.stripColor(sign.getLine(3));
            playerDepartment = getDepartment(null, departmentId);
            if (playerDepartment != null) {
                if (playerDepartment.getPlayerOwnedShops().get(shopName) != null) {
                    return playerDepartment.getPlayerOwnedShops().get(shopName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private PlayerShop getShop(UUID playerUUID, String shopName) {
        if (departmentPlayer.containsKey(playerUUID)) {
            String departmentId = departmentPlayer.get(playerUUID);
            if (departments.containsKey(departmentId)) {
                PlayerDepartment pd = departments.get(departmentId);
                if (pd != null && pd.getPlayerOwnedShops().containsKey(shopName)) {
                    return pd.getPlayerOwnedShops().get(shopName);
                } else {
                    return null;
                }
            }
        }
        return null;
    }

    private boolean isSignShopOfDepartmentManaged(String departmentId, Sign sign) {
        return departmentSigns.get(departmentId).contains(sign);
    }

    public void closeAllInstances() {
        departmentPlayer.forEach((k, v) -> {
            Player player = Bukkit.getServer().getPlayer(k);
            if (player != null) {
                player.closeInventory();
            }
        });
        instances.clear();
    }

    /**
     * Responsible for identifying any player shops created using a sign.
     *
     * @param signChangeEvent - The sign interact event.
     */
    @EventHandler
    public void on(SignChangeEvent signChangeEvent) {
        String temp = signChangeEvent.getLine(0);
        if (temp != null) {
            if (temp.equalsIgnoreCase("shop")) {
                DzShopPlugin.publishMessage("Attempting to create shop!");
                addShopToDepartment(signChangeEvent.getPlayer(), signChangeEvent);
            }
        }
    }

    @EventHandler
    public void on(PlayerInteractEvent signInteractEvent) {
        if (signInteractEvent.getAction().equals(Action.RIGHT_CLICK_BLOCK) && signInteractEvent.hasBlock()) {
            if (signInteractEvent.getClickedBlock().getType().name().contains("SIGN")) {
                Sign sign = (Sign) signInteractEvent.getClickedBlock().getState();
                String lineOne = ChatColor.stripColor(sign.getLine(0));
                if (lineOne.contains("SHOP")) {
                    PlayerShop playerShop = getShop(sign);
                    if (playerShop != null && playerShop.isActive() && isSignShopOfDepartmentManaged(playerShop.getPlayerDepartment().getDepartmentUUID(), sign)) {
                        try {
                            ShopInstance shopInstance = new ShopInstance(
                                    signInteractEvent.getPlayer(),
                                    playerShop.getPlayerDepartment(),
                                    playerShop
                            );
                            shopInstance.openInventory();
                            instances.put(shopInstance.getCustomer().getUniqueId(), shopInstance);
                        } catch (InventoryUtil.InventoryTooLargeException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void on(BlockBreakEvent signBreakEvent) {
        if (signBreakEvent.getBlock().getType().name().contains("SIGN")) {
            Sign sign = (Sign) signBreakEvent.getBlock().getState();
            String lineOne = ChatColor.stripColor(sign.getLine(0));
            if (lineOne.contains("SHOP")) {
                PlayerShop playerShop = getShop(sign);
                if (playerShop != null) {
                    DzShopPlugin.publishMessage("Removing sign.");
                    // Drop the sign from memory.
                    removeSignShopToDepartmentDirectory(playerShop.getPlayerDepartment().getDepartmentUUID(), sign);
                }
            }
            /*List<Component> temp = sign.line(0).children();
            for (Component component : temp) {
                if (component instanceof TextComponent textTemp) {
                    if (textTemp.content().contains("SHOP")) {
                        PlayerShop playerShop = getShop(sign);
                        if (playerShop != null) {
                            DzShopPlugin.publishMessage("Removing sign.");
                            // Drop the sign from memory.
                            removeSignShopToDepartmentDirectory(playerShop.getPlayerDepartment().getDepartmentUUID(), sign);
                        }
                    }
                }
            }*/
        }
    }

    @EventHandler
    public void on(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (!instances.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
        final ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) return;
        ItemMeta lore = clickedItem.getItemMeta();
        if (lore != null) {
            List<String> loreStrings = lore.getLore();
            if (loreStrings != null) {
                String transactionId = loreStrings.get(loreStrings.size() - 1);
                if (transactionId != null && !transactionId.isEmpty()) {
                    ShopInstance instance = instances.get(p.getUniqueId());
                    if (instance != null) {
                        instance.getPlayerShop().purchase(instance.getCustomer(), transactionId);
                    }
                }
            }
        }
        /*List<Component> components = clickedItem.getItemMeta().lore();
        if (components != null) {
            if (components.get(2) instanceof TextComponent textComponent) {
                ShopInstance instance = instances.get(p.getUniqueId());
                if (instance != null) {
                    instance.getPlayerShop().purchase(instance.getCustomer(), textComponent.content());
                }
            }
        }*/
    }

    @EventHandler
    public void on(final InventoryDragEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (instances.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void on(final InventoryCloseEvent e) {
        final Player p = (Player) e.getPlayer();
        if (instances.containsKey(p.getUniqueId())) {
            instances.remove(p.getUniqueId()).closeInventory();
        }
    }

}
