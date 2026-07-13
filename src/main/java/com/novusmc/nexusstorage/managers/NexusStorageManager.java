package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.StoredStack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Moteur de stockage virtuel Nexus - VERSION COMPACTEE.
 *
 * Chaque type d'item unique (materiau + meta : nom, lore, enchants, custom
 * model data) est stocke sous forme d'UNE seule entree avec une quantite
 * (long), sans limite pratique de stack (69, 1000, 1000000...). C'est le
 * meme principe qu'un terminal de stockage AE2/RS : on ne "voit" jamais un
 * slot vide reserve, seulement la liste des types d'items presents.
 *
 * Les donnees sont gardees EN MEMOIRE (Map live) apres le premier chargement
 * et modifiees directement par toutes les operations (deposit/withdraw),
 * sauvegardees a chaque mutation. Comme tous les viewers d'un meme reseau
 * lisent/ecrivent la MEME instance de map, il n'y a plus de risque de
 * duplication par double-ouverture (contrairement a un systeme qui chargerait
 * une copie independante a chaque ouverture de GUI et l'ecrirait a la fermeture).
 */
public class NexusStorageManager {

    private final Main plugin;
    private final File storageFolder;

    /** owner -> (signature -> StoredStack). LinkedHashMap = ordre d'insertion stable pour la pagination. */
    private final Map<UUID, LinkedHashMap<String, StoredStack>> cache = new java.util.HashMap<>();

    public NexusStorageManager(Main plugin) {
        this.plugin = plugin;
        this.storageFolder = new File(plugin.getDataFolder(), "storage");
        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }
    }

    private File fileFor(UUID owner) {
        return new File(storageFolder, owner.toString() + ".yml");
    }

    // ================= SIGNATURE =================

    /**
     * Construit une signature stable pour regrouper les items identiques
     * (meme materiau, meme nom, meme lore, memes enchants, meme custom model data).
     * Deux items avec la meme signature sont fusionnes dans la meme StoredStack.
     */
    public String signatureOf(ItemStack item) {
        StringBuilder sb = new StringBuilder(item.getType().name());
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) sb.append("|dn:").append(meta.getDisplayName());
            if (meta.hasLore()) sb.append("|lore:").append(String.join(",", meta.getLore()));
            if (meta.hasEnchants()) {
                TreeMap<String, Integer> sorted = new TreeMap<>();
                for (Map.Entry<Enchantment, Integer> e : meta.getEnchants().entrySet()) {
                    sorted.put(e.getKey().getKey().toString(), e.getValue());
                }
                sb.append("|ench:").append(sorted);
            }
            if (meta.hasCustomModelData()) sb.append("|cmd:").append(meta.getCustomModelData());
        }
        return sb.toString();
    }

    // ================= ACCES / CACHE =================

    /** Retourne la map LIVE des entrees d'un owner (chargee depuis le disque au premier appel). */
    public LinkedHashMap<String, StoredStack> getEntries(UUID owner) {
        return cache.computeIfAbsent(owner, this::loadFromDisk);
    }

    public int uniqueTypeCount(UUID owner) {
        return getEntries(owner).size();
    }

    // ================= DEPOSIT / WITHDRAW =================

    /**
     * Depose un item (son amount complet) dans le stockage compacte d'un owner.
     *
     * @param maxUniqueTypes limite de types d'items differents autorises (lie au tier du reseau)
     * @return true si tout l'item a ete absorbe, false si refuse (limite de types atteinte pour un NOUVEL item)
     */
    public boolean deposit(UUID owner, ItemStack item, int maxUniqueTypes) {
        if (item == null || item.getAmount() <= 0) return true;

        LinkedHashMap<String, StoredStack> entries = getEntries(owner);
        String sig = signatureOf(item);
        StoredStack existing = entries.get(sig);

        if (existing == null) {
            if (entries.size() >= maxUniqueTypes) {
                return false; // limite de types d'items atteinte
            }
            entries.put(sig, new StoredStack(item, item.getAmount()));
        } else {
            existing.add(item.getAmount());
        }

        save(owner);
        return true;
    }

    /**
     * Retire jusqu'a `amount` unites du type identifie par sa signature.
     * @return l'ItemStack effectivement retire (peut etre moins que demande), ou null si l'entree n'existe pas / est vide.
     */
    public ItemStack withdraw(UUID owner, String signature, long amount) {
        LinkedHashMap<String, StoredStack> entries = getEntries(owner);
        StoredStack stack = entries.get(signature);
        if (stack == null) return null;

        ItemStack result = stack.withdraw(amount);
        if (stack.getAmount() <= 0) {
            entries.remove(signature);
        }
        save(owner);
        return result;
    }

    // ================= PERSISTENCE =================

    private LinkedHashMap<String, StoredStack> loadFromDisk(UUID owner) {
        LinkedHashMap<String, StoredStack> entries = new LinkedHashMap<>();
        File file = fileFor(owner);
        if (!file.exists()) return entries;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            try {
                ItemStack template = yml.getItemStack(key + ".item");
                long amount = yml.getLong(key + ".amount", 0);
                if (template == null || amount <= 0) continue;
                entries.put(signatureOf(template), new StoredStack(template, amount));
            } catch (Exception e) {
                plugin.getLogger().warning("Entree de stockage invalide ignoree pour " + owner + ": " + key);
            }
        }
        return entries;
    }

    public void save(UUID owner) {
        LinkedHashMap<String, StoredStack> entries = cache.get(owner);
        File file = fileFor(owner);

        if (entries == null || entries.isEmpty()) {
            if (file.exists()) file.delete();
            return;
        }

        YamlConfiguration yml = new YamlConfiguration();
        int index = 0;
        for (StoredStack stack : entries.values()) {
            String key = "entry_" + (index++);
            yml.set(key + ".item", stack.getTemplate());
            yml.set(key + ".amount", stack.getAmount());
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder le stockage de " + owner, e);
        }
    }

    public void saveAll() {
        for (UUID owner : cache.keySet()) {
            save(owner);
        }
    }

    /** Supprime completement le stockage d'un owner. */
    public void deleteAll(UUID owner) {
        cache.remove(owner);
        File file = fileFor(owner);
        if (file.exists()) file.delete();
    }
}
