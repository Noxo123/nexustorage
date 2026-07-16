package com.novusmc.nexusstorage.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

/** Holder du GUI marche d'energie. */
public final class NexusEnergyMarketHolder implements InventoryHolder {

    private final UUID networkOwner;
    private Inventory inventory;

    public NexusEnergyMarketHolder(UUID networkOwner) { this.networkOwner = networkOwner; }

    public UUID getNetworkOwner() { return networkOwner; }

    @Override public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv)   { this.inventory = inv; }

    // slots fixes
    public static final int SLOT_PRICE_DISPLAY = 13;
    public static final int SLOT_PRICE_MINUS    = 11;
    public static final int SLOT_PRICE_PLUS     = 15;
    public static final int SLOT_AUTOSELL       = 31;
    public static final int SLOT_STATS          = 22;
    public static final int SLOT_BACK           = 49;
}
