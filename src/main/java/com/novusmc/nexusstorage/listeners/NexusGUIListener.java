package com.novusmc.nexusstorage.listeners;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.gui.holders.NexusAccessHolder;
import com.novusmc.nexusstorage.gui.holders.NexusMainHolder;
import com.novusmc.nexusstorage.gui.holders.NexusStorageHolder;
import com.novusmc.nexusstorage.gui.holders.NexusUpgradeHolder;
import com.novusmc.nexusstorage.model.AccessLevel;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ecoute tous les clics dans les GUI Nexus (menu principal, storage,
 * access, upgrades) ainsi que les fermetures d'inventaire (sauvegarde du
 * stockage) et la saisie chat pour ajouter un membre au reseau.
 */
public class NexusGUIListener implements Listener {

    private final Main plugin;

    /** Joueurs en attente de taper un pseudo dans le chat pour ajouter un membre. Valeur = owner du reseau cible. */
    private final Map<UUID, UUID> pendingAddMember = new HashMap<>();

    public NexusGUIListener(Main plugin) {
        this.plugin = plugin;
    }

    // ================= MENU PRINCIPAL =================

    @EventHandler
    public void onMainClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NexusMainHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        Player player = (Player) event.getWhoClicked();
        NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(holder.getOwner());

        switch (clicked.getType()) {
            case AMETHYST_SHARD -> plugin.getGuiManager().openStoragePage(player, network, 0);
            case PLAYER_HEAD -> plugin.getGuiManager().openAccessMenu(player, network);
            case NETHER_STAR -> plugin.getGuiManager().openUpgradeMenu(player, network);
            case COMPARATOR -> player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&7Les parametres avances arrivent dans une prochaine mise a jour."));
            default -> {}
        }
    }

    // ================= STORAGE =================

    @EventHandler
    public void onStorageClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NexusStorageHolder holder)) return;

        int rawSlot = event.getRawSlot();
        boolean inTopInventory = rawSlot >= 0 && rawSlot < event.getInventory().getSize();

        // Clic dans la rangee de navigation (slots 45-53) : toujours annule.
        if (inTopInventory && rawSlot >= 45) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(holder.getOwner());
            int maxPages = plugin.getUpgradeManager().getPagesForTier(network.getTier());

            if (rawSlot == 45 && holder.getPage() > 0) {
                double cost = plugin.getConfig().getDouble("economy.cost-change-page", 0.0);
                if (plugin.getEconomyManager().withdraw(player, cost)) {
                    plugin.getGuiManager().openStoragePage(player, network, holder.getPage() - 1);
                } else {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("messages.not-enough-money")));
                }
            } else if (rawSlot == 53 && holder.getPage() < maxPages - 1) {
                double cost = plugin.getConfig().getDouble("economy.cost-change-page", 0.0);
                if (plugin.getEconomyManager().withdraw(player, cost)) {
                    plugin.getGuiManager().openStoragePage(player, network, holder.getPage() + 1);
                } else {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("messages.not-enough-money")));
                }
            }
            return;
        }

        // Verification des permissions pour deposer / retirer dans la zone de stockage (slots 0-44).
        NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(holder.getOwner());
        Player player = (Player) event.getWhoClicked();
        AccessLevel level = network.getAccessFor(player.getUniqueId());
        if (level == null) {
            event.setCancelled(true);
            return;
        }

        if (inTopInventory) {
            boolean placingItem = event.getCursor() != null && event.getCursor().getType() != Material.AIR;
            boolean takingItem = event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR;
            if (placingItem && !level.canDeposit()) {
                event.setCancelled(true);
            } else if (takingItem && !level.canWithdraw() && !placingItem) {
                event.setCancelled(true);
            }
        } else {
            // Clic dans l'inventaire du joueur (shift-click vers le haut) : necessite le droit de deposer.
            if (event.isShiftClick() && !level.canDeposit()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof NexusStorageHolder holder)) return;

        ItemStack[] contents = new ItemStack[45];
        for (int i = 0; i < 45; i++) {
            contents[i] = event.getInventory().getItem(i);
        }
        plugin.getStorageManager().savePage(holder.getOwner(), holder.getPage(), contents);
    }

    // ================= ACCESS =================

    @EventHandler
    public void onAccessClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NexusAccessHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(holder.getOwner());

        if (!player.getUniqueId().equals(network.getOwner())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cSeul le proprietaire peut gerer les acces."));
            return;
        }

        if (clicked.getType() == Material.EMERALD) {
            pendingAddMember.put(player.getUniqueId(), network.getOwner());
            player.closeInventory();
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&aTape le pseudo du joueur a ajouter dans le chat (ou 'annuler')."));
            return;
        }

        if (clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta skullMeta
                && skullMeta.getOwningPlayer() != null) {
            OfflinePlayer target = skullMeta.getOwningPlayer();
            UUID targetId = target.getUniqueId();

            if (event.isRightClick()) {
                plugin.getAccessManager().removeMember(network, targetId);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.member-removed")));
            } else {
                AccessLevel current = network.getAccessFor(targetId);
                AccessLevel next = switch (current == null ? AccessLevel.READ_ONLY : current) {
                    case READ_ONLY -> AccessLevel.DEPOSIT;
                    case DEPOSIT -> AccessLevel.WITHDRAW;
                    case WITHDRAW -> AccessLevel.ADMIN;
                    case ADMIN -> AccessLevel.READ_ONLY;
                };
                plugin.getAccessManager().addMember(network, targetId, next);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&aPermission de " + target.getName() + " changee en: &f" + next.getLabel()));
            }
            plugin.getGuiManager().openAccessMenu(player, network);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID ownerId = pendingAddMember.get(player.getUniqueId());
        if (ownerId == null) return;

        event.setCancelled(true);
        pendingAddMember.remove(player.getUniqueId());
        String message = event.getMessage().trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Ajout de membre annule."));
                return;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(message);
            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cJoueur introuvable: " + message));
                return;
            }

            double cost = plugin.getConfig().getDouble("economy.cost-add-member", 0.0);
            if (!plugin.getEconomyManager().withdraw(player, cost)) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.not-enough-money")));
                return;
            }

            NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(ownerId);
            plugin.getAccessManager().addMember(network, target.getUniqueId(), AccessLevel.READ_ONLY);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.member-added")));
        });
    }

    // ================= UPGRADES =================

    @EventHandler
    public void onUpgradeClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NexusUpgradeHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.NETHER_STAR) return;

        Player player = (Player) event.getWhoClicked();
        NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(holder.getOwner());

        if (!player.getUniqueId().equals(network.getOwner())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cSeul le proprietaire peut ameliorer le reseau."));
            return;
        }

        if (plugin.getUpgradeManager().upgrade(player, network)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.upgrade-success")));
        } else if (plugin.getUpgradeManager().isMaxTier(network)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.upgrade-max")));
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.not-enough-money")));
        }
        plugin.getGuiManager().openUpgradeMenu(player, network);
    }
}
