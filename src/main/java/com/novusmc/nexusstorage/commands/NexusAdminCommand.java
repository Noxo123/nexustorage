package com.novusmc.nexusstorage.commands;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.EnergyBlockType;
import net.md_5.bungee.api.ChatColor;
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
 * Gère les commandes administratives du Nexus (Give et Configuration ItemsAdder).
 */
public class NexusAdminCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public NexusAdminCommand(Main plugin) {
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

        if (!player.hasPermission("nexusstorage.admin")) {
            msg(player, "&cPermission refusée.");
            return true;
        }

        if (args.length == 0) {
            msg(player, "&cUsage: /" + label + " [give|set]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> handleGive(player, args);
            case "set"  -> handleSet(player, args);
            default     -> msg(player, "&cUsage: /" + label + " [give|set]");
        }
        return true;
    }

    private void handleGive(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&cUsage: /nexusadmin give <item|upgrade>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "core" -> {
                ItemStack core = plugin.getItemsAdderManager().resolve("nexus-core");
                ItemMeta meta = core.getItemMeta();
                if (meta != null) {
                    if (!meta.getPersistentDataContainer().has(plugin.getNexusCoreKey(), PersistentDataType.BOOLEAN)) {
                        meta.getPersistentDataContainer().set(plugin.getNexusCoreKey(), PersistentDataType.BOOLEAN, true);
                        core.setItemMeta(meta);
                    }
                }
                player.getInventory().addItem(core);
                msg(player, "&aTu as reçu un Nexus Core.");
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
                player.getInventory().addItem(tablet);
                msg(player, "&aTu as reçu une Nexus Tablet.");
            }
            case "solarpanel"      -> giveEnergyItem(player, EnergyBlockType.SOLAR_PANEL_BASIC);
            case "solarpanel2"     -> giveEnergyItem(player, EnergyBlockType.SOLAR_PANEL_ADVANCED);
            case "capacitor"       -> giveEnergyItem(player, EnergyBlockType.CAPACITOR_BASIC);
            case "capacitor2"      -> giveEnergyItem(player, EnergyBlockType.CAPACITOR_ADVANCED);
            case "cable"           -> giveEnergyItem(player, EnergyBlockType.CABLE_BASIC);
            case "cable2"          -> giveEnergyItem(player, EnergyBlockType.CABLE_INSULATED);
            case "interface"       -> giveEnergyItem(player, EnergyBlockType.INTERFACE_BLOCK);
            case "electricfurnace" -> giveEnergyItem(player, EnergyBlockType.ELECTRIC_FURNACE);
            case "energycore"      -> giveEnergyItem(player, EnergyBlockType.ENERGY_CORE);
            case "regulator"       -> giveEnergyItem(player, EnergyBlockType.REDSTONE_REGULATOR);
            case "monitor"         -> giveEnergyItem(player, EnergyBlockType.ENERGY_MONITOR);
            case "chestlink"       -> {
                ItemStack chestLink = plugin.getItemsAdderManager().resolve("nexus-chestlink");
                player.getInventory().addItem(chestLink);
                msg(player, "&aTu as reçu un Nexus Chest Link.");
            }
            case "connectedblock"  -> {
                ItemStack connectedBlock = plugin.getItemsAdderManager().resolve("nexus-connected-block");
                player.getInventory().addItem(connectedBlock);
                msg(player, "&aTu as reçu un Nexus Connected Block.");
            }
            case "upgrade" -> {
                int tier = 1;
                if (args.length >= 3) {
                    try {
                        tier = Math.max(1, Math.min(3, Integer.parseInt(args[2])));
                    } catch (NumberFormatException ignored) {}
                }
                player.getInventory().addItem(plugin.buildUpgradeCrystal(tier));
                msg(player, "&aTu as reçu un Nexus Upgrade Crystal [Tier " + tier + "].");
            }
            default -> msg(player, "&cItem inconnu.");
        }
    }

    private void giveEnergyItem(Player player, EnergyBlockType type) {
        player.getInventory().addItem(plugin.buildEnergyItem(type));
        msg(player, "&aTu as reçu : &f" + type.getDisplayName().replaceAll("&[0-9a-fk-orA-FK-OR]", ""));
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
        if (itemInHand == null || itemInHand.getType().isAir()) {
            msg(player, "&cTu dois avoir l'objet d'ItemsAdder dans ta main !");
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
            msg(player, "&c&lAction refusée ! &cCet objet n'est pas un item ItemsAdder (c'est un item Vanilla).");
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

        if (args.length == 1) {
            options.addAll(List.of("give", "set"));
        } 
        else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give")) {
                options.addAll(List.of("core", "tablet", "connectedblock", "solarpanel", "solarpanel2",
                        "capacitor", "capacitor2", "cable", "cable2", "interface", "electricfurnace",
                        "energycore", "regulator", "monitor", "chestlink", "upgrade"));
            } else if (args[0].equalsIgnoreCase("set")) {
                options.addAll(List.of("core", "tablet", "chestlink", "connectedblock"));
            }
        } 
        else if (args.length == 3 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("upgrade")) {
            options.addAll(List.of("1", "2", "3"));
        }

        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .toList();
    }
}
