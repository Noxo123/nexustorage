package com.novusmc.nexusstorage.commands;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Implemente /nexus, /nexus give core|tablet, /nexus upgrade et /nexus access.
 */
public class NexusCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public NexusCommand(Main plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender sender, String s) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', s));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            msg(sender, "&cCette commande doit etre executee par un joueur.");
            return true;
        }

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
            case "give" -> handleGive(player, args);
            case "upgrade" -> handleUpgrade(player);
            case "access" -> handleAccess(player);
            default -> msg(player, "&cUsage: /nexus [give|upgrade|access]");
        }
        return true;
    }

    private void handleGive(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&cUsage: /nexus give <core|tablet>");
            return;
        }
        if (!player.hasPermission("nexusstorage.admin") && !player.hasPermission("nexusstorage.use")) {
            msg(player, "&cPermission refusee.");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "core" -> {
                ItemStack core = new ItemStack(Material.LODESTONE);
                ItemMeta meta = core.getItemMeta();
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&d&lNexus Core"));
                meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&',
                        "&7Pose ce bloc pour creer ou etendre ton reseau Nexus Storage.")));
                meta.getPersistentDataContainer().set(plugin.getNexusCoreKey(), PersistentDataType.BOOLEAN, true);
                core.setItemMeta(meta);
                player.getInventory().addItem(core);
                msg(player, "&aTu as recu un Nexus Core.");
            }
            case "tablet" -> {
                ItemStack tablet = new ItemStack(Material.GLASS);
                ItemMeta meta = tablet.getItemMeta();
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&lNexus Tablet"));
                meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&',
                        "&7Clique droit pour acceder a ton stockage Nexus.")));
                meta.getPersistentDataContainer().set(plugin.getNexusTabletKey(), PersistentDataType.BOOLEAN, true);
                tablet.setItemMeta(meta);
                player.getInventory().addItem(tablet);
                msg(player, "&aTu as recu une Nexus Tablet.");
            }
            default -> msg(player, "&cUsage: /nexus give <core|tablet>");
        }
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("give", "upgrade", "access"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            options.addAll(List.of("core", "tablet"));
        }
        return options.stream().filter(o -> o.startsWith(args[args.length - 1].toLowerCase())).toList();
    }
}
