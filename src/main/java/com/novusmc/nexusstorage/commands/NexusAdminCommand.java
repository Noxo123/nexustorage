package com.novusmc.nexusstorage.commands;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.EnergyBlockType;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Commande d'administration /nexusadmin : toggles Vault/ItemsAdder, reload,
 * gestion du réseau d'un joueur, configuration ItemsAdder, et distribution d'items.
 * Permission requise : nexusstorage.admin
 */
public class NexusAdminCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public NexusAdminCommand(Main plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender sender, String s) {
        if (s == null) return;
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
                msg(sender, "&cUtilise /nexusadmin gui en jeu, ou une sous-commande (reload, toggle, network, give, set).");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "gui" -> {
                if (sender instanceof Player player) {
                    plugin.getGuiManager().openAdminMenu(player);
                } else {
                    msg(sender, "&cCette sous-commande nécessite un joueur en jeu.");
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getEconomyManager().refresh();
                plugin.getItemsAdderManager().refresh();
                msg(sender, plugin.getConfig().getString("messages.admin-reloaded"));
            }
            case "toggle"  -> handleToggle(sender, args);
            case "network" -> handleNetwork(sender, args);
            case "give"    -> handleGive(sender, args);
            case "set"     -> {
                if (sender instanceof Player player) {
                    handleSet(player, args);
                } else {
                    msg(sender, "&cCette commande ne peut être exécutée que par un joueur.");
                }
            }
            default        -> msg(sender, "&cUsage: /nexusadmin [gui|reload|toggle|network|give|set]");
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
            plugin.getItemsAdderManager().refresh();
        }

        String label = feature.equals("vault") ? "Vault" : "ItemsAdder (en preparation)";
        String state = newState ? "&aActive" : "&cDesactive";
        
