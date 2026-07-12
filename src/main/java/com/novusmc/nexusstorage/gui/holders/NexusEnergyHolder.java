package com.novusmc.nexusstorage.gui.holders;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Holder pour le tableau de bord d'energie (ouvert depuis le menu principal ou un Nexus Energy Monitor). */
public class NexusEnergyHolder implements InventoryHolder {

    private final UUID owner;
    private final Location monitorLocation; // null si ouvert depuis le menu principal (vue agregee)
    private Inventory inventory;

    public NexusEnergyHolder(UUID owner, Location monitorLocation) {
        this.owner = owner;
        this.monitorLocation = monitorLocation;
    }

    public UUID getOwner() {
        return owner;
    }

    public Location getMonitorLocation() {
        return monitorLocation;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
