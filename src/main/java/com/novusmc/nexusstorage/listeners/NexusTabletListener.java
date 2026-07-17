package com.novusmc.nexusstorage.listeners;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Gere le clic droit sur la Nexus Tablet : verifie que le joueur possede
 * (ou a acces a) un reseau Nexus, puis ouvre le GUI principal.
 */
public class NexusTabletListener implements Listener {

    private final Main plugin;

    public NexusTabletListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getItemMeta() == null) return;

        Boolean isTablet = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getNexusTabletKey(), PersistentDataType.BOOLEAN);
        if (isTablet == null || !isTablet) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.no-network")));
            return;
        }

        if (!network.hasAccess(player.getUniqueId())) {
            // Vérifier aussi l'accès via entreprise
            com.novusmc.nexusstorage.model.Company company =
                    plugin.getCompanyManager().getByPlayer(player.getUniqueId());
            boolean accessViaCompany = company != null && company.getOwner().equals(network.getOwner());
            if (!accessViaCompany) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("messages.no-access")));
                return;
            }
        }

        double cost = plugin.getConfig().getDouble("economy.cost-open-tablet", 0.0);
        if (!plugin.getEconomyManager().withdraw(player, cost)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.not-enough-money")));
            return;
        }

        plugin.getGuiManager().openMainMenu(player, network);
    }
}
