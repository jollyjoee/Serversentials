package com.jolly.serversentials.commands.teleports;

import com.jolly.serversentials.Scheduler;
import com.jolly.serversentials.Serversentials;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class Teleports implements CommandExecutor {
    private final Serversentials plugin;
    private final Scheduler scheduler;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Random random = new Random();

    public Teleports(Serversentials plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>Only players can use this command!"));
            return true;
        }

        String cmd = command.getName().toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "top" -> handleTop(player);
            case "rtp" -> handleRtp(player);
            default -> player.sendActionBar(mm.deserialize("<red>Unknown command."));
        }
        return true;
    }

    private void handleTop(Player player) {
        if (!player.hasPermission("serversentials.teleports.top")) {
            player.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.no-permission", "<red>You do not have permission to use this command!</red>")));
            return;
        }

        World world = player.getWorld();
        Location playerLoc = player.getLocation();
        Location topBlockLoc = world.getHighestBlockAt(playerLoc).getLocation();
        double x = playerLoc.getX();
        double y = topBlockLoc.getY() + 1.0; // One block above the highest solid block
        double z = playerLoc.getZ();
        float yaw = playerLoc.getYaw();
        float pitch = playerLoc.getPitch();
        Location targetLoc = new Location(world, x, y, z, yaw, pitch);

        Block targetBlock = targetLoc.getBlock();
        if (targetBlock.getType().isSolid() || targetBlock.getType().name().contains("LAVA")) {
            targetLoc.add(0, 1, 0);
        }

        player.teleportAsync(targetLoc);
    }

    private void handleRtp(Player player) {
        if (!player.hasPermission("serversentials.teleports.rtp")) {
            player.sendActionBar(mm.deserialize(plugin.getConfig().getString("messages.no-permission", "<red>You do not have permission to use this command!</red>")));
            return;
        }
        World world = player.getWorld();
        RegionScheduler regionScheduler = Bukkit.getRegionScheduler();
        WorldBorder border = world.getWorldBorder();
        double radius = border.getSize() / 2;
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();
        int minY = world.getMinHeight();

        Runnable[] attemptRunner = new Runnable[1];
        attemptRunner[0] = () -> {
            int x = (int) (centerX + (random.nextDouble() * 2 - 1) * radius);
            int z = (int) (centerZ + (random.nextDouble() * 2 - 1) * radius);

            regionScheduler.run(plugin, world, x >> 4, z >> 4, regionTask -> {
                int y = world.getHighestBlockYAt(x, z);
                Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
                world.loadChunk(loc.getChunk().getX(), loc.getChunk().getZ(), true);
                world.loadChunk(loc.getChunk().getX() + 1, loc.getChunk().getZ() + 1, true);
                world.loadChunk(loc.getChunk().getX() - 1, loc.getChunk().getZ() - 1, true);
                //world.getChunkAtAsync(loc.getChunk().getX(), loc.getChunk().getZ());
                if (!border.isInside(loc) || loc.getBlockY() < minY || !isSafeLocation(loc, getUnsafeBlocks())) {
                    attemptRunner[0].run();
                    return;
                }

                regionScheduler.runDelayed(plugin, player.getLocation(), playerRegionTask -> {
                    player.teleportAsync(loc).thenRun(() -> {
                        String soundStr = plugin.getConfig().getString("effects.teleport-sound", "ENTITY_ENDERMAN_TELEPORT");
                        try {
                            player.playSound(loc, Sound.valueOf(soundStr), 1f, 1f);
                        } catch (IllegalArgumentException ignored) {}
                        try {
                            Particle particle = Particle.valueOf(plugin.getConfig().getString("effects.teleport-particle", "PORTAL"));
                            int count = plugin.getConfig().getInt("effects.particle-count", 40);
                            world.spawnParticle(particle, loc.clone().add(0, 1, 0), count, 0.5, 1, 0.5, 0.1);
                        } catch (Exception ignored) {}
                    }).exceptionally(ex -> {
                        player.sendMessage(Component.text("§cTeleport failed."));
                        return null;
                    });
                }, 4L);
            });
        };
        attemptRunner[0].run();
    }

    private boolean isSafeLocation(Location loc, Set<Material> unsafe) {
        Block block = loc.getBlock();
        Block below = block.getRelative(0, -1, 0);
        return !below.isEmpty() && !unsafe.contains(below.getType()) && !unsafe.contains(block.getType()) && loc.getY() > 5;
    }

    private Set<Material> getUnsafeBlocks() {
        return EnumSet.of(
                Material.LAVA, Material.FIRE, Material.CACTUS, Material.MAGMA_BLOCK,
                Material.CAMPFIRE, Material.SOUL_FIRE, Material.SOUL_CAMPFIRE, Material.WATER
        );
    }
}
