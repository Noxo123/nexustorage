package com.novusmc.nexusstorage.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Holder pour l'onglet Settings (stats + gestion du reseau). */
public class NexusSettingsHolder implements InventoryHolder {

    private final UUID owner;
    private Inventory inventory;

    public NexusSettingsHolder(UUID owner) {
        this.owner = owner;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
