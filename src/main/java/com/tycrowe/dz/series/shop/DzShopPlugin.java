package com.tycrowe.dz.series.shop;

import com.tycrowe.dz.series.shop.components.DepartmentManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

public class DzShopPlugin extends JavaPlugin implements Listener {

    public static String PLUGIN_NAME = "DzShop";
    public static String playerPath = "/players";

    private DepartmentManager departmentManager;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();

        departmentManager = new DepartmentManager(this);
        getServer().getPluginManager().registerEvents(departmentManager, this);
        Objects.requireNonNull(this.getCommand("shop")).setExecutor(departmentManager);
        publishMessage("DzShop <shop> command registered.");

        // Attempt to create the 'players' directory.
        File directory = new File(getDataFolder().getPath() + playerPath);
        if (directory.mkdirs()) {
            publishMessage("Directory successfully created!");
        }

        // Load our departments!
        departmentManager.loadDepartments();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        departmentManager.closeAllInstances();
    }

    public static void publishMessage(String message) {
        Bukkit.getServer().getConsoleSender().sendMessage(
                String.format("%s[%s%s%s]%s: %s", ChatColor.GRAY, ChatColor.GREEN, PLUGIN_NAME, ChatColor.GRAY,
                        ChatColor.GRAY, message)
        );
    }

    public static void publishMessage(CommandSender sender, String message) {
        sender.sendMessage(
                String.format("%s[%s%s%s]%s: %s", ChatColor.GRAY, ChatColor.GREEN, PLUGIN_NAME, ChatColor.GRAY,
                        ChatColor.GRAY, ChatColor.translateAlternateColorCodes('&', message))
        );
    }

}
