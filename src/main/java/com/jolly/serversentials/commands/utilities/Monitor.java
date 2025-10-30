package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class Monitor implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Track per-player state
    private final Map<UUID, Location> startLocations = new HashMap<>();
    private final Map<UUID, GameMode> startGamemodes = new HashMap<>();
    private final Set<UUID> monitorUsers = new HashSet<>();

    public Monitor(Scheduler scheduler, Serversentials plugin) {
        this.scheduler = scheduler;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (!player.hasPermission("serversentials.monitor")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return true;
        }
        UUID uuid = player.getUniqueId();
        if (args.length == 0) {
            if (!monitorUsers.contains(uuid)) {
                player.sendActionBar(mm.deserialize("<yellow>Usage:</yellow> /monitor <player>"));
                return true;
            }
            stopMonitoring(player);
            return true;
        }

        // Get target safely on the main thread
        String targetName = args[0];
        scheduler.run(player, () -> {
            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                player.sendActionBar(mm.deserialize("<red>Player not found!"));
                return;
            }

            if (!monitorUsers.contains(uuid)) {
                // ✅ Start monitoring
                monitorUsers.add(uuid);
                startLocations.put(uuid, player.getLocation().clone());
                startGamemodes.put(uuid, player.getGameMode());

                player.setGameMode(GameMode.SPECTATOR);
                player.teleportAsync(target.getLocation().add(0, 2, 0));
                player.sendActionBar(mm.deserialize("<green>You are now monitoring " + target.getName() + "!"));
            } else {
                stopMonitoring(player);
            }
        });

        return true;
    }

    public void stopMonitoring(Player player) {
        // ✅ Stop monitoring
        UUID uuid = player.getUniqueId();
        monitorUsers.remove(uuid);
        Location loc = startLocations.getOrDefault(uuid, player.getWorld().getSpawnLocation());
        GameMode gm = startGamemodes.getOrDefault(uuid, GameMode.SURVIVAL);
        player.setGameMode(gm);
        player.teleportAsync(loc);
        player.sendActionBar(mm.deserialize("<yellow>Stopped monitoring."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("monitor")) return null;
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
