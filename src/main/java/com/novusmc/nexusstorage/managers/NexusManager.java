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
 * Gere les Nexus Core places dans le monde (cores.yml)
 * et le cache en memoire des NexusNetwork.
 */
public class NexusManager {

    private final Main plugin;

    private final File coresFile;
    private YamlConfiguration coresYml;

    /** owner UUID -> NexusNetwork */
    private final Map<UUID, NexusNetwork> networks = new HashMap<>();

    // Connected blocks
    private final File connectedBlocksFile;
    private YamlConfiguration connectedBlocksYml;


    public NexusManager(Main plugin) {
        this.plugin = plugin;

        this.coresFile = new File(plugin.getDataFolder(), "cores.yml");
        this.connectedBlocksFile = new File(plugin.getDataFolder(), "connected_blocks.yml");

        loadCoresFile();
    }


    // ==================================================
    // CORES.YML
    // ==================================================

    private void loadCoresFile() {

        if (!coresFile.exists()) {
            try {
                coresFile.getParentFile().mkdirs();
                coresFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger()
                        .log(Level.SEVERE, "Impossible de creer cores.yml", e);
            }
        }

        coresYml = YamlConfiguration.loadConfiguration(coresFile);
    }


    private void saveCoresFile() {

        try {
            coresYml.save(coresFile);

        } catch (IOException e) {
            plugin.getLogger()
                    .log(Level.SEVERE, "Impossible de sauvegarder cores.yml", e);
        }
    }


    private String keyFor(Location loc) {

        return loc.getWorld().getName()
                + ";"
                + loc.getBlockX()
                + ";"
                + loc.getBlockY()
                + ";"
                + loc.getBlockZ();
    }



    // ==================================================
    // NETWORK
    // ==================================================

    public NexusNetwork getOrCreateNetwork(UUID owner) {

        return networks.computeIfAbsent(
                owner,
                id -> plugin.getAccessManager().loadNetwork(id)
        );
    }


    public NexusNetwork getNetworkIfExists(UUID playerUuid) {

        File accessFolder = new File(
                plugin.getDataFolder(),
                "access"
        );


        // propriétaire
        File ownFile = new File(
                accessFolder,
                playerUuid + ".yml"
        );


        if (ownFile.exists()) {
            return getOrCreateNetwork(playerUuid);
        }


        // membre cache
        for (NexusNetwork network : networks.values()) {

            if (network.getMembers().containsKey(playerUuid)) {
                return network;
            }
        }



        // scan fichiers access
        if (accessFolder.exists()) {

            File[] files = accessFolder.listFiles();

            if (files != null) {

                for (File f : files) {


                    if (!f.getName().endsWith(".yml")) {
                        continue;
                    }


                    try {

                        UUID owner = UUID.fromString(
                                f.getName().replace(".yml", "")
                        );


                        if (owner.equals(playerUuid)) {
                            continue;
                        }


                        NexusNetwork net =
                                getOrCreateNetwork(owner);


                        if (net.getMembers().containsKey(playerUuid)) {
                            return net;
                        }


                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }



        // entreprise
        Company company =
                plugin.getCompanyManager()
                        .getByPlayer(playerUuid);


        if (company != null) {

            UUID owner = company.getOwner();

            File ownerFile =
                    new File(accessFolder, owner + ".yml");


            if (ownerFile.exists()
                    || networks.containsKey(owner)) {

                return getOrCreateNetwork(owner);
            }
        }


        return null;
    }



    // ==================================================
    // CORES
    // ==================================================

    public NexusNetwork registerCore(UUID owner, Location location) {

        String key = keyFor(location);


        coresYml.set(
                key,
                owner.toString()
        );


        saveCoresFile();


        NexusNetwork network =
                getOrCreateNetwork(owner);


        network.addCore(
                key,
                location
        );


        return network;
    }



    public void unregisterCore(Location location) {

        String key = keyFor(location);


        String owner =
                coresYml.getString(key);


        coresYml.set(
                key,
                null
        );


        saveCoresFile();



        if (owner != null) {

            try {

                NexusNetwork net =
                        networks.get(
                                UUID.fromString(owner)
                        );


                if (net != null) {
                    net.removeCore(key);
                }


            } catch (IllegalArgumentException ignored) {
            }
        }
    }



    public UUID getOwnerAt(Location location) {

        String value =
                coresYml.getString(
                        keyFor(location)
                );


        if (value == null) {
            return null;
        }


        try {

            return UUID.fromString(value);

        } catch (IllegalArgumentException e) {

            return null;
        }
    }



    public boolean isCoreLocation(Location location) {

        return coresYml.contains(
                keyFor(location)
        );
    }


    public int cachedNetworkCount() {

        return networks.size();
    }



    // ==================================================
    // CONNECTED BLOCKS
    // ==================================================

    private YamlConfiguration getConnectedYml() {

        if (connectedBlocksYml == null) {


            if (!connectedBlocksFile.exists()) {

                try {

                    connectedBlocksFile
                            .getParentFile()
                            .mkdirs();


                    connectedBlocksFile.createNewFile();


                } catch (IOException e) {

                    plugin.getLogger()
                            .log(Level.SEVERE,
                                    "Impossible de creer connected_blocks.yml",
                                    e);
                }
            }


            connectedBlocksYml =
                    YamlConfiguration.loadConfiguration(
                            connectedBlocksFile
                    );
        }


        return connectedBlocksYml;
    }



    private void saveConnectedYml() {

        try {

            getConnectedYml()
                    .save(
                            connectedBlocksFile
                    );


        } catch (IOException e) {

            plugin.getLogger()
                    .log(Level.SEVERE,
                            "Impossible de sauvegarder connected_blocks.yml",
                            e);
        }
    }



    public void registerConnectedBlock(Location loc, UUID owner) {

        getConnectedYml()
                .set(
                        keyFor(loc),
                        owner.toString()
                );


        saveConnectedYml();
    }



    public void unregisterConnectedBlock(Location loc) {

        getConnectedYml()
                .set(
                        keyFor(loc),
                        null
                );


        saveConnectedYml();
    }



    public UUID getConnectedBlockOwner(Location loc) {

        String value =
                getConnectedYml()
                        .getString(
                                keyFor(loc)
                        );


        if (value == null) {
            return null;
        }


        try {

            return UUID.fromString(value);

        } catch (IllegalArgumentException e) {

            return null;
        }
    }



    public boolean isConnectedBlock(Location loc) {

        return getConnectedYml()
                .contains(
                        keyFor(loc)
                );
    }



    // ==================================================
    // SAVE
    // ==================================================

    public void saveAll() {

        saveCoresFile();
        saveConnectedYml();


        for (NexusNetwork network : networks.values()) {

            plugin.getAccessManager()
                    .saveNetworkMeta(network);
        }
    }
}
