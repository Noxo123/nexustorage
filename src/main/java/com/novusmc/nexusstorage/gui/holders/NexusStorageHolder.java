package com.novusmc.nexusstorage.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holder du stockage Nexus.
 *
 * Cette classe ne contient AUCUNE logique métier.
 * Elle sert uniquement à identifier un inventaire Nexus ouvert
 * et à mémoriser les signatures des objets affichés.
 */
public final class NexusStorageHolder implements InventoryHolder {

    private final UUID owner;
    private final int page;

    private Inventory inventory;

    /**
     * slot -> signature réelle du StoredStack
     */
    private final Map<Integer, String> slotSignatures = new HashMap<>();

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

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /* =======================================================
       SLOT SIGNATURES
       ======================================================= */

    public void setSlotSignature(int slot, String signature) {
        if (signature == null || signature.isEmpty()) {
            slotSignatures.remove(slot);
            return;
        }

        slotSignatures.put(slot, signature);
    }

    public String getSlotSignature(int slot) {
        return slotSignatures.get(slot);
    }

    public boolean hasSlotSignature(int slot) {
        return slotSignatures.containsKey(slot);
    }

    public void removeSlotSignature(int slot) {
        slotSignatures.remove(slot);
    }

    public void clearSlotSignatures() {
        slotSignatures.clear();
    }

    public Map<Integer, String> getSlotSignatures() {
        return Collections.unmodifiableMap(slotSignatures);
    }

    public boolean isStorageSlot(int slot) {
        return slot >= 0 && slot < 45;
    }

    public boolean isNavigationSlot(int slot) {
        return slot >= 45 && slot <= 53;
    }

    @Override
    public String toString() {
        return "NexusStorageHolder{" +
                "owner=" + owner +
                ", page=" + page +
                ", signatures=" + slotSignatures.size() +
                '}';
    }
}
