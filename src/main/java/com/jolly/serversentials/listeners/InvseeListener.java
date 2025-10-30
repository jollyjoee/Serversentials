package com.jolly.serversentials.listeners;

import com.jolly.serversentials.commands.Containers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public class InvseeListener implements Listener {
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!Containers.invseeUsers.contains(player.getUniqueId())) return;
        Inventory inv = event.getInventory();
        if (inv.getType() == InventoryType.PLAYER) {
            if (!player.hasPermission("serversentials.invsee.edit")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!Containers.invseeUsers.contains(player.getUniqueId())) return;
        Containers.invseeUsers.remove(player.getUniqueId());
    }
}
