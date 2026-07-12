package com.novusmc.nexusstorage.listeners;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Gere la pose du bloc Nexus Core (Lodestone avec PDC nexus_core=true) :
 * creation du reseau et enregistrement de la position dans cores.yml.
 */
public class NexusCoreListener implements Listener {

    private final Main plugin;

    public NexusCoreListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        var item = event.getItemInHand();
        if (item.getItemMeta() == null) return;
        Boolean isCore = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getNexusCoreKey(), PersistentDataType.BOOLEAN);
        if (isCore == null || !isCore) return;

        Player player = event.getPlayer();
        UUID ownerId = player.getUniqueId();

        NexusNetwork network = plugin.getNexusManager().registerCore(ownerId, event.getBlock().getLocation());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.core-created", "&aReseau Nexus cree.")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Tier actuel: &f" + network.getTier() + "&7 (" +
                        plugin.getUpgradeManager().getPagesForTier(network.getTier()) + " pages)"));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getNexusManager().isCoreLocation(event.getBlock().getLocation())) return;

        UUID owner = plugin.getNexusManager().getOwnerAt(event.getBlock().getLocation());
        plugin.getNexusManager().unregisterCore(event.getBlock().getLocation());

        Player player = event.getPlayer();
        if (owner != null && owner.equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&eTon Nexus Core a ete retire. Ton reseau et son contenu restent intacts."));
        }
    }
}
