package com.novusmc.nexusstorage.listeners;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.gui.holders.*;
import com.novusmc.nexusstorage.managers.EnergyMarketManager;
import com.novusmc.nexusstorage.model.AccessLevel;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
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
    // UUID joueur -> UUID owner du réseau, en attente de saisie recherche
    private final java.util.Map<java.util.UUID, java.util.UUID> pendingSearch = new java.util.concurrent.ConcurrentHashMap<>();

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

        // Vérification d'accès : propriétaire, membre direct, ou membre d'entreprise
        AccessLevel level = network.getAccessFor(player.getUniqueId());
        if (level == null && !player.getUniqueId().equals(network.getOwner())) {
            com.novusmc.nexusstorage.model.Company company =
                    plugin.getCompanyManager().getByPlayer(player.getUniqueId());
            boolean accessViaCompany = company != null && company.getOwner().equals(network.getOwner());
            if (!accessViaCompany) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cVous n'avez pas accès à ce réseau."));
                player.closeInventory();
                return;
            }
        }

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
        
        // Bloquer l'interaction par défaut immédiatement (Anti-Duplication)
        event.setCancelled(true); 

        Player player = (Player) event.getWhoClicked();
        NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(holder.getOwner());
        AccessLevel level = network.getAccessFor(player.getUniqueId());

        // Protection : S'il n'a aucun droit et n'est pas le proprio, vérifier l'entreprise
        if (level == null && !player.getUniqueId().equals(network.getOwner())) {
            com.novusmc.nexusstorage.model.Company company =
                    plugin.getCompanyManager().getByPlayer(player.getUniqueId());
            boolean accessViaCompany = company != null && company.getOwner().equals(network.getOwner());
            if (!accessViaCompany) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cVous n'avez pas accès à ce stockage."));
                player.closeInventory();
                return;
            }
            // Les membres d'entreprise ont accès WITHDRAW par défaut
            level = com.novusmc.nexusstorage.model.AccessLevel.WITHDRAW;
        }

        ClickType clickType = event.getClick();
        InventoryAction action = event.getAction();
        int rawSlot = event.getRawSlot();
        boolean inTopInventory = rawSlot >= 0 && rawSlot < event.getInventory().getSize();

        // 🛑 ANTI-DUPLICATION : Interdire TOUTES les actions et clics complexes pouvant contourner les limites
        if (clickType == ClickType.NUMBER_KEY || 
            clickType == ClickType.DOUBLE_CLICK || 
            clickType == ClickType.SWAP_OFFHAND || 
            action == InventoryAction.COLLECT_TO_CURSOR || 
            action == InventoryAction.DROP_ALL_CURSOR || 
            action == InventoryAction.DROP_ALL_SLOT || 
            action == InventoryAction.DROP_ONE_CURSOR || 
            action == InventoryAction.DROP_ONE_SLOT) {
            return;
        }

        // --- Navigation ou Actions dans le TOP Inventory (slots 45-53) ---
        if (inTopInventory && rawSlot >= 45) {
            int maxPages = plugin.getUpgradeManager().getPagesForTier(network.getTier());

            // Slot 45 : page précédente
            if (rawSlot == 45 && holder.getPage() > 0) {
                double cost = plugin.getConfig().getDouble("economy.cost-change-page", 0.0);
                if (plugin.getEconomyManager().withdraw(player, cost)) {
                    plugin.getGuiManager().openStoragePage(player, network, holder.getPage() - 1);
                } else {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.not-enough-money")));
                }
            }
            // Slot 53 : page suivante
            else if (rawSlot == 53 && holder.getPage() < maxPages - 1) {
                double cost = plugin.getConfig().getDouble("economy.cost-change-page", 0.0);
                if (plugin.getEconomyManager().withdraw(player, cost)) {
                    plugin.getGuiManager().openStoragePage(player, network, holder.getPage() + 1);
                } else {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.not-enough-money")));
                }
            }
            // Slot 46 : TOUT DEPOSER (v2)
            else if (rawSlot == NexusStorageHolder.SLOT_DEPOSIT_ALL) {
                if (level != null && !level.canDeposit() && !player.getUniqueId().equals(network.getOwner())) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cVous n'avez pas la permission de deposer."));
                    return;
                }
                int maxUniqueTypes = plugin.getUpgradeManager().getPagesForTier(network.getTier()) * 45;
                int deposited = 0;
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    if (item == null || item.getType() == org.bukkit.Material.AIR) continue;
                    if (plugin.getStorageManager().deposit(holder.getOwner(), item.clone(), maxUniqueTypes)) {
                        deposited += item.getAmount();
                        player.getInventory().setItem(i, null);
                    }
                }
                if (deposited > 0) {
                    String msg = plugin.getConfig().getString("messages.inventory-deposited",
                            "&a{items} objet(s) deposes dans le Nexus.")
                            .replace("{items}", String.valueOf(deposited));
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                    plugin.getGuiManager().refreshStorageViewers(holder.getOwner());
                } else {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Aucun objet a deposer."));
                }
            }
            // Slot 50 : RECHERCHE (v2)
            else if (rawSlot == NexusStorageHolder.SLOT_SEARCH) {
                if (holder.getSearchQuery() != null) {
                    // Effacer la recherche
                    holder.setSearchQuery(null);
                    plugin.getGuiManager().refreshStorageViewers(holder.getOwner());
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Recherche effacee."));
                } else {
                    // Activer le mode saisie chat
                    pendingSearch.put(player.getUniqueId(), holder.getOwner());
                    player.closeInventory();
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&bRecherche Nexus &7\u2192 Tape le nom ou le materiau de l'objet dans le chat."));
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&7Tape &fannuler &7pour annuler."));
                }
            }
            return;
        }

        // --- Clic dans l'INVENTAIRE DU JOUEUR (Dépôt rapide ou clic standard) ---
        if (!inTopInventory) {
            // Seul le SHIFT_LEFT / SHIFT_RIGHT (Dépôt rapide) nous intéresse depuis l'inventaire du bas
            if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
                if (level != null && !level.canDeposit() && !player.getUniqueId().equals(network.getOwner())) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cVous n'avez pas la permission de déposer."));
                    return;
                }
                
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

                int maxUniqueTypes = plugin.getUpgradeManager().getPagesForTier(network.getTier()) * 45;
                
                // Clone pour éviter des modifications mutables asynchrones pendant le process
                ItemStack toDeposit = clickedItem.clone();
                boolean absorbed = plugin.getStorageManager().deposit(holder.getOwner(), toDeposit, maxUniqueTypes);
                
                if (!absorbed) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cStockage plein : limite atteinte ou item incompatible."));
                    return;
                }
                
                // Mettre à jour l'item du joueur de manière sécurisée
                event.setCurrentItem(null);
                plugin.getGuiManager().refreshStorageViewers(holder.getOwner());
            }
            return; // On ignore les autres types de clics simples dans l'inventaire du joueur
        }

        // --- Zone de stockage compacte TOP INVENTORY (slots 0-44) ---
        int maxUniqueTypes = plugin.getUpgradeManager().getPagesForTier(network.getTier()) * 45;
        ItemStack cursor = event.getCursor();
        ItemStack clickedDisplay = event.getCurrentItem();
        boolean cursorHasItem = cursor != null && cursor.getType() != Material.AIR;
        boolean slotHasItem = clickedDisplay != null && clickedDisplay.getType() != Material.AIR;

        if (cursorHasItem) {
            // --- ACTION : DEPOT DEPUISE LE CURSEUR ---
            if (level != null && !level.canDeposit() && !player.getUniqueId().equals(network.getOwner())) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cVous n'avez pas la permission de déposer."));
                return;
            }

            String slotSig = holder.getSlotSignature(rawSlot);
            if (slotSig != null && !plugin.getStorageManager().signatureOf(cursor).equals(slotSig)) {
                return; // Anti-overwrite : Impossible d'écraser un slot existant différent
            }

            ItemStack toDeposit = cursor.clone();
            boolean absorbed = plugin.getStorageManager().deposit(holder.getOwner(), toDeposit, maxUniqueTypes);
            if (!absorbed) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cStockage plein : limite de types d'objets atteinte pour ce tier."));
                return;
            }
            
            event.getWhoClicked().setItemOnCursor(null);
            plugin.getGuiManager().refreshStorageViewers(holder.getOwner());

        } else if (slotHasItem) {
            // --- ACTION : RETRAIT ---
            if (level != null && !level.canWithdraw() && !player.getUniqueId().equals(network.getOwner())) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cVous n'avez pas la permission de retirer."));
                return;
            }

            String signature = holder.getSlotSignature(rawSlot);
            if (signature == null) return;

            // Déterminer la quantité demandée selon le clic de manière ultra-strict
            long requested = 1;
            if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
                requested = 64; // Stack complet avec Shift
            } else if (clickType == ClickType.RIGHT) {
                requested = 1; // Unité via clic droit
            } else if (clickType != ClickType.LEFT) {
                return; // Bloque tout autre type de clic exotique
            }

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
        AccessLevel viewerLevel = network.getAccessFor(player.getUniqueId());

        // 🛡️ SECURISATION : Seul le Propriétaire ou un ADMIN peut modifier les accès
        if (!player.getUniqueId().equals(network.getOwner()) && viewerLevel != AccessLevel.ADMIN) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cSeul le propriétaire ou un administrateur peut gérer les accès."));
            return;
        }

        if (clicked.getType() == Material.EMERALD) {
            pendingAddMember.put(player.getUniqueId(), network.getOwner());
            player.closeInventory();
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aTape le pseudo du joueur a ajouter dans le chat (ou 'annuler')."));
            return;
        }

        if (clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta skullMeta && skullMeta.getOwningPlayer() != null) {
            OfflinePlayer target = skullMeta.getOwningPlayer();
            UUID targetId = target.getUniqueId();

            // Interdire de s'auto-modifier ses propres permissions si on est Admin (seul le Owner peut toucher à l'admin)
            if (targetId.equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cVous ne pouvez pas modifier vos propres permissions."));
                return;
            }
            // Protéger le Owner : on ne peut pas modifier le rang du propriétaire du réseau
            if (targetId.equals(network.getOwner())) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cImpossible de modifier les droits du propriétaire."));
                return;
            }

            if (event.isRightClick()) {
                plugin.getAccessManager().removeMember(network, targetId);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.member-removed")));
            } else {
                AccessLevel current = network.getAccessFor(targetId);
                AccessLevel next = switch (current == null ? AccessLevel.READ_ONLY : current) {
                    case READ_ONLY -> AccessLevel.DEPOSIT;
                    case DEPOSIT -> AccessLevel.WITHDRAW;
                    case WITHDRAW -> AccessLevel.ADMIN;
                    case ADMIN -> AccessLevel.READ_ONLY;
                };
                plugin.getAccessManager().addMember(network, targetId, next);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aPermission de " + target.getName() + " changee en: &f" + next.getLabel()));
            }
            plugin.getGuiManager().openAccessMenu(player, network);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        UUID addMemberOwner = pendingAddMember.get(playerUuid);
        UUID renameOwner    = pendingRename.get(playerUuid);
        UUID searchOwner    = pendingSearch.get(playerUuid);

        if (addMemberOwner == null && renameOwner == null && searchOwner == null) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();

        if (addMemberOwner != null) {
            pendingAddMember.remove(playerUuid);
            handleAddMemberInput(player, addMemberOwner, message);
        } else if (renameOwner != null) {
            pendingRename.remove(playerUuid);
            handleRenameInput(player, renameOwner, message);
        } else {
            // Recherche stockage (v2)
            pendingSearch.remove(playerUuid);
            if (message.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Recherche annulee."));
                return;
            }
            final String query = message;
            Bukkit.getScheduler().runTask(plugin, () -> {
                com.novusmc.nexusstorage.model.NexusNetwork network =
                        plugin.getNexusManager().getNetworkIfExists(playerUuid);
                if (network != null)
                    plugin.getGuiManager().openStoragePageWithSearch(player, network, 0, query);
            });
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
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.not-enough-money")));
                return;
            }

            NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(ownerId);
            plugin.getAccessManager().addMember(network, target.getUniqueId(), AccessLevel.READ_ONLY);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.member-added")));
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
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.network-renamed")));
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
        AccessLevel viewerLevel = network.getAccessFor(player.getUniqueId());

        // 🛡️ SECURISATION SETTINGS : Seul Owner ou ADMIN
        if (!player.getUniqueId().equals(network.getOwner()) && viewerLevel != AccessLevel.ADMIN) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cSeul le proprietaire ou un admin peut modifier les paramètres."));
            return;
        }

        switch (event.getSlot()) {
            case 19 -> {
                pendingRename.put(player.getUniqueId(), network.getOwner());
                player.closeInventory();
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aTape le nouveau nom du reseau dans le chat (ou 'annuler')."));
            }
            case 21 -> {
                network.setNotificationsEnabled(!network.isNotificationsEnabled());
                plugin.getAccessManager().saveNetworkMeta(network);
                String state = network.isNotificationsEnabled() ? "&aActivees" : "&cDesactivees";
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.notifications-toggled", "").replace("{state}", state)));
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
        if (!player.hasPermission("nexusstorage.admin")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cVous n'avez pas la permission administrateur."));
            player.closeInventory();
            return;
        }

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
                plugin.getItemsAdderManager().reload();
                plugin.getGuiManager().openAdminMenu(player);
            }
            case 15 -> {
                plugin.reloadConfig();
                plugin.getEconomyManager().refresh();
                plugin.getItemsAdderManager().reload();
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.admin-reloaded")));
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
        AccessLevel viewerLevel = network.getAccessFor(player.getUniqueId());

        // 🛡️ SECURISATION UPGRADES : Seul Owner ou ADMIN peut faire évoluer le système
        if (!player.getUniqueId().equals(network.getOwner()) && viewerLevel != AccessLevel.ADMIN) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&cSeul le proprietaire ou un administrateur peut ameliorer le reseau."));
            return;
        }

        if (plugin.getUpgradeManager().upgrade(player, network)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.upgrade-success")));
        } else if (plugin.getUpgradeManager().isMaxTier(network)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.upgrade-max")));
        } else {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.not-enough-money")));
        }
        plugin.getGuiManager().openUpgradeMenu(player, network);
    }

    // ── GUI Marché d'énergie (v2) ─────────────────────────────────────────

    @org.bukkit.event.EventHandler
    public void onEnergyMarketClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof NexusEnergyMarketHolder holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        java.util.UUID owner = holder.getNetworkOwner();

        if (slot == NexusEnergyMarketHolder.SLOT_PRICE_PLUS) {
            plugin.getEnergyMarketManager().adjustPrice(owner, +0.10, player);
            // Rafraîchir le GUI
            com.novusmc.nexusstorage.model.NexusNetwork net =
                    plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
            if (net != null) plugin.getGuiManager().openEnergyMarket(player, net);
        } else if (slot == NexusEnergyMarketHolder.SLOT_PRICE_MINUS) {
            plugin.getEnergyMarketManager().adjustPrice(owner, -0.10, player);
            com.novusmc.nexusstorage.model.NexusNetwork net =
                    plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
            if (net != null) plugin.getGuiManager().openEnergyMarket(player, net);
        } else if (slot == NexusEnergyMarketHolder.SLOT_AUTOSELL) {
            plugin.getEnergyMarketManager().toggleAutoSell(owner);
            com.novusmc.nexusstorage.model.NexusNetwork net =
                    plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
            if (net != null) plugin.getGuiManager().openEnergyMarket(player, net);
        } else if (slot == NexusEnergyMarketHolder.SLOT_BACK) {
            com.novusmc.nexusstorage.model.NexusNetwork net =
                    plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
            if (net != null) plugin.getGuiManager().openMainMenu(player, net);
        }
    }
}
