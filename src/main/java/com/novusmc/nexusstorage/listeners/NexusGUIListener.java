package com.novusmc.nexusstorage.listeners;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.gui.holders.NexusAccessHolder;
import com.novusmc.nexusstorage.gui.holders.NexusAdminHolder;
import com.novusmc.nexusstorage.gui.holders.NexusEnergyHolder;
import com.novusmc.nexusstorage.gui.holders.NexusMainHolder;
import com.novusmc.nexusstorage.gui.holders.NexusSettingsHolder;
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

    /** Joueurs en attente de taper un nouveau nom de reseau dans le chat. Valeur = owner du reseau cible. */
    private final Map<UUID, UUID> pendingRename = new HashMap<>();

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
            case COMPARATOR -> plugin.getGuiManager().openSettingsMenu(player, network);
            case SEA_LANTERN -> plugin.getGuiManager().openEnergyOverview(player, network);
            default -> {}
        }
    }

    // ================= STORAGE =================

    @EventHandler
    public void onStorageClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NexusStorageHolder holder)) return;
        event.setCancelled(true); // GUI 100% manuel : on gere nous-memes deposit/withdraw

        int rawSlot = event.getRawSlot();
        boolean inTopInventory = rawSlot >= 0 && rawSlot < event.getInventory().getSize();
        if (!inTopInventory) return; // clic dans l'inventaire du joueur : rien a faire ici

        Player player = (Player) event.getWhoClicked();
        NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(holder.getOwner());

        // --- Navigation (slots 45-53) ---
        if (rawSlot >= 45) {
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

        // --- Zone de stockage compacte (slots 0-44) ---
        AccessLevel level = network.getAccessFor(player.getUniqueId());
        if (level == null) return;

        int maxUniqueTypes = plugin.getUpgradeManager().getPagesForTier(network.getTier()) * 45;
        ItemStack cursor = event.getCursor();
        ItemStack clickedDisplay = event.getCurrentItem();
        boolean cursorHasItem = cursor != null && cursor.getType() != Material.AIR;
        boolean slotHasItem = clickedDisplay != null && clickedDisplay.getType() != Material.AIR;

        if (cursorHasItem) {
            // --- DEPOT : on pose le curseur entier dans une entree existante ou nouvelle ---
            if (!level.canDeposit()) return;
            String slotSig = holder.getSlotSignature(rawSlot);
            if (slotSig != null && !plugin.getStorageManager().signatureOf(cursor).equals(slotSig)) {
                return; // ne peut pas deposer un item different par-dessus une autre pile
            }

            boolean absorbed = plugin.getStorageManager().deposit(holder.getOwner(), cursor, maxUniqueTypes);
            if (!absorbed) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&cStockage plein : limite de types d'objets atteinte pour ce tier."));
                return;
            }
            event.getWhoClicked().setItemOnCursor(null);
            plugin.getGuiManager().refreshStorageViewers(holder.getOwner());
        } else if (slotHasItem) {
            // --- RETRAIT : 1 par clic normal, 10 avec shift + clic droit ---
            if (!level.canWithdraw()) return;

            String signature = holder.getSlotSignature(rawSlot);
            if (signature == null) return;

            long requested = (event.isShiftClick() && event.isRightClick()) ? 10 : 1;
            ItemStack withdrawn = plugin.getStorageManager().withdraw(holder.getOwner(), signature, requested);
            if (withdrawn == null) return;

            giveOrDrop(player, withdrawn);
            plugin.getGuiManager().refreshStorageViewers(holder.getOwner());
        }
    }

    /** Donne l'item au joueur (inventaire), ou le fait tomber a ses pieds si son inventaire est plein. */
    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
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

        UUID addMemberOwner = pendingAddMember.get(player.getUniqueId());
        UUID renameOwner = pendingRename.get(player.getUniqueId());
        if (addMemberOwner == null && renameOwner == null) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();

        if (addMemberOwner != null) {
            pendingAddMember.remove(player.getUniqueId());
            handleAddMemberInput(player, addMemberOwner, message);
        } else {
            pendingRename.remove(player.getUniqueId());
            handleRenameInput(player, renameOwner, message);
        }
    }

    private void handleAddMemberInput(Player player, UUID ownerId, String message) {
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

    private void handleRenameInput(Player player, UUID ownerId, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Renommage annule."));
                return;
            }
            if (message.length() < 3 || message.length() > 32) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cLe nom doit faire entre 3 et 32 caracteres."));
                return;
            }

            NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(ownerId);
            network.setName(message);
            plugin.getAccessManager().saveNetworkMeta(network);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.network-renamed")));
        });
    }

    // ================= SETTINGS =================

    @EventHandler
    public void onSettingsClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NexusSettingsHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(holder.getOwner());

        if (!player.getUniqueId().equals(network.getOwner())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cSeul le proprietaire peut modifier les settings."));
            return;
        }

        switch (event.getSlot()) {
            case 19 -> {
                pendingRename.put(player.getUniqueId(), network.getOwner());
                player.closeInventory();
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&aTape le nouveau nom du reseau dans le chat (ou 'annuler')."));
            }
            case 21 -> {
                network.setNotificationsEnabled(!network.isNotificationsEnabled());
                plugin.getAccessManager().saveNetworkMeta(network);
                String state = network.isNotificationsEnabled() ? "&aActivees" : "&cDesactivees";
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.notifications-toggled", "").replace("{state}", state)));
                plugin.getGuiManager().openSettingsMenu(player, network);
            }
            case 23 -> plugin.getGuiManager().openAccessMenu(player, network);
            case 25 -> plugin.getGuiManager().openMainMenu(player, network);
            default -> {}
        }
    }

    // ================= ENERGY DASHBOARD =================

    @EventHandler
    public void onEnergyClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NexusEnergyHolder holder)) return;
        event.setCancelled(true); // Tableau de bord en lecture seule
    }

    // ================= ADMIN =================

    @EventHandler
    public void onAdminClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof NexusAdminHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) return;

        Player player = (Player) event.getWhoClicked();
        if (!player.hasPermission("nexusstorage.admin")) return;

        switch (event.getSlot()) {
            case 11 -> {
                boolean current = plugin.getConfig().getBoolean("integrations.vault.enabled", true);
                plugin.getConfig().set("integrations.vault.enabled", !current);
                plugin.saveConfig();
                plugin.getEconomyManager().refresh();
                plugin.getGuiManager().openAdminMenu(player);
            }
            case 13 -> {
                boolean current = plugin.getConfig().getBoolean("integrations.itemsadder.enabled", false);
                plugin.getConfig().set("integrations.itemsadder.enabled", !current);
                plugin.saveConfig();
                plugin.getItemsAdderManager().refresh();
                plugin.getGuiManager().openAdminMenu(player);
            }
            case 15 -> {
                plugin.reloadConfig();
                plugin.getEconomyManager().refresh();
                plugin.getItemsAdderManager().refresh();
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.admin-reloaded")));
                plugin.getGuiManager().openAdminMenu(player);
            }
            default -> {}
        }
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
