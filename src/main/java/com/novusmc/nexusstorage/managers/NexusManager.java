package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
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
 */
public class NexusManager {

    private final Main plugin;
    private final File coresFile;
    private YamlConfiguration coresYml;

    // owner UUID -> reseau (charge a la demande, garde en cache tant que le serveur tourne)
    private final Map<UUID, NexusNetwork> networks = new HashMap<>();

    public NexusManager(Main plugin) {
        this.plugin = plugin;
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
        try {
            coresYml.save(coresFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder cores.yml", e);
        }
    }

    private String keyFor(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    /**
     * Retourne le reseau Nexus d'un joueur, en le chargeant depuis le disque
     * si necessaire, ou en le creant s'il n'existe pas encore.
     */
    public NexusNetwork getOrCreateNetwork(UUID owner) {
        return networks.computeIfAbsent(owner, id -> plugin.getAccessManager().loadNetwork(id));
    }

    /**
     * Retourne le reseau d'un joueur uniquement s'il existe deja
     * (soit en cache, soit un fichier access existe sur le disque).
     */
    public NexusNetwork getNetworkIfExists(UUID owner) {
        if (networks.containsKey(owner)) return networks.get(owner);
        File accessFile = new File(new File(plugin.getDataFolder(), "access"), owner + ".yml");
        boolean hasCore = false;
        for (String key : coresYml.getKeys(false)) {
            if (owner.toString().equals(coresYml.getString(key))) {
                hasCore = true;
                break;
            }
        }
        if (accessFile.exists() || hasCore) {
            return getOrCreateNetwork(owner);
        }
        return null;
    }

    /**
     * Enregistre un nouveau Nexus Core pose par un joueur, et cree son
     * reseau s'il n'en avait pas deja un.
     */
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
                UUID owner = UUID.fromString(ownerStr);
                NexusNetwork network = networks.get(owner);
                if (network != null) {
                    network.removeCore(key);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /**
     * Retourne l'owner d'un Nexus Core a une position donnee, ou null
     * si aucun core n'est enregistre a cet endroit.
     */
    public UUID getOwnerAt(Location location) {
        String key = keyFor(location);
        String ownerStr = coresYml.getString(key);
        if (ownerStr == null) return null;
        try {
            return UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isCoreLocation(Location location) {
        return coresYml.contains(keyFor(location));
    }

    public int cachedNetworkCount() {
        return networks.size();
    }

    public void saveAll() {
        saveCoresFile();
        for (NexusNetwork network : networks.values()) {
            plugin.getAccessManager().saveNetworkMeta(network);
        }
    }
}
