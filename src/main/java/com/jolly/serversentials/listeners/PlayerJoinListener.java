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
        }, 40L);
    }
}
