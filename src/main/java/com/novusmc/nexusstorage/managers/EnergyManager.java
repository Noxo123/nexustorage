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
import org.bukkit.block.data.Lightable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Gestionnaire du réseau d'énergie physique de câbles (style RF/FE).
 * Les rôles INTERFACE, FURNACE et SHIELD sont SÉPARÉS pour éviter
 * que runInterfaces() ne tente de cuire des items dans un Shield Generator.
 */
public class EnergyManager {

    private static final BlockFace[] DIRECTIONS = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    // Remplacement par une structure mutable propre (ou un record si non modifié à la volée, 
    // mais r.stored et r.threshold changent, donc classe finale)
    private static final class BlockRecord {
        private final EnergyBlockType type;
        private final Location location;
        private UUID owner;
        private long stored;
        private int threshold;

        public BlockRecord(EnergyBlockType type, Location location) {
            this.type = type;
            this.location = location.clone();
        }
    }

    private final Main plugin;
    private final File file;
    private final Map<Location, BlockRecord> registry = new HashMap<>();
    private final Map<Location, EnergyGraph> graphs = new HashMap<>();

    // Listes séparées par rôle dans chaque graphe (évite les boucles incorrectes)
    private final Map<Location, List<Location>> graphFurnaces = new HashMap<>();
    private final Map<Location, List<Location>> graphShields = new HashMap<>();

