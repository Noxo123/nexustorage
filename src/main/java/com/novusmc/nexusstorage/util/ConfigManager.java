package com.novusmc.nexusstorage.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

/**
 * Fusionne intelligemment la config par defaut (JAR) avec la config existante
 * sur disque : ajoute uniquement les cles manquantes, ne supprime rien.
 */
public class ConfigManager {

    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) { this.plugin = plugin; }

    public void mergeDefaultConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) { plugin.saveDefaultConfig(); return; }

        FileConfiguration defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return;
            defaults.loadFromString(new String(in.readAllBytes()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Impossible de lire la config par defaut du JAR", e);
            return;
        }

        FileConfiguration current = plugin.getConfig();
        boolean dirty = false;
        for (String key : defaults.getKeys(true)) {
            if (!current.contains(key)) {
                current.set(key, defaults.get(key));
                dirty = true;
            }
        }

        if (dirty) {
            try {
                current.save(configFile);
                plugin.getLogger().info("config.yml : nouvelles cles ajoutees sans ecraser l'existant.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder config.yml apres fusion", e);
            }
        }
    }
}
