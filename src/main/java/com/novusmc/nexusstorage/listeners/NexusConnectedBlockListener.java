package com.novusmc.nexusstorage.listeners;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.UUID;

/**
 * Gère le Nexus Connected Block : un bloc physique posé dans le monde qui,
 * une fois associé à un réseau Nexus, ouvre directement l'interface de
 * stockage au clic droit — sans avoir besoin de la Tablet.
 *
 * Cycle de vie :
 *  • Pose  → si l'item a le PDC nexus_connected_block=true, on enregistre la
 *            position dans connected_blocks.yml avec l'UUID du poseur.
 *  • Clic  → ouvre le stockage du réseau associé (vérification d'accès incluse).
 *  • Casse → dés-enregistre le bloc et rend l'item au joueur.
 *
 * Le material du bloc (vanilla ou ItemsAdder) est défini dans itemsadder.yml
 * sous la clé "nexus-connected-block".
 */
public class NexusConnectedBlockListener implements Listener {

    private final Main plugin;
    private static final String PDC_KEY = "nexus_connected_block";

    public NexusConnectedBlockListener(Main plugin) {
        this.plugin = plugin;
    }

    // ── Pose ──────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || item.getItemMeta() == null) return;

        Boolean isConnected = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getNexusConnectedBlockKey(), PersistentDataType.BOOLEAN);
        if (isConnected == null || !isConnected) return;

        Player player = event.getPlayer();
        Location loc  = event.getBlock().getLocation();

        // Vérifie que le joueur possède bien un réseau
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) {
            player.sendMessage(c(plugin.getConfig().getString("messages.no-network")));
            event.setCancelled(true);
            return;
        }

        // Enregistre la position liée à l'owner du réseau
        plugin.getNexusManager().registerConnectedBlock(loc, network.getOwner());
        player.sendMessage(c("&aNexus Connected Block &7posé et lié au réseau &f" + network.getName() + "&7."));
        player.sendMessage(c("&7Clic droit dessus pour ouvrir le stockage."));
    }

    // ── Clic droit ────────────────────────────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        Location loc = event.getClickedBlock().getLocation();

        // Vérifier si c'est un Connected Block enregistré
        if (!plugin.getNexusManager().isConnectedBlock(loc)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        UUID ownerUuid = plugin.getNexusManager().getConnectedBlockOwner(loc);
        if (ownerUuid == null) {
            player.sendMessage(c("&cCe bloc n'est associé à aucun réseau. Cassez-le et replacez-le."));
            return;
        }

        // Charger le réseau de l'owner
        NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(ownerUuid);

        // Vérifier l'accès du joueur cliqueur
        if (!network.hasAccess(player.getUniqueId())) {
            // Vérifier aussi via entreprise
            com.novusmc.nexusstorage.model.Company company =
                    plugin.getCompanyManager().getByPlayer(player.getUniqueId());
            boolean accessViaCompany = company != null && company.getOwner().equals(ownerUuid);

            if (!accessViaCompany) {
                player.sendMessage(c(plugin.getConfig().getString("messages.no-access")));
                return;
            }
        }

        plugin.getGuiManager().openStoragePage(player, network, 0);
        player.sendMessage(c(plugin.getConfig().getString("messages.nexus-block-opened",
                "&aAccès au stockage Nexus !")));
    }

    // ── Casse ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!plugin.getNexusManager().isConnectedBlock(loc)) return;

        event.setCancelled(true); // on gère nous-mêmes
        Player player = event.getPlayer();

        java.util.UUID ownerUuid = plugin.getNexusManager().getConnectedBlockOwner(loc);

        // Seul l'owner ou un admin peut casser le bloc
        boolean isOwner  = ownerUuid != null && ownerUuid.equals(player.getUniqueId());
        boolean isAdmin  = player.hasPermission("nexusstorage.admin");
        if (!isOwner && !isAdmin) {
            player.sendMessage(c("&cSeul le propriétaire du réseau peut casser ce bloc."));
            return;
        }

        // Retirer le bloc et rendre l'item
        plugin.getNexusManager().unregisterConnectedBlock(loc);
        event.getBlock().setType(Material.AIR);

        ItemStack returned = plugin.buildConnectedBlockItem();
        player.getInventory().addItem(returned);
        player.sendMessage(c("&aNexus Connected Block récupéré."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s != null ? s : "");
    }
}
