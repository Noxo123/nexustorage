package com.novusmc.nexusstorage.listeners;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.Company;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Clic droit sur un Nexus Block (Normal ou WiFi) → ouvre le stockage.
 *
 * Les materials sont lus depuis itemsadder.yml :
 *   nexus-block-normal  (fallback AMETHYST_BLOCK)
 *   nexus-block-wifi    (fallback LIGHTNING_ROD)
 *
 * La vérification d'accès tient compte des membres directs ET des entreprises.
 */
public class NexusBlockListener implements Listener {

    private final Main plugin;

    public NexusBlockListener(Main plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();

        boolean isNexusBlock = plugin.getItemsAdderManager().isNexusAccessBlock(block.getType());
        if (!isNexusBlock) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // Résoudre le réseau accessible par ce joueur
        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) {
            player.sendMessage(c(plugin.getConfig().getString("messages.no-network")));
            return;
        }

        // Vérifier l'accès : membre direct OU entreprise
        if (!canAccess(player, network)) {
            player.sendMessage(c(plugin.getConfig().getString("messages.no-access")));
            return;
        }

        plugin.getGuiManager().openStoragePage(player, network, 0);
        player.sendMessage(c(plugin.getConfig().getString("messages.nexus-block-opened",
                "&aAccès au stockage Nexus !")));
    }

    /**
     * Vérifie si le joueur a accès au réseau, que ce soit :
     *  - en tant que propriétaire
     *  - en tant que membre enregistré
     *  - en tant que membre d'une entreprise liée au réseau
     */
    private boolean canAccess(Player player, NexusNetwork network) {
        if (network.hasAccess(player.getUniqueId())) return true;
        Company company = plugin.getCompanyManager().getByPlayer(player.getUniqueId());
        return company != null && company.getOwner().equals(network.getOwner());
    }

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s != null ? s : "");
    }
}
