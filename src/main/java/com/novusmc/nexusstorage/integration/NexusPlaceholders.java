package com.novusmc.nexusstorage.integration;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.managers.EnergyMarketManager;
import com.novusmc.nexusstorage.model.Company;
import com.novusmc.nexusstorage.model.NexusNetwork;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Placeholders disponibles (%nexusstorage_XXX%) :
 *
 * Réseau    : has_network, network_name, network_tier, network_pages
 * Énergie   : energy_price, energy_autosell, energy_sold_24h, energy_earned_total
 * Entreprise: in_company, company_name, company_role, company_members
 * Divers    : pending_invitations
 */
public class NexusPlaceholders extends PlaceholderExpansion {

    private final Main plugin;

    public NexusPlaceholders(Main plugin) { this.plugin = plugin; }

    @Override public @NotNull String getIdentifier() { return "nexusstorage"; }
    @Override public @NotNull String getAuthor()     { return "NovusMC"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String id) {
        if (player == null) return "";
        UUID uuid = player.getUniqueId();
        NexusNetwork net = plugin.getNexusManager().getNetworkIfExists(uuid);

        return switch (id) {
            // ── Réseau ──────────────────────────────────────────────────
            case "has_network"   -> net != null ? "true" : "false";
            case "network_name"  -> net != null ? net.getName()  : "Aucun";
            case "network_tier"  -> net != null ? String.valueOf(net.getTier()) : "0";
            case "network_pages" -> net != null
                    ? String.valueOf(plugin.getUpgradeManager().getPagesForTier(net.getTier())) : "0";

            // ── Marché énergie ───────────────────────────────────────────
            case "energy_price" -> {
                if (net == null) yield "0.00";
                EnergyMarketManager.MarketData d = plugin.getEnergyMarketManager().getOrCreate(net.getOwner());
                yield String.format("%.2f", d.price);
            }
            case "energy_autosell" -> {
                if (net == null) yield "false";
                yield String.valueOf(plugin.getEnergyMarketManager().getOrCreate(net.getOwner()).autoSell);
            }
            case "energy_sold_24h" -> {
                if (net == null) yield "0";
                yield String.valueOf(plugin.getEnergyMarketManager().soldInLastHours(net.getOwner(), 24));
            }
            case "energy_earned_total" -> {
                if (net == null) yield "0.00";
                yield String.format("%.2f", plugin.getEnergyMarketManager().getOrCreate(net.getOwner()).totalEarned);
            }

            // ── Entreprise ───────────────────────────────────────────────
            case "in_company" -> plugin.getCompanyManager().getByPlayer(uuid) != null ? "true" : "false";
            case "company_name" -> {
                Company c = plugin.getCompanyManager().getByPlayer(uuid);
                yield c != null ? c.getName() : "Aucune";
            }
            case "company_role" -> {
                Company c = plugin.getCompanyManager().getByPlayer(uuid);
                yield c != null ? c.getRole(uuid).name() : "-";
            }
            case "company_members" -> {
                Company c = plugin.getCompanyManager().getByPlayer(uuid);
                yield c != null ? String.valueOf(c.getMembers().size()) : "0";
            }

            // ── Divers ───────────────────────────────────────────────────
            case "pending_invitations" ->
                    String.valueOf(plugin.getCompanyManager().pendingInvitations(uuid).size());

            default -> null;
        };
    }
}
