package com.jolly.serversentials.listeners;

import com.jolly.serversentials.Serversentials;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.HashMap;
import java.util.Map;

public class CommandSendListener implements Listener {

    private final Map<String, String> commandPermissions = new HashMap<>();

    public CommandSendListener(Serversentials plugin) {
        setupPermissions();
    }

    private void setupPermissions() {
        register("ssreload", "serversentials.reload");
        register("fly", "serversentials.fly");
        register("gms", "serversentials.gms");
        register("gmc", "serversentials.gmc");
        register("gmsp", "serversentials.gmsp");
        register("nick", "serversentials.nick");
        register("tpa", "serversentials.tpa");
        register("tpahere", "serversentials.tpahere");
        register("tpah", "serversentials.tpahere");
        register("tpaccept", "serversentials.tpaccept");
        register("tpc", "serversentials.tpaccept");
        register("tpdeny", "serversentials.tpdeny");
        register("tpd", "serversentials.tpdeny");
        register("tptoggle", "serversentials.tptoggle");
        register("tpt", "serversentials.tptoggle");
        register("tpo", "serversentials.tpo");
        register("tpohere", "serversentials.tpohere");
        register("home", "serversentials.home");
        register("sethome", "serversentials.sethome");
        register("delhome", "serversentials.delhome");
        register("homes", "serversentials.homes");
        register("craft", "serversentials.craft");
        register("anvil", "serversentials.anvil");
        register("loom", "serversentials.loom");
        register("echest", "serversentials.echest");
        register("ec", "serversentials.echest");
        register("invsee", "serversentials.invsee");
        register("inv", "serversentials.invsee");
        register("stonecutter", "serversentials.stonecutter");
        register("scutter", "serversentials.stonecutter");
        register("smithingtable", "serversentials.smithingtable");
        register("smith", "serversentials.smithingtable");
        register("vanish", "serversentials.vanish");
        register("v", "serversentials.vanish");
        register("monitor", "serversentials.monitor");
        register("mon", "serversentials.monitor");
        register("heal", "serversentials.heal");
        register("feed", "serversentials.feed");
        register("god", "serversentials.god");
        register("warp", "serversentials.warp");
        register("warps", "serversentials.warps");
        register("setwarp", "serversentials.setwarp");
        register("delwarp", "serversentials.delwarp");
        register("item", "serversentials.item");
        register("i", "serversentials.item");
        register("itemrename", "serversentials.itemrename");
        register("iname", "serversentials.itemrename");
        register("itemname", "serversentials.itemrename");
        register("lore", "serversentials.lore");
        register("enchant", "serversentials.enchant");
        register("ench", "serversentials.enchant");
        register("balance", "serversentials.balance");
        register("bal", "serversentials.balance");
        register("pay", "serversentials.pay");
        register("economy", "serversentials.economy");
        register("eco", "serversentials.economy");
        register("baltop", "serversentials.baltop");
        register("day", "serversentials.time");
        register("noon", "serversentials.time");
        register("night", "serversentials.time");
        register("clear", "serversentials.weather");
        register("rain", "serversentials.weather");
        register("storm", "serversentials.weather");
        register("top", "serversentials.top");
        register("rtp", "serversentials.rtp");
        register("back", "serversentials.back");
        register("msg", "serversentials.msg");
        register("w", "serversentials.msg");
        register("tell", "serversentials.msg");
        register("pm", "serversentials.msg");
        register("message", "serversentials.msg");
        register("r", "serversentials.r");
        register("reply", "serversentials.r");
        register("socialspy", "serversentials.socialspy");
        register("sc", "serversentials.staffchat");
        register("staffchat", "serversentials.staffchat");
        register("whois", "serversentials.whois");
    }

    private void register(String command, String permission) {
        commandPermissions.put(command.toLowerCase(), permission);
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        event.getCommands().removeIf(cmd -> {
            String cleanCmd = cmd.toLowerCase();
            if (commandPermissions.containsKey(cleanCmd)) {
                String permission = commandPermissions.get(cleanCmd);
                return !player.hasPermission(permission);
            }
            return false;
        });
    }
}
