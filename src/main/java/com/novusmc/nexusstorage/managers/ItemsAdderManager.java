package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

/**
 * Gère la résolution des items/blocs depuis itemsadder.yml.
 *
 * Pour chaque clé (ex: "nexus-core"), on lit :
 *   itemsadder: "namespace:id"  → tente de créer via ItemsAdder API
 *   itemsadder: "default"       → utilise directement le fallback vanilla
 *
 * Si ItemsAdder n'est pas installé ou si la résolution échoue,
 * on retourne toujours un ItemStack vanilla basé sur "fallback".
 */
public class ItemsAdderManager {

    private final Main plugin;
    private boolean pluginPresent;
    private boolean enabled;
    private FileConfiguration iaConfig;

    public ItemsAdderManager(Main plugin) {
        this.plugin = plugin;
        saveDefaultIAConfig();
        reload();
    }

    // ── Init ──────────────────────────────────────────────────────────────

    private void saveDefaultIAConfig() {
        File file = new File(plugin.getDataFolder(), "itemsadder.yml");
        if (!file.exists()) {
            try (InputStream in = plugin.getResource("itemsadder.yml")) {
                if (in != null) {
                    file.getParentFile().mkdirs();
                    java.nio.file.Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Impossible de copier itemsadder.yml par défaut", e);
            }
        }
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "itemsadder.yml");
        iaConfig = YamlConfiguration.loadConfiguration(file);

        // Fusionner les clés manquantes depuis le fichier par défaut du JAR
        try (InputStream in = plugin.getResource("itemsadder.yml")) {
            if (in != null) {
                FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(in));
                for (String key : defaults.getKeys(true)) {
                    if (!iaConfig.contains(key)) iaConfig.set(key, defaults.get(key));
                }
                iaConfig.save(file);
            }
        } catch (Exception ignored) {}

        this.pluginPresent = plugin.getServer().getPluginManager().getPlugin("ItemsAdder") != null;
        this.enabled = plugin.getConfig().getBoolean("integrations.itemsadder.enabled", false)
                && pluginPresent;

        if (plugin.getConfig().getBoolean("integrations.itemsadder.enabled", false) && !pluginPresent) {
            plugin.getLogger().warning("ItemsAdder activé dans la config mais le plugin n'est pas présent.");
        }
        if (enabled) plugin.getLogger().info("ItemsAdderManager : actif, items lus depuis itemsadder.yml.");
    }

    // ── Résolution d'item ─────────────────────────────────────────────────

    /**
     * Résout un item pour la clé donnée (ex: "nexus-core").
     * Retourne un ItemStack vanilla si ItemsAdder est inactif ou si la valeur est "default".
     */
    public ItemStack resolve(String key) {
        String iaId  = iaConfig.getString(key + ".itemsadder", "default");
        String fallback = iaConfig.getString(key + ".fallback", "STONE");

        // Tentative ItemsAdder si activé et ID non vide / non "default"
        if (enabled && !iaId.equalsIgnoreCase("default") && !iaId.isBlank()) {
            try {
                // ItemsAdder API : dev.lone.itemsadder.api.CustomStack
                Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object customStack = csClass.getMethod("getInstance", String.class).invoke(null, iaId);
                if (customStack != null) {
                    ItemStack item = (ItemStack) csClass.getMethod("getItemStack").invoke(customStack);
                    if (item != null) return item.clone();
                }
                plugin.getLogger().warning("ItemsAdder : item introuvable '" + iaId + "', fallback vanilla.");
            } catch (ClassNotFoundException ignored) {
                // ItemsAdder pas au classpath
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Erreur ItemsAdder pour clé '" + key + "'", e);
            }
        }

        // Fallback vanilla
        try {
            Material mat = Material.valueOf(fallback.toUpperCase());
            return new ItemStack(mat);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Material vanilla invalide '" + fallback + "' pour clé '" + key + "', utilise STONE.");
            return new ItemStack(Material.STONE);
        }
    }

    /**
     * Résout uniquement le Material vanilla de fallback (utile pour les checks de bloc).
     */
    public Material resolveMaterial(String key) {
        String fallback = iaConfig.getString(key + ".fallback", "STONE");
        try { return Material.valueOf(fallback.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.STONE; }
    }

    /**
     * Vérifie si un Material correspond à la clé (en tenant compte ItemsAdder si actif).
     * Utilisé dans les listeners pour détecter un clic sur un bloc spécifique.
     */
    public boolean matches(String key, Material material) {
        // Pour l'instant on compare toujours au material vanilla de fallback.
        // ItemsAdder utilise des blocs NOTE_BLOCK/MUSHROOM_BLOCK custom détectés via leur PDC.
        return resolveMaterial(key) == material;
    }

    /**
     * Vérifie si un bloc (par son Material) est le ConnectedBlock.
     */
    public boolean isConnectedBlock(Material material) {
        return matches("nexus-connected-block", material);
    }

    /**
     * Vérifie si un bloc est un Nexus Block (normal ou WiFi).
     */
    public boolean isNexusAccessBlock(Material material) {
        return matches("nexus-block-normal", material) || matches("nexus-block-wifi", material);
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public boolean isEnabled()       { return enabled; }
    public boolean isPluginPresent() { return pluginPresent; }
    public FileConfiguration getIAConfig() { return iaConfig; }
}
