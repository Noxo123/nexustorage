package com.novusmc.nexusstorage.model;

/**
 * Niveaux de permission qu'un owner peut accorder a un membre de son reseau Nexus.
 */
public enum AccessLevel {
    READ_ONLY(0, "Lecture seule"),
    DEPOSIT(1, "Deposer"),
    WITHDRAW(2, "Retirer"),
    ADMIN(3, "Admin");

    private final int weight;
    private final String label;

    AccessLevel(int weight, String label) {
        this.weight = weight;
        this.label = label;
    }

    public int getWeight() {
        return weight;
    }

    public String getLabel() {
        return label;
    }

    public boolean canDeposit() {
        return weight >= DEPOSIT.weight;
    }

    public boolean canWithdraw() {
        return weight >= WITHDRAW.weight;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    public static AccessLevel fromString(String s) {
        try {
            return AccessLevel.valueOf(s.toUpperCase());
        } catch (Exception e) {
            return READ_ONLY;
        }
    }
}
