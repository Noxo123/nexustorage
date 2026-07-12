package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;

/**
 * Integration ItemsAdder — EN PREPARATION.
 * Ce manager expose deja le toggle (config + /nexusadmin toggle itemsadder) et
 * detecte si le plugin ItemsAdder est present, mais aucun item/texture custom
 * n'est encore branche dessus : c'est une base prete pour une prochaine mise a jour
 * (remplacement des Materials vanilla de NexusStorage par des items ItemsAdder).
 */
public class ItemsAdderManager {

    private final Main plugin;
    private boolean enabled;
    private boolean pluginPresent;

    public ItemsAdderManager(Main plugin) {
        this.plugin = plugin;
        refresh();
    }

    /** Recharge l'etat (toggle config + presence reelle du plugin ItemsAdder). */
    public void refresh() {
        this.pluginPresent = plugin.getServer().getPluginManager().getPlugin("ItemsAdder") != null;
        boolean wanted = plugin.getConfig().getBoolean("integrations.itemsadder.enabled", false);
        this.enabled = wanted && pluginPresent;

        if (wanted && !pluginPresent) {
            plugin.getLogger().warning("ItemsAdder est active dans la config mais le plugin n'est pas installe.");
        }
    }

    /** true seulement si active en config ET le plugin ItemsAdder est present. */
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPluginPresent() {
        return pluginPresent;
    }
}
