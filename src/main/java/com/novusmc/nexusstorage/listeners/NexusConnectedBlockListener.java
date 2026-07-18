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
 * Gère le Nexus Connected Block : un bloc physique posé dans le monde qui
 * est lié définitivement à son poseur (l'owner). Quand un joueur clique dessus,
 * cela ouvre le réseau de l'owner (si le joueur a les permissions requises).
 */
public class NexusConnectedBlockListener implements Listener {

    private final Main plugin;

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

        // Vérifie que le poseur possède bien un réseau à lui
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) {
            player.sendMessage(c(plugin.getConfig().getString("messages.no-network")));
            event.setCancelled(true);
            return;
        }

        // Enregistre la position liée à l'owner (le poseur)
        plugin.getNexusManager().registerConnectedBlock(loc, network.getOwner());
        player.sendMessage(c("&aNexus Connected Block &7posé et lié à votre réseau &f" + network.getName() + "&7."));
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

        // 1. Récupérer l'UUID du propriétaire d'origine du bloc
        UUID ownerUuid = plugin.getNexusManager().getConnectedBlockOwner(loc);
        if (ownerUuid == null) {
            player.sendMessage(c("&cCe bloc n'est associé à aucun réseau. Cassez-le et replacez-le."));
            return;
        }

        // 2. Charger le réseau du PROPRIÉTAIRE du bloc
        NexusNetwork ownerNetwork = plugin.getNexusManager().getOrCreateNetwork(ownerUuid);
        if (ownerNetwork == null) {
            player.sendMessage(c("&cLe réseau associé à ce bloc n'existe plus."));
            return;
        }

        // 3. Vérifier les accès du joueur qui vient de cliquer
        boolean hasAccess = false;

        // Cas A : Le cliqueur est le propriétaire lui-même
        if (player.getUniqueId().equals(ownerUuid)) {
            hasAccess = true;
        } 
        // Cas B : Le cliqueur a reçu la permission partagée dans le Nexus
        else if (ownerNetwork.hasAccess(player.getUniqueId())) {
            hasAccess = true;
        } 
        // Cas C : Le cliqueur fait partie de l'entreprise (Company) de l'owner
        else {
            com.novusmc.nexusstorage.model.Company company = 
                    plugin.getCompanyManager().getByPlayer(player.getUniqueId());
            if (company != null && company.getOwner().equals(ownerUuid)) {
                hasAccess = true;
            }
        }

        // Si aucun accès n'est valide, on refuse l'ouverture
        if (!hasAccess) {
            player.sendMessage(c(plugin.getConfig().getString("messages.no-access", "&cTu n'as pas l'accès au réseau de ce bloc !")));
            return;
        }

        // 4. OUVERTURE : On ouvre bien le réseau de l'owner (ownerNetwork) à la personne qui clique (player)
        plugin.getGuiManager().openStoragePage(player, ownerNetwork, 0);
        
        String openedMessage = plugin.getConfig().getString("messages.nexus-block-opened", "&aAccès au stockage Nexus de %owner% !");
        // Remplacement dynamique du nom si tu veux afficher le nom du propriétaire dans le message
        openedMessage = openedMessage.replace("%owner%", ownerNetwork.getName());
        player.sendMessage(c(openedMessage));
    }

    // ── Casse ─────────────────────────────────────────────────────────────

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!plugin.getNexusManager().isConnectedBlock(loc)) return;

        event.setCancelled(true); 
        Player player = event.getPlayer();

        UUID ownerUuid = plugin.getNexusManager().getConnectedBlockOwner(loc);

        // Seul l'owner du bloc ou un admin avec permission peut détruire l'installation
        boolean isOwner = ownerUuid != null && ownerUuid.equals(player.getUniqueId());
        boolean isAdmin = player.hasPermission("nexusstorage.admin");
        
        if (!isOwner && !isAdmin) {
            player.sendMessage(c("&cSeul le propriétaire de ce réseau peut récupérer ce bloc."));
            return;
        }

        // Retirer le bloc
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
