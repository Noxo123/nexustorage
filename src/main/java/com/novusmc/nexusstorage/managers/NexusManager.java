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
 * Gere l'ensemble des Nexus Core places dans le monde (cores.yml) ainsi que
 * le cache en memoire des reseaux Nexus (NexusNetwork) des joueurs.
 *
 * v2 : getNetworkIfExists() remonte aussi le reseau du proprietaire de
 * l'entreprise dont le joueur est membre, corrigeant le bug "Vous n'etes
 * dans aucun reseau" lors de l'utilisation de /nexus apres acceptation.
 */
public class NexusManager {

    private final Main plugin;
    private final File coresFile;
    private YamlConfiguration coresYml;

    private final Map<UUID, NexusNetwork> networks = new HashMap<>();

    public NexusManager(Main plugin) {
        this.plugin    = plugin;
        this.coresFile = new File(plugin.getDataFolder(), "cores.yml");
        loadCoresFile();
    }

    private void loadCoresFile() {
        if (!coresFile.exists()) {
            try {
                coresFile.getParentFile().mkdirs();
                coresFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Impossible de creer cores.yml", e);
            }
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

    public NexusNetwork getOrCreateNetwork(UUID owner) {
        return networks.computeIfAbsent(owner, id -> plugin.getAccessManager().loadNetwork(id));
    }

    /**
     * Retourne le reseau d'un joueur s'il existe.
     *
     * Ordre de resolution :
     *  1. Le joueur est proprietaire d'un reseau (fichier access/ ou core enregistre).
     *  2. Le joueur est membre d'une entreprise → reseau du proprietaire de l'entreprise.
     */
    public NexusNetwork getNetworkIfExists(UUID playerUuid) {
        // 1. Reseau propre
        if (networks.containsKey(playerUuid)) return networks.get(playerUuid);
        File accessFile = new File(new File(plugin.getDataFolder(), "access"), playerUuid + ".yml");
        boolean hasCore = false;
        for (String key : coresYml.getKeys(false)) {
            if (playerUuid.toString().equals(coresYml.getString(key))) { hasCore = true; break; }
        }
        if (accessFile.exists() || hasCore) return getOrCreateNetwork(playerUuid);

        // 2. Reseau via entreprise (FIX bug invitation)
        Company company = plugin.getCompanyManager().getByPlayer(playerUuid);
        if (company != null) {
            UUID companyOwner = company.getOwner();
            // On ne cree pas le reseau si l'owner n'en a pas encore
            if (networks.containsKey(companyOwner)) return networks.get(companyOwner);
            File ownerAccess = new File(new File(plugin.getDataFolder(), "access"), companyOwner + ".yml");
            boolean ownerHasCore = false;
            for (String key : coresYml.getKeys(false)) {
                if (companyOwner.toString().equals(coresYml.getString(key))) { ownerHasCore = true; break; }
            }
            if (ownerAccess.exists() || ownerHasCore) return getOrCreateNetwork(companyOwner);
        }

        return null;
    }

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
                NexusNetwork network = networks.get(UUID.fromString(ownerStr));
                if (network != null) network.removeCore(key);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public UUID getOwnerAt(Location location) {
        String ownerStr = coresYml.getString(keyFor(location));
        if (ownerStr == null) return null;
        try { return UUID.fromString(ownerStr); } catch (IllegalArgumentException e) { return null; }
    }

    public boolean isCoreLocation(Location location) { return coresYml.contains(keyFor(location)); }
    public int cachedNetworkCount()                   { return networks.size(); }

    public void saveAll() {
        saveCoresFile();
        for (NexusNetwork network : networks.values())
            plugin.getAccessManager().saveNetworkMeta(network);
    }
}
