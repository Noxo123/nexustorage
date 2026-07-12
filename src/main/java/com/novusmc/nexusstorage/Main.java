package com.novusmc.nexusstorage;

import com.novusmc.nexusstorage.commands.NexusCommand;
import com.novusmc.nexusstorage.gui.NexusGUIManager;
import com.novusmc.nexusstorage.listeners.EnergyListener;
import com.novusmc.nexusstorage.listeners.NexusCoreListener;
import com.novusmc.nexusstorage.listeners.NexusGUIListener;
import com.novusmc.nexusstorage.listeners.NexusTabletListener;
import com.novusmc.nexusstorage.managers.EconomyManager;
import com.novusmc.nexusstorage.managers.EnergyManager;
import com.novusmc.nexusstorage.managers.NexusAccessManager;
import com.novusmc.nexusstorage.managers.NexusManager;
import com.novusmc.nexusstorage.managers.NexusStorageManager;
import com.novusmc.nexusstorage.managers.NexusUpgradeManager;
import com.novusmc.nexusstorage.model.EnergyBlockType;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Point d'entree du plugin NexusStorage.
 * Systeme de stockage interconnecte SMP premium, independant de tout autre plugin.
 */
public class Main extends JavaPlugin {

    private NexusManager nexusManager;
    private NexusAccessManager accessManager;
    private NexusStorageManager storageManager;
    private NexusUpgradeManager upgradeManager;
    private EconomyManager economyManager;
    private NexusGUIManager guiManager;
    private EnergyManager energyManager;

    private NamespacedKey nexusCoreKey;
    private NamespacedKey nexusTabletKey;
    private NamespacedKey energyTypeKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        nexusCoreKey = new NamespacedKey(this, "nexus_core");
        nexusTabletKey = new NamespacedKey(this, "nexus_tablet");
        energyTypeKey = new NamespacedKey(this, "nexus_energy_type");

        // Managers (ordre important : les managers dependants sont crees apres leurs dependances)
        this.accessManager = new NexusAccessManager(this);
        this.storageManager = new NexusStorageManager(this);
        this.economyManager = new EconomyManager(this);
        this.upgradeManager = new NexusUpgradeManager(this);
        this.nexusManager = new NexusManager(this);
        this.guiManager = new NexusGUIManager(this);
        this.energyManager = new EnergyManager(this);

        // Listeners
        getServer().getPluginManager().registerEvents(new NexusCoreListener(this), this);
        getServer().getPluginManager().registerEvents(new NexusTabletListener(this), this);
        getServer().getPluginManager().registerEvents(new NexusGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new EnergyListener(this), this);

        // Commandes
        NexusCommand command = new NexusCommand(this);
        getCommand("nexus").setExecutor(command);
        getCommand("nexus").setTabCompleter(command);

        // Simulation du reseau d'energie (production / transfert / consommation)
        long interval = getConfig().getLong("energy.tick-interval", 20);
        Bukkit.getScheduler().runTaskTimer(this, () -> energyManager.tick(), interval, interval);

        getLogger().info("NexusStorage active - stockage interconnecte + reseau d'energie prets.");
        if (!economyManager.isEnabled()) {
            getLogger().warning("Vault non detecte : tous les couts economiques seront ignores.");
        }
    }

    @Override
    public void onDisable() {
        if (nexusManager != null) {
            nexusManager.saveAll();
        }
        if (energyManager != null) {
            energyManager.saveAll();
        }
        getLogger().info("NexusStorage desactive - donnees sauvegardees.");
    }

    /**
     * Construit l'item physique d'un bloc du systeme d'energie
     * (Solar Panel, Capacitor, Cable, Interface, Energy Core, Regulator, Monitor).
     */
    public ItemStack buildEnergyItem(EnergyBlockType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getDisplayName()));
        if (type.getLore().length > 0) {
            meta.setLore(List.of(type.getLore()).stream()
                    .map(l -> ChatColor.translateAlternateColorCodes('&', l))
                    .toList());
        }
        meta.getPersistentDataContainer().set(energyTypeKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public NexusManager getNexusManager() {
        return nexusManager;
    }

    public NexusAccessManager getAccessManager() {
        return accessManager;
    }

    public NexusStorageManager getStorageManager() {
        return storageManager;
    }

    public NexusUpgradeManager getUpgradeManager() {
        return upgradeManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public NexusGUIManager getGuiManager() {
        return guiManager;
    }

    public NamespacedKey getNexusCoreKey() {
        return nexusCoreKey;
    }

    public NamespacedKey getNexusTabletKey() {
        return nexusTabletKey;
    }

    public EnergyManager getEnergyManager() {
        return energyManager;
    }

    public NamespacedKey getEnergyTypeKey() {
        return energyTypeKey;
    }
}
