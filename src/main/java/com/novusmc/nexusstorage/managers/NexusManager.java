package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.Company;
import com.novusmc.nexusstorage.model.NexusNetwork;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gere les Nexus Core places dans le monde (cores.yml) et le cache en memoire
 * des NexusNetwork.
 *
 * Résolution d'accès pour un joueur (getNetworkIfExists) :
 *   1. Le joueur est propriétaire → son réseau.
 *   2. Le joueur est membre listé dans le fichier access/<owner>.yml → réseau de cet owner.
 *   3. Le joueur est membre d'une entreprise → réseau du propriétaire de l'entreprise.
 *
 * Cela corrige le bug où un joueur ajouté via /nexus access voit "aucun réseau".
 */
public class NexusManager {

    private final Main plugin;
    private final File coresFile;
    private YamlConfiguration coresYml;

    /** owner UUID -> NexusNetwork (cache mémoire) */
    private final Map<UUID, NexusNetwork> networks = new HashMap<>();

    public NexusManager(Main plugin) {
        this.plugin    = plugin;
        this.coresFile = new File(plugin.getDataFolder(), "cores.yml");
        loadCoresFile();
    }

    // ── Fichier cores.yml ─────────────────────────────────────────────────

    private void loadCoresFile() {
        if (!coresFile.exists()) {
            try { coresFile.getParentFile().mkdirs(); coresFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Impossible de creer cores.yml", e); }
        }
        coresYml = YamlConfiguration.loadConfiguration(coresFile);
    }

    private void saveCoresFile() {
        try { coresYml.save(coresFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder cores.yml", e); }
    }

    private String keyFor(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    // ── Réseau mémoire ────────────────────────────────────────────────────

    public NexusNetwork getOrCreateNetwork(UUID owner) {
        return networks.computeIfAbsent(owner, id -> plugin.getAccessManager().loadNetwork(id));
    }

    /**
     * Résout le réseau visible pour un joueur donné.
     *
     * Ordre de résolution :
     *  1. Propriétaire direct.
     *  2. Membre listé dans un fichier access/ existant (scan des fichiers).
     *  3. Membre d'une entreprise → réseau du propriétaire de l'entreprise.
     */
    public NexusNetwork getNetworkIfExists(UUID playerUuid) {
        // 1. Propriétaire
        File accessFolder = new File(plugin.getDataFolder(), "access");
        File ownFile = new File(accessFolder, playerUuid + ".yml");
        if (ownFile.exists()) return getOrCreateNetwork(playerUuid);
        // Vérifie aussi le cache
        if (networks.containsKey(playerUuid)) return networks.get(playerUuid);

        // 2. Membre d'un réseau existant (scan des fichiers access/)
        //    D'abord chercher dans les réseaux déjà en cache (O(n) mais évite I/O)
        for (NexusNetwork cached : networks.values()) {
            if (cached.getMembers().containsKey(playerUuid)) return cached;
        }
        //    Ensuite scan des fichiers pour trouver un réseau qui liste ce joueur
        if (accessFolder.exists()) {
            for (File f : accessFolder.listFiles()) {
                if (!f.getName().endsWith(".yml")) continue;
                try {
                    UUID ownerUuid = UUID.fromString(f.getName().replace(".yml", ""));
                    if (ownerUuid.equals(playerUuid)) continue;
                    // Charger en cache si pas encore présent
                    NexusNetwork net = getOrCreateNetwork(ownerUuid);
                    if (net.getMembers().containsKey(playerUuid)) return net;
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // 3. Membre d'une entreprise
        Company company = plugin.getCompanyManager().getByPlayer(playerUuid);
        if (company != null) {
            UUID companyOwner = company.getOwner();
            File ownerFile = new File(accessFolder, companyOwner + ".yml");
            if (ownerFile.exists() || networks.containsKey(companyOwner))
                return getOrCreateNetwork(companyOwner);
        }

        return null;
    }

    // ── Cores ─────────────────────────────────────────────────────────────

    public NexusNetwork registerCore(UUID owner, Location location) {
        String key = keyFor(location);
        coresYml.set(key, owner.toString());
        saveCoresFile();
        NexusNetwork network = getOrCreateNetwork(owner);
        network.addCore(key, location);
        return network;
    }

    public void unregisterCore(Location location) {
        String key = keyFor(location);
        String ownerStr = coresYml.getString(key);
        coresYml.set(key, null);
        saveCoresFile();
        if (ownerStr != null) {
            try {
                NexusNetwork net = networks.get(UUID.fromString(ownerStr));
                if (net != null) net.removeCore(key);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public UUID getOwnerAt(Location location) {
        String s = coresYml.getString(keyFor(location));
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    public boolean isCoreLocation(Location location) { return coresYml.contains(keyFor(location)); }
    public int cachedNetworkCount() { return networks.size(); }

    // ── Blocs connectés (ConnectedBlock) ──────────────────────────────────
    // Fichier connected_blocks.yml : "world;x;y;z" -> ownerUUID

    private final File connectedBlocksFile = new File(
            plugin.getDataFolder(), "connected_blocks.yml");
    private YamlConfiguration connectedBlocksYml = null;

    private YamlConfiguration getConnectedYml() {
        if (connectedBlocksYml == null) {
            if (!connectedBlocksFile.exists()) {
                try { connectedBlocksFile.getParentFile().mkdirs(); connectedBlocksFile.createNewFile(); }
                catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Impossible de creer connected_blocks.yml", e); }
            }
            connectedBlocksYml = YamlConfiguration.loadConfiguration(connectedBlocksFile);
        }
        return connectedBlocksYml;
    }

    private void saveConnectedYml() {
        try { getConnectedYml().save(connectedBlocksFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder connected_blocks.yml", e); }
    }

    public void registerConnectedBlock(Location loc, UUID owner) {
        getConnectedYml().set(keyFor(loc), owner.toString());
        saveConnectedYml();
    }

    public void unregisterConnectedBlock(Location loc) {
        getConnectedYml().set(keyFor(loc), null);
        saveConnectedYml();
    }

    public UUID getConnectedBlockOwner(Location loc) {
        String s = getConnectedYml().getString(keyFor(loc));
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    public boolean isConnectedBlock(Location loc) { return getConnectedYml().contains(keyFor(loc)); }

    // ── Sauvegarde ────────────────────────────────────────────────────────

    public void saveAll() {
        saveCoresFile();
        for (NexusNetwork network : networks.values())
            plugin.getAccessManager().saveNetworkMeta(network);
    }
}
