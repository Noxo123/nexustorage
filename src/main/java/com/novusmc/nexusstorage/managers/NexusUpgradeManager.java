package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Gere les tiers de reseau (nombre de pages de stockage disponibles) et
 * le cout Vault pour passer au tier superieur.
 */
public class NexusUpgradeManager {

    public static final int MAX_TIER = 5;

    private final Main plugin;

    public NexusUpgradeManager(Main plugin) {
        this.plugin = plugin;
    }

    public int getPagesForTier(int tier) {
        FileConfiguration cfg = plugin.getConfig();
        return cfg.getInt("upgrades.tier" + tier + ".pages", defaultPages(tier));
    }

    private int defaultPages(int tier) {
        return switch (tier) {
            case 1 -> 3;
            case 2 -> 10;
            case 3 -> 50;
            case 4 -> 200;
            case 5 -> 1000;
            default -> 3;
        };
    }

    public double getCostForTier(int tier) {
        FileConfiguration cfg = plugin.getConfig();
        return cfg.getDouble("upgrades.tier" + tier + ".cost", 0.0);
    }

    public boolean isMaxTier(NexusNetwork network) {
        return network.getTier() >= MAX_TIER;
    }

    /**
     * Tente d'ameliorer le reseau du joueur vers le tier suivant.
     * Retourne true si l'amelioration a reussi.
     */
    public boolean upgrade(Player player, NexusNetwork network) {
        if (isMaxTier(network)) return false;
        int nextTier = network.getTier() + 1;
        double cost = getCostForTier(nextTier);

        EconomyManager economy = plugin.getEconomyManager();
        if (!economy.has(player, cost)) {
            return false;
        }
        if (!economy.withdraw(player, cost)) {
            return false;
        }
        network.setTier(nextTier);
        plugin.getAccessManager().saveNetworkMeta(network);
        return true;
    }
}
