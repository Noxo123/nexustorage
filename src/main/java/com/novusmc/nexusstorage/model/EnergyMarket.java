package com.novusmc.nexusstorage.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gère le système de vente d'électricité pour un réseau Nexus.
 * Chaque réseau peut vendre son excédent d'énergie aux autres joueurs.
 */
public class EnergyMarket {
    private final UUID networkOwnerId;
    private double energyPrice = 0.1; // Prix par unité d'énergie
    private long totalEnergySold = 0;
    private double totalMoneyEarned = 0.0;
    private final Map<Long, Long> energySaleHistory = new HashMap<>(); // timestamp -> amount
    private boolean autoSellEnabled = false;

    public EnergyMarket(UUID networkOwnerId) {
        this.networkOwnerId = networkOwnerId;
    }

    public UUID getNetworkOwnerId() {
        return networkOwnerId;
    }

    /**
     * Définit le prix de vente de l'électricité.
     * @param price Prix par unité d'énergie
     */
    public void setEnergyPrice(double price) {
        if (price < 0) {
            this.energyPrice = 0;
        } else {
            this.energyPrice = price;
        }
    }

    public double getEnergyPrice() {
        return energyPrice;
    }

    /**
     * Enregistre une vente d'énergie.
     * @param amount Quantité d'énergie vendue
     * @param profit Argent gagné
     */
    public void recordSale(long amount, double profit) {
        this.totalEnergySold += amount;
        this.totalMoneyEarned += profit;
        this.energySaleHistory.put(System.currentTimeMillis(), amount);
    }

    public long getTotalEnergySold() {
        return totalEnergySold;
    }

    public double getTotalMoneyEarned() {
        return totalMoneyEarned;
    }

    public Map<Long, Long> getEnergySaleHistory() {
        return new HashMap<>(energySaleHistory);
    }

    /**
     * Obtient la quantité d'énergie vendue dans les dernières N heures.
     */
    public long getEnergySoldInLastHours(int hours) {
        long threshold = System.currentTimeMillis() - (hours * 3600000L);
        return energySaleHistory.entrySet().stream()
                .filter(e -> e.getKey() >= threshold)
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    public boolean isAutoSellEnabled() {
        return autoSellEnabled;
    }

    public void setAutoSellEnabled(boolean enabled) {
        this.autoSellEnabled = enabled;
    }
}
