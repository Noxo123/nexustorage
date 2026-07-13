package com.novusmc.nexusstorage.model;

import java.util.*;

/**
 * Représente une entreprise pouvant regrouper plusieurs joueurs avec un réseau Nexus partagé.
 */
public class Company {
    private final UUID id;
    private final UUID owner;
    private final String name;
    private final long creationDate;
    
    private long lastMaintenanceDate;
    private final Map<UUID, CompanyRole> members = new HashMap<>();
    private final Map<UUID, Long> invitations = new HashMap<>(); // UUID -> expiration timestamp
    private boolean active = true;

    public enum CompanyRole {
        OWNER,      // Propriétaire - contrôle total
        MANAGER,    // Gérant - gère les membres et les settings
        MEMBER      // Membre - accès au stockage et énergie partagée
    }

    public Company(UUID owner, String name) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.name = name;
        this.creationDate = System.currentTimeMillis();
        this.lastMaintenanceDate = System.currentTimeMillis();
        this.members.put(owner, CompanyRole.OWNER);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public long getCreationDate() {
        return creationDate;
    }

    public long getLastMaintenanceDate() {
        return lastMaintenanceDate;
    }

    public void setLastMaintenanceDate(long date) {
        this.lastMaintenanceDate = date;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Map<UUID, CompanyRole> getMembers() {
        return members;
    }

    public void addMember(UUID uuid, CompanyRole role) {
        members.put(uuid, role);
        invitations.remove(uuid);
    }

    public void removeMember(UUID uuid) {
        if (!uuid.equals(owner)) {
            members.remove(uuid);
        }
    }

    public CompanyRole getMemberRole(UUID uuid) {
        return members.getOrDefault(uuid, null);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean isManager(UUID uuid) {
        CompanyRole role = members.get(uuid);
        return role == CompanyRole.OWNER || role == CompanyRole.MANAGER;
    }

    /**
     * Envoie une invitation au joueur. Si elle existe déjà, elle est renouvelée.
     * @param playerUuid UUID du joueur
     * @param expiryHours Heures avant expiration (0 = jamais)
     */
    public void sendInvitation(UUID playerUuid, long expiryHours) {
        long expiration = expiryHours > 0 ? System.currentTimeMillis() + (expiryHours * 3600000L) : -1;
        invitations.put(playerUuid, expiration);
    }

    /**
     * Vérifie si le joueur a une invitation valide.
     */
    public boolean hasValidInvitation(UUID playerUuid) {
        Long expiration = invitations.get(playerUuid);
        if (expiration == null) return false;
        if (expiration == -1) return true; // Pas d'expiration
        return System.currentTimeMillis() <= expiration;
    }

    /**
     * Accepte l'invitation et ajoute le joueur en tant que membre.
     */
    public boolean acceptInvitation(UUID playerUuid) {
        if (!hasValidInvitation(playerUuid)) {
            return false;
        }
        addMember(playerUuid, CompanyRole.MEMBER);
        return true;
    }

    public void cancelInvitation(UUID playerUuid) {
        invitations.remove(playerUuid);
    }

    public Map<UUID, Long> getPendingInvitations() {
        return new HashMap<>(invitations);
    }
}
