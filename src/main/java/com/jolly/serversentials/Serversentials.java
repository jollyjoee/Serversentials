package com.jolly.serversentials;

import com.jolly.serversentials.commands.teleports.*;
import com.jolly.serversentials.commands.utilities.*;
import com.jolly.serversentials.commands.tools.*;
import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.listeners.PlayerJoinListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class Serversentials extends JavaPlugin {
    private static MiniMessage mm;
    private static FileConfiguration config;
    private static DatabaseManager db;
    private static Scheduler scheduler;
    private static Nick nick;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        db = new DatabaseManager(this, false, "", 0, "", "", "");
        mm = MiniMessage.miniMessage();
        config = getConfig();
        scheduler = new Scheduler(this);
        TpaManager tpa = new TpaManager(this, scheduler);
        Fly fly = new Fly(scheduler, this);
        Nick nick = new Nick(scheduler, this);
        GMS gms = new GMS(this);
        GMC gmc = new GMC(this);
        GMSP gmsp = new GMSP(this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, fly, nick, tpa), this);
        if (isModuleEnabled("fly")) {
            getCommand("fly").setExecutor(fly);
            getCommand("fly").setTabCompleter(fly);
        }
        if (isModuleEnabled("gamemode")) {
            getCommand("gms").setExecutor(gms);
            getCommand("gms").setTabCompleter(gms);
            getCommand("gmsp").setExecutor(gmsp);
            getCommand("gmsp").setTabCompleter(gmsp);
            getCommand("gmc").setExecutor(gmc);
            getCommand("gmc").setTabCompleter(gmc);
        }
        if (isModuleEnabled("nick.enabled")) {
            getCommand("nick").setExecutor(nick);
        }
        if (isModuleEnabled("tpa.enabled")) {
            getCommand("tpa").setExecutor(tpa);
            getCommand("tpa").setTabCompleter(tpa);
        }
        if (isModuleEnabled("tpahere.enabled")) {
            getCommand("tpahere").setExecutor(tpa);
            getCommand("tpahere").setTabCompleter(tpa);
        }
        if (isModuleEnabled("tptoggle")) {
            getCommand("tptoggle").setExecutor(tpa);
        }
        getCommand("tpaccept").setExecutor(tpa);
        getCommand("tpdeny").setExecutor(tpa);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            scheduler.runLater(() -> {
                new Placeholder(this, nick, fly).register();
                getLogger().info("✅ Registered Serversentials placeholders with PlaceholderAPI!");
            }, 20L);
        }

        getDatabase().updateSafe("""
            CREATE TABLE IF NOT EXISTS fly_data (
                uuid TEXT PRIMARY KEY,
                flying BOOLEAN
            )
            """);
        getDatabase().updateSafe("""
            CREATE TABLE IF NOT EXISTS nick_data (
                 uuid VARCHAR(36) PRIMARY KEY,
                 nickname TEXT
            )
            """);
        getDatabase().updateSafe("""
            CREATE TABLE IF NOT EXISTS tptoggle_data (
                uuid TEXT PRIMARY KEY,
                tptoggle BOOLEAN
            )
            """);
    }

    public DatabaseManager getDatabase() {
        return db;
    }

    @Override
    public void onDisable() {
        if (db != null) {
            db.close();
        }
    }

    public boolean isModuleEnabled(String module) {
        return getConfig().getBoolean("modules." + module, true);
    }

    public static Component mm(String string) {
        return mm.deserialize(string);
    }

    public static String prefixMessage(String path) {
        String message = config.getString(path, "");
        String prefix = config.getString("messages.prefix", "");
        return message.replace("{prefix}", prefix);
    }
}
