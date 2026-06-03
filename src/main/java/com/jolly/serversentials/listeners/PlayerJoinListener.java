package com.jolly.serversentials.listeners;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import com.jolly.serversentials.commands.teleports.TpaManager;
import com.jolly.serversentials.commands.utilities.*;
import com.jolly.serversentials.economy.EconomyManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final Serversentials plugin;
    private final Fly flyCommand;
    private final Nick nickCommand;
    private final TpaManager tptoggle;
    private final Vanish vanish;
    private final Hide hide;
    private final Generic generic;
    private final Scheduler scheduler;
    private final EconomyManager economy;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public PlayerJoinListener(Serversentials plugin, Fly flyCommand, Nick nickCommand, TpaManager tptoggle, Vanish vanish, Hide hide, Generic generic, Scheduler scheduler, EconomyManager economy) {
        this.plugin = plugin;
        this.flyCommand = flyCommand;
        this.nickCommand = nickCommand;
        this.tptoggle = tptoggle;
        this.vanish = vanish;
        this.hide = hide;
        this.generic = generic;
        this.scheduler = scheduler;
        this.economy = economy;
    }


    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        economy.setStartingBalance(player);
        flyCommand.loadFlyStateAsync(player);
        scheduler.runLater(() -> {
            generic.loadGodStatus(player);
            nickCommand.loadNicknameAsync(player);
            tptoggle.loadToggleState(player);
            vanish.loadVanishStatus(player);
            
            if (player.hasPermission("serversentials.persistentgamemode")) {
                loadPersistentGameMode(player);
            }
        }, 1L);
    }

    private void loadPersistentGameMode(Player player) {
        UUID uuid = player.getUniqueId();
        scheduler.runAsync(() -> {
            String gmName = plugin.getDatabase().querySafe(
                    "SELECT gamemode FROM gamemode_data WHERE uuid = ?",
                    rs -> rs.next() ? rs.getString("gamemode") : null,
                    uuid.toString()
            );
            if (gmName != null) {
                scheduler.run(player, () -> {
                    try {
                        org.bukkit.GameMode gm = org.bukkit.GameMode.valueOf(gmName);
                        player.setGameMode(gm);
                        player.sendActionBar(mm.deserialize("<green>GameMode restored to <yellow>" + gm.name().toLowerCase()));
                    } catch (IllegalArgumentException ex) {
                        // Ignore invalid/unsupported gamemode names saved
                    }
                });
            }
        });
    }
}
