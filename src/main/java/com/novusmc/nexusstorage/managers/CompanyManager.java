package com.novusmc.nexusstorage.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.Company;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gere le cycle de vie des entreprises (creation, invitation, acception,
 * dissolution) et leur persistance dans companies.json.
 */
public class CompanyManager {

    private final Main plugin;
    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** id -> Company */
    private final Map<UUID, Company>  byId     = new ConcurrentHashMap<>();
    /** nom (lowercase) -> id */
    private final Map<String, UUID>   byName   = new ConcurrentHashMap<>();
    /** playerUuid -> company id */
    private final Map<UUID, UUID>     byPlayer = new ConcurrentHashMap<>();

    public CompanyManager(Main plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "companies.json");
        load();
    }

    // ── Création ─────────────────────────────────────────────────────────

    /**
     * Cree une nouvelle entreprise apres deduction du cout.
     * @return la company, ou null si nom deja pris ou joueur deja dans une company.
     */
    public Company create(Player owner, String name) {
        if (byName.containsKey(name.toLowerCase())) return null;
        if (byPlayer.containsKey(owner.getUniqueId())) return null;

        double cost = plugin.getConfig().getDouble("companies.creation-cost", 50_000.0);
        if (!plugin.getEconomyManager().withdraw(owner, cost)) {
            owner.sendMessage(color(plugin.getConfig().getString("messages.not-enough-money", "&cPas assez d'argent.")));
            return null;
        }

        Company c = new Company(owner.getUniqueId(), name);
        register(c);
        save();
        return c;
    }

    private void register(Company c) {
        byId.put(c.getId(), c);
        byName.put(c.getName().toLowerCase(), c.getId());
        for (UUID m : c.getMembers().keySet()) byPlayer.put(m, c.getId());
    }

    // ── Invitations ───────────────────────────────────────────────────────

    public boolean invite(Company c, UUID target, Player sender) {
        if (!c.isManager(sender.getUniqueId())) return false;
        if (c.isMember(target)) return false;
        if (byPlayer.containsKey(target)) return false;
        long hours = plugin.getConfig().getLong("companies.invitation-expiry-hours", 72);
        c.invite(target, hours);
        save();
        return true;
    }

    /** Accepte une invitation. Retourne false si invitation invalide ou joueur deja dans une company. */
    public boolean accept(UUID playerUuid, String companyName) {
        Company c = getByName(companyName);
        if (c == null || !c.hasValidInvitation(playerUuid)) return false;
        if (byPlayer.containsKey(playerUuid)) return false;
        c.acceptInvitation(playerUuid);
        byPlayer.put(playerUuid, c.getId());
        save();
        return true;
    }

    public void decline(UUID playerUuid, String companyName) {
        Company c = getByName(companyName);
        if (c != null) { c.removeInvitation(playerUuid); save(); }
    }

    // ── Membres ──────────────────────────────────────────────────────────

    public boolean leave(UUID playerUuid) {
        Company c = getByPlayer(playerUuid);
        if (c == null) return false;
        if (!c.removeMember(playerUuid)) return false; // owner ne peut pas partir
        byPlayer.remove(playerUuid);
        save();
        return true;
    }

    public boolean kick(Company c, UUID target, UUID requester) {
        if (!c.isManager(requester) || c.isOwner(target)) return false;
        c.removeMember(target);
        byPlayer.remove(target);
        save();
        return true;
    }

    // ── Dissolution ───────────────────────────────────────────────────────

    public void dissolve(Company c) {
        byId.remove(c.getId());
        byName.remove(c.getName().toLowerCase());
        c.getMembers().keySet().forEach(byPlayer::remove);
        save();
    }

    // ── Lookups ───────────────────────────────────────────────────────────

    public Company getById(UUID id)         { return byId.get(id); }
    public Company getByName(String name)   { UUID id = byName.get(name.toLowerCase()); return id != null ? byId.get(id) : null; }
    public Company getByPlayer(UUID player) { UUID id = byPlayer.get(player); return id != null ? byId.get(id) : null; }
    public Collection<Company> all()        { return byId.values(); }

    /** Retourne toutes les invitations en attente (valides) pour un joueur. */
    public List<Company> pendingInvitations(UUID playerUuid) {
        List<Company> list = new ArrayList<>();
        for (Company c : byId.values())
            if (c.hasValidInvitation(playerUuid)) list.add(c);
        return list;
    }

    // ── Persistance JSON ──────────────────────────────────────────────────

    public void saveAll() { save(); }

    private void save() {
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Company c : byId.values()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",        c.getId().toString());
                m.put("owner",     c.getOwner().toString());
                m.put("name",      c.getName());
                m.put("createdAt", c.getCreatedAt());

                Map<String, String> membersMap = new LinkedHashMap<>();
                c.getMembers().forEach((u, r) -> membersMap.put(u.toString(), r.name()));
                m.put("members", membersMap);

                Map<String, Long> invMap = new LinkedHashMap<>();
                c.getInvitations().forEach((u, exp) -> invMap.put(u.toString(), exp));
                m.put("invitations", invMap);

                list.add(m);
            }
            dataFile.getParentFile().mkdirs();
            Files.writeString(dataFile.toPath(), gson.toJson(list), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder companies.json", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!dataFile.exists()) return;
        try {
            String json = Files.readString(dataFile.toPath(), StandardCharsets.UTF_8);
            if (json.isBlank()) return;
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> list = gson.fromJson(json, listType);
            if (list == null) return;
            for (Map<String, Object> m : list) {
                try {
                    UUID ownerId = UUID.fromString((String) m.get("owner"));
                    String name  = (String) m.get("name");
                    Company c = new Company(ownerId, name);

                    Map<String, String> membersMap = (Map<String, String>) m.get("members");
                    if (membersMap != null) membersMap.forEach((k, v) -> {
                        try { c.getMembers().put(UUID.fromString(k), Company.Role.valueOf(v)); }
                        catch (Exception ignored) {}
                    });

                    Map<String, Object> invMap = (Map<String, Object>) m.get("invitations");
                    if (invMap != null) invMap.forEach((k, v) -> {
                        try { c.getInvitations().put(UUID.fromString(k), ((Number) v).longValue()); }
                        catch (Exception ignored) {}
                    });

                    register(c);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Entree company invalide ignoree", e);
                }
            }
            plugin.getLogger().info("CompanyManager : " + byId.size() + " entreprise(s) chargee(s).");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de lire companies.json", e);
        }
    }

    private String color(String s) { return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', s); }
}
