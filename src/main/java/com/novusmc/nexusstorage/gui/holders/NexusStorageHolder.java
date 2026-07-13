package com.novusmc.nexusstorage.gui.holders;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Holder pour une page de stockage virtuel (onglet Storage). */
public class NexusStorageHolder implements InventoryHolder {

    private final UUID owner;
    private final int page;
    private Inventory inventory;

    /**
     * Signature de chaque slot affiché.
     * Permet de savoir quel StoredStack correspond réellement à un slot,
     * même si l'ItemStack affiché possède un lore décoratif.
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

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Associe la signature du StoredStack à un slot de l'inventaire.
     */
    public void setSlotSignature(int slot, String signature) {
        if (signature == null) {
            slotSignatures.remove(slot);
        } else {
            slotSignatures.put(slot, signature);
        }
    }

    /**
     * Retourne la signature associée à un slot.
     */
    public String getSlotSignature(int slot) {
        return slotSignatures.get(slot);
    }

    /**
     * Vide toutes les signatures.
     */
    public void clearSlotSignatures() {
        slotSignatures.clear();
    }
}
