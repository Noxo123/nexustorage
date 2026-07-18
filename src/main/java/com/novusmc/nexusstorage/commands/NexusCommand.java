package com.novusmc.nexusstorage.commands;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Gère les commandes Nexus accessibles aux joueurs.
 */
public class NexusCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public NexusCommand(Main plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String m) {
        s.sendMessage(ChatColor.translateAlternateColorCodes('&', m));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            msg(sender, "&cCette commande doit être exécutée par un joueur.");
            return true;
        }

        // /nexus de base -> Ouvre le menu principal du Nexus
        if (args.length == 0) {
            NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
            if (network == null) {
                msg(player, plugin.getConfig().getString("messages.no-network"));
                return true;
            }
            plugin.getGuiManager().openMainMenu(player, network);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "upgrade" -> handleUpgrade(player);
            case "access"  -> handleAccess(player);
            case "energy"  -> handleEnergy(player);
            default        -> msg(player, "&cUsage: /nexus [upgrade|access|energy]");
        }
        return true;
    }

    private void handleUpgrade(Player player) {
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) {
            msg(player, plugin.getConfig().getString("messages.no-network"));
            return;
        }
        plugin.getGuiManager().openUpgradeMenu(player, network);
    }

    private void handleAccess(Player player) {
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) {
            msg(player, plugin.getConfig().getString("messages.no-network"));
            return;
        }
        plugin.getGuiManager().openAccessMenu(player, network);
    }

    private void handleEnergy(Player player) {
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) {
            msg(player, plugin.getConfig().getString("messages.no-network"));
            return;
        }
        plugin.getGuiManager().openEnergyMarket(player, network);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("upgrade", "access", "energy").stream()
                    .filter(o -> o.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return new ArrayList<>();
    }
}
