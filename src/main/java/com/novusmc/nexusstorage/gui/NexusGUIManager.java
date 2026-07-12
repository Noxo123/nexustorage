package com.novusmc.nexusstorage.gui;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.gui.holders.NexusAccessHolder;
import com.novusmc.nexusstorage.gui.holders.NexusEnergyHolder;
import com.novusmc.nexusstorage.gui.holders.NexusMainHolder;
import com.novusmc.nexusstorage.gui.holders.NexusSettingsHolder;
import com.novusmc.nexusstorage.gui.holders.NexusStorageHolder;
import com.novusmc.nexusstorage.gui.holders.NexusUpgradeHolder;
import com.novusmc.nexusstorage.managers.EnergyManager;
import com.novusmc.nexusstorage.managers.NexusUpgradeManager;
import com.novusmc.nexusstorage.model.AccessLevel;
import com.novusmc.nexusstorage.model.EnergyGraph;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Construit tous les GUI de NexusStorage : menu principal, pages de
 * stockage, gestion des acces et amelioration de tier.
 */
public class NexusGUIManager {

    private final Main plugin;

    public NexusGUIManager(Main plugin) {
        this.plugin = plugin;
    }

    private ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (lore.length > 0) {
            meta.setLore(List.of(lore).stream()
                    .map(l -> ChatColor.translateAlternateColorCodes('&', l))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    // ================= MENU PRINCIPAL =================

    public void openMainMenu(Player player, NexusNetwork network) {
        NexusMainHolder holder = new NexusMainHolder(network.getOwner());
        String title = network.getName() != null ? network.getName() : "Nexus Storage";
        Inventory inv = Bukkit.createInventory(holder, 36, ChatColor.translateAlternateColorCodes('&', "&5&l" + title));
        holder.setInventory(inv);

        for (int i = 0; i < 36; i++) inv.setItem(i, filler());

        inv.setItem(4, named(Material.CLOCK, "&f&l📱 Tablet Link",
                "&7Owner: &f" + Bukkit.getOfflinePlayer(network.getOwner()).getName(),
                "&7Cores actifs: &f" + network.getCorePositions().size()));

        inv.setItem(19, named(Material.AMETHYST_SHARD, "&d&l📦 Storage",
                "&7Acceder a ton stockage virtuel", "&7interconnecte.", "", "&aClique pour ouvrir"));
        inv.setItem(21, named(Material.PLAYER_HEAD, "&b&l👥 Access",
                "&7Gerer les membres autorises", "&7a acceder a ton reseau.", "", "&aClique pour ouvrir"));
        inv.setItem(23, named(Material.NETHER_STAR, "&e&l💰 Upgrades",
                "&7Tier actuel: &f" + network.getTier() + "/" + NexusUpgradeManager.MAX_TIER,
                "&7Ameliore ton reseau pour plus", "&7de pages de stockage.", "", "&aClique pour ouvrir"));
        inv.setItem(25, named(Material.COMPARATOR, "&7&l⚙ Settings",
                "&7Statistiques et gestion", "&7de ton reseau Nexus.", "", "&aClique pour ouvrir"));

        EnergyManager.EnergyStats stats = plugin.getEnergyManager().getStatsForOwner(network.getOwner());
        inv.setItem(31, named(Material.SEA_LANTERN, "&e&l⚡ Energy",
                "&7Reseaux d'energie: &f" + stats.networkCount(),
                "&7Stockee: &f" + stats.stored() + " / " + stats.capacity(),
                "&7Production: &f+" + Math.round(stats.production()) + "&7/cycle",
                "", "&aClique pour ouvrir"));

        player.openInventory(inv);
    }

    // ================= STORAGE =================

    public void openStoragePage(Player player, NexusNetwork network, int page) {
        int maxPages = plugin.getUpgradeManager().getPagesForTier(network.getTier());
        page = Math.max(0, Math.min(page, maxPages - 1));

        NexusStorageHolder holder = new NexusStorageHolder(network.getOwner(), page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.translateAlternateColorCodes('&', "&5Nexus Storage &7- Page " + (page + 1) + "/" + maxPages));
        holder.setInventory(inv);

        ItemStack[] contents = plugin.getStorageManager().loadPage(network.getOwner(), page);
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) inv.setItem(i, contents[i]);
        }

        for (int i = 45; i < 54; i++) inv.setItem(i, filler());
        if (page > 0) {
            inv.setItem(45, named(Material.ARROW, "&a« Page precedente"));
        }
        inv.setItem(49, named(Material.BOOK, "&fPage " + (page + 1) + "/" + maxPages));
        if (page < maxPages - 1) {
            inv.setItem(53, named(Material.ARROW, "&aPage suivante »"));
        }

        player.openInventory(inv);
    }

