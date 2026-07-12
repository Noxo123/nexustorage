package com.novusmc.nexusstorage.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Holder pour le menu principal du Nexus Tablet (onglets Storage/Access/Upgrades/Settings/Tablet Link). */
public class NexusMainHolder implements InventoryHolder {

    private final UUID owner;
    private Inventory inventory;

    public NexusMainHolder(UUID owner) {
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
