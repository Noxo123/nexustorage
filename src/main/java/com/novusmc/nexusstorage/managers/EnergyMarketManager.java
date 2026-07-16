package com.novusmc.nexusstorage.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.novusmc.nexusstorage.Main;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gere le marche d'energie : prix de vente par reseau, vente automatique,
 * et historique de ventes. Persistance dans energy_market.json.
 */
public class EnergyMarketManager {

    /** Données de marché pour un réseau (owner). */
    public static class MarketData {
        public double price       = 0.1;
        public boolean autoSell   = false;
        public long totalSold     = 0;
        public double totalEarned = 0.0;
        // timestamp -> quantite vendue (garde les 48 dernieres heures)
        public final Map<Long, Long> history = new LinkedHashMap<>();
    }

    private final Main plugin;
    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** owner UUID -> données de marché */
    private final Map<UUID, MarketData> markets = new ConcurrentHashMap<>();

    public EnergyMarketManager(Main plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "energy_market.json");
        load();
    }

    // ── Accès ────────────────────────────────────────────────────────────

    public MarketData getOrCreate(UUID ownerUuid) {
        return markets.computeIfAbsent(ownerUuid, k -> {
            MarketData d = new MarketData();
            d.price = plugin.getConfig().getDouble("energy.base-energy-price", 0.1);
            return d;
        });
    }

    // ── Modification du prix ──────────────────────────────────────────────

    public void setPrice(UUID ownerUuid, double price, Player player) {
        MarketData d = getOrCreate(ownerUuid);
        d.price = Math.max(0.0, price);
        String msg = plugin.getConfig().getString("messages.energy-price-set",
                "&aLe prix est maintenant: &f${price} par unite")
                .replace("{price}", String.format("%.2f", d.price));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        save();
    }

    public void adjustPrice(UUID ownerUuid, double delta, Player player) {
        MarketData d = getOrCreate(ownerUuid);
        setPrice(ownerUuid, d.price + delta, player);
    }

    // ── Vente automatique ─────────────────────────────────────────────────

    public void toggleAutoSell(UUID ownerUuid) {
        MarketData d = getOrCreate(ownerUuid);
        d.autoSell = !d.autoSell;
        save();
    }

    /**
     * Tente de vendre amount unites d'energie pour ce reseau au prix fixe.
     * @return argent gagne (0 si autoSell desactive ou economie absente)
     */
    public double trySell(UUID ownerUuid, long amount, Player ownerOnline) {
        MarketData d = getOrCreate(ownerUuid);
        if (!d.autoSell || amount <= 0) return 0.0;
        double profit = amount * d.price;
        if (profit <= 0.0) return 0.0;
        plugin.getEconomyManager().deposit(ownerUuid, profit);
        d.totalSold   += amount;
        d.totalEarned += profit;
        d.history.put(System.currentTimeMillis(), amount);
        pruneHistory(d);
        if (ownerOnline != null) {
            String msg = plugin.getConfig().getString("messages.energy-sold",
                    "&a⚡ Tu as vendu &f{amount} &aenergie pour &f${profit}")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{profit}", String.format("%.2f", profit));
            ownerOnline.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }
        save();
        return profit;
    }

    /** Retourne l'energie vendue dans les N dernieres heures. */
    public long soldInLastHours(UUID ownerUuid, int hours) {
        MarketData d = markets.get(ownerUuid);
        if (d == null) return 0;
        long threshold = System.currentTimeMillis() - (long) hours * 3_600_000L;
        return d.history.entrySet().stream()
                .filter(e -> e.getKey() >= threshold)
                .mapToLong(Map.Entry::getValue).sum();
    }

    private void pruneHistory(MarketData d) {
        long threshold = System.currentTimeMillis() - 48L * 3_600_000L;
        d.history.entrySet().removeIf(e -> e.getKey() < threshold);
    }

    // ── Persistance ───────────────────────────────────────────────────────

    public void saveAll() { save(); }

    private void save() {
        try {
            Map<String, MarketData> serialized = new LinkedHashMap<>();
            markets.forEach((k, v) -> serialized.put(k.toString(), v));
            dataFile.getParentFile().mkdirs();
            Files.writeString(dataFile.toPath(), gson.toJson(serialized), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder energy_market.json", e);
        }
    }

    private void load() {
        if (!dataFile.exists()) return;
        try {
            String json = Files.readString(dataFile.toPath(), StandardCharsets.UTF_8);
            if (json.isBlank()) return;
            @SuppressWarnings("unchecked")
            Map<String, ?> raw = gson.fromJson(json, Map.class);
            raw.forEach((k, v) -> {
                try {
                    UUID uuid = UUID.fromString(k);
                    MarketData d = gson.fromJson(gson.toJson(v), MarketData.class);
                    if (d != null) markets.put(uuid, d);
                } catch (Exception ignored) {}
            });
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de lire energy_market.json", e);
        }
    }
}
