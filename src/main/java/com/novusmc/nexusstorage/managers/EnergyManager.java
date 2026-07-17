package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.EnergyBlockType;
import com.novusmc.nexusstorage.model.EnergyGraph;
import com.novusmc.nexusstorage.model.NexusNetwork;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.FurnaceInventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class EnergyManager {

    private static final BlockFace[] DIRECTIONS = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private static class BlockRecord {
        EnergyBlockType type;
        Location location;
        UUID owner;      
        long stored;     
        int threshold;   
    }

    private final Main plugin;
    private final File file;
    private final Map<String, BlockRecord> registry = new HashMap<>();
    private final Map<Location, EnergyGraph> graphs = new HashMap<>();

    public EnergyManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "energy_blocks.yml");
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
                String typeName = yml.getString(key + ".type");
                if (typeName == null) continue;
                EnergyBlockType type = EnergyBlockType.valueOf(typeName);

                String[] parts = key.split(";");
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                Location loc = new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));

                BlockRecord record = new BlockRecord();
                record.type = type;
                record.location = loc;
                record.stored = yml.getLong(key + ".stored", 0);
                record.threshold = yml.getInt(key + ".threshold",
                        plugin.getConfig().getInt("energy.regulator.default-threshold", 20));
                String ownerStr = yml.getString(key + ".owner");
                if (ownerStr != null) record.owner = UUID.fromString(ownerStr);

                registry.put(key, record);
            } catch (Exception e) {
                plugin.getLogger().warning("Entree energy_blocks.yml invalide ignoree: " + key);
            }
        }
    }

    public void saveAll() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, BlockRecord> entry : registry.entrySet()) {
            String key = entry.getKey();
            BlockRecord r = entry.getValue();
            yml.set(key + ".type", r.type.name());
            yml.set(key + ".stored", r.stored);
            yml.set(key + ".threshold", r.threshold);
            if (r.owner != null) yml.set(key + ".owner", r.owner.toString());
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder energy_blocks.yml", e);
        }
    }

    // ================= REGISTRE =================

    public void registerBlock(Location location, EnergyBlockType type, Player placer) {
        BlockRecord record = new BlockRecord();
        record.type = type;
        record.location = location.clone();
        if (type.getRole() == EnergyBlockType.Role.CORE) {
            record.owner = placer.getUniqueId();
        }
        if (type.getRole() == EnergyBlockType.Role.REGULATOR) {
            record.threshold = plugin.getConfig().getInt("energy.regulator.default-threshold", 20);
        }
        registry.put(keyFor(location), record);
    }

    public void unregisterBlock(Location location) {
        registry.remove(keyFor(location));
    }

    public boolean isEnergyBlock(Location location) {
        return registry.containsKey(keyFor(location));
    }

    public EnergyBlockType getType(Location location) {
        BlockRecord r = registry.get(keyFor(location));
        return r == null ? null : r.type;
    }

    public int cycleThreshold(Location location, boolean increase) {
        BlockRecord r = registry.get(keyFor(location));
        if (r == null || r.type.getRole() != EnergyBlockType.Role.REGULATOR) return -1;
        r.threshold = Math.max(0, Math.min(100, r.threshold + (increase ? 5 : -5)));
        return r.threshold;
    }

    // ================= BFS / GRAPHES =================

    private List<Location> neighbors(Location loc) {
        List<Location> result = new ArrayList<>(6);
        for (BlockFace face : DIRECTIONS) {
            result.add(loc.clone().add(face.getModX(), face.getModY(), face.getModZ()));
        }
        return result;
    }

    private void rebuildGraphs() {
        graphs.clear();
        Set<String> visited = new HashSet<>();

        for (BlockRecord record : registry.values()) {
            if (record.type.getRole() != EnergyBlockType.Role.CORE) continue;
            String coreKey = keyFor(record.location);
            if (visited.contains(coreKey)) continue;

            EnergyGraph graph = new EnergyGraph(record.location.clone(), record.owner);
            visited.add(coreKey);

            Deque<Location> queue = new ArrayDeque<>();
            queue.add(record.location);

            while (!queue.isEmpty()) {
                Location current = queue.poll();
                for (Location next : neighbors(current)) {
                    String nextKey = keyFor(next);
                    if (visited.contains(nextKey)) continue;
                    BlockRecord neighborRecord = registry.get(nextKey);
                    if (neighborRecord == null) continue;

                    visited.add(nextKey);
                    queue.add(next);

                    switch (neighborRecord.type.getRole()) {
                        case CABLE -> graph.getCables().add(next);
                        case SOURCE -> graph.getSources().add(next);
                        case STORAGE -> {
                            graph.getStorages().add(next);
                            graph.setTotalCapacity(graph.getTotalCapacity() + capacityOf(neighborRecord.type));
                            graph.setTotalStored(graph.getTotalStored() + neighborRecord.stored);
                        }
                        case INTERFACE -> graph.getInterfaces().add(next);
                        case REGULATOR -> graph.getRegulators().add(next);
                        case MONITOR -> graph.getMonitors().add(next);
                        case CORE -> { }
                    }
                }
            }

            graphs.put(record.location, graph);
        }
    }

    private long capacityOf(EnergyBlockType type) {
        return switch (type) {
            case CAPACITOR_BASIC -> plugin.getConfig().getLong("energy.capacitor_basic.capacity", 10000);
            case CAPACITOR_ADVANCED -> plugin.getConfig().getLong("energy.capacitor_advanced.capacity", 100000);
            default -> 0;
        };
    }

    private double productionOf(EnergyBlockType type, Location loc) {
        Block block = loc.getBlock();
        World world = loc.getWorld();
        boolean skyExposed = block.getLightFromSky() >= 15;
        boolean isDay = world.isDayTime();
        double weatherFactor = world.hasStorm() ? 0.35 : 1.0;

        if (!skyExposed || !isDay) return 0;

        return switch (type) {
            case SOLAR_PANEL_BASIC -> plugin.getConfig().getDouble("energy.solar_panel_basic.production", 20) * weatherFactor;
            case SOLAR_PANEL_ADVANCED -> {
                int minY = plugin.getConfig().getInt("energy.solar_panel_advanced.min-y", 100);
                if (loc.getBlockY() < minY) yield 0.0;
                yield plugin.getConfig().getDouble("energy.solar_panel_advanced.production", 60) * weatherFactor;
            }
            default -> 0.0;
        };
    }

    private double lossFactorFor(EnergyGraph graph) {
        double basicLoss = plugin.getConfig().getDouble("energy.cable_basic.loss-per-block", 0.02);
        int cableCount = graph.getCables().size();
        return Math.min(0.6, cableCount * (basicLoss / 4.0));
    }

    // ================= SIMULATION =================

    public void tick() {
        rebuildGraphs();

        for (EnergyGraph graph : graphs.values()) {
            double production = 0;
            for (Location src : graph.getSources()) {
                EnergyBlockType type = getType(src);
                if (type != null) production += productionOf(type, src);
            }

            double loss = lossFactorFor(graph);
            double netProduction = production * (1 - loss);
            graph.setLastProduction(netProduction);

            distributeToStorages(graph, netProduction);

            // Somme des consommations des interfaces et des fours
            double consumed = runInterfaces(graph) + runElectricFurnaces(graph);
            graph.setLastConsumption(consumed);

            checkRegulators(graph);
        }

        saveAll();
    }

    private void distributeToStorages(EnergyGraph graph, double amount) {
        if (amount <= 0 || graph.getStorages().isEmpty()) return;
        long toDistribute = Math.round(amount);

        for (Location storageLoc : graph.getStorages()) {
            if (toDistribute <= 0) break;
            BlockRecord record = registry.get(keyFor(storageLoc));
            if (record == null) continue;
            long capacity = capacityOf(record.type);
            long space = capacity - record.stored;
            if (space <= 0) continue;
            long add = Math.min(space, toDistribute);
            record.stored += add;
            toDistribute -= add;
        }
    }

    public long totalStoredOf(EnergyGraph graph) {
        long total = 0;
        for (Location loc : graph.getStorages()) {
            BlockRecord r = registry.get(keyFor(loc));
            if (r != null) total += r.stored;
        }
        return total;
    }

    private boolean consumeEnergy(EnergyGraph graph, long amount) {
        long available = totalStoredOf(graph);
        if (available < amount) return false;

        long remaining = amount;
        for (Location loc : graph.getStorages()) {
            if (remaining <= 0) break;
            BlockRecord r = registry.get(keyFor(loc));
            if (r == null || r.stored <= 0) continue;
            long take = Math.min(r.stored, remaining);
            r.stored -= take;
            remaining -= take;
        }
        return true;
    }

    private double runInterfaces(EnergyGraph graph) {
        if (graph.isInterfacesPaused() || graph.getOwnerId() == null) return 0;

        double energyPerItem = plugin.getConfig().getDouble("energy.interface_block.energy-per-item", 5);
        double consumed = 0;
        NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(graph.getOwnerId());
        int maxUniqueTypes = plugin.getUpgradeManager().getPagesForTier(network.getTier()) * 45;

        for (Location loc : graph.getInterfaces()) {
            if (getType(loc) != EnergyBlockType.INTERFACE_BLOCK) continue; // Sécurité si rôles partagés

            Inventory adjacentInv = findAdjacentInventory(loc);
            if (adjacentInv == null) continue;

            ItemStack toMove = firstNonEmptySlot(adjacentInv);
            if (toMove == null) continue;

            if (!consumeEnergy(graph, Math.round(energyPerItem))) continue;

            ItemStack single = toMove.clone();
            single.setAmount(1);
            boolean absorbed = plugin.getStorageManager().deposit(graph.getOwnerId(), single, maxUniqueTypes);
            if (absorbed) {
                toMove.setAmount(toMove.getAmount() - 1);
                consumed += energyPerItem;
            }
        }
        if (consumed > 0) {
            plugin.getGuiManager().refreshStorageViewers(graph.getOwnerId());
        }
        return consumed;
    }

    /**
     * Simule le fonctionnement des Fours Électriques.
     * Consomme beaucoup d'énergie pour accélérer ou alimenter la cuisson d'un bloc Four adjacent.
     */
    private double runElectricFurnaces(EnergyGraph graph) {
        if (graph.isInterfacesPaused()) return 0;

        // Grosse consommation configurable dans le config.yml (ex: 75 EU par tick)
        double energyPerTick = plugin.getConfig().getDouble("energy.electric_furnace.energy-per-tick", 75.0);
        double consumed = 0;

        for (Location loc : graph.getInterfaces()) { 
            // On part du principe que ELECTRIC_FURNACE a aussi le rôle Role.INTERFACE pour être capté par le BFS
            if (getType(loc) != EnergyBlockType.ELECTRIC_FURNACE) continue;

            Furnace adjacentFurnace = findAdjacentFurnace(loc);
            if (adjacentFurnace == null) continue;

            FurnaceInventory furnaceInv = adjacentFurnace.getInventory();
            ItemStack smelting = furnaceInv.getSmelting();

            // S'il y a un item à cuire et qu'il reste de la place dans le slot de sortie
            if (smelting != null && smelting.getType() != Material.AIR) {
                ItemStack resultSlot = furnaceInv.getResult();
                if (resultSlot == null || resultSlot.getAmount() < resultSlot.getMaxStackSize()) {
                    
                    // On tente de consommer la grosse quantité d'énergie
                    if (consumeEnergy(graph, Math.round(energyPerTick))) {
                        consumed += energyPerTick;
                        
                        // Force le four à s'allumer (sans consommer de charbon vanilla)
                        if (adjacentFurnace.getBurnTime() < 2) {
                            adjacentFurnace.setBurnTime((short) 20); 
                        }
                        
                        // Accélère la cuisson : augmente la jauge de progression
                        short newCookTime = (short) (adjacentFurnace.getCookTime() + 5); // Avance de 5 ticks par tick réel
                        adjacentFurnace.setCookTime(newCookTime);
                        adjacentFurnace.update(true);
                    }
                }
            }
        }
        return consumed;
    }

    private Furnace findAdjacentFurnace(Location electricFurnaceLoc) {
        for (Location neighbor : neighbors(electricFurnaceLoc)) {
            Block block = neighbor.getBlock();
            if (block.getState() instanceof Furnace furnace) {
                return furnace;
            }
        }
        return null;
    }

    private ItemStack firstNonEmptySlot(Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) return item;
        }
        return null;
    }

    private Inventory findAdjacentInventory(Location interfaceLoc) {
        for (Location neighbor : neighbors(interfaceLoc)) {
            Block block = neighbor.getBlock();
            if (block.getState() instanceof InventoryHolder holder) {
                return holder.getInventory();
            }
        }
        return null;
    }

    private void checkRegulators(EnergyGraph graph) {
        if (graph.getRegulators().isEmpty()) return;

        int lowestThreshold = 100;
        for (Location loc : graph.getRegulators()) {
            BlockRecord r = registry.get(keyFor(loc));
            if (r != null) lowestThreshold = Math.min(lowestThreshold, r.threshold);
        }

        boolean shouldPause = graph.getFillPercent() < lowestThreshold;
        boolean wasPaused = graph.isInterfacesPaused();
        graph.setInterfacesPaused(shouldPause);

        if (shouldPause && !wasPaused && graph.getOwnerId() != null) {
            NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(graph.getOwnerId());
            if (network.isNotificationsEnabled()) {
                Player owner = Bukkit.getPlayer(graph.getOwnerId());
                if (owner != null) {
                    String msg = plugin.getConfig().getString("messages.low-energy", "")
                            .replace("{name}", network.getName() == null ? "" : network.getName())
                            .replace("{percent}", String.valueOf(Math.round(graph.getFillPercent())));
                    owner.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                }
            }
        }
    }

    public EnergyGraph getGraphContaining(Location loc) {
        for (EnergyGraph graph : graphs.values()) {
            if (graph.getCoreLocation().equals(loc)
                    || graph.getMonitors().contains(loc)
                    || graph.getCables().contains(loc)
                    || graph.getSources().contains(loc)
                    || graph.getStorages().contains(loc)
                    || graph.getInterfaces().contains(loc)
                    || graph.getRegulators().contains(loc)) {
                return graph;
            }
        }
        return null;
    }

    public int registrySize() {
        return registry.size();
    }

    // ================= STATS AGREGEES (pour le GUI) =================

    public record EnergyStats(long capacity, long stored, double production, double consumption,
                               int cableCount, int machineCount, int networkCount) {}

    public EnergyStats getStatsForOwner(UUID owner) {
        long capacity = 0, stored = 0;
        double production = 0, consumption = 0;
        int cables = 0, machines = 0, networks = 0;

        for (EnergyGraph graph : graphs.values()) {
            if (!owner.equals(graph.getOwnerId())) continue;
            capacity += graph.getTotalCapacity();
            stored += totalStoredOf(graph);
            production += graph.getLastProduction();
            consumption += graph.getLastConsumption();
            cables += graph.getCables().size();
            machines += graph.machineCount();
            networks++;
        }
        return new EnergyStats(capacity, stored, production, consumption, cables, machines, networks);
    }
}
