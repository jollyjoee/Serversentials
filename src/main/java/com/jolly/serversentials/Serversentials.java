package com.jolly.serversentials;

import com.jolly.serversentials.economy.*;
import com.jolly.serversentials.commands.Containers;
import com.jolly.serversentials.commands.teleports.*;
import com.jolly.serversentials.commands.utilities.*;
import com.jolly.serversentials.listeners.GodProtectionListener;
import com.jolly.serversentials.listeners.InvseeListener;
import com.jolly.serversentials.listeners.PlayerJoinListener;
import com.jolly.serversentials.listeners.PlayerLeaveListener;
import com.jolly.serversentials.commands.ReloadCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Serversentials extends JavaPlugin {
    private static MiniMessage mm;
    private static FileConfiguration config;
    private static DatabaseManager db;
    private static Scheduler scheduler;
    @Override
    public void onEnable() {
        saveDefaultConfig();
        mm = MiniMessage.miniMessage();
        config = getConfig();

        ConfigurationSection dbConfig = config.getConfigurationSection("database");
        String type = dbConfig.getString("type", "sqlite").toLowerCase();
        boolean mysql = getConfig().getBoolean("database.mysql", false);
        // ================================
        // 📦 DATABASE SETUP
        // ================================
        if (type.equals("mysql")) {
            String host = dbConfig.getString("host", "localhost");
            int port = dbConfig.getInt("port", 3306);
            String database = dbConfig.getString("database", "serversentials");
            String username = dbConfig.getString("username", "root");
            String password = dbConfig.getString("password", "");

            db = new DatabaseManager(this, true, host, port, database, username, password, null);
            getLogger().info("✅ Using MySQL database at " + host + ":" + port);
        } else {
            // SQLite
            String fileName = dbConfig.getString("file", "serversentials.db");
            File sqliteFile = new File(getDataFolder(), fileName);

            db = new DatabaseManager(this, false, "", 0, "", "", "", sqliteFile);
            getLogger().info("✅ Using SQLite database at " + sqliteFile.getAbsolutePath());
        }

        // ================================
        // 📆 MANAGERS AND COMMANDS
        // ================================
        scheduler = new Scheduler(this);
        HomeManager home = new HomeManager(this, scheduler);
        TpaManager tpa = new TpaManager(this, scheduler);
        TpoManager tpo = new TpoManager(this, scheduler);
        Fly fly = new Fly(scheduler, this);
        Nick nick = new Nick(scheduler, this);
        GMS gms = new GMS(this);
        GMC gmc = new GMC(this);
        GMSP gmsp = new GMSP(this);
        Containers cont = new Containers(this);
        Vanish vanish = new Vanish(scheduler, this);
        Hide hide = new Hide(scheduler, this);
        Monitor mon = new Monitor(scheduler, this);
        Generic gen = new Generic(this, scheduler);
        WarpManager warp = new WarpManager(this, scheduler);
        Item item = new Item(this, scheduler);
        Enchant ench = new Enchant(this, scheduler);
        // ================================
        // 💰 ECONOMY INITIALIZATION
        // ================================
        // Initialize VaultHook
        VaultHook vaultHook = new VaultHook(this, scheduler);

        // Initialize EconomyManager
        EconomyManager economy = new EconomyManager(db, scheduler, vaultHook, getConfig().getString("economy.currency-symbol", "$"), this);

        // Immediately register with Vault synchronously
        Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, economy, this, org.bukkit.plugin.ServicePriority.Normal);

        getLogger().info("✅ Serversentials economy registered with Vault (if Vault is present)");
        // ================================
        // 🧩 EVENT REGISTRATION
        // ================================
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, fly, nick, tpa, vanish, hide, gen, scheduler, economy), this);
        getServer().getPluginManager().registerEvents(new InvseeListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerLeaveListener(this, scheduler), this);
        getServer().getPluginManager().registerEvents(new GodProtectionListener(), this);
        // ================================
        // ⚙️ COMMAND REGISTRATION (Config-based)
        // ================================
        getCommand("ssreload").setExecutor(new ReloadCommand(this));
        if (isModuleEnabled("fly")) {
            getCommand("fly").setExecutor(fly);
            getCommand("fly").setTabCompleter(fly);
            getLogger().info("[Serversentials]✅ Fly module enabled.");
        }
        if (isModuleEnabled("gamemode")) {
            getCommand("gms").setExecutor(gms);
            getCommand("gms").setTabCompleter(gms);
            getCommand("gmsp").setExecutor(gmsp);
            getCommand("gmsp").setTabCompleter(gmsp);
            getCommand("gmc").setExecutor(gmc);
            getCommand("gmc").setTabCompleter(gmc);
            getLogger().info("[Serversentials]✅ Gamemode module enabled.");
        }
        if (isModuleEnabled("nick.enabled")) {
            getCommand("nick").setExecutor(nick);
            getLogger().info("[Serversentials]✅ Nickname module enabled.");
        }
        if (isModuleEnabled("tpa.enabled")) {
            getCommand("tpa").setExecutor(tpa);
            getCommand("tpa").setTabCompleter(tpa);
            getCommand("tpaccept").setExecutor(tpa);
            getCommand("tpdeny").setExecutor(tpa);
            getLogger().info("[Serversentials]✅ Tpa module enabled.");
        }
        if (isModuleEnabled("tpahere.enabled")) {
            getCommand("tpahere").setExecutor(tpa);
            getCommand("tpahere").setTabCompleter(tpa);
            getLogger().info("[Serversentials]✅ Tpahere module enabled.");
        }
        if (isModuleEnabled("tptoggle")) {
            getCommand("tptoggle").setExecutor(tpa);
            getLogger().info("[Serversentials]✅ Tptoggle module enabled.");
        }
        if (isModuleEnabled("tpo")) {
            getCommand("tpo").setExecutor(tpo);
            getCommand("tpo").setTabCompleter(tpo);
            getLogger().info("[Serversentials]✅ Tpo module enabled.");
        }
        if (isModuleEnabled("tpohere")) {
            getCommand("tpohere").setExecutor(tpo);
            getCommand("tpohere").setTabCompleter(tpo);
            getLogger().info("[Serversentials]✅ Tpohere module enabled.");
        }
        if (isModuleEnabled("home")) {
            getCommand("home").setExecutor(home);
            getCommand("home").setTabCompleter(home);
            getCommand("sethome").setExecutor(home);
            getCommand("sethome").setTabCompleter(home);
            getCommand("homes").setExecutor(home);
            getCommand("delhome").setExecutor(home);
            getCommand("delhome").setTabCompleter(home);
            getLogger().info("[Serversentials]✅ Home module enabled.");
        }
        if (isModuleEnabled("craft")) {
            getCommand("craft").setExecutor(cont);
            getLogger().info("[Serversentials]✅ Craft module enabled.");
        }
        if (isModuleEnabled("anvil")) {
            getCommand("anvil").setExecutor(cont);
            getLogger().info("[Serversentials]✅ Anvil module enabled.");
        }
        if (isModuleEnabled("loom")) {
            getCommand("loom").setExecutor(cont);
            getLogger().info("[Serversentials]✅ loom module enabled.");
        }
        if (isModuleEnabled("echest")) {
            getCommand("echest").setExecutor(cont);
            getCommand("ec").setExecutor(cont);
            getLogger().info("[Serversentials]✅ Echest module enabled.");
        }
        if (isModuleEnabled("invsee")) {
            getCommand("invsee").setExecutor(cont);
            getCommand("inv").setExecutor(cont);
            getLogger().info("[Serversentials]✅ Invsee module enabled.");
        }
        if (isModuleEnabled("stonecutter")) {
            getCommand("stonecutter").setExecutor(cont);
            getCommand("scutter").setExecutor(cont);
            getLogger().info("[Serversentials]✅ Stonecutter module enabled.");
        }
        if (isModuleEnabled("smithingtable")) {
            getCommand("smithingtable").setExecutor(cont);
            getCommand("smith").setExecutor(cont);
            getLogger().info("[Serversentials]✅ Smithing Table module enabled.");
        }
        if (isModuleEnabled("vanish")) {
            getCommand("vanish").setExecutor(vanish);
            getCommand("vanish").setTabCompleter(vanish);
            getLogger().info("[Serversentials]✅ Vanish module enabled.");
        }
        if (isModuleEnabled("monitor")) {
            getCommand("monitor").setExecutor(mon);
            getCommand("monitor").setTabCompleter(mon);
            getLogger().info("[Serversentials]✅ Monitor module enabled.");
        }
        if (isModuleEnabled("heal")) {
            getCommand("heal").setExecutor(gen);
            getCommand("heal").setTabCompleter(gen);
            getLogger().info("[Serversentials]✅ Heal module enabled.");
        }
        if (isModuleEnabled("feed")) {
            getCommand("feed").setExecutor(gen);
            getCommand("feed").setTabCompleter(gen);
            getLogger().info("[Serversentials]✅ Feed module enabled.");
        }
        if (isModuleEnabled("god")) {
            getCommand("god").setExecutor(gen);
            getCommand("god").setTabCompleter(gen);
            getLogger().info("[Serversentials]✅ God module enabled.");
        }
        if (isModuleEnabled("warp")) {
            getCommand("warp").setExecutor(warp);
            getCommand("warps").setExecutor(warp);
            getCommand("setwarp").setExecutor(warp);
            getCommand("delwarp").setExecutor(warp);
            getCommand("warp").setTabCompleter(warp);
            getCommand("warps").setTabCompleter(warp);
            getCommand("setwarp").setTabCompleter(warp);
            getCommand("delwarp").setTabCompleter(warp);
            getLogger().info("[Serversentials]✅ Warp module enabled.");
        }
        if (isModuleEnabled("item.enabled")) {
            getCommand("item").setExecutor(item);
            getCommand("item").setTabCompleter(item);
            getLogger().info("[Serversentials]✅ Item module enabled.");
        }
        if (isModuleEnabled("enchant.enabled")) {
            getCommand("enchant").setExecutor(ench);
            getCommand("enchant").setTabCompleter(ench);
            getLogger().info("[Serversentials]✅ Enchant module enabled.");
        }
        if (isModuleEnabled("economy.enabled")) {
            getCommand("balance").setExecutor(new BalanceCommand(this, economy));
            getCommand("balance").setTabCompleter(new BalanceCommand(this, economy));

            getCommand("pay").setExecutor(new PayCommand(this, economy));
            getCommand("pay").setTabCompleter(new PayCommand(this, economy));

            getCommand("baltop").setExecutor(new BaltopCommand(this, economy));

            getCommand("economy").setExecutor(new EconomyCommand(this, economy));
            getCommand("economy").setTabCompleter(new EconomyCommand(this, economy));
            getLogger().info("[Serversentials]✅ Economy module enabled.");
        }
        //if (isModuleEnabled("hide")) {
        //    getCommand("hide").setExecutor(hide);
        //}
        // Always register /tpaccept and /tpdeny
        // ================================
        // 🪄 PLACEHOLDERAPI HOOK
        // ================================
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            scheduler.runLater(() -> {
                new Placeholder(this, nick, fly, vanish, hide, economy).register();
                getLogger().info("✅ Registered Serversentials placeholders with PlaceholderAPI!");
            }, 20L);
        }

        // ================================
        // 🧱 TABLE CREATION
        // ================================
        getDatabase().updateSafe("""
        CREATE TABLE IF NOT EXISTS fly_data (
            uuid VARCHAR(36) PRIMARY KEY,
            flying BOOLEAN NOT NULL DEFAULT 0
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
            uuid VARCHAR(36) PRIMARY KEY,
            tptoggle BOOLEAN NOT NULL DEFAULT 0
        )
    """);

        getDatabase().updateSafe("""
        CREATE TABLE IF NOT EXISTS homes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uuid VARCHAR(36) NOT NULL,
            name VARCHAR(32) NOT NULL,
            world VARCHAR(64) NOT NULL,
            x DOUBLE NOT NULL,
            y DOUBLE NOT NULL,
            z DOUBLE NOT NULL,
            yaw FLOAT NOT NULL,
            pitch FLOAT NOT NULL,
            server VARCHAR(64) NOT NULL,
            created_at TEXT DEFAULT (datetime('now')),
            UNIQUE (uuid, name)
        )
    """);

        getDatabase().updateSafe("""
        CREATE TABLE IF NOT EXISTS leave_data (
            uuid TEXT PRIMARY KEY,
            world TEXT NOT NULL,
            x DOUBLE NOT NULL,
            y DOUBLE NOT NULL,
            z DOUBLE NOT NULL,
            left_at TEXT DEFAULT (datetime('now'))
        )
    """);

        getDatabase().updateSafe("""
        CREATE TABLE IF NOT EXISTS vanish_data (
            uuid VARCHAR(36) PRIMARY KEY,
            status BOOLEAN NOT NULL
        )
    """);

        getLogger().info("✅ Serversentials fully initialized!");
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
        return config.getString(path, path + " is not configured. Check your config.yml!");
    }
}
