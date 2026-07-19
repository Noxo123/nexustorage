package com.novusmc.nexusstorage.model;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represente le reseau de stockage interconnecte d'un joueur (l'owner).
 * Un joueur ne possede qu'un seul reseau, mais peut avoir plusieurs
 * Nexus Core repartis sur la carte pointant vers ce meme reseau.
 */
public class NexusNetwork {

    private final UUID owner;
    private int tier;
    private final Map<UUID, AccessLevel> members = new HashMap<>();
    private final Map<String, Location> corePositions = new HashMap<>();
    private String name;
    private boolean notificationsEnabled = true;

    public NexusNetwork(UUID owner) {
        this.owner = owner;
        this.tier = 1;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public Map<UUID, AccessLevel> getMembers() {
        return members;
    }

    public void addMember(UUID uuid, AccessLevel level) {
        members.put(uuid, level);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public AccessLevel getAccessFor(UUID uuid) {
        if (uuid.equals(owner)) return AccessLevel.ADMIN;
        return members.getOrDefault(uuid, null);
    }

    public boolean hasAccess(UUID uuid) {
        return uuid.equals(owner) || members.containsKey(uuid);
    }

    public Map<String, Location> getCorePositions() {
        return corePositions;
    }

    public void addCore(String key, Location loc) {
        corePositions.put(key, loc);
    }

    public void removeCore(String key) {
        corePositions.remove(key);
    }

    /**
     * Alias de getMembers() pour compatibilite.
     */
    public java.util.Map<UUID, AccessLevel> getAccessMap() {
        return members;
    }

}