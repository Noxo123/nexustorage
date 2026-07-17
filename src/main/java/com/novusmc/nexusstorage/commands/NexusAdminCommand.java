package com.novusmc.nexusstorage.commands;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Commande d'administration /nexusadmin : toggles Vault/ItemsAdder, reload,
 * gestion du reseau d'un joueur quelconque, give admin, et GUI stylee.
 * Permission requise: nexusstorage.admin (op par defaut).
 */
public class NexusAdminCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public NexusAdminCommand(Main plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender sender, String s) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', s));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nexusstorage.admin")) {
            msg(sender, plugin.getConfig().getString("messages.admin-no-permission"));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.getGuiManager().openAdminMenu(player);
            } else {
                msg(sender, "&cUtilise /nexusadmin gui en jeu, ou une sous-commande (reload, toggle, network, give).");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "gui" -> {
                if (sender instanceof Player player) {
                    plugin.getGuiManager().openAdminMenu(player);
                } else {
                    msg(sender, "&cCette sous-commande necessite un joueur.");
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                new com.novusmc.nexusstorage.util.ConfigManager(plugin).mergeDefaultConfig();
                plugin.getEconomyManager().refresh();
                plugin.getItemsAdderManager().reload();
                msg(sender, plugin.getConfig().getString("messages.admin-reloaded"));
            }
            case "toggle" -> handleToggle(sender, args);
            case "network" -> handleNetwork(sender, args);
            case "give" -> handleGive(sender, args);
            default -> msg(sender, "&cUsage: /nexusadmin [gui|reload|toggle|network|give]");
        }
        return true;
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "&cUsage: /nexusadmin toggle <vault|itemsadder>");
            return;
        }
        String feature = args[1].toLowerCase();
        String path = switch (feature) {
            case "vault" -> "integrations.vault.enabled";
            case "itemsadder" -> "integrations.itemsadder.enabled";
            default -> null;
        };
        if (path == null) {
            msg(sender, "&cUsage: /nexusadmin toggle <vault|itemsadder>");
            return;
        }

        boolean newState = !plugin.getConfig().getBoolean(path, true);
        plugin.getConfig().set(path, newState);
        plugin.saveConfig();

        if (feature.equals("vault")) {
            plugin.getEconomyManager().refresh();
        } else {
            plugin.getItemsAdderManager().reload();
        }

        String label = feature.equals("vault") ? "Vault" : "ItemsAdder (en preparation)";
        String state = newState ? "&aActive" : "&cDesactive";
        msg(sender, plugin.getConfig().getString("messages.admin-toggle")
                .replace("{feature}", label).replace("{state}", ChatColor.translateAlternateColorCodes('&', state)));
    }

    private void handleNetwork(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "&cUsage: /nexusadmin network <joueur> [settier <n>|wipe|kickall]");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(target.getUniqueId());
        if (network == null) {
            msg(sender, plugin.getConfig().getString("messages.admin-network-not-found"));
            return;
        }

        if (args.length == 2) {
            msg(sender, "&7Reseau de &f" + target.getName() + "&7: nom=&f" + network.getName()
                    + "&7, tier=&f" + network.getTier() + "&7, membres=&f" + network.getMembers().size());
            return;
        }

        switch (args[2].toLowerCase()) {
            case "settier" -> {
                if (args.length < 4) {
                    msg(sender, "&cUsage: /nexusadmin network <joueur> settier <1-5>");
                    return;
                }
                try {
                    int tier = Math.max(1, Math.min(5, Integer.parseInt(args[3])));
                    network.setTier(tier);
                    plugin.getAccessManager().saveNetworkMeta(network);
                    msg(sender, "&aTier de " + target.getName() + " defini a " + tier + ".");
                } catch (NumberFormatException e) {
                    msg(sender, "&cTier invalide.");
                }
            }
            case "wipe" -> {
                plugin.getStorageManager().deleteAll(target.getUniqueId());
                msg(sender, "&aStockage de " + target.getName() + " efface.");
            }
            case "kickall" -> {
                network.getMembers().keySet().clear();
                plugin.getAccessManager().saveNetworkMeta(network);
                msg(sender, "&aTous les membres de " + target.getName() + " ont ete retires.");
            }
            default -> msg(sender, "&cUsage: /nexusadmin network <joueur> [settier <n>|wipe|kickall]");
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            msg(sender, "&cUsage: /nexusadmin give <joueur> <core|tablet|chestlink|upgrade> [tier]");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            msg(sender, "&cJoueur introuvable ou hors ligne: " + args[1]);
            return;
        }

        switch (args[2].toLowerCase()) {
            case "chestlink" -> target.getInventory().addItem(plugin.buildChestLinkItem());
            case "upgrade" -> {
                int tier = 1;
                if (args.length >= 4) {
                    try {
                        tier = Math.max(1, Math.min(3, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                target.getInventory().addItem(plugin.buildUpgradeCrystal(tier));
            }
            default -> {
                msg(sender, "&cType inconnu. Utilise /nexus give <type> pour les blocs standards.");
                return;
            }
        }
        msg(sender, "&aItem donne a " + target.getName() + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("gui", "reload", "toggle", "network", "give"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            options.addAll(List.of("vault", "itemsadder"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("network") || args[0].equalsIgnoreCase("give"))) {
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("network")) {
            options.addAll(List.of("settier", "wipe", "kickall"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            options.addAll(List.of("chestlink", "upgrade"));
        }
        return options.stream().filter(o -> o.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).toList();
    }
}
