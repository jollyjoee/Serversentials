package com.jolly.serversentials.commands.utilities;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.*;

public class Generic implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();
    public static final Set<UUID> godUsers = new HashSet<>();

    public Generic(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        createTable();
    }

    private void createTable() {
        plugin.getDatabase().updateSafe("""
                CREATE TABLE IF NOT EXISTS god_mode (
                    uuid TEXT PRIMARY KEY,
                    status BOOLEAN
                )
        """);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "heal" -> handleHeal(player, args);
            case "feed" -> handleFeed(player, args);
            case "god" -> handleGod(player, args);
            case "top" -> handleTop(player);
            default -> player.sendActionBar(mm.deserialize("<red>Unknown command!"));
        }

        return true;
    }

    // ==========================
    // 🔹 /heal [player]
    // ==========================
    private void handleHeal(Player player, String[] args) {
        if (!player.hasPermission("serversentials.heal")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        if (args.length == 1 && player.hasPermission("serversentials.heal.others")) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendActionBar(mm.deserialize("<red>Player not found!"));
                return;
            }
            target.setHealth(target.getMaxHealth());
            player.sendActionBar(mm.deserialize("<green>You healed <white>" + target.getName() + "</white>."));
            target.sendActionBar(mm.deserialize("<green>You have been healed!"));
            return;
        }
        player.setHealth(player.getMaxHealth());
        player.sendActionBar(mm.deserialize("<green>You have been healed!"));
    }

    // ==========================
    // 🔹 /feed [player]
    // ==========================
    private void handleFeed(Player player, String[] args) {
        if (!player.hasPermission("serversentials.feed")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return;
        }

        if (args.length == 1 && player.hasPermission("serversentials.feed.others")) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendActionBar(mm.deserialize("<red>Player not found!"));
                return;
            }
            target.setFoodLevel(20);
            target.setSaturation(20);
            player.sendActionBar(mm.deserialize("<green>You fed <white>" + target.getName() + "</white>."));
            target.sendActionBar(mm.deserialize("<green>You have been fed!"));
            return;
        }
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.sendActionBar(mm.deserialize("<green>You have been fed!"));
    }

    // ==========================
    // 🔹 /god [player]
    // ==========================
    private void handleGod(Player player, String[] args) {
        if (!player.hasPermission("serversentials.god")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return;
        }

        Player target = player;
        if (args.length == 1 && player.hasPermission("serversentials.god.others")) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendActionBar(mm.deserialize("<red>Player not found!"));
                return;
            }
        }

        UUID targetId = target.getUniqueId();
        boolean enabled = !godUsers.contains(targetId);

        if (enabled) {
            godUsers.add(targetId);
            target.setInvulnerable(true);
            saveGodStatus(targetId, true);
            target.sendActionBar(mm.deserialize("<green>God mode enabled."));
            if (!target.equals(player))
                player.sendActionBar(mm.deserialize("<green>Enabled god mode for <white>" + target.getName() + "</white>."));
        } else {
            godUsers.remove(targetId);
            target.setInvulnerable(false);
            saveGodStatus(targetId, false);
            target.sendActionBar(mm.deserialize("<yellow>God mode disabled."));
            if (!target.equals(player))
                player.sendActionBar(mm.deserialize("<yellow>Disabled god mode for <white>" + target.getName() + "</white>."));
        }
    }

    private void handleTop(Player player) {
        if (!player.hasPermission("serversentials.top")) {
            player.sendActionBar(mm.deserialize(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        World world = player.getWorld();
        Block topBlock = world.getHighestBlockAt(player.getLocation());
        while (!topBlock.getType().isSolid() && topBlock.getY() > 0) {
            topBlock = topBlock.getRelative(BlockFace.DOWN);
        }
        Location teleportLoc = topBlock.getLocation().add(0.5, 1, 0.5); // center in block + 1 Y
        player.teleport(teleportLoc);
    }

    // ==========================
    // 🔹 Database Save / Load
    // ==========================
    private void saveGodStatus(UUID uuid, boolean status) {
        scheduler.runAsync(() -> plugin.getDatabase().updateSafe(
                "REPLACE INTO god_mode (uuid, status) VALUES (?, ?)",
                uuid.toString(), status
        ));
    }

    public void loadGodStatus(Player player) {
        UUID uuid = player.getUniqueId();
        scheduler.runAsync(() -> {
            plugin.getDatabase().querySafe(
                    "SELECT status FROM god_mode WHERE uuid = ?",
                    rs -> {
                        if (rs.next() && rs.getBoolean("status")) {
                            godUsers.add(uuid);
                            scheduler.run(player, () -> {
                                player.setInvulnerable(true);
                                player.sendActionBar(mm.deserialize("<green>God mode restored."));
                            });
                        }
                        return null;
                    },
                    uuid.toString()
            );
        });
    }

    // ==========================
    // 🔹 Tab completion
    // ==========================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equalsIgnoreCase(sender.getName())) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