        String rawMsg = plugin.getConfig().getString("messages.admin-toggle");
        if (rawMsg != null) {
            msg(sender, rawMsg.replace("{feature}", label).replace("{state}", ChatColor.translateAlternateColorCodes('&', state)));
        }
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
            msg(sender, "&cUsage: /nexusadmin give <joueur> <item> [tier/quantite]");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            msg(sender, "&cJoueur introuvable ou hors ligne: " + args[1]);
            return;
        }

        String itemType = args[2].toLowerCase();
        switch (itemType) {
            case "core" -> {
                ItemStack core = plugin.getItemsAdderManager().resolve("nexus-core");
                ItemMeta meta = core.getItemMeta();
                if (meta != null) {
                    if (!meta.getPersistentDataContainer().has(plugin.getNexusCoreKey(), PersistentDataType.BOOLEAN)) {
                        meta.getPersistentDataContainer().set(plugin.getNexusCoreKey(), PersistentDataType.BOOLEAN, true);
                        core.setItemMeta(meta);
                    }
                }
                target.getInventory().addItem(core);
                msg(sender, "&aNexus Core donne à " + target.getName() + ".");
            }
            case "tablet" -> {
                ItemStack tablet = plugin.getItemsAdderManager().resolve("nexus-tablet");
                ItemMeta meta = tablet.getItemMeta();
                if (meta != null) {
                    if (!meta.getPersistentDataContainer().has(plugin.getNexusTabletKey(), PersistentDataType.BOOLEAN)) {
                        meta.getPersistentDataContainer().set(plugin.getNexusTabletKey(), PersistentDataType.BOOLEAN, true);
                        tablet.setItemMeta(meta);
                    }
                }
                target.getInventory().addItem(tablet);
                msg(sender, "&aNexus Tablet donnee à " + target.getName() + ".");
            }
            case "chestlink" -> {
                target.getInventory().addItem(plugin.getItemsAdderManager().resolve("nexus-chestlink"));
                msg(sender, "&aNexus Chest Link donne à " + target.getName() + ".");
            }
            case "connectedblock" -> {
                target.getInventory().addItem(plugin.getItemsAdderManager().resolve("nexus-connected-block"));
                msg(sender, "&aNexus Connected Block donne à " + target.getName() + ".");
            }
            case "upgrade" -> {
                int tier = 1;
                if (args.length >= 4) {
                    try {
                        tier = Math.max(1, Math.min(3, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ignored) {}
                }
                target.getInventory().addItem(plugin.buildUpgradeCrystal(tier));
                msg(sender, "&aNexus Upgrade Crystal [Tier " + tier + "] donne à " + target.getName() + ".");
            }
            case "solarpanel"      -> giveEnergyItem(sender, target, EnergyBlockType.SOLAR_PANEL_BASIC);
            case "solarpanel2"     -> giveEnergyItem(sender, target, EnergyBlockType.SOLAR_PANEL_ADVANCED);
            case "capacitor"       -> giveEnergyItem(sender, target, EnergyBlockType.CAPACITOR_BASIC);
            case "capacitor2"      -> giveEnergyItem(sender, target, EnergyBlockType.CAPACITOR_ADVANCED);
            case "cable"           -> giveEnergyItem(sender, target, EnergyBlockType.CABLE_BASIC);
            case "cable2"          -> giveEnergyItem(sender, target, EnergyBlockType.CABLE_INSULATED);
            case "interface"       -> giveEnergyItem(sender, target, EnergyBlockType.INTERFACE_BLOCK);
            case "electricfurnace" -> giveEnergyItem(sender, target, EnergyBlockType.ELECTRIC_FURNACE);
            case "energycore"      -> giveEnergyItem(sender, target, EnergyBlockType.ENERGY_CORE);
            case "regulator"       -> giveEnergyItem(sender, target, EnergyBlockType.REDSTONE_REGULATOR);
            case "monitor"         -> giveEnergyItem(sender, target, EnergyBlockType.ENERGY_MONITOR);
            case "shielddome"      -> giveEnergyItem(sender, target, EnergyBlockType.SHIELD_DOME);
            default -> msg(sender, "&cType d'item inconnu.");
        }
    }

    private void giveEnergyItem(CommandSender sender, Player target, EnergyBlockType type) {
        if (type == null) return;
        target.getInventory().addItem(plugin.buildEnergyItem(type));
        String cleanName = type.getDisplayName().replaceAll("&[0-9a-fk-orA-FK-OR]", "");
        msg(sender, "&a" + cleanName + " donne à " + target.getName() + ".");
    }

    private void handleSet(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&cUsage: /nexusadmin set <core|tablet|chestlink|connectedblock>");
            return;
        }

        String targetKey = args[1].toLowerCase();
        if (!List.of("core", "tablet", "chestlink", "connectedblock").contains(targetKey)) {
            msg(player, "&cType inconnu. Choix : core, tablet, chestlink, connectedblock");
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType().isAir()) {
            msg(player, "&cTu doit avoir l'objet d'ItemsAdder dans ta main !");
            return;
        }

        String itemsAdderId = null;
        try {
            Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object customStack = csClass.getMethod("byItemStack", ItemStack.class).invoke(null, itemInHand);

            if (customStack != null) {
                itemsAdderId = (String) csClass.getMethod("getNamespacedID").invoke(customStack);
            }
        } catch (ClassNotFoundException e) {
            msg(player, "&c[Erreur] ItemsAdder n'est pas chargé sur le serveur.");
            return;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la lecture de l'item ItemsAdder", e);
            msg(player, "&c[Erreur] Impossible de lire l'objet ItemsAdder.");
            return;
        }

        if (itemsAdderId == null || itemsAdderId.isBlank()) {
            msg(player, "&c&lAction refusee ! &cCet objet n'est pas un item ItemsAdder (c'est un item Vanilla).");
            return;
        }

        org.bukkit.configuration.file.FileConfiguration iaConfig = plugin.getItemsAdderManager().getIAConfig();
        String configKey = "nexus-" + targetKey;

        iaConfig.set(configKey + ".itemsadder", itemsAdderId);
        iaConfig.set(configKey + ".fallback", itemInHand.getType().name());

        try {
            File file = new File(plugin.getDataFolder(), "itemsadder.yml");
            iaConfig.save(file);

            msg(player, "&a&lSuccès ! &7La clé &e" + configKey + " &7a été liée à : &b" + itemsAdderId);
            msg(player, "&7Matériau de fallback défini sur : &f" + itemInHand.getType().name());

            plugin.getItemsAdderManager().reload();
        } catch (IOException e) {
            msg(player, "&cErreur lors de la sauvegarde du fichier itemsadder.yml.");
            e.printStackTrace();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (!sender.hasPermission("nexusstorage.admin")) return options;

        if (args.length == 1) {
            options.addAll(List.of("gui", "reload", "toggle", "network", "give", "set"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            options.addAll(List.of("vault", "itemsadder"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            options.addAll(List.of("core", "tablet", "chestlink", "connectedblock"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("network") || args[0].equalsIgnoreCase("give"))) {
            for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("network")) {
            options.addAll(List.of("settier", "wipe", "kickall"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            options.addAll(List.of("core", "tablet", "chestlink", "connectedblock", "upgrade", "solarpanel", 
                    "solarpanel2", "capacitor", "capacitor2", "cable", "cable2", "interface", 
                    "electricfurnace", "energycore", "regulator", "monitor", "shielddome"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give") && args[2].equalsIgnoreCase("upgrade")) {
            options.addAll(List.of("1", "2", "3"));
        }
        
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .toList();
    }
}
