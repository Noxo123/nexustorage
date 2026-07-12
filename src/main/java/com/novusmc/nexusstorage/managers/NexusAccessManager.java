package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.AccessLevel;
import com.novusmc.nexusstorage.model.NexusNetwork;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gere la lecture/ecriture des fichiers access/<owner_uuid>.yml
 * qui stockent le tier du reseau ainsi que la liste des membres autorises.
 */
public class NexusAccessManager {

    private final Main plugin;
    private final File accessFolder;

    public NexusAccessManager(Main plugin) {
        this.plugin = plugin;
        this.accessFolder = new File(plugin.getDataFolder(), "access");
        if (!accessFolder.exists()) {
            accessFolder.mkdirs();
        }
    }

    private File fileFor(UUID owner) {
        return new File(accessFolder, owner.toString() + ".yml");
    }

    /**
     * Charge (ou cree) le reseau Nexus d'un owner depuis le disque.
     */
    public NexusNetwork loadNetwork(UUID owner) {
        NexusNetwork network = new NexusNetwork(owner);
        File file = fileFor(owner);
        if (!file.exists()) {
            String defaultName = plugin.getServer().getOfflinePlayer(owner).getName();
            network.setName((defaultName != null ? defaultName : "Nexus") + "'s Nexus");
            return network;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        network.setTier(yml.getInt("tier", 1));
        network.setName(yml.getString("name", plugin.getServer().getOfflinePlayer(owner).getName() + "'s Nexus"));
        network.setNotificationsEnabled(yml.getBoolean("notifications", true));

        if (yml.isConfigurationSection("members")) {
            for (String key : yml.getConfigurationSection("members").getKeys(false)) {
                try {
                    UUID memberUuid = UUID.fromString(key);
                    AccessLevel level = AccessLevel.fromString(yml.getString("members." + key));
                    network.addMember(memberUuid, level);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return network;
    }

    /**
     * Sauvegarde uniquement les metadonnees (tier + membres) du reseau.
     * Le contenu du stockage est gere separement par NexusStorageManager.
     */
    public void saveNetworkMeta(NexusNetwork network) {
        File file = fileFor(network.getOwner());
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("tier", network.getTier());
        yml.set("name", network.getName() != null ? network.getName()
                : plugin.getServer().getOfflinePlayer(network.getOwner()).getName() + "'s Nexus");
        yml.set("notifications", network.isNotificationsEnabled());

        Map<String, String> members = new HashMap<>();
        for (Map.Entry<UUID, AccessLevel> entry : network.getMembers().entrySet()) {
            members.put(entry.getKey().toString(), entry.getValue().name());
        }
        for (Map.Entry<String, String> entry : members.entrySet()) {
            yml.set("members." + entry.getKey(), entry.getValue());
        }

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder l'acces du reseau " + network.getOwner(), e);
        }
    }

    public void addMember(NexusNetwork network, UUID member, AccessLevel level) {
        network.addMember(member, level);
        saveNetworkMeta(network);
    }

    public void removeMember(NexusNetwork network, UUID member) {
        network.removeMember(member);
        saveNetworkMeta(network);
    }
}