    // ================= ACCESS =================

    public void openAccessMenu(Player player, NexusNetwork network) {
        NexusAccessHolder holder = new NexusAccessHolder(network.getOwner());
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.translateAlternateColorCodes('&', "&bNexus Access"));
        holder.setInventory(inv);

        for (int i = 45; i < 54; i++) inv.setItem(i, filler());
        inv.setItem(49, named(Material.EMERALD, "&a+ Ajouter un membre",
                "&7Tape le pseudo du joueur", "&7dans le chat apres avoir clique."));

        int slot = 0;
        for (Map.Entry<UUID, AccessLevel> entry : network.getMembers().entrySet()) {
            if (slot >= 45) break;
            OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getKey());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&f" + target.getName()));
            meta.setLore(List.of(
                    ChatColor.translateAlternateColorCodes('&', "&7Permission: &f" + entry.getValue().getLabel()),
                    ChatColor.translateAlternateColorCodes('&', "&eClique gauche pour changer le niveau"),
                    ChatColor.translateAlternateColorCodes('&', "&cClique droit pour retirer")
            ));
            head.setItemMeta(meta);
            inv.setItem(slot, head);
            slot++;
        }

        player.openInventory(inv);
    }

    // ================= UPGRADES =================

    public void openUpgradeMenu(Player player, NexusNetwork network) {
        NexusUpgradeHolder holder = new NexusUpgradeHolder(network.getOwner());
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.translateAlternateColorCodes('&', "&eNexus Upgrades"));
        holder.setInventory(inv);

        for (int i = 0; i < 27; i++) inv.setItem(i, filler());

        int[] slots = {10, 11, 13, 15, 16};
        for (int tier = 1; tier <= NexusUpgradeManager.MAX_TIER; tier++) {
            int pages = plugin.getUpgradeManager().getPagesForTier(tier);
            double cost = plugin.getUpgradeManager().getCostForTier(tier);
            String status;
            Material mat;
            if (tier < network.getTier() + 1) {
                status = "&a✔ Debloque";
                mat = Material.LIME_DYE;
            } else if (tier == network.getTier() + 1) {
                status = "&eCout: &f" + plugin.getEconomyManager().format(cost);
                mat = Material.NETHER_STAR;
            } else {
                status = "&cNiveau precedent requis";
                mat = Material.GRAY_DYE;
            }
            inv.setItem(slots[tier - 1], named(mat, "&d&lTier " + tier,
                    "&7Pages de stockage: &f" + pages,
                    status));
        }

        player.openInventory(inv);
    }

    // ================= SETTINGS =================

    public void openSettingsMenu(Player player, NexusNetwork network) {
        NexusSettingsHolder holder = new NexusSettingsHolder(network.getOwner());
        Inventory inv = Bukkit.createInventory(holder, 36, ChatColor.translateAlternateColorCodes('&', "&7Nexus Settings"));
        holder.setInventory(inv);

        for (int i = 0; i < 36; i++) inv.setItem(i, filler());

        EnergyManager.EnergyStats stats = plugin.getEnergyManager().getStatsForOwner(network.getOwner());
        int maxPages = plugin.getUpgradeManager().getPagesForTier(network.getTier());

        // --- Stats ---
        inv.setItem(10, named(Material.BOOK, "&b&lInformations reseau",
                "&7Nom: &f" + network.getName(),
                "&7Owner: &f" + Bukkit.getOfflinePlayer(network.getOwner()).getName(),
                "&7Membres: &f" + network.getMembers().size(),
                "&7Tier: &f" + network.getTier() + "/" + NexusUpgradeManager.MAX_TIER,
                "&7Pages disponibles: &f" + maxPages));

        inv.setItem(12, named(Material.SEA_LANTERN, "&e&lStats energie",
                "&7Reseaux physiques: &f" + stats.networkCount(),
                "&7Machines: &f" + stats.machineCount(),
                "&7Cables: &f" + stats.cableCount(),
                "&7Stockee: &f" + stats.stored() + " / " + stats.capacity(),
                "&7Production: &f+" + Math.round(stats.production()) + "&7/cycle",
                "&7Consommation: &f-" + Math.round(stats.consumption()) + "&7/cycle"));

        // --- Gestion ---
        inv.setItem(19, named(Material.NAME_TAG, "&a&lRenommer le reseau",
                "&7Nom actuel: &f" + network.getName(),
                "", "&aClique pour taper un nouveau nom dans le chat"));

        boolean notif = network.isNotificationsEnabled();
        inv.setItem(21, named(notif ? Material.BELL : Material.BARRIER,
                "&e&lNotifications: " + (notif ? "&aActivees" : "&cDesactivees"),
                "&7Alertes (energie faible, etc).", "", "&aClique pour basculer"));

        inv.setItem(23, named(Material.SHIELD, "&c&lSecurite",
                "&7Membres actuels: &f" + network.getMembers().size(),
                "&7Gere les acces depuis l'onglet Access.", "",
                "&aClique pour ouvrir Access"));

        inv.setItem(25, named(Material.BARRIER, "&4&lRetour",
                "&7Retour au menu principal."));

        player.openInventory(inv);
    }

    // ================= ENERGY DASHBOARD =================

    /** Ouvert depuis un Nexus Energy Monitor place dans le monde : stats de SON reseau physique uniquement. */
    public void openEnergyMonitor(Player player, Location monitorLocation) {
        EnergyGraph graph = plugin.getEnergyManager().getGraphContaining(monitorLocation);
        if (graph == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.core-required")));
            return;
        }
        openEnergyDashboard(player, graph, monitorLocation);
    }

    /** Ouvert depuis le menu principal : vue agregee de tous les reseaux physiques de l'owner. */
    public void openEnergyOverview(Player player, NexusNetwork network) {
        EnergyManager.EnergyStats stats = plugin.getEnergyManager().getStatsForOwner(network.getOwner());
        NexusEnergyHolder holder = new NexusEnergyHolder(network.getOwner(), null);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.translateAlternateColorCodes('&', "&e⚡ Energie - Vue globale"));
        holder.setInventory(inv);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler());

        inv.setItem(10, named(Material.BEACON, "&d&lReseaux physiques",
                "&7Nombre de reseaux (Energy Core): &f" + stats.networkCount()));
        inv.setItem(12, named(Material.NETHERITE_BLOCK, "&b&lStockage",
                "&7Energie stockee: &f" + stats.stored(),
                "&7Capacite totale: &f" + stats.capacity()));
        inv.setItem(14, named(Material.SUNFLOWER, "&e&lProduction / cycle",
                "&7+&f" + Math.round(stats.production()) + " &7energie/cycle"));
        inv.setItem(16, named(Material.HOPPER, "&a&lConsommation / cycle",
                "&7-&f" + Math.round(stats.consumption()) + " &7energie/cycle (Interfaces)"));
        inv.setItem(22, named(Material.CHAIN, "&f&lMachines connectees",
                "&7Cables: &f" + stats.cableCount(),
                "&7Total machines: &f" + stats.machineCount()));

        player.openInventory(inv);
    }

    private void openEnergyDashboard(Player player, EnergyGraph graph, Location monitorLocation) {
        if (graph == null) return; // openEnergyOverview gere l'affichage agrege separement

        NexusEnergyHolder holder = new NexusEnergyHolder(graph.getOwnerId(), monitorLocation);
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.translateAlternateColorCodes('&', "&e⚡ Nexus Energy Monitor"));
        holder.setInventory(inv);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler());

        long stored = plugin.getEnergyManager().totalStoredOf(graph);
        inv.setItem(10, named(Material.NETHERITE_BLOCK, "&b&lStockage local",
                "&7Energie: &f" + stored + " / " + graph.getTotalCapacity(),
                "&7Remplissage: &f" + Math.round(graph.getFillPercent()) + "%"));
        inv.setItem(12, named(Material.SUNFLOWER, "&e&lProduction",
                "&7+&f" + Math.round(graph.getLastProduction()) + " &7/cycle",
                "&7Sources: &f" + graph.getSources().size()));
        inv.setItem(14, named(Material.HOPPER, "&a&lConsommation",
                "&7-&f" + Math.round(graph.getLastConsumption()) + " &7/cycle",
                "&7Interfaces: &f" + graph.getInterfaces().size(),
                graph.isInterfacesPaused() ? "&c⚠ En pause (energie faible)" : "&aActif"));
        inv.setItem(16, named(Material.CHAIN, "&f&lStructure",
                "&7Cables: &f" + graph.getCables().size(),
                "&7Regulateurs: &f" + graph.getRegulators().size(),
                "&7Moniteurs: &f" + graph.getMonitors().size()));

        player.openInventory(inv);
    }
}
