package com.jolly.serversentials;

import com.jolly.serversentials.commands.WorldCommands;
import com.jolly.serversentials.economy.*;
import com.jolly.serversentials.commands.Containers;
import com.jolly.serversentials.commands.teleports.*;
import com.jolly.serversentials.commands.utilities.*;
import com.jolly.serversentials.listeners.ChatListener;
import com.jolly.serversentials.listeners.GodProtectionListener;
import com.jolly.serversentials.listeners.InvseeListener;
import com.jolly.serversentials.listeners.PlayerJoinListener;
import com.jolly.serversentials.listeners.PlayerLeaveListener;
import com.jolly.serversentials.listeners.TeleportListener;
import com.jolly.serversentials.commands.ReloadCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class Serversentials extends JavaPlugin {
    private static MiniMessage mm;
    private static FileConfiguration config;
    private static DatabaseManager db;
    private static Scheduler scheduler;
    private static FormatUtility formatUtility;

    // Overhauled references for dynamic reloading
    private TpaManager tpaManager;
    private WarpManager warpManager;
    private EconomyManager economyManager;

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
        Metrics metrics = new Metrics(this, 27850);
        scheduler = new Scheduler(this);
        formatUtility = new FormatUtility(this);
        HomeManager home = new HomeManager(this, scheduler);
        this.tpaManager = new TpaManager(this, scheduler);
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
        this.warpManager = new WarpManager(this, scheduler);
        Item item = new Item(this, scheduler);
        Enchant ench = new Enchant(this, scheduler);
        WorldCommands world = new WorldCommands(this, scheduler);
        Teleports tp = new Teleports(this, scheduler);

        // ================================
        // 💰 ECONOMY INITIALIZATION
        // ================================
        // Initialize VaultHook
        VaultHook vaultHook = new VaultHook(this, scheduler);

        // Initialize EconomyManager
        this.economyManager = new EconomyManager(db, scheduler, vaultHook, getConfig().getString("economy.currency-symbol", "$"), this);

        // Immediately register with Vault synchronously
        Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, economyManager, this, org.bukkit.plugin.ServicePriority.Highest);
        getLogger().info("✅ Serversentials economy registered with Vault");

        // ✅ Activate Vault Hook
        vaultHook.hook();

        // ================================
        // 🧩 EVENT REGISTRATION
        // ================================
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, fly, nick, tpaManager, vanish, hide, gen, scheduler, economyManager), this);
        getServer().getPluginManager().registerEvents(new InvseeListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerLeaveListener(this, scheduler), this);
        getServer().getPluginManager().registerEvents(new GodProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new TeleportListener(this, scheduler), this);
        
        if (getConfig().getBoolean("modules.chat.enabled", true)) {
            getServer().getPluginManager().registerEvents(new ChatListener(this, vaultHook), this);
        }

        // ================================
        // ⚙️ COMMAND REGISTRATION (Unconditional)
        // ================================
        getCommand("ssreload").setExecutor(new ReloadCommand(this));
        
        getCommand("fly").setExecutor(fly);
        getCommand("fly").setTabCompleter(fly);
        
        getCommand("gms").setExecutor(gms);
        getCommand("gms").setTabCompleter(gms);
        getCommand("gmsp").setExecutor(gmsp);
        getCommand("gmsp").setTabCompleter(gmsp);
        getCommand("gmc").setExecutor(gmc);
        getCommand("gmc").setTabCompleter(gmc);
        
        getCommand("nick").setExecutor(nick);
        
        getCommand("back").setExecutor(new BackCommand(this, scheduler));
        
        getCommand("tpa").setExecutor(tpaManager);
        getCommand("tpa").setTabCompleter(tpaManager);
        getCommand("tpaccept").setExecutor(tpaManager);
        getCommand("tpdeny").setExecutor(tpaManager);
        getCommand("tpahere").setExecutor(tpaManager);
        getCommand("tpahere").setTabCompleter(tpaManager);
        getCommand("tptoggle").setExecutor(tpaManager);
        
        getCommand("tpo").setExecutor(tpo);
        getCommand("tpo").setTabCompleter(tpo);
        getCommand("tpohere").setExecutor(tpo);
        getCommand("tpohere").setTabCompleter(tpo);
        
        getCommand("home").setExecutor(home);
        getCommand("home").setTabCompleter(home);
        getCommand("sethome").setExecutor(home);
        getCommand("sethome").setTabCompleter(home);
        getCommand("homes").setExecutor(home);
        getCommand("delhome").setExecutor(home);
        getCommand("delhome").setTabCompleter(home);
        
        getCommand("craft").setExecutor(cont);
        getCommand("anvil").setExecutor(cont);
        getCommand("loom").setExecutor(cont);
        getCommand("echest").setExecutor(cont);
        getCommand("ec").setExecutor(cont);
        getCommand("invsee").setExecutor(cont);
        getCommand("inv").setExecutor(cont);
        getCommand("stonecutter").setExecutor(cont);
        getCommand("scutter").setExecutor(cont);
        getCommand("smithingtable").setExecutor(cont);
        getCommand("smith").setExecutor(cont);
        
        getCommand("vanish").setExecutor(vanish);
        getCommand("vanish").setTabCompleter(vanish);
        
        getCommand("monitor").setExecutor(mon);
        getCommand("monitor").setTabCompleter(mon);
        
        getCommand("heal").setExecutor(gen);
        getCommand("heal").setTabCompleter(gen);
        getCommand("feed").setExecutor(gen);
        getCommand("feed").setTabCompleter(gen);
        getCommand("god").setExecutor(gen);
        getCommand("god").setTabCompleter(gen);
        
        getCommand("warp").setExecutor(warpManager);
        getCommand("warps").setExecutor(warpManager);
        getCommand("setwarp").setExecutor(warpManager);
        getCommand("delwarp").setExecutor(warpManager);
        getCommand("warp").setTabCompleter(warpManager);
        getCommand("warps").setTabCompleter(warpManager);
        getCommand("setwarp").setTabCompleter(warpManager);
        getCommand("delwarp").setTabCompleter(warpManager);
        
        getCommand("item").setExecutor(item);
        getCommand("item").setTabCompleter(item);
        
        getCommand("itemrename").setExecutor(new ItemRenameCommand(this));
        getCommand("itemrename").setTabCompleter(new ItemRenameCommand(this));
        
        getCommand("lore").setExecutor(new LoreCommand(this));
        getCommand("lore").setTabCompleter(new LoreCommand(this));
        
        getCommand("enchant").setExecutor(ench);
        getCommand("enchant").setTabCompleter(ench);
        
        getCommand("balance").setExecutor(new BalanceCommand(this, economyManager));
        getCommand("balance").setTabCompleter(new BalanceCommand(this, economyManager));
        getCommand("pay").setExecutor(new PayCommand(this, economyManager));
        getCommand("pay").setTabCompleter(new PayCommand(this, economyManager));
        getCommand("baltop").setExecutor(new BaltopCommand(this, economyManager));
        getCommand("economy").setExecutor(new EconomyCommand(this, economyManager));
        getCommand("economy").setTabCompleter(new EconomyCommand(this, economyManager));
        
        getCommand("day").setExecutor(world);
        getCommand("night").setExecutor(world);
        getCommand("noon").setExecutor(world);
        getCommand("clear").setExecutor(world);
        getCommand("rain").setExecutor(world);
        getCommand("storm").setExecutor(world);
        
        getCommand("top").setExecutor(tp);
        getCommand("rtp").setExecutor(tp);

        // ================================
        // 🪄 PLACEHOLDERAPI HOOK
        // ================================
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            scheduler.runLater(() -> {
                new Placeholder(this, nick, fly, vanish, hide, economyManager).register();
                getLogger().info("✅ Registered Serversentials placeholders with PlaceholderAPI!");
            }, 20L);

        } else {
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onPluginEnable(PluginEnableEvent event) {
                    if (event.getPlugin().getName().equals("PlaceholderAPI")) {
                        scheduler.runLater(() -> {
                            new Placeholder(Serversentials.this, nick, fly, vanish, hide, economyManager).register();
                            getLogger().info("✅ Registered Serversentials placeholders with PlaceholderAPI (delayed)!");
                        }, 20L);
                    }
                }
            }, this);
        }

        // ================================
        // 🧱 TABLE CREATION (MySQL Safe)
        // ================================
        boolean isMySQL = getDatabase().isMySQL();

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

        if (isMySQL) {
            getDatabase().updateSafe("""
            CREATE TABLE IF NOT EXISTS homes (
                id INT AUTO_INCREMENT,
                uuid VARCHAR(36) NOT NULL,
                name VARCHAR(32) NOT NULL,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL,
                server VARCHAR(64) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (id),
                UNIQUE (uuid, name)
            )
            """);
        } else {
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
        }

        if (isMySQL) {
            getDatabase().updateSafe("""
            CREATE TABLE IF NOT EXISTS leave_data (
                uuid VARCHAR(36) PRIMARY KEY,
                world VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                left_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        } else {
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
        }

        getDatabase().updateSafe("""
        CREATE TABLE IF NOT EXISTS vanish_data (
            uuid VARCHAR(36) PRIMARY KEY,
            status BOOLEAN NOT NULL
        )
        """);

        getDatabase().updateSafe("""
        CREATE TABLE IF NOT EXISTS back_data (
            uuid VARCHAR(36) PRIMARY KEY,
            world VARCHAR(64) NOT NULL,
            x DOUBLE NOT NULL,
            y DOUBLE NOT NULL,
            z DOUBLE NOT NULL,
            yaw FLOAT NOT NULL,
            pitch FLOAT NOT NULL,
            server VARCHAR(64) NOT NULL
        )
        """);

        getDatabase().updateSafe("""
        CREATE TABLE IF NOT EXISTS gamemode_data (
            uuid VARCHAR(36) PRIMARY KEY,
            gamemode VARCHAR(16) NOT NULL
        )
        """);

        // Run migrations
        if (!columnExists("back_data", "server")) {
            getDatabase().updateSafe("ALTER TABLE back_data ADD COLUMN server VARCHAR(64) NOT NULL DEFAULT 'unknown'");
        }

        getLogger().info("✅ Serversentials fully initialized!");
    }

    private boolean columnExists(String tableName, String columnName) {
        try (Connection conn = db.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName)) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public DatabaseManager getDatabase() {
        return db;
    }

    public FormatUtility getFormatUtility() {
        return formatUtility;
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

    public void reloadPlugin() {
        reloadConfig();
        config = getConfig();
        if (tpaManager != null) tpaManager.reload();
        if (warpManager != null) warpManager.reload();
        if (economyManager != null) economyManager.reload();
    }

    public static Component mm(String string) {
        return mm.deserialize(string);
    }

    public static String prefixMessage(String path) {
        return config.getString(path, path + " is not configured. Check your config.yml!");
    }
}
