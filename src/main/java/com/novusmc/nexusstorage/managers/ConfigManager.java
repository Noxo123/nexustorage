package com.novusmc.nexusstorage.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

/**
 * Gestionnaire de configuration qui préserve les paramètres existants
 * lors de l'ajout de nouvelles clés.
 */
public class ConfigManager {
    private final JavaPlugin plugin;
    private final FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    /**
     * Charge la configuration sans l'écraser si elle existe déjà.
     * Ajoute seulement les clés manquantes depuis le fichier par défaut.
     */
    public void mergeDefaultConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        // Si le fichier n'existe pas, créer une copie par défaut
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            return;
        }

        // Charger la config par défaut
        FileConfiguration defaultConfig = new YamlConfiguration();
        try {
            InputStream input = plugin.getResource("config.yml");
            if (input != null) {
                String content = new String(input.readAllBytes());
                defaultConfig.loadFromString(content);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Impossible de charger la config par défaut", e);
            return;
        }

        // Copier les clés manquantes de la config par défaut
        boolean modified = false;
        for (String key : defaultConfig.getKeys(true)) {
            if (!config.contains(key)) {
                config.set(key, defaultConfig.get(key));
                modified = true;
            }
        }

        // Sauvegarder si des modifications ont été faites
        if (modified) {
            try {
                config.save(configFile);
                plugin.getLogger().info("Configuration mise à jour avec les nouvelles clés.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur lors de la sauvegarde de la configuration", e);
            }
        }
    }

    /**
     * Ajoute une clé à la configuration si elle n'existe pas.
     */
    public void addIfMissing(String key, Object defaultValue) {
        if (!config.contains(key)) {
            config.set(key, defaultValue);
            try {
                config.save(new File(plugin.getDataFolder(), "config.yml"));
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Impossible de sauvegarder la clé: " + key, e);
            }
        }
    }

    /**
     * Recharge la configuration depuis le fichier.
     */
    public void reload() {
        plugin.reloadConfig();
    }

    /**
     * Obtient la configuration actuelle.
     */
    public FileConfiguration getConfig() {
        return config;
    }
}
