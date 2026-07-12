package com.novusmc.nexusstorage;

import com.novusmc.nexusstorage.commands.NexusCommand;
import com.novusmc.nexusstorage.gui.NexusGUIManager;
import com.novusmc.nexusstorage.listeners.NexusCoreListener;
import com.novusmc.nexusstorage.listeners.NexusGUIListener;
import com.novusmc.nexusstorage.listeners.NexusTabletListener;
import com.novusmc.nexusstorage.managers.EconomyManager;
import com.novusmc.nexusstorage.managers.NexusAccessManager;
import com.novusmc.nexusstorage.managers.NexusManager;
import com.novusmc.nexusstorage.managers.NexusStorageManager;
import com.novusmc.nexusstorage.managers.NexusUpgradeManager;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

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

    private NamespacedKey nexusCoreKey;
    private NamespacedKey nexusTabletKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        nexusCoreKey = new NamespacedKey(this, "nexus_core");
        nexusTabletKey = new NamespacedKey(this, "nexus_tablet");

        // Managers (ordre important : les managers dependants sont crees apres leurs dependances)
        this.accessManager = new NexusAccessManager(this);
        this.storageManager = new NexusStorageManager(this);
        this.economyManager = new EconomyManager(this);
        this.upgradeManager = new NexusUpgradeManager(this);
        this.nexusManager = new NexusManager(this);
        this.guiManager = new NexusGUIManager(this);

        // Listeners
        getServer().getPluginManager().registerEvents(new NexusCoreListener(this), this);
        getServer().getPluginManager().registerEvents(new NexusTabletListener(this), this);
        getServer().getPluginManager().registerEvents(new NexusGUIListener(this), this);

        // Commandes
        NexusCommand command = new NexusCommand(this);
        getCommand("nexus").setExecutor(command);
        getCommand("nexus").setTabCompleter(command);

        getLogger().info("NexusStorage active - stockage interconnecte pret.");
        if (!economyManager.isEnabled()) {
            getLogger().warning("Vault non detecte : tous les couts economiques seront ignores.");
        }
    }

    @Override
    public void onDisable() {
        if (nexusManager != null) {
            nexusManager.saveAll();
        }
        getLogger().info("NexusStorage desactive - donnees sauvegardees.");
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
}
