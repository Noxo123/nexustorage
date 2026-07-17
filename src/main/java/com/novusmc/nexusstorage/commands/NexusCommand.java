package com.novusmc.nexusstorage.commands;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.EnergyBlockType;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
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
 * Gere les sous-commandes du plugin NexusStorage.
 */
public class NexusCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public NexusCommand(Main plugin) { this.plugin = plugin; }

    private void msg(CommandSender s, String m) {
        s.sendMessage(ChatColor.translateAlternateColorCodes('&', m));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            msg(sender, "&cCette commande doit etre executee par un joueur.");
            return true;
        }

        if (args.length == 0) {
            NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
            if (network == null) { msg(player, plugin.getConfig().getString("messages.no-network")); return true; }
            plugin.getGuiManager().openMainMenu(player, network);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give"    -> handleGive(player, args);
            case "upgrade" -> handleUpgrade(player);
            case "access"  -> handleAccess(player);
            case "energy"  -> handleEnergy(player);
            default        -> msg(player, "&cUsage: /nexus [give|upgrade|access|energy]");
        }
        return true;
    }

    // ── give ──────────────────────────────────────────────────────────────

    private void handleGive(Player player, String[] args) {
        if (args.length < 2) { msg(player, "&cUsage: /nexus give <core|tablet|...>"); return; }
        if (!player.hasPermission("nexusstorage.admin")) { msg(player, "&cPermission refusee."); return; }

        switch (args[1].toLowerCase()) {
            case "core" -> {
                // Utilisation du manager d'ItemsAdder
                ItemStack core = plugin.getItemsAdderManager().resolve("nexus-core");
                ItemMeta meta = core.getItemMeta();
                if (meta != null) {
                    // On s'assure d'injecter la clé PDC nécessaire au fonctionnement du plugin si elle n'y est pas déjà
                    if (!meta.getPersistentDataContainer().has(plugin.getNexusCoreKey(), PersistentDataType.BOOLEAN)) {
                        meta.getPersistentDataContainer().set(plugin.getNexusCoreKey(), PersistentDataType.BOOLEAN, true);
                        core.setItemMeta(meta);
                    }
                }
                player.getInventory().addItem(core);
                msg(player, "&aTu as recu un Nexus Core.");
            }
            case "tablet" -> {
                // Utilisation du manager d'ItemsAdder
                ItemStack tablet = plugin.getItemsAdderManager().resolve("nexus-tablet");
                ItemMeta meta = tablet.getItemMeta();
                if (meta != null) {
                    // Injection de la clé PDC pour faire fonctionner la tablette au clic droit
                    if (!meta.getPersistentDataContainer().has(plugin.getNexusTabletKey(), PersistentDataType.BOOLEAN)) {
                        meta.getPersistentDataContainer().set(plugin.getNexusTabletKey(), PersistentDataType.BOOLEAN, true);
                        tablet.setItemMeta(meta);
                    }
                }
                player.getInventory().addItem(tablet);
                msg(player, "&aTu as recu une Nexus Tablet.");
            }
            case "solarpanel"     -> give(player, EnergyBlockType.SOLAR_PANEL_BASIC);
            case "solarpanel2"    -> give(player, EnergyBlockType.SOLAR_PANEL_ADVANCED);
            case "capacitor"      -> give(player, EnergyBlockType.CAPACITOR_BASIC);
            case "capacitor2"     -> give(player, EnergyBlockType.CAPACITOR_ADVANCED);
            case "cable"          -> give(player, EnergyBlockType.CABLE_BASIC);
            case "cable2"         -> give(player, EnergyBlockType.CABLE_INSULATED);
            case "interface"      -> give(player, EnergyBlockType.INTERFACE_BLOCK);
            case "electricfurnace" -> give(player, EnergyBlockType.ELECTRIC_FURNACE);
            case "energycore"     -> give(player, EnergyBlockType.ENERGY_CORE);
            case "regulator"      -> give(player, EnergyBlockType.REDSTONE_REGULATOR);
            case "monitor"        -> give(player, EnergyBlockType.ENERGY_MONITOR);
            case "chestlink"      -> { 
                // Récupération via ItemsAdder au lieu du build en dur
                ItemStack chestLink = plugin.getItemsAdderManager().resolve("nexus-chestlink");
                player.getInventory().addItem(chestLink); 
                msg(player, "&aTu as recu un Nexus Chest Link."); 
            }
            case "connectedblock" -> { 
                // Récupération via ItemsAdder au lieu du build en dur
                ItemStack connectedBlock = plugin.getItemsAdderManager().resolve("nexus-connected-block");
                player.getInventory().addItem(connectedBlock); 
                msg(player, "&aTu as recu un Nexus Connected Block."); 
            }
            case "upgrade"        -> {
                int tier = 1;
                if (args.length >= 3) try { tier = Math.max(1, Math.min(3, Integer.parseInt(args[2]))); } catch (NumberFormatException ignored) {}
                player.getInventory().addItem(plugin.buildUpgradeCrystal(tier));
                msg(player, "&aTu as recu un Nexus Upgrade Crystal [Tier " + tier + "].");
            }
            default -> msg(player, "&cItem inconnu.");
        }
    }

    private void give(Player player, EnergyBlockType type) {
        player.getInventory().addItem(plugin.buildEnergyItem(type));
        msg(player, "&aTu as recu: &f" + type.getDisplayName().replaceAll("&[0-9a-fk-orA-FK-OR]", ""));
    }

    // ── upgrade ───────────────────────────────────────────────────────────

    private void handleUpgrade(Player player) {
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) { msg(player, plugin.getConfig().getString("messages.no-network")); return; }
        plugin.getGuiManager().openUpgradeMenu(player, network);
    }

    // ── access ────────────────────────────────────────────────────────────

    private void handleAccess(Player player) {
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) { msg(player, plugin.getConfig().getString("messages.no-network")); return; }
        plugin.getGuiManager().openAccessMenu(player, network);
    }

    // ── energy (marche d'energie) ─────────────────────────────────────────

    private void handleEnergy(Player player) {
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) { msg(player, plugin.getConfig().getString("messages.no-network")); return; }
        plugin.getGuiManager().openEnergyMarket(player, network);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) options.addAll(List.of("give", "upgrade", "access", "energy"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("give"))
            options.addAll(List.of("core", "tablet", "connectedblock", "solarpanel", "solarpanel2",
                    "capacitor", "capacitor2", "cable", "cable2", "interface", "electricfurnace",
                    "energycore", "regulator", "monitor", "chestlink", "upgrade"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("upgrade"))
            options.addAll(List.of("1", "2", "3"));
        return options.stream().filter(o -> o.startsWith(args[args.length - 1].toLowerCase())).toList();
    }
}
