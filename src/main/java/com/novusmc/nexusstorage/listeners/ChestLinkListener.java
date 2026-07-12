package com.novusmc.nexusstorage.listeners;

import com.novusmc.nexusstorage.Main;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
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
 * Gere la pose/casse du Nexus Chest Link et l'application des
 * Nexus Upgrade Crystal (clic droit sur un Chest Link avec le cristal en main).
 */
public class ChestLinkListener implements Listener {

    private static final BlockFace[] DIRECTIONS = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final Main plugin;

    public ChestLinkListener(Main plugin) {
        this.plugin = plugin;
    }

    private boolean hasAdjacentChest(Location loc) {
        for (BlockFace face : DIRECTIONS) {
            BlockState state = loc.getBlock().getRelative(face).getState();
            if (state instanceof Chest) return true;
        }
        return false;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getItemMeta() == null) return;

        Boolean isChestLink = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.getChestLinkKey(), PersistentDataType.BOOLEAN);
        if (isChestLink == null || !isChestLink) return;

        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (!hasAdjacentChest(loc)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.chestlink-no-chest")));
            return;
        }

        plugin.getChestLinkManager().register(loc, player);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.chestlink-placed")));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!plugin.getChestLinkManager().isChestLink(loc)) return;

        plugin.getChestLinkManager().unregister(loc);
        event.setDropItems(false);
        event.getBlock().getWorld().dropItemNaturally(loc.add(0.5, 0.5, 0.5), plugin.buildChestLinkItem());
    }

    @EventHandler
    public void onUpgradeUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        Location loc = event.getClickedBlock().getLocation();
        if (!plugin.getChestLinkManager().isChestLink(loc)) return;

        ItemStack inHand = event.getItem();
        if (inHand == null || inHand.getItemMeta() == null) return;

        Integer tier = inHand.getItemMeta().getPersistentDataContainer()
                .get(plugin.getUpgradeCrystalKey(), PersistentDataType.INTEGER);
        if (tier == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        int newLevel = plugin.getChestLinkManager().applyUpgrade(loc);

        if (newLevel < 0) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.chestlink-max-level")));
            return;
        }

        inHand.setAmount(inHand.getAmount() - 1);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.chestlink-upgraded").replace("{level}", String.valueOf(newLevel))));
    }
}
