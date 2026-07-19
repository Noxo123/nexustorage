package com.novusmc.nexusstorage.commands;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.EnergyBlockType;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
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
 * /nexus — commande joueur principale.
 * Inclut maintenant /nexus give <item> pour que les joueurs puissent
 * obtenir leurs blocs via commande (si permission accordee).
 */
public class NexusCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    private static final List<String> GIVE_ITEMS = List.of(
            "core", "tablet", "chestlink", "upgrade",
            "solarpanel", "solarpanel2", "capacitor", "capacitor2",
            "cable", "cable2", "interface", "electricfurnace",
            "energycore", "regulator", "monitor", "connectedblock"
    );

    public NexusCommand(Main plugin) { 
        this.plugin = plugin; 
    }

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
            if (network == null) {
                msg(player, plugin.getConfig().getString("messages.no-network"));
                return true;
            }
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

    // ── give ─────────────────────────────────────────────────────────────

    private void handleGive(Player player, String[] args) {
        if (!player.hasPermission("nexusstorage.give") && !player.hasPermission("nexusstorage.admin")) {
            msg(player, "&cTu n'as pas la permission d'utiliser /nexus give.");
            return;
        }
        if (args.length < 2) {
            msg(player, "&cUsage: /nexus give <" + String.join("|", GIVE_ITEMS) + ">");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "core" -> {
                ItemStack item = plugin.getItemsAdderManager().resolve("nexus-core");
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&d&lNexus Core"));
                    meta.getPersistentDataContainer().set(plugin.getNexusCoreKey(), PersistentDataType.BOOLEAN, true);
                    item.setItemMeta(meta);
                }
                player.getInventory().addItem(item);
                msg(player, "&aTu as recu un &dNexus Core&a.");
            }
            case "tablet" -> {
                ItemStack item = plugin.getItemsAdderManager().resolve("nexus-tablet");
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b&lNexus Tablet"));
                    meta.getPersistentDataContainer().set(plugin.getNexusTabletKey(), PersistentDataType.BOOLEAN, true);
                    item.setItemMeta(meta);
                }
                player.getInventory().addItem(item);
                msg(player, "&aTu as recu une &bNexus Tablet&a.");
            }
            case "chestlink" -> {
                player.getInventory().addItem(plugin.buildChestLinkItem());
                msg(player, "&aTu as recu un &6Nexus Chest Link&a.");
            }
            case "connectedblock" -> {
                player.getInventory().addItem(plugin.buildConnectedBlockItem());
                msg(player, "&aTu as recu un &dNexus Connected Block&a.");
            }
            case "upgrade" -> {
                int tier = 1;
                if (args.length >= 3) { 
                    try { 
                        tier = Math.max(1, Math.min(3, Integer.parseInt(args[2]))); 
                    } catch (NumberFormatException ignored) {} 
                }
                player.getInventory().addItem(plugin.buildUpgradeCrystal(tier));
                msg(player, "&aTu as recu un &dNexus Upgrade Crystal &7[Tier " + tier + "]&a.");
            }
            case "solarpanel"      -> giveEnergy(player, EnergyBlockType.SOLAR_PANEL_BASIC);
            case "solarpanel2"     -> giveEnergy(player, EnergyBlockType.SOLAR_PANEL_ADVANCED);
            case "capacitor"       -> giveEnergy(player, EnergyBlockType.CAPACITOR_BASIC);
            case "capacitor2"      -> giveEnergy(player, EnergyBlockType.CAPACITOR_ADVANCED);
            case "cable"           -> giveEnergy(player, EnergyBlockType.CABLE_BASIC);
            case "cable2"          -> giveEnergy(player, EnergyBlockType.CABLE_INSULATED);
            case "interface"       -> giveEnergy(player, EnergyBlockType.INTERFACE_BLOCK);
            case "electricfurnace" -> giveEnergy(player, EnergyBlockType.ELECTRIC_FURNACE);
            case "energycore"      -> giveEnergy(player, EnergyBlockType.ENERGY_CORE);
            case "regulator"       -> giveEnergy(player, EnergyBlockType.REDSTONE_REGULATOR);
            case "monitor"         -> giveEnergy(player, EnergyBlockType.ENERGY_MONITOR);
            default -> msg(player, "&cItem inconnu. Utilise /nexus give <item>.");
        }
    }

    private void giveEnergy(Player player, EnergyBlockType type) {
        player.getInventory().addItem(plugin.buildEnergyItem(type));
        String name = type.getDisplayName().replaceAll("&[0-9a-fk-orA-FK-OR]", "");
        msg(player, "&aTu as recu: &f" + name);
    }

    // ── autres ───────────────────────────────────────────────────────────

    private void handleUpgrade(Player player) {
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) { msg(player, plugin.getConfig().getString("messages.no-network")); return; }
        plugin.getGuiManager().openUpgradeMenu(player, network);
    }

    private void handleAccess(Player player) {
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) { msg(player, plugin.getConfig().getString("messages.no-network")); return; }
        plugin.getGuiManager().openAccessMenu(player, network);
    }

    private void handleEnergy(Player player) {
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) { msg(player, plugin.getConfig().getString("messages.no-network")); return; }
        plugin.getGuiManager().openEnergyMarket(player, network);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        
        if (args.length == 1) {
            options.add("upgrade");
            options.add("access");
            options.add("energy");
            // N'affiche le sous-argument "give" que si le joueur a le droit de l'utiliser
            if (sender.hasPermission("nexusstorage.give") || sender.hasPermission("nexusstorage.admin")) {
                options.add("give");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            if (sender.hasPermission("nexusstorage.give") || sender.hasPermission("nexusstorage.admin")) {
                options.addAll(GIVE_ITEMS);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("upgrade")) {
            if (sender.hasPermission("nexusstorage.give") || sender.hasPermission("nexusstorage.admin")) {
                options.addAll(List.of("1", "2", "3"));
            }
        }
        
        String lastArg = args[args.length - 1].toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lastArg))
                .toList();
    }
}
