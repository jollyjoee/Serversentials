package com.jolly.serversentials.listeners;

import com.jolly.serversentials.commands.utilities.Generic;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityExplodeEvent;

public class GodProtectionListener implements Listener {
    @EventHandler
    public void onAirLoss(EntityAirChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (Generic.godUsers.contains(player.getUniqueId())) {
                event.setCancelled(true);
                player.setRemainingAir(player.getMaximumAir());
            }
        }
    }

    @EventHandler
    public void onHungerLoss(EntityExplodeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (Generic.godUsers.contains(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }
}
