package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gere les Nexus Chest Link : blocs poses a cote d'un coffre simple ou double
 * qui transferent automatiquement son contenu vers le stockage virtuel Nexus
 * du proprietaire. Ameliorable via Nexus Upgrade Crystal (jusqu'a 3 niveaux)
 * pour transferer plus d'items par cycle.
 */
public class ChestLinkManager {

    private static final BlockFace[] DIRECTIONS = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private static class ChestLinkRecord {
        Location location;
        UUID owner;
        int upgradeLevel;
    }

    private final Main plugin;
    private final File file;
    private final Map<String, ChestLinkRecord> registry = new HashMap<>();

    public ChestLinkManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chest_links.yml");
        load();
    }

    private String keyFor(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    // ================= PERSISTENCE =================

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            try {
                String[] parts = key.split(";");
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                Location loc = new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));

                ChestLinkRecord record = new ChestLinkRecord();
                record.location = loc;
                record.owner = UUID.fromString(yml.getString(key + ".owner"));
                record.upgradeLevel = yml.getInt(key + ".level", 0);
                registry.put(key, record);
            } catch (Exception e) {
                plugin.getLogger().warning("Entree chest_links.yml invalide ignoree: " + key);
            }
        }
    }

    public void saveAll() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, ChestLinkRecord> entry : registry.entrySet()) {
            ChestLinkRecord r = entry.getValue();
            yml.set(entry.getKey() + ".owner", r.owner.toString());
            yml.set(entry.getKey() + ".level", r.upgradeLevel);
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder chest_links.yml", e);
        }
    }

    // ================= REGISTRE =================

    public void register(Location location, Player owner) {
        ChestLinkRecord record = new ChestLinkRecord();
        record.location = location.clone();
        record.owner = owner.getUniqueId();
        record.upgradeLevel = 0;
        registry.put(keyFor(location), record);
    }

    public void unregister(Location location) {
        registry.remove(keyFor(location));
    }

    public boolean isChestLink(Location location) {
        return registry.containsKey(keyFor(location));
    }

    public int getUpgradeLevel(Location location) {
        ChestLinkRecord r = registry.get(keyFor(location));
        return r == null ? -1 : r.upgradeLevel;
    }

    /**
     * Applique un cristal d'amelioration sur un Chest Link.
     * @return le nouveau niveau, ou -1 si deja au maximum / bloc introuvable.
     */
    public int applyUpgrade(Location location) {
        ChestLinkRecord r = registry.get(keyFor(location));
        if (r == null) return -1;
        int maxLevel = plugin.getConfig().getInt("chest-link.max-upgrade-level", 3);
        if (r.upgradeLevel >= maxLevel) return -1;
        r.upgradeLevel++;
        return r.upgradeLevel;
    }

    public int registrySize() {
        return registry.size();
    }

    // ================= SIMULATION =================

    public void tick() {
        if (registry.isEmpty()) return;

        int base = plugin.getConfig().getInt("chest-link.base-items-per-cycle", 1);
        int perLevel = plugin.getConfig().getInt("chest-link.items-per-upgrade-level", 1);

        for (ChestLinkRecord record : registry.values()) {
            Inventory chestInv = findAdjacentChest(record.location);
            if (chestInv == null) continue;

            int budget = base + record.upgradeLevel * perLevel;
            NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(record.owner);
            int maxUniqueTypes = plugin.getUpgradeManager().getPagesForTier(network.getTier()) * 45;
            boolean changed = false;

            for (int i = 0; i < budget; i++) {
                ItemStack toMove = firstNonEmptySlot(chestInv);
                if (toMove == null) break;

                ItemStack single = toMove.clone();
                single.setAmount(1);
                boolean absorbed = plugin.getStorageManager().deposit(record.owner, single, maxUniqueTypes);
                if (!absorbed) break; // stockage plein (limite de types atteinte), on s'arrete pour ce cycle

                toMove.setAmount(toMove.getAmount() - 1);
                changed = true;
            }

            if (changed) {
                plugin.getGuiManager().refreshStorageViewers(record.owner);
            }
        }
    }

    private ItemStack firstNonEmptySlot(Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) return item;
        }
        return null;
    }

    /**
     * Cherche un coffre simple ou double adjacent au Chest Link.
     * Un coffre double renvoie automatiquement son inventaire fusionne (54 slots)
     * via getState().getInventory() sur n'importe laquelle de ses deux moities.
     */
    private Inventory findAdjacentChest(Location linkLocation) {
        for (BlockFace face : DIRECTIONS) {
            Block block = linkLocation.getBlock().getRelative(face);
            BlockState state = block.getState();
            if (state instanceof Chest chest) {
                return chest.getInventory();
            }
        }
        return null;
    }
}
