package com.novusmc.nexusstorage;

import com.novusmc.nexusstorage.commands.CompanyCommand;
import com.novusmc.nexusstorage.commands.NexusAdminCommand;
import com.novusmc.nexusstorage.commands.NexusCommand;
import com.novusmc.nexusstorage.gui.NexusGUIManager;
import com.novusmc.nexusstorage.integration.NexusPlaceholders;
import com.novusmc.nexusstorage.listeners.ChestLinkListener;
import com.novusmc.nexusstorage.listeners.EnergyListener;
import com.novusmc.nexusstorage.listeners.NexusBlockListener;
import com.novusmc.nexusstorage.listeners.NexusCoreListener;
import com.novusmc.nexusstorage.listeners.NexusGUIListener;
import com.novusmc.nexusstorage.listeners.NexusTabletListener;
import com.novusmc.nexusstorage.managers.ChestLinkManager;
import com.novusmc.nexusstorage.managers.CompanyManager;
import com.novusmc.nexusstorage.managers.EconomyManager;
import com.novusmc.nexusstorage.managers.EnergyManager;
import com.novusmc.nexusstorage.managers.EnergyMarketManager;
import com.novusmc.nexusstorage.managers.ItemsAdderManager;
import com.novusmc.nexusstorage.managers.NexusAccessManager;
import com.novusmc.nexusstorage.managers.NexusManager;
import com.novusmc.nexusstorage.managers.NexusStorageManager;
import com.novusmc.nexusstorage.managers.NexusUpgradeManager;
import com.novusmc.nexusstorage.model.EnergyBlockType;
import com.novusmc.nexusstorage.util.ConfigManager;
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
    private CompanyManager companyManager;
    private EnergyMarketManager energyMarketManager;

    private NamespacedKey nexusCoreKey;
    private NamespacedKey nexusTabletKey;
    private NamespacedKey energyTypeKey;
    private NamespacedKey chestLinkKey;
    private NamespacedKey upgradeCrystalKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        new ConfigManager(this).mergeDefaultConfig();

        nexusCoreKey      = new NamespacedKey(this, "nexus_core");
        nexusTabletKey    = new NamespacedKey(this, "nexus_tablet");
        energyTypeKey     = new NamespacedKey(this, "nexus_energy_type");
        chestLinkKey      = new NamespacedKey(this, "nexus_chest_link");
        upgradeCrystalKey = new NamespacedKey(this, "nexus_upgrade_crystal");

        this.accessManager       = new NexusAccessManager(this);
        this.storageManager      = new NexusStorageManager(this);
        this.economyManager      = new EconomyManager(this);
        this.itemsAdderManager   = new ItemsAdderManager(this);
        this.upgradeManager      = new NexusUpgradeManager(this);
        this.nexusManager        = new NexusManager(this);
        this.guiManager          = new NexusGUIManager(this);
        this.energyManager       = new EnergyManager(this);
        this.chestLinkManager    = new ChestLinkManager(this);
        this.companyManager      = new CompanyManager(this);
        this.energyMarketManager = new EnergyMarketManager(this);

        getServer().getPluginManager().registerEvents(new NexusCoreListener(this),   this);
        getServer().getPluginManager().registerEvents(new NexusTabletListener(this), this);
        getServer().getPluginManager().registerEvents(new NexusGUIListener(this),    this);
        getServer().getPluginManager().registerEvents(new EnergyListener(this),      this);
        getServer().getPluginManager().registerEvents(new ChestLinkListener(this),   this);
        getServer().getPluginManager().registerEvents(new NexusBlockListener(this),  this);

        NexusCommand nexusCmd = new NexusCommand(this);
        registerCommandSafely("nexus", nexusCmd, nexusCmd);

        NexusAdminCommand adminCmd = new NexusAdminCommand(this);
        registerCommandSafely("nexusadmin", adminCmd, adminCmd);

        CompanyCommand companyCmd = new CompanyCommand(this);
        registerCommandSafely("company", companyCmd, companyCmd);

        if (getConfig().getBoolean("integrations.placeholderapi.enabled", true)
                && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new NexusPlaceholders(this).register();
            getLogger().info("PlaceholderAPI hook actif.");
        }

        long energyInterval = getConfig().getLong("energy.tick-interval", 20);
        Bukkit.getScheduler().runTaskTimer(this, () -> energyManager.tick(), energyInterval, energyInterval);

        long chestInterval = getConfig().getLong("chest-link.tick-interval", 20);
        Bukkit.getScheduler().runTaskTimer(this, () -> chestLinkManager.tick(), chestInterval, chestInterval);

        getLogger().info("NexusStorage v2.0 actif.");
        if (!economyManager.isEnabled())
            getLogger().warning("Vault non detecte : les couts economiques seront ignores.");
    }

    @Override
    public void onDisable() {
        if (nexusManager        != null) nexusManager.saveAll();
        if (storageManager      != null) storageManager.saveAll();
        if (energyManager       != null) energyManager.saveAll();
        if (chestLinkManager    != null) chestLinkManager.saveAll();
        if (companyManager      != null) companyManager.saveAll();
        if (energyMarketManager != null) energyMarketManager.saveAll();
        getLogger().info("NexusStorage desactive - donnees sauvegardees.");
    }

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
            Constructor<PluginCommand> ctor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            ctor.setAccessible(true);
            PluginCommand pc = ctor.newInstance(name, this);
            pc.setExecutor(executor);
            pc.setTabCompleter(tabCompleter);
            commandMap.register(getName().toLowerCase(), pc);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Impossible d'enregistrer /" + name, e);
            PluginCommand cmd = getCommand(name);
            if (cmd != null) { cmd.setExecutor(executor); cmd.setTabCompleter(tabCompleter); }
        }
    }

    public ItemStack buildEnergyItem(EnergyBlockType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getDisplayName()));
        if (type.getLore().length > 0)
            meta.setLore(List.of(type.getLore()).stream().map(l -> ChatColor.translateAlternateColorCodes('&', l)).toList());
        meta.getPersistentDataContainer().set(energyTypeKey, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

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

    public NexusManager          getNexusManager()        { return nexusManager; }
    public NexusAccessManager    getAccessManager()       { return accessManager; }
    public NexusStorageManager   getStorageManager()      { return storageManager; }
    public NexusUpgradeManager   getUpgradeManager()      { return upgradeManager; }
    public EconomyManager        getEconomyManager()      { return economyManager; }
    public ItemsAdderManager     getItemsAdderManager()   { return itemsAdderManager; }
    public NexusGUIManager       getGuiManager()          { return guiManager; }
    public EnergyManager         getEnergyManager()       { return energyManager; }
    public ChestLinkManager      getChestLinkManager()    { return chestLinkManager; }
    public CompanyManager        getCompanyManager()      { return companyManager; }
    public EnergyMarketManager   getEnergyMarketManager() { return energyMarketManager; }
    public NamespacedKey getNexusCoreKey()      { return nexusCoreKey; }
    public NamespacedKey getNexusTabletKey()    { return nexusTabletKey; }
    public NamespacedKey getEnergyTypeKey()     { return energyTypeKey; }
    public NamespacedKey getChestLinkKey()      { return chestLinkKey; }
    public NamespacedKey getUpgradeCrystalKey() { return upgradeCrystalKey; }
}
