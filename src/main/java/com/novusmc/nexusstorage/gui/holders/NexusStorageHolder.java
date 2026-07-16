package com.novusmc.nexusstorage.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holder du stockage Nexus.
 * Slots 0-44   : items du stockage
 * Slot  45     : flèche page précédente
 * Slot  46     : bouton "Tout déposer"   (NOUVEAU v2)
 * Slot  49     : livre page actuelle
 * Slot  50     : bouton recherche        (NOUVEAU v2)
 * Slot  53     : flèche page suivante
 */
public final class NexusStorageHolder implements InventoryHolder {

    private final UUID owner;
    private final int  page;
    private Inventory  inventory;
    private final Map<Integer, String> slotSignatures = new HashMap<>();

    /** Recherche active (null = aucune). */
    private String searchQuery = null;

    public NexusStorageHolder(UUID owner, int page) {
        this.owner = owner;
        this.page  = page;
    }

    public UUID    getOwner()    { return owner; }
    public int     getPage()     { return page; }
    public String  getSearchQuery() { return searchQuery; }
    public void    setSearchQuery(String q) { this.searchQuery = (q == null || q.isBlank()) ? null : q.trim(); }

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public void setSlotSignature(int slot, String signature) {
        if (signature == null || signature.isEmpty()) { slotSignatures.remove(slot); return; }
        slotSignatures.put(slot, signature);
    }
    public String  getSlotSignature(int slot)  { return slotSignatures.get(slot); }
    public boolean hasSlotSignature(int slot)  { return slotSignatures.containsKey(slot); }
    public void    removeSlotSignature(int slot) { slotSignatures.remove(slot); }
    public void    clearSlotSignatures()         { slotSignatures.clear(); }
    public Map<Integer, String> getSlotSignatures() { return Collections.unmodifiableMap(slotSignatures); }

    public boolean isStorageSlot(int slot)    { return slot >= 0 && slot < 45; }
    public boolean isNavigationSlot(int slot) { return slot >= 45 && slot <= 53; }

    /** Slot fixe du bouton "Tout déposer". */
    public static final int SLOT_DEPOSIT_ALL = 46;
    /** Slot fixe du bouton "Recherche". */
    public static final int SLOT_SEARCH      = 50;
}
