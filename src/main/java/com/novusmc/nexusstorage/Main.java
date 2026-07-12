package com.novusmc.nexusstorage;

import com.novusmc.nexusstorage.commands.NexusAdminCommand;
import com.novusmc.nexusstorage.commands.NexusCommand;
import com.novusmc.nexusstorage.gui.NexusGUIManager;
import com.novusmc.nexusstorage.listeners.ChestLinkListener;
import com.novusmc.nexusstorage.listeners.EnergyListener;
import com.novusmc.nexusstorage.listeners.NexusCoreListener;
import com.novusmc.nexusstorage.listeners.NexusGUIListener;
import com.novusmc.nexusstorage.listeners.NexusTabletListener;
import com.novusmc.nexusstorage.managers.ChestLinkManager;
import com.novusmc.nexusstorage.managers.EconomyManager;
import com.novusmc.nexusstorage.managers.EnergyManager;
import com.novusmc.nexusstorage.managers.ItemsAdderManager;
import com.novusmc.nexusstorage.managers.NexusAccessManager;
import com.novusmc.nexusstorage.managers.NexusManager;
import com.novusmc.nexusstorage.managers.NexusStorageManager;
import com.novusmc.nexusstorage.managers.NexusUpgradeManager;
import com.novusmc.nexusstorage.model.EnergyBlockType;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.TabCompleter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

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
    private ItemsAdderManager itemsAdderManager;
    private NexusGUIManager guiManager;
    private EnergyManager energyManager;
    private ChestLinkManager chestLinkManager;

    private NamespacedKey nexusCoreKey;
    private NamespacedKey nexusTabletKey;
    private NamespacedKey energyTypeKey;
    private NamespacedKey chestLinkKey;
    private NamespacedKey upgradeCrystalKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        nexusCoreKey = new NamespacedKey(this, "nexus_core");
        nexusTabletKey = new NamespacedKey(this, "nexus_tablet");
        energyTypeKey = new NamespacedKey(this, "nexus_energy_type");
        chestLinkKey = new NamespacedKey(this, "nexus_chest_link");
        upgradeCrystalKey = new NamespacedKey(this, "nexus_upgrade_crystal");

        // Managers (ordre important : les managers dependants sont crees apres leurs dependances)
        this.accessManager = new NexusAccessManager(this);
        this.storageManager = new NexusStorageManager(this);
        this.economyManager = new EconomyManager(this);
        this.itemsAdderManager = new ItemsAdderManager(this);
        this.upgradeManager = new NexusUpgradeManager(this);
        this.nexusManager = new NexusManager(this);
        this.guiManager = new NexusGUIManager(this);
        this.energyManager = new EnergyManager(this);
        this.chestLinkManager = new ChestLinkManager(this);

        // Listeners
        getServer().getPluginManager().registerEvents(new NexusCoreListener(this), this);
        getServer().getPluginManager().registerEvents(new NexusTabletListener(this), this);
        getServer().getPluginManager().registerEvents(new NexusGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new EnergyListener(this), this);
        getServer().getPluginManager().registerEvents(new ChestLinkListener(this), this);

        // Commandes (enregistrement manuel : survit aux reload PlugMan qui ne
        // rafraichissent pas toujours la CommandMap de Paper/Brigadier - voir registerCommandSafely)
        NexusCommand command = new NexusCommand(this);
        registerCommandSafely("nexus", command, command);

        NexusAdminCommand adminCommand = new NexusAdminCommand(this);
        registerCommandSafely("nexusadmin", adminCommand, adminCommand);

        // Simulation du reseau d'energie (production / transfert / consommation)
        long energyInterval = getConfig().getLong("energy.tick-interval", 20);
        Bukkit.getScheduler().runTaskTimer(this, () -> energyManager.tick(), energyInterval, energyInterval);

        // Simulation des Chest Link (transfert coffres <-> stockage virtuel)
        long chestInterval = getConfig().getLong("chest-link.tick-interval", 20);
        Bukkit.getScheduler().runTaskTimer(this, () -> chestLinkManager.tick(), chestInterval, chestInterval);

        getLogger().info("NexusStorage active - stockage interconnecte, energie et chest link prets.");
        if (!economyManager.isEnabled()) {
            getLogger().warning("Vault desactive ou non detecte : tous les couts economiques seront ignores.");
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
        if (chestLinkManager != null) {
            chestLinkManager.saveAll();
        }
        getLogger().info("NexusStorage desactive - donnees sauvegardees.");
    }

    /**
     * Enregistre une commande directement dans la CommandMap de Bukkit, en ecrasant
     * de force toute ancienne entree (ex: laissee par un /plugman reload qui n'a pas
     * correctement remplace l'objet PluginCommand lie a l'ancienne instance du plugin).
     * Sans ca, PlugManX/PlugMan peut laisser /nexus lie a une instance desactivee,
     * causant l'erreur "Cannot execute command ... plugin is disabled".
     */
    @SuppressWarnings("unchecked")
    private void registerCommandSafely(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        try {
            Field commandMapField = getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);
            knownCommands.remove(name);
            knownCommands.remove(getName().toLowerCase() + ":" + name);

            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand pluginCommand = constructor.newInstance(name, this);
            pluginCommand.setExecutor(executor);
            pluginCommand.setTabCompleter(tabCompleter);

            commandMap.register(getName().toLowerCase(), pluginCommand);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Impossible d'enregistrer /" + name + " manuellement, fallback sur plugin.yml", e);
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(executor);
                getCommand(name).setTabCompleter(tabCompleter);
            }
        }
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

    /** Construit l'item Nexus Chest Link (a poser a cote d'un coffre simple ou double). */
    public ItemStack buildChestLinkItem() {
        ItemStack item = new ItemStack(org.bukkit.Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6&lNexus Chest Link"));
        meta.setLore(List.of(
                ChatColor.translateAlternateColorCodes('&', "&7Pose ce bloc a cote d'un coffre"),
                ChatColor.translateAlternateColorCodes('&', "&7simple ou double pour le connecter"),
                ChatColor.translateAlternateColorCodes('&', "&7a ton stockage Nexus virtuel."),
                ChatColor.translateAlternateColorCodes('&', "&eClique avec un Upgrade Crystal pour l'ameliorer.")
        ));
        meta.getPersistentDataContainer().set(chestLinkKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    /** Construit un item Nexus Upgrade Crystal (tier 1 a 3), tres cher, pour ameliorer un Chest Link. */
    public ItemStack buildUpgradeCrystal(int tier) {
        org.bukkit.Material mat = switch (tier) {
            case 2 -> org.bukkit.Material.AMETHYST_CLUSTER;
            case 3 -> org.bukkit.Material.NETHER_STAR;
            default -> org.bukkit.Material.AMETHYST_SHARD;
        };
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&d&lNexus Upgrade Crystal &7[Tier " + tier + "]"));
        meta.setLore(List.of(
                ChatColor.translateAlternateColorCodes('&', "&7Item premium tres rare."),
                ChatColor.translateAlternateColorCodes('&', "&7Clique droit sur un Nexus Chest Link"),
                ChatColor.translateAlternateColorCodes('&', "&7pour ameliorer sa vitesse de transfert.")
        ));
        meta.getPersistentDataContainer().set(upgradeCrystalKey, PersistentDataType.INTEGER, tier);
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

    public ItemsAdderManager getItemsAdderManager() {
        return itemsAdderManager;
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

    public ChestLinkManager getChestLinkManager() {
        return chestLinkManager;
    }

    public NamespacedKey getChestLinkKey() {
        return chestLinkKey;
    }

    public NamespacedKey getUpgradeCrystalKey() {
        return upgradeCrystalKey;
    }
}
