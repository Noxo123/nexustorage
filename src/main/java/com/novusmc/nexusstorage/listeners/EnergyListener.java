package com.novusmc.nexusstorage.listeners;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.managers.EnergyManager;
import com.novusmc.nexusstorage.model.EnergyBlockType;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Gere la pose et la destruction des blocs du systeme d'energie, ainsi que
 * l'interaction avec le Regulator (cycle de seuil) et le Monitor (ouverture
 * du tableau de bord). Voir NexusGUIListener pour le GUI du Monitor.
 */
public class EnergyListener implements Listener {

    private final Main plugin;

    public EnergyListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getItemMeta() == null) return;

        String typeName = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getEnergyTypeKey(), PersistentDataType.STRING);
        if (typeName == null) return;

        EnergyBlockType type;
        try {
            type = EnergyBlockType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return;
        }

        Player player = event.getPlayer();
        plugin.getEnergyManager().registerBlock(event.getBlock().getLocation(), type, player);

        if (type.getRole() == EnergyBlockType.Role.CORE) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&d⚡ Nexus Energy Core place. Relie tous les cables/machines adjacents a ton reseau."));
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getEnergyManager().isEnergyBlock(event.getBlock().getLocation())) return;
        EnergyBlockType type = plugin.getEnergyManager().getType(event.getBlock().getLocation());
        plugin.getEnergyManager().unregisterBlock(event.getBlock().getLocation());

        // Rend l'item d'origine (avec son PDC) plutot que le drop vanilla brut.
        if (type != null) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                    plugin.buildEnergyItem(type));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        EnergyBlockType type = plugin.getEnergyManager().getType(event.getClickedBlock().getLocation());
        if (type == null) return;

        Player player = event.getPlayer();

        if (type.getRole() == EnergyBlockType.Role.REGULATOR) {
            event.setCancelled(true);
            boolean increase = !player.isSneaking();
            int newThreshold = plugin.getEnergyManager().cycleThreshold(event.getClickedBlock().getLocation(), increase);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&cSeuil de securite Regulator: &f" + newThreshold + "%"));
        } else if (type.getRole() == EnergyBlockType.Role.MONITOR) {
            event.setCancelled(true);
            plugin.getGuiManager().openEnergyMonitor(player, event.getClickedBlock().getLocation());
        }
    }
}
