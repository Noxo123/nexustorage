package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gere le stockage virtuel interconnecte : chaque reseau Nexus dispose de
 * plusieurs "pages" (jusqu'a 1000 selon le tier), chaque page contenant
 * 45 slots de stockage utilisables (les 9 slots du bas servent a la navigation).
 */
public class NexusStorageManager {

    public static final int STORAGE_SLOTS_PER_PAGE = 45; // slots 0..44

    private final Main plugin;
    private final File storageFolder;

    public NexusStorageManager(Main plugin) {
        this.plugin = plugin;
        this.storageFolder = new File(plugin.getDataFolder(), "storage");
        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }
    }

    private File pageFile(UUID owner, int page) {
        File ownerFolder = new File(storageFolder, owner.toString());
        if (!ownerFolder.exists()) {
            ownerFolder.mkdirs();
        }
        return new File(ownerFolder, "page_" + page + ".yml");
    }

    /**
     * Charge le contenu (45 slots) d'une page de stockage depuis le disque.
     * Retourne un tableau vide si la page n'existe pas encore.
     */
    public ItemStack[] loadPage(UUID owner, int page) {
        ItemStack[] contents = new ItemStack[STORAGE_SLOTS_PER_PAGE];
        File file = pageFile(owner, page);
        if (!file.exists()) {
            return contents;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        List<?> rawList = yml.getList("items");
        if (rawList != null) {
            for (int i = 0; i < Math.min(rawList.size(), STORAGE_SLOTS_PER_PAGE); i++) {
                Object obj = rawList.get(i);
                if (obj instanceof ItemStack item) {
                    contents[i] = item;
                }
            }
        }
        return contents;
    }

    /**
     * Sauvegarde le contenu (45 slots) d'une page de stockage sur le disque.
     * N'ecrit rien si la page est totalement vide et n'existait pas deja,
     * pour eviter de creer des milliers de fichiers inutiles.
     */
    public void savePage(UUID owner, int page, ItemStack[] contents) {
        boolean empty = true;
        List<ItemStack> toSave = new ArrayList<>();
        for (ItemStack item : contents) {
            toSave.add(item);
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                empty = false;
            }
        }

        File file = pageFile(owner, page);
        if (empty && !file.exists()) {
            return;
        }

        YamlConfiguration yml = new YamlConfiguration();
        yml.set("items", toSave);
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder la page " + page + " de " + owner, e);
        }
    }

    /**
     * Supprime completement le stockage d'un owner (toutes ses pages).
     */
    public void deleteAll(UUID owner) {
        File ownerFolder = new File(storageFolder, owner.toString());
        if (ownerFolder.exists()) {
            File[] files = ownerFolder.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            ownerFolder.delete();
        }
    }
}
