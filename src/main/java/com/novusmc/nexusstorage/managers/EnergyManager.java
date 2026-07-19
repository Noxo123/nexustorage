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
 * Gestionnaire du réseau d'énergie physique (style RF/FE).
 *
 * Toutes les clés du registre sont des Strings "world;x;y;z" pour garantir
 * l'égalité lors des lookups (Location.equals() est basé sur des doubles
 * et peut être inconsistant entre objets créés séparément).
 */
public class EnergyManager {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST
    };

    // ─── Données par bloc ────────────────────────────────────────────────

    private static class BlockRecord {
        final EnergyBlockType type;
        final Location        location;   // clone normalisé (block coords)
        UUID   owner;
        long   stored;
        int    threshold = 20;

        BlockRecord(EnergyBlockType type, Location location) {
            this.type     = type;
            // Normaliser en coordonnées bloc entières pour cohérence
            this.location = location.getWorld()
                    .getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ())
                    .getLocation();
        }
    }

    // ─── État interne ────────────────────────────────────────────────────

    private final Main plugin;
    private final File file;

    /** Clé "world;x;y;z" → enregistrement. String key = pas de problème d'égalité. */
    private final Map<String, BlockRecord> registry = new HashMap<>();

    /** Core Location → graphe. Reconstruit à chaque tick. */
    private final Map<String, EnergyGraph>       graphs       = new HashMap<>();
    private final Map<String, List<Location>>    graphFurnaces = new HashMap<>();
    private final Map<String, List<Location>>    graphShields  = new HashMap<>();

    public EnergyManager(Main plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "energy_blocks.yml");
        load();
    }

    // ─── Clé String ──────────────────────────────────────────────────────

    private static String key(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private static String key(int x, int y, int z, String world) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    // ─── Persistence ──────────────────────────────────────────────────────

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            try {
                String typeName = yml.getString(k + ".type");
                if (typeName == null) continue;
                EnergyBlockType type = EnergyBlockType.valueOf(typeName);

                String[] parts = k.split(";");
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                Location loc = world.getBlockAt(Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3])).getLocation();

                BlockRecord rec = new BlockRecord(type, loc);
                rec.stored    = yml.getLong(k + ".stored", 0);
                rec.threshold = yml.getInt(k + ".threshold",
                        plugin.getConfig().getInt("energy.regulator.default-threshold", 20));
                String ownerStr = yml.getString(k + ".owner");
                if (ownerStr != null) rec.owner = UUID.fromString(ownerStr);

                registry.put(k, rec);
            } catch (Exception e) {
                plugin.getLogger().warning("energy_blocks.yml entrée invalide ignorée: " + k);
            }
        }
    }

    public void saveAll() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, BlockRecord> e : registry.entrySet()) {
            String k = e.getKey();
            BlockRecord r = e.getValue();
            yml.set(k + ".type", r.type.name());
            yml.set(k + ".stored", r.stored);
            yml.set(k + ".threshold", r.threshold);
            if (r.owner != null) yml.set(k + ".owner", r.owner.toString());
        }
        try { yml.save(file); }
        catch (IOException ex) { plugin.getLogger().log(Level.SEVERE, "Sauvegarde energy_blocks.yml échouée", ex); }
    }

    // ─── Registre public ─────────────────────────────────────────────────

    public void registerBlock(Location location, EnergyBlockType type, Player placer) {
        BlockRecord rec = new BlockRecord(type, location);
        if (type.getRole() == EnergyBlockType.Role.CORE)
            rec.owner = placer.getUniqueId();
        if (type.getRole() == EnergyBlockType.Role.REGULATOR)
            rec.threshold = plugin.getConfig().getInt("energy.regulator.default-threshold", 20);
        registry.put(key(location), rec);
    }

    public void unregisterBlock(Location location) { registry.remove(key(location)); }

    public boolean isEnergyBlock(Location location) { return registry.containsKey(key(location)); }

    public EnergyBlockType getType(Location location) {
        BlockRecord r = registry.get(key(location));
        return r == null ? null : r.type;
    }

    public int cycleThreshold(Location location, boolean increase) {
        BlockRecord r = registry.get(key(location));
        if (r == null || r.type.getRole() != EnergyBlockType.Role.REGULATOR) return -1;
        r.threshold = Math.max(0, Math.min(100, r.threshold + (increase ? 5 : -5)));
        return r.threshold;
    }

    public int registrySize() { return registry.size(); }

    // ─── BFS / reconstruction des graphes ────────────────────────────────

    private List<Location> neighbors(Location loc) {
        List<Location> res = new ArrayList<>(6);
        for (BlockFace f : FACES)
            res.add(loc.getWorld().getBlockAt(
                    loc.getBlockX() + f.getModX(),
                    loc.getBlockY() + f.getModY(),
                    loc.getBlockZ() + f.getModZ()).getLocation());
        return res;
    }

    private void rebuildGraphs() {
        graphs.clear();
        graphFurnaces.clear();
        graphShields.clear();
        Set<String> visited = new HashSet<>();

        for (BlockRecord seed : registry.values()) {
            if (seed.type.getRole() != EnergyBlockType.Role.CORE) continue;
            String seedKey = key(seed.location);
            if (visited.contains(seedKey)) continue;

            EnergyGraph graph        = new EnergyGraph(seed.location.clone(), seed.owner);
            List<Location> furnaces  = new ArrayList<>();
            List<Location> shields   = new ArrayList<>();
            visited.add(seedKey);

            Deque<Location> queue = new ArrayDeque<>();
            queue.add(seed.location);

            while (!queue.isEmpty()) {
                Location cur = queue.poll();
                for (Location nb : neighbors(cur)) {
                    String nbKey = key(nb);
                    if (visited.contains(nbKey)) continue;
                    BlockRecord nbRec = registry.get(nbKey);
                    if (nbRec == null) continue;
                    visited.add(nbKey);
                    queue.add(nb);

                    switch (nbRec.type.getRole()) {
                        case CABLE     -> graph.getCables().add(nb);
                        case SOURCE    -> graph.getSources().add(nb);
                        case STORAGE   -> {
                            graph.getStorages().add(nb);
                            graph.setTotalCapacity(graph.getTotalCapacity() + storageCapOf(nbRec.type));
                            graph.setTotalStored(graph.getTotalStored() + nbRec.stored);
                        }
                        case INTERFACE -> graph.getInterfaces().add(nb);
                        case FURNACE   -> furnaces.add(nb);
                        case SHIELD    -> shields.add(nb);
                        case REGULATOR -> graph.getRegulators().add(nb);
                        case MONITOR   -> graph.getMonitors().add(nb);
                        case CORE      -> {} // second core ignoré
                    }
                }
            }
            graphs.put(seedKey, graph);
            graphFurnaces.put(seedKey, furnaces);
            graphShields.put(seedKey, shields);
        }
    }

    // ─── Capacité / production ───────────────────────────────────────────

    private long storageCapOf(EnergyBlockType type) {
        return switch (type) {
            case CAPACITOR_BASIC    -> plugin.getConfig().getLong("energy.capacitor_basic.capacity",    10_000);
            case CAPACITOR_ADVANCED -> plugin.getConfig().getLong("energy.capacitor_advanced.capacity", 100_000);
            default -> 0;
        };
    }

    private double productionOf(EnergyBlockType type, Location loc) {
        Block block = loc.getBlock();
        if (block.getLightFromSky() < 15 || !loc.getWorld().isDayTime()) return 0;
        double wf = loc.getWorld().hasStorm() ? 0.35 : 1.0;
        return switch (type) {
            case SOLAR_PANEL_BASIC    -> plugin.getConfig().getDouble("energy.solar_panel_basic.production",    20) * wf;
            case SOLAR_PANEL_ADVANCED -> {
                if (loc.getBlockY() < plugin.getConfig().getInt("energy.solar_panel_advanced.min-y", 100)) yield 0.0;
                yield plugin.getConfig().getDouble("energy.solar_panel_advanced.production", 60) * wf;
            }
            default -> 0;
        };
    }

    private double cableLoss(EnergyGraph g) {
        double loss = plugin.getConfig().getDouble("energy.cable_basic.loss-per-block", 0.02);
        return Math.min(0.6, g.getCables().size() * loss / 4.0);
    }

    // ─── Simulation principale ───────────────────────────────────────────

    public void tick() {
        rebuildGraphs();
        for (Map.Entry<String, EnergyGraph> entry : graphs.entrySet()) {
            String coreLoc    = entry.getKey();
            EnergyGraph graph = entry.getValue();

            // Production solaire
            double prod = 0;
            for (Location src : graph.getSources()) {
                EnergyBlockType t = getType(src);
                if (t != null) prod += productionOf(t, src);
            }
            double net = prod * (1 - cableLoss(graph));
            graph.setLastProduction(net);
            fillStorages(graph, net);

            // Vente auto du surplus
            autoSell(graph);

            // Consommateurs
            double cons = runInterfaces(graph)
                        + runFurnaces(graph, graphFurnaces.getOrDefault(coreLoc, Collections.emptyList()));
            graph.setLastConsumption(cons);

            // Régulateurs
            checkRegulators(graph);
        }
        saveAll();
    }

    // ─── Stockage ────────────────────────────────────────────────────────

    private void fillStorages(EnergyGraph g, double amount) {
        if (amount <= 0 || g.getStorages().isEmpty()) return;
        long remaining = Math.round(amount);
        for (Location loc : g.getStorages()) {
            if (remaining <= 0) break;
            BlockRecord r = registry.get(key(loc));
            if (r == null) continue;
            long space = storageCapOf(r.type) - r.stored;
            if (space <= 0) continue;
            long add = Math.min(space, remaining);
            r.stored  += add;
            remaining -= add;
        }
    }

    public long totalStoredOf(EnergyGraph g) {
        long total = 0;
        for (Location loc : g.getStorages()) {
            BlockRecord r = registry.get(key(loc));
            if (r != null) total += r.stored;
        }
        return total;
    }

    public boolean consumeEnergy(EnergyGraph g, long amount) {
        if (totalStoredOf(g) < amount) return false;
        long remaining = amount;
        for (Location loc : g.getStorages()) {
            if (remaining <= 0) break;
            BlockRecord r = registry.get(key(loc));
            if (r == null || r.stored <= 0) continue;
            long take = Math.min(r.stored, remaining);
            r.stored  -= take;
            remaining -= take;
        }
        return true;
    }

    // ─── Vente auto ───────────────────────────────────────────────────────

    private void autoSell(EnergyGraph g) {
        UUID owner = g.getOwnerId();
        if (owner == null) return;
        EnergyMarketManager.MarketData data = plugin.getEnergyMarketManager().getOrCreate(owner);
        if (!data.autoSell || g.getTotalCapacity() <= 0) return;
        long surplus = totalStoredOf(g) - (long)(g.getTotalCapacity() * 0.9);
        if (surplus <= 0) return;
        if (!consumeEnergy(g, surplus)) return;
        plugin.getEnergyMarketManager().trySell(owner, surplus, Bukkit.getPlayer(owner));
    }

    // ─── Interfaces (transfert items) ────────────────────────────────────

    private double runInterfaces(EnergyGraph g) {
        if (g.isInterfacesPaused() || g.getOwnerId() == null) return 0;
        double costPerItem = plugin.getConfig().getDouble("energy.interface_block.energy-per-item", 5);
        double consumed    = 0;
        NexusNetwork net = plugin.getNexusManager().getOrCreateNetwork(g.getOwnerId());
        int maxTypes     = plugin.getUpgradeManager().getPagesForTier(net.getTier()) * 45;

        for (Location loc : g.getInterfaces()) {
            Inventory adj = adjacentInventory(loc);
            if (adj == null) continue;
            ItemStack first = firstNonEmpty(adj);
            if (first == null) continue;
            if (!consumeEnergy(g, Math.round(costPerItem))) continue;
            ItemStack single = first.clone(); single.setAmount(1);
            if (plugin.getStorageManager().deposit(g.getOwnerId(), single, maxTypes)) {
                first.setAmount(first.getAmount() - 1);
                consumed += costPerItem;
            }
        }
        if (consumed > 0) plugin.getGuiManager().refreshStorageViewers(g.getOwnerId());
        return consumed;
    }

    // ─── Fours électriques ───────────────────────────────────────────────

    private double runFurnaces(EnergyGraph g, List<Location> locs) {
        if (g.isInterfacesPaused() || locs.isEmpty()) return 0;
        double costPerTick = plugin.getConfig().getDouble("energy.electric_furnace.energy-per-tick", 75);
        double consumed    = 0;

        for (Location loc : locs) {
            Furnace furnace = adjacentFurnace(loc);
            if (furnace == null) continue;
            FurnaceInventory inv = furnace.getInventory();
            ItemStack smelting   = inv.getSmelting();
            if (smelting == null || smelting.getType() == Material.AIR) {
                // Éteindre visuellement si plus rien à cuire
                setFurnaceLit(furnace, false);
                continue;
            }
            ItemStack result = inv.getResult();
            if (result != null && result.getType() != Material.AIR
                    && result.getAmount() >= result.getMaxStackSize()) continue;
            if (!consumeEnergy(g, Math.round(costPerTick))) continue;
            consumed += costPerTick;
            if (furnace.getBurnTime() < 2) furnace.setBurnTime((short) 40);
            setFurnaceLit(furnace, true);
            furnace.setCookTime((short) Math.min(furnace.getCookTime() + 5, furnace.getCookTimeTotal() - 1));
            furnace.update(true, false);
        }
        return consumed;
    }

    private void setFurnaceLit(Furnace furnace, boolean lit) {
        if (furnace.getBlockData() instanceof Lightable l && l.isLit() != lit) {
            l.setLit(lit);
            furnace.setBlockData(l);
            furnace.update(true, false);
        }
    }

    // ─── Régulateurs ─────────────────────────────────────────────────────

    private void checkRegulators(EnergyGraph g) {
        if (g.getRegulators().isEmpty()) return;
        int threshold = 100;
        for (Location loc : g.getRegulators()) {
            BlockRecord r = registry.get(key(loc));
            if (r != null) threshold = Math.min(threshold, r.threshold);
        }
        boolean shouldPause = g.getFillPercent() < threshold;
        boolean wasPaused   = g.isInterfacesPaused();
        g.setInterfacesPaused(shouldPause);

        if (shouldPause && !wasPaused && g.getOwnerId() != null) {
            NexusNetwork net = plugin.getNexusManager().getOrCreateNetwork(g.getOwnerId());
            if (net.isNotificationsEnabled()) {
                Player p = Bukkit.getPlayer(g.getOwnerId());
                if (p != null) {
                    String msg = plugin.getConfig().getString("messages.low-energy", "")
                            .replace("{name}", net.getName() == null ? "" : net.getName())
                            .replace("{percent}", String.valueOf(Math.round(g.getFillPercent())));
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                }
            }
        }
    }

    // ─── Utilitaires voisins ─────────────────────────────────────────────

    private Inventory adjacentInventory(Location loc) {
        for (BlockFace f : FACES) {
            Block b = loc.getBlock().getRelative(f);
            if (b.getState() instanceof InventoryHolder h) return h.getInventory();
        }
        return null;
    }

    private Furnace adjacentFurnace(Location loc) {
        for (BlockFace f : FACES) {
            Block b = loc.getBlock().getRelative(f);
            if (b.getState() instanceof Furnace fr) return fr;
        }
        return null;
    }

    private ItemStack firstNonEmpty(Inventory inv) {
        for (ItemStack item : inv.getContents())
            if (item != null && item.getType() != Material.AIR) return item;
        return null;
    }

    // ─── API GUI ─────────────────────────────────────────────────────────

    public EnergyGraph getGraphContaining(Location loc) {
        String k = key(loc);
        for (EnergyGraph g : graphs.values()) {
            if (key(g.getCoreLocation()).equals(k)) return g;
            for (Location l : g.getSources())    if (key(l).equals(k)) return g;
            for (Location l : g.getStorages())   if (key(l).equals(k)) return g;
            for (Location l : g.getCables())     if (key(l).equals(k)) return g;
            for (Location l : g.getInterfaces()) if (key(l).equals(k)) return g;
            for (Location l : g.getRegulators()) if (key(l).equals(k)) return g;
            for (Location l : g.getMonitors())   if (key(l).equals(k)) return g;
        }
        return null;
    }

    public record EnergyStats(long capacity, long stored, double production, double consumption,
                               int cableCount, int machineCount, int networkCount) {}

    public EnergyStats getStatsForOwner(UUID owner) {
        long cap = 0, stored = 0; double prod = 0, cons = 0;
        int cables = 0, machines = 0, nets = 0;
        for (EnergyGraph g : graphs.values()) {
            if (!owner.equals(g.getOwnerId())) continue;
            cap     += g.getTotalCapacity();
            stored  += totalStoredOf(g);
            prod    += g.getLastProduction();
            cons    += g.getLastConsumption();
            cables  += g.getCables().size();
            machines += g.machineCount();
            nets++;
        }
        return new EnergyStats(cap, stored, prod, cons, cables, machines, nets);
    }
}
