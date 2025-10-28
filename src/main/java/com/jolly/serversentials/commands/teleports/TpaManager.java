package com.jolly.serversentials.commands.teleports;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TpaManager implements CommandExecutor, TabCompleter {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public enum RequestType { TPA, TPAHERE }

    private static class TPRequest {
        UUID sender;
        RequestType type;

        TPRequest(UUID sender, RequestType type) {
            this.sender = sender;
            this.type = type;
        }
    }

    // Key: receiver UUID, Value: request
    private final Map<UUID, TPRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Object> expirationTasks = new ConcurrentHashMap<>();

    // Key: player UUID, Value: true if they can receive TP requests
    private final Map<UUID, Boolean> tptoggleCache = new ConcurrentHashMap<>();

    private final long expirationTicks;

    public TpaManager(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.expirationTicks = plugin.getConfig().getLong("modules.tpa.expiration", 60) * 20L;
    }

    // -----------------------------
    // Main Command Handler
    // -----------------------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        String cmd = command.getName().toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "tpa" -> handleTpa(player, args);
            case "tpahere" -> handleTpahere(player, args);
            case "tpaccept", "tpac" -> handleAccept(player);
            case "tpdeny", "tpd" -> handleDeny(player);
            case "tptoggle", "tpt" -> handleToggle(player);
            default -> player.sendActionBar(mm.deserialize("<red>Unknown command."));
        }
        return true;
    }

    // -----------------------------
    // Sending Requests
    // -----------------------------
    private void handleTpa(Player sender, String[] args) {
        if (!sender.hasPermission("serversentials.tpa")) {
            sender.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        if (args.length == 0) {
            sender.sendActionBar(mm.deserialize("<red>Usage: /tpa <player>"));
            return;
        }
        Player receiver = Bukkit.getPlayer(args[0]);
        if (!isValidTarget(sender, receiver)) return;
        sendRequest(sender, receiver, RequestType.TPA);
    }

    private void handleTpahere(Player sender, String[] args) {
        if (!sender.hasPermission("serversentials.tpahere")) {
            sender.sendActionBar(plugin.mm(plugin.prefixMessage("messages.no-permission")));
            return;
        }
        if (args.length == 0) {
            sender.sendActionBar(mm.deserialize("<red>Usage: /tpahere <player>"));
            return;
        }
        Player receiver = Bukkit.getPlayer(args[0]);
        if (!isValidTarget(sender, receiver)) return;
        sendRequest(sender, receiver, RequestType.TPAHERE);
    }

    private boolean isValidTarget(Player sender, Player receiver) {
        if (receiver == null || !receiver.isOnline()) {
            sender.sendActionBar(mm.deserialize("<red>That player is not online!"));
            return false;
        }
        if (receiver.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendActionBar(mm.deserialize("<red>You cannot send a teleport request to yourself!"));
            return false;
        }
        return true;
    }

    private void sendRequest(Player sender, Player receiver, RequestType type) {
        // Respect tptoggle
        if (!tptoggleCache.getOrDefault(receiver.getUniqueId(), true)) {
            sender.sendActionBar(mm.deserialize("<red>" + receiver.getName() + " is not accepting teleport requests."));
            return;
        }

        pendingRequests.put(receiver.getUniqueId(), new TPRequest(sender.getUniqueId(), type));

        String typeMessage = type == RequestType.TPA ? "to teleport to you!" : "to teleport you to them!";
        sender.sendActionBar(mm.deserialize("<green>Teleport request sent to <yellow>" + receiver.getName()));
        receiver.sendActionBar(mm.deserialize("<yellow>" + sender.getName() + " <green>wants " + typeMessage));
        receiver.sendActionBar(mm.deserialize("<gray>Type <aqua>/tpaccept | /tpac</aqua> to accept or <red>/tpdeny | /tpd</red> to deny."));

        // Schedule expiration
        Object expireTask = scheduler.runLater(receiver, () -> {
            if (pendingRequests.remove(receiver.getUniqueId()) != null) {
                sender.sendActionBar(mm.deserialize("<red>Your teleport request to <yellow>" + receiver.getName() + "</yellow> has expired."));
                receiver.sendActionBar(mm.deserialize("<gray>The teleport request from <yellow>" + sender.getName() + "</yellow> has expired."));
            }
            expirationTasks.remove(receiver.getUniqueId());
        }, expirationTicks);

        expirationTasks.put(receiver.getUniqueId(), expireTask);
    }

    // -----------------------------
    // Accept / Deny Requests
    // -----------------------------
    private void handleAccept(Player receiver) {
        TPRequest request = pendingRequests.remove(receiver.getUniqueId());
        if (request == null) {
            receiver.sendActionBar(mm.deserialize("<red>You have no pending teleport requests."));
            return;
        }

        Player sender = Bukkit.getPlayer(request.sender);
        if (sender == null || !sender.isOnline()) {
            receiver.sendActionBar(mm.deserialize("<red>The player who sent the request is no longer online."));
            return;
        }

        scheduler.cancelTask(expirationTasks.remove(receiver.getUniqueId()));
        //plugin.getLogger().info("Teleport request accepted");
        Player monitored; // the player whose movement cancels the teleport
        long countdownSeconds;
        if (request.type == RequestType.TPA) {
            //plugin.getLogger().info("Teleport request is TPA");
            sender.sendActionBar(mm.deserialize("<green>Your /tpa request to <yellow>" + receiver.getName() + "</yellow> was accepted!"));
            receiver.sendActionBar(mm.deserialize("<green>You accepted the /tpa request from <yellow>" + sender.getName() + "</yellow>."));
            monitored = sender;
            countdownSeconds = plugin.getConfig().getLong("modules.tpa.countdown", 5);
        } else {
            //plugin.getLogger().info("Teleport request is TPAHERE");
            sender.sendActionBar(mm.deserialize("<green>Your /tpahere request to <yellow>" + receiver.getName() + "</yellow> was accepted!"));
            receiver.sendActionBar(mm.deserialize("<green>You accepted the /tpahere request from <yellow>" + sender.getName() + "</yellow>."));
            monitored = receiver;
            countdownSeconds = plugin.getConfig().getLong("modules.tpahere.countdown", 5);
        }

        final var startLoc = monitored.getLocation().clone();
        final List<Object> countdownTasks = new ArrayList<>();

        // Countdown loop
        for (long i = 0; i < countdownSeconds; i++) {
            long delay = i * 20L;
            long remaining = countdownSeconds - i;

            Object task = scheduler.runLater(monitored, () -> {
                if (!monitored.isOnline()) return;

                // Cancel if moved
                if (!monitored.getLocation().getBlock().equals(startLoc.getBlock())) {
                    monitored.sendActionBar(mm.deserialize("<red>Teleport canceled due to movement!"));
                    if (request.type == RequestType.TPA) sender.sendActionBar(mm.deserialize("<red>Teleport canceled because you moved."));
                    else receiver.sendActionBar(mm.deserialize("<red>Teleport canceled because you moved."));

                    // Cancel remaining countdown tasks
                    countdownTasks.forEach(scheduler::cancelTask);
                    countdownTasks.clear();
                    return;
                }

                monitored.sendActionBar(mm.deserialize("<aqua>Teleporting in <yellow>" + remaining + "s<aqua>..."));
            }, delay);

            countdownTasks.add(task);
        }

        // Schedule actual teleport
        Object teleportTask = scheduler.runLater(monitored, () -> {
            if (!monitored.isOnline()) return;
            if (!monitored.getLocation().getBlock().equals(startLoc.getBlock())) {
                return; // canceled by movement
            }
            if (request.type == RequestType.TPA) sender.teleportAsync(receiver.getLocation());
            else receiver.teleportAsync(sender.getLocation());
        }, countdownSeconds * 20L);

        countdownTasks.add(teleportTask);
    }

    private void handleDeny(Player receiver) {
        TPRequest request = pendingRequests.remove(receiver.getUniqueId());
        if (request == null) {
            receiver.sendActionBar(mm.deserialize("<red>You have no pending teleport requests."));
            return;
        }

        Player sender = Bukkit.getPlayer(request.sender);
        if (sender != null && sender.isOnline()) {
            sender.sendActionBar(mm.deserialize("<red>Your teleport request to <yellow>" + receiver.getName() + "</yellow> was denied."));
        }

        scheduler.cancelTask(expirationTasks.remove(receiver.getUniqueId()));

        receiver.sendActionBar(mm.deserialize("<gray>You denied the teleport request."));
    }

    // -----------------------------
    // TP Toggle Command
    // -----------------------------
    private void handleToggle(Player player) {
        UUID uuid = player.getUniqueId();
        boolean currentlyEnabled = tptoggleCache.getOrDefault(uuid, true);
        boolean newState = !currentlyEnabled;
        tptoggleCache.put(uuid, newState);

        // Save asynchronously
        scheduler.runAsync(() -> {
            plugin.getDatabase().updateSafe(
                    "INSERT INTO tptoggle_data (uuid, tptoggle) VALUES (?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET tptoggle = ?",
                    uuid.toString(), newState, newState
            );
        });

        player.sendActionBar(mm.deserialize(
                newState ? "<green>You can now receive TPA requests." :
                        "<red>You will no longer receive TPA requests."
        ));
    }

    // -----------------------------
    // Tab Completion
    // -----------------------------
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);

            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player)) // exclude self
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted()
                    .toList();
        }
        return List.of();
    }

    // -----------------------------
    // Helper: load tptoggle state on join
    // -----------------------------
    public void loadToggleState(Player player) {
        scheduler.runAsync(() -> {
            Boolean enabled = plugin.getDatabase().querySafe(
                    "SELECT tptoggle FROM tptoggle_data WHERE uuid = ?",
                    rs -> rs.next() ? rs.getBoolean("tptoggle") : true,
                    player.getUniqueId().toString()
            );
            tptoggleCache.put(player.getUniqueId(), enabled);
        });
    }
}
