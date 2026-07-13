package com.novusmc.nexusstorage.integration;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.Company;
import com.novusmc.nexusstorage.model.NexusNetwork;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Extension PlaceholderAPI pour NexusStorage.
 * Fournit des placeholders pour afficher les informations du réseau Nexus.
 *
 * Placeholders disponibles :
 * - %nexusstorage_has_network% : true/false
 * - %nexusstorage_network_name% : nom du réseau
 * - %nexusstorage_network_tier% : tier du réseau
 * - %nexusstorage_network_pages% : nombre de pages
 * - %nexusstorage_energy_balance% : quantité d'énergie actuelle
 * - %nexusstorage_energy_price% : prix de vente d'énergie
 * - %nexusstorage_company_name% : nom de l'entreprise (si membre)
 * - %nexusstorage_company_role% : rôle dans l'entreprise
 * - %nexusstorage_company_member_count% : nombre de membres
 */
public class NexusPlaceholders extends PlaceholderExpansion {
    private final Main plugin;

    public NexusPlaceholders(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "nexusstorage";
    }

    @Override
    public @NotNull String getAuthor() {
        return "NovusMC";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }

        NexusNetwork network = plugin.getNexusManager().getNetworkForPlayer(player.getUniqueId());

        return switch (identifier) {
            // Informations du réseau
            case "has_network" -> network != null ? "true" : "false";
            case "network_name" -> network != null ? network.getName() : "Aucun";
            case "network_tier" -> network != null ? String.valueOf(network.getTier()) : "0";
            case "network_pages" -> network != null ? String.valueOf(getNetworkPages(network)) : "0";
            case "network_members" -> network != null ? String.valueOf(network.getMembers().size() + 1) : "0";

            // Informations d'énergie
            case "energy_balance" -> network != null ? String.valueOf(plugin.getEnergyManager().getNetworkEnergy(network.getOwner())) : "0";
            case "energy_price" -> network != null ? String.format("%.2f", getEnergyPrice(network.getOwner())) : "0.00";
            case "energy_sold_1h" -> network != null ? String.valueOf(getEnergySoldLastHours(network.getOwner(), 1)) : "0";
            case "energy_sold_24h" -> network != null ? String.valueOf(getEnergySoldLastHours(network.getOwner(), 24)) : "0";
            case "energy_earned_total" -> network != null ? String.format("%.2f", getEnergyEarnings(network.getOwner())) : "0.00";

            // Informations d'entreprise
            case "in_company" -> getPlayerCompany(player.getUniqueId()) != null ? "true" : "false";
            case "company_name" -> {
                Company company = getPlayerCompany(player.getUniqueId());
                yield company != null ? company.getName() : "Aucune";
            }
            case "company_role" -> {
                Company company = getPlayerCompany(player.getUniqueId());
                yield company != null ? company.getMemberRole(player.getUniqueId()).name() : "Aucun";
            }
            case "company_members" -> {
                Company company = getPlayerCompany(player.getUniqueId());
                yield company != null ? String.valueOf(company.getMembers().size()) : "0";
            }

            // Invitations en attente
            case "pending_invitations" -> {
                int count = plugin.getCompanyManager().getPendingInvitations(player.getUniqueId()).size();
                yield String.valueOf(count);
            }

            // Par défaut
            default -> null;
        };
    }

    /**
     * Obtient le nombre total de pages du réseau.
     */
    private int getNetworkPages(NexusNetwork network) {
        int tier = network.getTier();
        return switch (tier) {
            case 1 -> plugin.getConfig().getInt("upgrades.tier1.pages", 3);
            case 2 -> plugin.getConfig().getInt("upgrades.tier2.pages", 10);
            case 3 -> plugin.getConfig().getInt("upgrades.tier3.pages", 50);
            case 4 -> plugin.getConfig().getInt("upgrades.tier4.pages", 200);
            case 5 -> plugin.getConfig().getInt("upgrades.tier5.pages", 1000);
            default -> 3;
        };
    }

    /**
     * Obtient le prix de vente d'électricité pour un réseau.
     */
    private double getEnergyPrice(java.util.UUID ownerUuid) {
        // Cette méthode devrait être appelée depuis le EnergyManager
        // À implémenter selon la structure actuelle
        return plugin.getConfig().getDouble("energy.base-energy-price", 0.1);
    }

    /**
     * Obtient l'énergie vendue dans les N dernières heures.
     */
    private long getEnergySoldLastHours(java.util.UUID ownerUuid, int hours) {
        // À implémenter selon la structure du EnergyManager
        return 0;
    }

    /**
     * Obtient les gains totaux en énergie.
     */
    private double getEnergyEarnings(java.util.UUID ownerUuid) {
        // À implémenter selon la structure du EnergyManager
        return 0.0;
    }

    /**
     * Obtient l'entreprise d'un joueur.
     */
    private Company getPlayerCompany(java.util.UUID playerUuid) {
        return plugin.getCompanyManager().getPlayerCompany(playerUuid);
    }
}
