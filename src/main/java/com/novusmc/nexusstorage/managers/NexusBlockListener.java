package com.novusmc.nexusstorage.listeners;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import net.md_5.bungee.api.ChatColor;

/**
 * Listener pour l'interaction avec les blocs Nexus (WiFi et Normal).
 * Un clic droit sur un bloc Nexus ouvre directement l'interface du stockage.
 */
public class NexusBlockListener implements Listener {
    private final Main plugin;

    public NexusBlockListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNexusBlockClick(PlayerInteractEvent event) {
        // On vérifie seulement les clics droits sur un bloc
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Player player = event.getPlayer();
        Material blockMaterial = block.getType();

        // Vérifier si c'est un bloc Nexus (vanilla ou ItemsAdder)
        if (!isNexusBlock(blockMaterial)) {
            return;
        }

        event.setCancelled(true);

        // Obtenir le réseau du joueur
        NexusNetwork network = plugin.getNexusManager().getNetworkForPlayer(player.getUniqueId());
        
        if (network == null) {
            String message = plugin.getConfig().getString("messages.no-network", 
                    "&cTu ne possèdes pas encore de réseau Nexus.");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        // Vérifier l'accès
        if (!network.hasAccess(player.getUniqueId())) {
            String message = plugin.getConfig().getString("messages.no-access",
                    "&cTu n'as pas la permission d'ouvrir ce réseau Nexus.");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }

        // Ouvrir l'interface du stockage
        plugin.getGuiManager().openStorageGUI(player, network, 1);
        
        String openMessage = plugin.getConfig().getString("messages.nexus-block-opened",
                "&aAccès au stockage Nexus ouvert!");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', openMessage));
    }

    /**
     * Vérifie si un bloc est un bloc Nexus (vanilla ou ItemsAdder).
     */
    private boolean isNexusBlock(Material material) {
        // Blocs Nexus vanilla (configurés dans config.yml)
        String normalMaterial = plugin.getConfig().getString("nexus-blocks.normal.material", "AMETHYST_BLOCK");
        String wifiMaterial = plugin.getConfig().getString("nexus-blocks.wifi.material", "LIGHTNING_ROD");

        if (material.toString().equalsIgnoreCase(normalMaterial) || 
            material.toString().equalsIgnoreCase(wifiMaterial)) {
            return true;
        }

        // Blocs ItemsAdder (si activés)
        if (plugin.getItemsAdderManager().isEnabled()) {
            String normalItemsAdder = plugin.getConfig().getString("nexus-blocks.normal.itemsadder", "");
            String wifiItemsAdder = plugin.getConfig().getString("nexus-blocks.wifi.itemsadder", "");
            
            // Vérifier si c'est un bloc ItemsAdder
            // (L'implémentation exacte dépend de la lib ItemsAdder)
            // Pour l'instant, on retourne false car c'est une vérification plus complexe
        }

        return false;
    }
}
