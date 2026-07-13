package com.novusmc.nexusstorage.model;

import org.bukkit.inventory.ItemStack;

/**
 * Represente une pile compactee d'un type d'item dans le stockage virtuel
 * Nexus : un template (l'item avec ses meta, amount=1) + une quantite qui
 * peut depasser 64 (aucune limite pratique autre que Long.MAX_VALUE).
 */
public class StoredStack {

    private final ItemStack template;
    private long amount;

    public StoredStack(ItemStack template, long amount) {
        ItemStack clone = template.clone();
        clone.setAmount(1);
        this.template = clone;
        this.amount = amount;
    }

    public ItemStack getTemplate() {
        return template;
    }

    public long getAmount() {
        return amount;
    }

    public void add(long delta) {
        this.amount += delta;
    }

    /**
     * Retire jusqu'a `requested` unites (limite par ce qui est disponible
     * et par la taille de stack max du materiau). Retourne l'ItemStack
     * effectivement retire, ou null si rien n'a pu etre retire.
     */
    public ItemStack withdraw(long requested) {
        if (amount <= 0) return null;
        int maxStack = template.getMaxStackSize();
        long take = Math.min(requested, Math.min(amount, maxStack));
        if (take <= 0) return null;

        amount -= take;
        ItemStack result = template.clone();
        result.setAmount((int) take);
        return result;
    }

    /** Construit l'ItemStack a afficher dans le GUI (amount visuel plafonne a 64, quantite reelle en lore). */
    public ItemStack buildDisplayItem() {
        ItemStack display = template.clone();
        display.setAmount((int) Math.min(64, Math.max(1, amount)));
        return display;
    }
}
