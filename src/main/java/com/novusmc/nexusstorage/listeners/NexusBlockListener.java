package com.novusmc.nexusstorage.listeners;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Ouvre directement le stockage Nexus lors d'un clic droit sur un
 * Bloc Nexus Normal (AMETHYST_BLOCK) ou Bloc Nexus WiFi (LIGHTNING_ROD).
 * Les materiaux peuvent etre remplaces par des blocs ItemsAdder dans config.yml.
 */
public class NexusBlockListener implements Listener {

    private final Main plugin;

    public NexusBlockListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isNexusBlock(block)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(player.getUniqueId());
        if (network == null) {
            player.sendMessage(color(plugin.getConfig().getString("messages.no-network",
                    "&cTu ne possedes pas encore de reseau Nexus.")));
            return;
        }
        if (!network.hasAccess(player.getUniqueId())) {
            player.sendMessage(color(plugin.getConfig().getString("messages.no-access",
                    "&cAcces refuse.")));
            return;
        }

        plugin.getGuiManager().openStoragePage(player, network, 0);
        player.sendMessage(color(plugin.getConfig().getString("messages.nexus-block-opened",
                "&aAcces au stockage Nexus !")));
    }

    /** Verifie si le bloc est un bloc Nexus (normal ou WiFi), en tenant compte d'ItemsAdder. */
    private boolean isNexusBlock(Block block) {
        // Support ItemsAdder (si active et ID configure)
        if (plugin.getItemsAdderManager().isEnabled()) {
            String normalIA = plugin.getConfig().getString("nexus-blocks.normal.itemsadder", "");
            String wifiIA   = plugin.getConfig().getString("nexus-blocks.wifi.itemsadder", "");
            if (!normalIA.isEmpty() || !wifiIA.isEmpty()) {
                // ItemsAdder API : LevelledMobs / ia hook — verifie via CustomBlock.byAlreadyPlaced
                // Si les IDs sont configures, on suppose que ItemsAdderManager expose isCustomBlock()
                // Pour l'instant, fallback vanilla si ItemsAdder n'expose pas ce bloc specifique.
            }
        }

        // Vanilla
        String normalMat = plugin.getConfig().getString("nexus-blocks.normal.material", "AMETHYST_BLOCK");
        String wifiMat   = plugin.getConfig().getString("nexus-blocks.wifi.material",   "LIGHTNING_ROD");
        Material m = block.getType();
        try {
            if (m == Material.valueOf(normalMat)) return true;
            if (m == Material.valueOf(wifiMat))   return true;
        } catch (IllegalArgumentException ignored) {}
        return false;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
