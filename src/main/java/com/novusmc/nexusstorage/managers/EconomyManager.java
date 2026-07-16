package com.novusmc.nexusstorage.managers;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import com.novusmc.nexusstorage.Main;

/**
 * Enveloppe l'API Vault pour toutes les transactions economiques du plugin.
 * Si Vault n'est pas present ou desactive en config, les couts sont ignores.
 */
public class EconomyManager {

    private final Main plugin;
    private Economy economy;
    private boolean enabled;

    public EconomyManager(Main plugin) {
        this.plugin = plugin;
        refresh();
    }

    /** Recharge l'etat (toggle config + presence reelle de Vault). Utilise par /nexusadmin toggle vault. */
    public void refresh() {
        this.enabled = plugin.getConfig().getBoolean("integrations.vault.enabled", true);
        if (enabled) {
            setupEconomy();
        } else {
            economy = null;
        }
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            enabled = false;
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            enabled = false;
            return false;
        }
        economy = rsp.getProvider();
        return true;
    }

    public boolean isEnabled() {
        return enabled && economy != null;
    }

    public double getBalance(Player player) {
        if (!isEnabled()) return Double.MAX_VALUE;
        return economy.getBalance(player);
    }

    public boolean has(Player player, double amount) {
        if (!isEnabled() || amount <= 0) return true;
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (!isEnabled() || amount <= 0) return true;
        if (!economy.has(player, amount)) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /** Retire de l'argent au compte d'un joueur offline (par UUID). */
    public boolean withdraw(java.util.UUID uuid, double amount) {
        if (!isEnabled() || amount <= 0) return true;
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        if (!economy.has(op, amount)) return false;
        return economy.withdrawPlayer(op, amount).transactionSuccess();
    }

    /** Dépose de l'argent sur le compte d'un joueur (par UUID). */
    public void deposit(java.util.UUID uuid, double amount) {
        if (!isEnabled() || amount <= 0) return;
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        economy.depositPlayer(op, amount);
    }

    public String format(double amount) {
        if (isEnabled()) return economy.format(amount);
        return String.valueOf(amount);
    }
}
