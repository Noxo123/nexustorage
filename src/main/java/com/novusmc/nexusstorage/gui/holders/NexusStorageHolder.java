package com.novusmc.nexusstorage.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Holder pour une page de stockage virtuel (onglet Storage). */
public class NexusStorageHolder implements InventoryHolder {

    private final UUID owner;
    private final int page;
    private Inventory inventory;

    public NexusStorageHolder(UUID owner, int page) {
        this.owner = owner;
        this.page = page;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getPage() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
