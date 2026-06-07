package com.jolly.serversentials;

import com.google.common.io.ByteArrayDataInput;
import com.jolly.serversentials.commands.Containers;
import com.jolly.serversentials.commands.teleports.TpaManager;
import com.jolly.serversentials.commands.utilities.Nick;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkPacketHandler {

    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Pending cross-server teleports: PlayerA UUID -> TargetLocation details
    public static final Map<UUID, Location> pendingTeleports = new ConcurrentHashMap<>();
    // Pending cross-server monitor teleports: PlayerA UUID -> TargetLocation details
    public static final Map<UUID, Location> pendingMonitorTeleports = new ConcurrentHashMap<>();
    // Pending cross-server TPA requests: TargetPlayer Name -> SenderPlayer Name
    public static final Map<String, String> pendingTpaRequests = new ConcurrentHashMap<>();
    // Pending cross-server TPAHere requests: TargetPlayer Name -> SenderPlayer Name
    public static final Map<String, String> pendingTpaHereRequests = new ConcurrentHashMap<>();

    public NetworkPacketHandler(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    public void handle(Player player, String subChannel, ByteArrayDataInput in) {
        switch (subChannel) {
            case "TPA_REQUEST": {
                String senderName = in.readUTF();
                String targetName = in.readUTF();
                boolean isHere = in.readBoolean();
                
                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null) {
                    if (isHere) {
                        pendingTpaHereRequests.put(target.getName().toLowerCase(), senderName);
                        target.sendMessage(mm.deserialize("<gold>" + senderName + " has requested you to teleport to them."));
                        target.sendMessage(mm.deserialize("<gold>Type <green>/tpaccept<gold> or <red>/tpdeny<gold> to respond."));
                    } else {
                        pendingTpaRequests.put(target.getName().toLowerCase(), senderName);
                        target.sendMessage(mm.deserialize("<gold>" + senderName + " has requested to teleport to you."));
                        target.sendMessage(mm.deserialize("<gold>Type <green>/tpaccept<gold> or <red>/tpdeny<gold> to respond."));
                    }
                }
                break;
            }

            case "TPA_RESPONSE": {
                String senderName = in.readUTF();
                String targetName = in.readUTF();
                boolean accepted = in.readBoolean();
                boolean isHere = in.readBoolean();

                if (accepted) {
                    String serverName = in.readUTF();
                    String worldName = in.readUTF();
                    double x = in.readDouble();
                    double y = in.readDouble();
                    double z = in.readDouble();
                    float yaw = in.readFloat();
                    float pitch = in.readFloat();

                    Player sender = Bukkit.getPlayerExact(senderName);
                    if (sender != null) {
                        sender.sendMessage(mm.deserialize("<green>" + targetName + " accepted your teleport request. Teleporting..."));
                        plugin.getTpaManager().startCrossServerCountdown(sender, serverName, worldName, x, y, z, yaw, pitch);
                    }
                } else {
                    Player sender = Bukkit.getPlayerExact(senderName);
                    if (sender != null) {
                        sender.sendMessage(mm.deserialize("<red>" + targetName + " denied your teleport request."));
                    }
                }
                break;
            }

            case "TPAHERE_ACCEPT": {
                String senderName = in.readUTF();
                String receiverName = in.readUTF();
                UUID receiverUUID = UUID.fromString(in.readUTF());

                Player sender = Bukkit.getPlayerExact(senderName);
                if (sender != null) {
                    String localServer = plugin.getConfig().getString("server-name", "unknown");
                    Location loc = sender.getLocation();
                    pendingTeleports.put(receiverUUID, loc);
                    plugin.getNetworkManager().requestPlayerTransfer(sender, receiverName, localServer);
                    sender.sendMessage(mm.deserialize("<green>" + receiverName + " accepted your /tpahere request. Teleporting them to you..."));
                    plugin.getNetworkManager().forwardToPlayer(sender, receiverName, "TPAHERE_CONFIRM", receiverName, senderName);
                }
                break;
            }

            case "TPAHERE_CONFIRM": {
                String receiverName = in.readUTF();
                String senderName = in.readUTF();
                Player receiver = Bukkit.getPlayerExact(receiverName);
                if (receiver != null) {
                    receiver.sendMessage(mm.deserialize("<green>Teleporting to " + senderName + "..."));
                }
                break;
            }

            case "TPO_REQUEST": {
                String senderName = in.readUTF();
                String targetName = in.readUTF();
                UUID senderUUID = UUID.fromString(in.readUTF());

                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null && target.isOnline()) {
                    String localServer = plugin.getConfig().getString("server-name", "unknown");
                    pendingTeleports.put(senderUUID, target.getLocation());
                    plugin.getNetworkManager().requestPlayerTransfer(target, senderName, localServer);
                    plugin.getNetworkManager().forwardToPlayer(target, senderName, "TPO_CONFIRM", senderName, target.getName());
                }
                break;
            }

            case "TPO_CONFIRM": {
                String senderName = in.readUTF();
                String targetName = in.readUTF();
                Player sender = Bukkit.getPlayerExact(senderName);
                if (sender != null) {
                    sender.sendMessage(mm.deserialize("<green>Teleporting to <yellow>" + targetName + "</yellow>..."));
                }
                break;
            }

            case "TPOHERE_REQUEST": {
                String senderName = in.readUTF();
                String targetName = in.readUTF();
                UUID senderUUID = UUID.fromString(in.readUTF());
                String serverName = in.readUTF();
                String worldName = in.readUTF();
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();

                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null && target.isOnline()) {
                    plugin.getNetworkManager().sendPluginMessage(target, "FORWARD_TO_SERVER", serverName, "TELEPORT_JOIN_REG", target.getUniqueId().toString(), worldName, x, y, z, yaw, pitch);
                    plugin.getNetworkManager().requestPlayerTransfer(target, target.getName(), serverName);
                    target.sendMessage(mm.deserialize("<green>Teleporting to <yellow>" + senderName + "</yellow>..."));
                    plugin.getNetworkManager().forwardToPlayer(target, senderName, "TPOHERE_CONFIRM_MSG", senderName, target.getName());
                }
                break;
            }

            case "TPOHERE_CONFIRM_MSG": {
                String senderName = in.readUTF();
                String targetName = in.readUTF();
                Player sender = Bukkit.getPlayerExact(senderName);
                if (sender != null) {
                    sender.sendMessage(mm.deserialize("<green>Teleported <yellow>" + targetName + "</yellow> to you."));
                }
                break;
            }

            case "TELEPORT_JOIN_REG": {
                UUID playerToTeleport = UUID.fromString(in.readUTF());
                String worldName = in.readUTF();
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();

                scheduler.runGlobal(() -> {
                    org.bukkit.World world = Bukkit.getWorld(worldName);
                    if (world == null && !Bukkit.getWorlds().isEmpty()) {
                        world = Bukkit.getWorlds().get(0);
                    }
                    if (world != null) {
                        Location loc = new Location(world, x, y, z, yaw, pitch);
                        pendingTeleports.put(playerToTeleport, loc);
                    }
                });
                break;
            }

            case "MONITOR_JOIN_REG": {
                UUID playerToTeleport = UUID.fromString(in.readUTF());
                String worldName = in.readUTF();
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();

                scheduler.runGlobal(() -> {
                    org.bukkit.World world = Bukkit.getWorld(worldName);
                    if (world == null && !Bukkit.getWorlds().isEmpty()) {
                        world = Bukkit.getWorlds().get(0);
                    }
                    if (world != null) {
                        Location loc = new Location(world, x, y, z, yaw, pitch);
                        pendingMonitorTeleports.put(playerToTeleport, loc);
                    }
                });
                break;
            }

            case "MONITOR_REQUEST": {
                String senderName = in.readUTF();
                String targetName = in.readUTF();
                UUID senderUUID = UUID.fromString(in.readUTF());

                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null && target.isOnline()) {
                    String localServer = plugin.getConfig().getString("server-name", "unknown");
                    pendingMonitorTeleports.put(senderUUID, target.getLocation().add(0, 2, 0));
                    plugin.getNetworkManager().requestPlayerTransfer(target, senderName, localServer);
                    plugin.getNetworkManager().forwardToPlayer(target, senderName, "MONITOR_CONFIRM", senderName, target.getName());
                }
                break;
            }

            case "MONITOR_CONFIRM": {
                String senderName = in.readUTF();
                String targetName = in.readUTF();
                Player sender = Bukkit.getPlayerExact(senderName);
                if (sender != null) {
                    sender.sendActionBar(mm.deserialize("<green>You are now monitoring <yellow>" + targetName + "</yellow>!"));
                }
                break;
            }

            case "INV_REQUEST": {
                String requesterName = in.readUTF();
                String targetName = in.readUTF();
                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null) {
                    String serialized = serializeInventory(target);
                    plugin.getNetworkManager().forwardToPlayer(target, requesterName, "INV_RESPONSE", requesterName, targetName, serialized);
                }
                break;
            }

            case "INV_RESPONSE": {
                String requesterName = in.readUTF();
                String targetName = in.readUTF();
                String serializedData = in.readUTF();
                Player requester = Bukkit.getPlayerExact(requesterName);
                if (requester != null) {
                    openVirtualInventory(requester, targetName, serializedData);
                }
                break;
            }

            case "INV_UPDATE": {
                String targetName = in.readUTF();
                int slot = in.readInt();
                String serializedItem = in.readUTF();
                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null) {
                    ItemStack item = deserializeItemStack(serializedItem);
                    scheduler.run(target, () -> {
                        if (slot >= 0 && slot < 36) {
                            target.getInventory().setItem(slot, item);
                        } else if (slot == 39) {
                            target.getInventory().setHelmet(item);
                        } else if (slot == 38) {
                            target.getInventory().setChestplate(item);
                        } else if (slot == 37) {
                            target.getInventory().setLeggings(item);
                        } else if (slot == 36) {
                            target.getInventory().setBoots(item);
                        } else if (slot == 40) {
                            target.getInventory().setItemInOffHand(item);
                        }
                    });
                }
                break;
            }

            case "EC_REQUEST": {
                String requesterName = in.readUTF();
                String targetName = in.readUTF();
                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null) {
                    String serialized = serializeEchest(target);
                    plugin.getNetworkManager().forwardToPlayer(target, requesterName, "EC_RESPONSE", requesterName, targetName, serialized);
                }
                break;
            }

            case "EC_RESPONSE": {
                String requesterName = in.readUTF();
                String targetName = in.readUTF();
                String serializedData = in.readUTF();
                Player requester = Bukkit.getPlayerExact(requesterName);
                if (requester != null) {
                    openVirtualEchest(requester, targetName, serializedData);
                }
                break;
            }

            case "EC_UPDATE": {
                String targetName = in.readUTF();
                int slot = in.readInt();
                String serializedItem = in.readUTF();
                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null) {
                    ItemStack item = deserializeItemStack(serializedItem);
                    scheduler.run(target, () -> {
                        if (slot >= 0 && slot < 27) {
                            target.getEnderChest().setItem(slot, item);
                        }
                    });
                }
                break;
            }

            case "NICK_REFRESH": {
                String targetName = in.readUTF();
                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null) {
                    Nick nickCmd = new Nick(scheduler, plugin);
                    nickCmd.loadNicknameAsync(target);
                }
                break;
            }

            case "MSG_RECEIVE": {
                String senderName = in.readUTF();
                String targetName = in.readUTF();
                String message = in.readUTF();

                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null) {
                    target.sendMessage(mm.deserialize("<gray>[" + senderName + " -> me] " + message));
                    plugin.setReplyTarget(target.getUniqueId(), senderName);
                }
                break;
            }

            case "SOCIALSPY_RECEIVE": {
                String senderName = in.readUTF();
                String targetName = in.readUTF();
                String message = in.readUTF();

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("serversentials.socialspy") && !p.getName().equalsIgnoreCase(senderName) && !p.getName().equalsIgnoreCase(targetName)) {
                        p.sendMessage(mm.deserialize("<red>[Spy] <gray>" + senderName + " -> " + targetName + ": " + message));
                    }
                }
                break;
            }

            case "BROADCAST_STAFF": {
                String staffMessage = in.readUTF();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("serversentials.staffchat")) {
                        p.sendMessage(mm.deserialize(staffMessage));
                    }
                }
                break;
            }

            case "PAY_CHECK_REQ": {
                String senderName = in.readUTF();
                String targetName = in.readUTF();
                double amount = in.readDouble();
                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null) {
                    plugin.getEconomyManager().depositPlayer(target, amount);
                    plugin.getNetworkManager().forwardToPlayer(target, senderName, "PAY_CHECK_RESP", senderName, targetName, amount, true);
                    target.sendMessage(mm.deserialize("<green>Received <white>" + amount + " <green>from <white>" + senderName + " (cross-server)."));
                } else {
                    plugin.getNetworkManager().forwardToPlayer(player, senderName, "PAY_CHECK_RESP", senderName, targetName, amount, false);
                }
                break;
            }

            case "PAY_CHECK_RESP": {
                String senderName = in.readUTF();
                String targetName = in.readUTF();
                double amount = in.readDouble();
                boolean success = in.readBoolean();

                Player sender = Bukkit.getPlayerExact(senderName);
                if (sender != null) {
                    if (success) {
                        sender.sendMessage(mm.deserialize("<green>Sent <white>" + amount + " <green>to <white>" + targetName + " (cross-server)."));
                    } else {
                        plugin.getEconomyManager().depositPlayer(sender, amount);
                        sender.sendMessage(mm.deserialize("<red>Failed to send money. Player not found or transaction failed. Refunded."));
                    }
                }
                break;
            }
        }
    }

    private String serializeInventory(Player player) {
        YamlConfiguration yaml = new YamlConfiguration();
        ItemStack[] inv = player.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null && !inv[i].getType().isAir()) {
                yaml.set("slot." + i, inv[i]);
            }
        }
        yaml.set("health", player.getHealth());
        yaml.set("max-health", player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        yaml.set("food", player.getFoodLevel());
        yaml.set("xp", player.getLevel());
        return yaml.saveToString();
    }

    private String serializeEchest(Player player) {
        YamlConfiguration yaml = new YamlConfiguration();
        ItemStack[] items = player.getEnderChest().getContents();
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && !items[i].getType().isAir()) {
                yaml.set("slot." + i, items[i]);
            }
        }
        return yaml.saveToString();
    }

    private String serializeItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        YamlConfiguration config = new YamlConfiguration();
        config.set("item", item);
        return config.saveToString();
    }

    private ItemStack deserializeItemStack(String data) {
        if (data == null || data.isEmpty()) return null;
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(data);
            return config.getItemStack("item");
        } catch (Exception e) {
            return null;
        }
    }

    private void openVirtualInventory(Player admin, String targetName, String serializedData) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(serializedData);
        } catch (Exception e) {
            admin.sendMessage(mm.deserialize("<red>Failed to load player inventory data."));
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54, mm.deserialize("<dark_gray>Invsee: " + targetName));

        for (int i = 0; i < 36; i++) {
            ItemStack item = yaml.getItemStack("slot." + i);
            if (item != null) gui.setItem(i, item);
        }

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        for (int i = 36; i < 45; i++) {
            gui.setItem(i, pane);
        }

        int[] armorSlots = {39, 38, 37, 36};
        for (int i = 0; i < 4; i++) {
            ItemStack item = yaml.getItemStack("slot." + armorSlots[i]);
            if (item != null) gui.setItem(45 + i, item);
        }

        ItemStack offhand = yaml.getItemStack("slot.40");
        if (offhand != null) gui.setItem(49, offhand);

        ItemStack echest = new ItemStack(Material.ENDER_CHEST);
        ItemMeta ecMeta = echest.getItemMeta();
        ecMeta.displayName(mm.deserialize("<purple>Ender Chest"));
        echest.setItemMeta(ecMeta);
        gui.setItem(50, echest);

        ItemStack status = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta statMeta = status.getItemMeta();
        statMeta.displayName(mm.deserialize("<gold>Status Info"));
        List<Component> lore = new ArrayList<>();
        double health = yaml.getDouble("health");
        double maxHealth = yaml.getDouble("max-health");
        int food = yaml.getInt("food");
        int xp = yaml.getInt("xp");
        lore.add(mm.deserialize("<gray>Health: <red>" + String.format("%.1f", health) + "/" + String.format("%.1f", maxHealth)));
        lore.add(mm.deserialize("<gray>Food Level: <gold>" + food + "/20"));
        lore.add(mm.deserialize("<gray>XP Level: <green>" + xp));
        statMeta.lore(lore);
        status.setItemMeta(statMeta);
        gui.setItem(53, status);

        admin.openInventory(gui);
        Containers.crossServerInvsee.put(admin.getUniqueId(), targetName);
    }

    private void openVirtualEchest(Player admin, String targetName, String serializedData) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(serializedData);
        } catch (Exception e) {
            admin.sendMessage(mm.deserialize("<red>Failed to load player ender chest data."));
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, mm.deserialize("<dark_gray>Ender Chest: " + targetName));
        for (int i = 0; i < 27; i++) {
            ItemStack item = yaml.getItemStack("slot." + i);
            if (item != null) gui.setItem(i, item);
        }
        admin.openInventory(gui);
        Containers.crossServerEchest.put(admin.getUniqueId(), targetName);
    }
}
