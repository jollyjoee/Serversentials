package com.jolly.serversentials.commands;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public class WorldCommands implements CommandExecutor {
    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public WorldCommands(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        if (!player.hasPermission("serversentials.worldcommands")) {
            player.sendActionBar(mm.deserialize(plugin.getConfig().getString("Messages.no-permission", "<red>You have no permission!")));
            return true;
        };

        String cmd = command.getName().toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "day" -> handleDay(player);
            case "noon" -> handleNoon(player);
            case "night" -> handleNight(player);
            case "clear" -> handleClear(player);
            case "rain" -> handleRain(player);
            case "storm" -> handleStorm(player);
            default -> player.sendActionBar(mm.deserialize("<red>Unknown command."));
        }
        return true;
    }

    private void handleDay(Player player) {
        World world = player.getWorld();
        world.setTime(1000);
    }

    private void handleNoon(Player player) {
        World world = player.getWorld();
        world.setTime(6000);
    }

    private void handleNight(Player player) {
        World world = player.getWorld();
        world.setTime(13000);
    }

    private void handleClear(Player player) {
        World world = player.getWorld();
        world.setStorm(false);
        world.setThundering(false);
    }

    private void handleRain(Player player) {
        World world = player.getWorld();
        world.setStorm(true);
        world.setThundering(false);
    }

    private void handleStorm(Player player) {
        World world = player.getWorld();
        world.setStorm(true);
        world.setThundering(true);
    }
}
