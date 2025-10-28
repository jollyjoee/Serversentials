package com.jolly.serversentials.listeners;

import com.jolly.serversentials.Serversentials;
import com.jolly.serversentials.commands.teleports.TpaManager;
import com.jolly.serversentials.commands.utilities.Fly;
import com.jolly.serversentials.commands.utilities.Nick;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final Serversentials plugin;
    private final Fly flyCommand;
    private final Nick nickCommand;
    private final TpaManager tptoggle;

    public PlayerJoinListener(Serversentials plugin, Fly flyCommand, Nick nickCommand, TpaManager tptoggle) {
        this.plugin = plugin;
        this.flyCommand = flyCommand;
        this.nickCommand = nickCommand;
        this.tptoggle = tptoggle;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        flyCommand.loadFlyStateAsync(e.getPlayer());
        nickCommand.loadNicknameAsync(e.getPlayer());
        tptoggle.loadToggleState(e.getPlayer());
    }
}
