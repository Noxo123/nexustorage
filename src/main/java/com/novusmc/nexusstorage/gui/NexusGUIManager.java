package com.novusmc.nexusstorage.gui;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.gui.holders.NexusAccessHolder;
import com.novusmc.nexusstorage.gui.holders.NexusMainHolder;
import com.novusmc.nexusstorage.gui.holders.NexusStorageHolder;
import com.novusmc.nexusstorage.gui.holders.NexusUpgradeHolder;
import com.novusmc.nexusstorage.managers.NexusUpgradeManager;
import com.novusmc.nexusstorage.model.AccessLevel;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
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
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.translateAlternateColorCodes('&', "&5&lNexus Storage"));
        holder.setInventory(inv);

        for (int i = 0; i < 27; i++) inv.setItem(i, filler());

        inv.setItem(10, named(Material.AMETHYST_SHARD, "&d&l📦 Storage",
                "&7Acceder a ton stockage virtuel", "&7interconnecte.", "", "&aClique pour ouvrir"));
        inv.setItem(12, named(Material.PLAYER_HEAD, "&b&l👥 Access",
                "&7Gerer les membres autorises", "&7a acceder a ton reseau.", "", "&aClique pour ouvrir"));
        inv.setItem(14, named(Material.NETHER_STAR, "&e&l💰 Upgrades",
                "&7Tier actuel: &f" + network.getTier() + "/" + NexusUpgradeManager.MAX_TIER,
                "&7Ameliore ton reseau pour plus", "&7de pages de stockage.", "", "&aClique pour ouvrir"));
        inv.setItem(16, named(Material.COMPARATOR, "&7&l⚙ Settings",
                "&7Parametres de ton reseau Nexus.", "", "&aClique pour ouvrir"));
        inv.setItem(4, named(Material.CLOCK, "&f&l📱 Tablet Link",
                "&7Owner: &f" + Bukkit.getOfflinePlayer(network.getOwner()).getName(),
                "&7Cores actifs: &f" + network.getCorePositions().size()));

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
}
