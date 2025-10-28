package com.jolly.serversentials;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

/**
 * Universal scheduler utility for Folia, Paper, and Spigot.
 * Provides consistent task handling with support for player-region, async, and delayed execution.
 */
public class Scheduler {

    private final Serversentials plugin;
    private final boolean folia;

    public Scheduler(Serversentials plugin) {
        this.plugin = plugin;
        this.folia = isFolia();
    }

    /**
     * Detects if the server is running Folia.
     */
    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    // ======================================================
    // 🔹 Global/Region-Safe Run
    // ======================================================

    /** Run a task immediately on the global or main server thread. */
    public void runGlobal(Runnable task) {
        if (folia) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Run a task immediately in the player’s region (Folia-safe). */
    public void run(Player player, Runnable task) {
        if (folia) {
            Bukkit.getRegionScheduler().run(plugin, player.getLocation() ,t -> {
                if (!plugin.isEnabled() || !player.isOnline()) return;
                task.run();
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled() || !player.isOnline()) return;
                task.run();
            });
        }
    }

    // ======================================================
    // 🔹 Delayed Execution
    // ======================================================

    public Object runLater(Runnable task, long delayTicks) {
        if (folia) {
            return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> {
                if (!plugin.isEnabled()) return;
                task.run();
            }, delayTicks);
        } else {
            return Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!plugin.isEnabled()) return;
                task.run();
            }, delayTicks);
        }
    }

    public Object runLater(Player player, Runnable task, long delayTicks) {
        if (delayTicks <= 0) delayTicks = 1;
        if (folia) {
            return player.getScheduler().runDelayed(plugin, t -> {
                if (!plugin.isEnabled() || !player.isOnline()) return;
                task.run();
            }, null, delayTicks);
        } else {
            return Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!plugin.isEnabled() || !player.isOnline()) return;
                task.run();
            }, delayTicks);
        }
    }

    // ======================================================
    // 🔹 Repeating Tasks
    // ======================================================

    public Object runTimer(Runnable task, long delayTicks, long periodTicks) {
        if (folia) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
                if (!plugin.isEnabled()) return;
                task.run();
            }, delayTicks, periodTicks);
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    // ======================================================
    // 🔹 Async Tasks
    // ======================================================

    public void runAsync(Runnable task) {
        if (folia) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> {
                if (!plugin.isEnabled()) return;
                task.run();
            });
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public void runAsyncLater(Runnable task, long delayMillis) {
        if (folia) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, t -> {
                if (!plugin.isEnabled()) return;
                task.run();
            }, delayMillis, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                if (!plugin.isEnabled()) return;
                task.run();
            }, Math.max(1, delayMillis / 50L));
        }
    }

    // ======================================================
    // 🔹 Cancellation Utility
    // ======================================================

    public void cancelTask(Object task) {
        if (task == null) return;
        if (task instanceof ScheduledTask scheduled) {
            scheduled.cancel();
        } else if (task instanceof BukkitTask bukkitTask) {
            bukkitTask.cancel();
        }
    }
}