    public EnergyManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "energy_blocks.yml");
        load();
    }

    // ─── Persistence ──────────────────────────────────────────────────────

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
                BlockRecord record = new BlockRecord(type, loc);
                record.stored = yml.getLong(key + ".stored", 0);
                record.threshold = yml.getInt(key + ".threshold", plugin.getConfig().getInt("energy.regulator.default-threshold", 20));
                
                String ownerStr = yml.getString(key + ".owner");
                if (ownerStr != null) {
                    record.owner = UUID.fromString(ownerStr);
                }
                
                registry.put(loc, record);
            } catch (Exception e) {
                plugin.getLogger().warning("Entrée energy_blocks.yml invalide ignorée: " + key);
            }
        }
    }

    public void saveAll() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<Location, BlockRecord> entry : registry.entrySet()) {
            Location loc = entry.getKey();
            BlockRecord r = entry.getValue();
            String key = String.format("%s;%d;%d;%d", loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            
            yml.set(key + ".type", r.type.name());
            yml.set(key + ".stored", r.stored);
            yml.set(key + ".threshold", r.threshold);
            if (r.owner != null) {
                yml.set(key + ".owner", r.owner.toString());
            }
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder energy_blocks.yml", e);
        }
    }

    // ─── Registre ─────────────────────────────────────────────────────────

    public void registerBlock(Location location, EnergyBlockType type, Player placer) {
        BlockRecord record = new BlockRecord(type, location);
        if (type.getRole() == EnergyBlockType.Role.CORE) {
            record.owner = placer.getUniqueId();
        }
        if (type.getRole() == EnergyBlockType.Role.REGULATOR) {
            record.threshold = plugin.getConfig().getInt("energy.regulator.default-threshold", 20);
        }
        registry.put(location, record);
    }

    public void unregisterBlock(Location location) { 
        registry.remove(location); 
    }
    
    public boolean isEnergyBlock(Location location) { 
        return registry.containsKey(location); 
    }
    
    public EnergyBlockType getType(Location location) {
        BlockRecord r = registry.get(location);
        return r == null ? null : r.type;
    }
    
    public int cycleThreshold(Location location, boolean increase) {
        BlockRecord r = registry.get(location);
        if (r == null || r.type.getRole() != EnergyBlockType.Role.REGULATOR) return -1;
        r.threshold = Math.max(0, Math.min(100, r.threshold + (increase ? 5 : -5)));
        return r.threshold;
    }
    
    public int registrySize() { 
        return registry.size(); 
    }

    // ─── BFS / Graphes ────────────────────────────────────────────────────

    private List<Location> getNeighbors(Location loc) {
        List<Location> result = new ArrayList<>(6);
        for (BlockFace face : DIRECTIONS) {
            result.add(loc.clone().add(face.getModX(), face.getModY(), face.getModZ()));
        }
        return result;
    }

    private void rebuildGraphs() {
        graphs.clear();
        graphFurnaces.clear();
        graphShields.clear();
        Set<Location> visited = new HashSet<>();

        for (BlockRecord record : registry.values()) {
            if (record.type.getRole() != EnergyBlockType.Role.CORE) continue;
            if (visited.contains(record.location)) continue;

            EnergyGraph graph = new EnergyGraph(record.location.clone(), record.owner);
            List<Location> furnaces = new ArrayList<>();
            List<Location> shields = new ArrayList<>();
            visited.add(record.location);

            Deque<Location> queue = new ArrayDeque<>();
            queue.add(record.location);

            while (!queue.isEmpty()) {
                Location current = queue.poll();
                for (Location next : getNeighbors(current)) {
                    if (visited.contains(next)) continue;
                    BlockRecord nr = registry.get(next);
                    if (nr == null) continue;
                    
                    visited.add(next);
                    queue.add(next);

                    switch (nr.type.getRole()) {
                        case CABLE      -> graph.getCables().add(next);
                        case SOURCE     -> graph.getSources().add(next);
                        case STORAGE    -> {
                            graph.getStorages().add(next);
                            graph.setTotalCapacity(graph.getTotalCapacity() + getCapacityOf(nr.type));
                            graph.setTotalStored(graph.getTotalStored() + nr.stored);
                        }
                        case INTERFACE -> graph.getInterfaces().add(next);
                        case FURNACE   -> furnaces.add(next);
                        case SHIELD    -> shields.add(next);
                        case REGULATOR -> graph.getRegulators().add(next);
                        case MONITOR   -> graph.getMonitors().add(next);
                        case CORE      -> {} // Deux cores connectés : le second est ignoré
                    }
                }
            }
            graphs.put(record.location, graph);
            graphFurnaces.put(record.location, furnaces);
            graphShields.put(record.location, shields);
        }
    }

    // ─── Production / capacité ────────────────────────────────────────────

    private long getCapacityOf(EnergyBlockType type) {
        return switch (type) {
            case CAPACITOR_BASIC    -> plugin.getConfig().getLong("energy.capacitor_basic.capacity", 10_000);
            case CAPACITOR_ADVANCED -> plugin.getConfig().getLong("energy.capacitor_advanced.capacity", 100_000);
            default -> 0L;
        };
    }

    private double getProductionOf(EnergyBlockType type, Location loc) {
        Block block = loc.getBlock();
        World world = loc.getWorld();
        
        if (block.getLightFromSky() < 15 || !world.isDayTime()) return 0.0;
        
        double weatherFactor = world.hasStorm() ? 0.35 : 1.0;
        return switch (type) {
            case SOLAR_PANEL_BASIC    -> plugin.getConfig().getDouble("energy.solar_panel_basic.production", 20) * weatherFactor;
            case SOLAR_PANEL_ADVANCED -> {
                if (loc.getBlockY() < plugin.getConfig().getInt("energy.solar_panel_advanced.min-y", 100)) yield 0.0;
                yield plugin.getConfig().getDouble("energy.solar_panel_advanced.production", 60) * weatherFactor;
            }
            default -> 0.0;
        };
    }

    private double getLossFactorFor(EnergyGraph graph) {
        double lossPerBlock = plugin.getConfig().getDouble("energy.cable_basic.loss-per-block", 0.02);
        return Math.min(0.6, graph.getCables().size() * (lossPerBlock / 4.0));
    }

    // ─── Simulation ───────────────────────────────────────────────────────

    public void tick() {
        rebuildGraphs();
        for (Map.Entry<Location, EnergyGraph> entry : graphs.entrySet()) {
            Location coreLoc = entry.getKey();
            EnergyGraph graph = entry.getValue();

            double production = 0;
            for (Location src : graph.getSources()) {
                EnergyBlockType t = getType(src);
                if (t != null) production += getProductionOf(t, src);
            }
            
            double net = production * (1 - getLossFactorFor(graph));
            graph.setLastProduction(net);
            distributeToStorages(graph, net);

            // Vente auto de l'excès
            tryAutoSell(graph);

            double consumed = runInterfaces(graph)
                    + runElectricFurnaces(graph, graphFurnaces.getOrDefault(coreLoc, Collections.emptyList()));
            graph.setLastConsumption(consumed);

            checkRegulators(graph);
        }
        saveAll();
    }

    private void distributeToStorages(EnergyGraph graph, double amount) {
        if (amount <= 0 || graph.getStorages().isEmpty()) return;
        long toDistribute = Math.round(amount);
        
        for (Location loc : graph.getStorages()) {
            if (toDistribute <= 0) break;
            BlockRecord r = registry.get(loc);
            if (r == null) continue;
            
            long cap = getCapacityOf(r.type);
            long space = cap - r.stored;
            if (space <= 0) continue;
            
            long add = Math.min(space, toDistribute);
            r.stored += add;
            toDistribute -= add;
        }
    }

    public long getTotalStoredOf(EnergyGraph graph) {
        long total = 0;
        for (Location loc : graph.getStorages()) {
            BlockRecord r = registry.get(loc);
            if (r != null) total += r.stored;
        }
        return total;
    }

    private boolean consumeEnergy(EnergyGraph graph, long amount) {
        if (getTotalStoredOf(graph) < amount) return false;
        long remaining = amount;
        
        for (Location loc : graph.getStorages()) {
            if (remaining <= 0) break;
            BlockRecord r = registry.get(loc);
            if (r == null || r.stored <= 0) continue;
            
            long take = Math.min(r.stored, remaining);
            r.stored -= take;
            remaining -= take;
        }
        return true;
    }

    /** Vente automatique de l'excès d'énergie via EnergyMarketManager. */
    private void tryAutoSell(EnergyGraph graph) {
        UUID owner = graph.getOwnerId();
        if (owner == null) return;
        
        EnergyMarketManager.MarketData data = plugin.getEnergyMarketManager().getOrCreate(owner);
        if (!data.autoSell) return;

        // Calcule le surplus au-dessus de 90% de capacité
        long stored = getTotalStoredOf(graph);
        long capacity = graph.getTotalCapacity();
        if (capacity <= 0) return;
        
        long threshold = (long) (capacity * 0.9);
        long surplus = stored - threshold;
        if (surplus <= 0) return;

        // Consomme l'énergie en surplus et la vend
        if (!consumeEnergy(graph, surplus)) return;
        Player ownerOnline = Bukkit.getPlayer(owner);
        plugin.getEnergyMarketManager().trySell(owner, surplus, ownerOnline);
    }

    /** Transfert items coffre -> stockage Nexus, consomme de l'énergie. */
    private double runInterfaces(EnergyGraph graph) {
        if (graph.isInterfacesPaused() || graph.getOwnerId() == null) return 0;
        
        double energyPerItem = plugin.getConfig().getDouble("energy.interface_block.energy-per-item", 5);
        double consumed = 0;
        NexusNetwork network = plugin.getNexusManager().getOrCreateNetwork(graph.getOwnerId());
        int maxTypes = plugin.getUpgradeManager().getPagesForTier(network.getTier()) * 45;

        for (Location loc : graph.getInterfaces()) {
            Inventory adj = findAdjacentInventory(loc);
            if (adj == null) continue;
            
            ItemStack toMove = firstNonEmpty(adj);
            if (toMove == null) continue;
            if (!consumeEnergy(graph, Math.round(energyPerItem))) continue;
            
            ItemStack single = toMove.clone();
            single.setAmount(1);
            if (plugin.getStorageManager().deposit(graph.getOwnerId(), single, maxTypes)) {
                toMove.setAmount(toMove.getAmount() - 1);
                consumed += energyPerItem;
            }
        }
        if (consumed > 0) {
            plugin.getGuiManager().refreshStorageViewers(graph.getOwnerId());
        }
        return consumed;
    }

    /** Cuisson accélérée via blocs FURNACE du réseau. */
    private double runElectricFurnaces(EnergyGraph graph, List<Location> furnaceLocs) {
        if (graph.isInterfacesPaused() || furnaceLocs.isEmpty()) return 0;
        
        double energyPerTick = plugin.getConfig().getDouble("energy.electric_furnace.energy-per-tick", 75.0);
        double consumed = 0;
        
        for (Location loc : furnaceLocs) {
            Furnace adj = findAdjacentFurnace(loc);
            if (adj == null) continue;
            
            FurnaceInventory inv = adj.getInventory();
            ItemStack smelting = inv.getSmelting();
            
            if (smelting == null || smelting.getType() == Material.AIR) {
                // Éteindre visuellement le four
                if (adj.getBlockData() instanceof Lightable l && l.isLit() && adj.getCookTime() == 0) {
                    l.setLit(false);
                    adj.setBlockData(l);
                    adj.update(true, false);
                }
                continue;
            }
            
            ItemStack result = inv.getResult();
            if (result != null && result.getType() != Material.AIR && result.getAmount() >= result.getMaxStackSize()) continue;
            if (!consumeEnergy(graph, Math.round(energyPerTick))) continue;
            
            consumed += energyPerTick;
            if (adj.getBurnTime() < 2) adj.setBurnTime((short) 40);
            if (adj.getBlockData() instanceof Lightable l && !l.isLit()) {
                l.setLit(true);
                adj.setBlockData(l);
            }
            adj.setCookTime((short) Math.min(adj.getCookTime() + 5, adj.getCookTimeTotal() - 1));
            adj.update(true, false);
        }
        return consumed;
    }

    private ItemStack firstNonEmpty(Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) return item;
        }
        return null;
    }

    private Inventory findAdjacentInventory(Location loc) {
        for (Location n : getNeighbors(loc)) {
            if (n.getBlock().getState() instanceof InventoryHolder h) return h.getInventory();
        }
        return null;
    }

    private Furnace findAdjacentFurnace(Location loc) {
        for (Location n : getNeighbors(loc)) {
            if (n.getBlock().getState() instanceof Furnace f) return f;
        }
        return null;
    }

    private void checkRegulators(EnergyGraph graph) {
        if (graph.getRegulators().isEmpty()) return;
        int lowest = 100;
        
        for (Location loc : graph.getRegulators()) {
            BlockRecord r = registry.get(loc);
            if (r != null) lowest = Math.min(lowest, r.threshold);
        }
        
        boolean shouldPause = graph.getFillPercent() < lowest;
        boolean wasPaused = graph.isInterfacesPaused();
        graph.setInterfacesPaused(shouldPause);
        
        if (shouldPause && !wasPaused && graph.getOwnerId() != null) {
            NexusNetwork net = plugin.getNexusManager().getOrCreateNetwork(graph.getOwnerId());
            if (net.isNotificationsEnabled()) {
                Player owner = Bukkit.getPlayer(graph.getOwnerId());
                if (owner != null) {
                    String rawMsg = plugin.getConfig().getString("messages.low-energy", "");
                    String msg = rawMsg.replace("{name}", net.getName() == null ? "" : net.getName())
                                       .replace("{percent}", String.valueOf(Math.round(graph.getFillPercent())));
                    owner.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                }
            }
        }
    }

    // ─── Utilitaires GUI ──────────────────────────────────────────────────

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

    public record EnergyStats(long capacity, long stored, double production, double consumption,
                             int cableCount, int machineCount, int networkCount) {}

    public EnergyStats getStatsForOwner(UUID owner) {
        long cap = 0, stored = 0;
        double prod = 0, cons = 0;
        int cables = 0, machines = 0, nets = 0;
        
        for (EnergyGraph g : graphs.values()) {
            if (!owner.equals(g.getOwnerId())) continue;
            cap += g.getTotalCapacity();
            stored += getTotalStoredOf(g);
            prod += g.getLastProduction();
            cons += g.getLastConsumption();
            cables += g.getCables().size();
            machines += g.machineCount();
            nets++;
        }
        return new EnergyStats(cap, stored, prod, cons, cables, machines, nets);
    }
}
