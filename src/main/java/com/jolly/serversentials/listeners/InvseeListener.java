package com.jolly.serversentials.listeners;

import com.jolly.serversentials.Serversentials;
import com.jolly.serversentials.commands.Containers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;

public class InvseeListener implements Listener {

    private final Serversentials plugin;

    public InvseeListener(Serversentials plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        // 1. Check local custom invsee
        java.util.UUID targetUUID = Containers.localInvseeTargets.get(admin.getUniqueId());
        if (targetUUID != null) {
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54) {
                return;
            }

            if (slot >= 36 && slot <= 44) {
                event.setCancelled(true);
                return;
            }
            if (slot >= 51 && slot <= 53) {
                event.setCancelled(true);
                return;
            }

            Player targetPlayer = Bukkit.getPlayer(targetUUID);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                event.setCancelled(true);
                admin.closeInventory();
                return;
            }

            if (slot == 50) {
                event.setCancelled(true);
                admin.closeInventory();
                admin.openInventory(targetPlayer.getEnderChest());
                return;
            }

            if (!admin.hasPermission("serversentials.invsee.edit")) {
                event.setCancelled(true);
                return;
            }

            plugin.getScheduler().runLater(admin, () -> {
                ItemStack updatedItem = event.getInventory().getItem(slot);
                int targetSlot = getTargetSlotFromGuiSlot(slot);
                if (targetSlot != -1) {
                    Player target = Bukkit.getPlayer(targetUUID);
                    if (target != null && target.isOnline()) {
                        if (targetSlot >= 0 && targetSlot < 36) {
                            target.getInventory().setItem(targetSlot, updatedItem);
                        } else if (targetSlot == 39) {
                            target.getInventory().setHelmet(updatedItem);
                        } else if (targetSlot == 38) {
                            target.getInventory().setChestplate(updatedItem);
                        } else if (targetSlot == 37) {
                            target.getInventory().setLeggings(updatedItem);
                        } else if (targetSlot == 36) {
                            target.getInventory().setBoots(updatedItem);
                        } else if (targetSlot == 40) {
                            target.getInventory().setItemInOffHand(updatedItem);
                        }
                    }
                }
            }, 1L);
            return;
        }

        // 2. Check cross-server invsee
        String targetName = Containers.crossServerInvsee.get(admin.getUniqueId());
        if (targetName != null) {
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54) {
                return;
            }

            if (slot >= 36 && slot <= 44) {
                event.setCancelled(true);
                return;
            }
            if (slot >= 51 && slot <= 53) {
                event.setCancelled(true);
                return;
            }

            if (slot == 50) {
                event.setCancelled(true);
                admin.closeInventory();
                Bukkit.dispatchCommand(admin, "ec " + targetName);
                return;
            }

            if (!admin.hasPermission("serversentials.invsee.edit")) {
                event.setCancelled(true);
                return;
            }

            plugin.getScheduler().runLater(admin, () -> {
                ItemStack updatedItem = event.getInventory().getItem(slot);
                int targetSlot = getTargetSlotFromGuiSlot(slot);
                if (targetSlot != -1) {
                    String serializedItem = serializeItemStack(updatedItem);
                    plugin.getNetworkManager().forwardToPlayer(admin, targetName, "INV_UPDATE", targetName, targetSlot, serializedItem != null ? serializedItem : "");
                }
            }, 1L);
            return;
        }

        // 3. Check cross-server ender chest
        String ecTargetName = Containers.crossServerEchest.get(admin.getUniqueId());
        if (ecTargetName != null) {
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 27) {
                return;
            }

            if (!admin.hasPermission("serversentials.echest.others")) {
                event.setCancelled(true);
                return;
            }

            plugin.getScheduler().runLater(admin, () -> {
                ItemStack updatedItem = event.getInventory().getItem(slot);
                String serializedItem = serializeItemStack(updatedItem);
                plugin.getNetworkManager().forwardToPlayer(admin, ecTargetName, "EC_UPDATE", ecTargetName, slot, serializedItem != null ? serializedItem : "");
            }, 1L);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Containers.invseeUsers.remove(player.getUniqueId());
        Containers.crossServerInvsee.remove(player.getUniqueId());
        Containers.crossServerEchest.remove(player.getUniqueId());
        Containers.localInvseeTargets.remove(player.getUniqueId());
    }

    private int getTargetSlotFromGuiSlot(int guiSlot) {
        if (guiSlot >= 0 && guiSlot <= 35) return guiSlot;
        if (guiSlot == 45) return 39;
        if (guiSlot == 46) return 38;
        if (guiSlot == 47) return 37;
        if (guiSlot == 48) return 36;
        if (guiSlot == 49) return 40;
        return -1;
    }

    private String serializeItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        YamlConfiguration config = new YamlConfiguration();
        config.set("item", item);
        return config.saveToString();
    }
}
