package com.novusmc.nexusstorage.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represente une entreprise regroupant plusieurs joueurs partageant un reseau Nexus.
 */
public class Company {

    public enum Role { OWNER, MANAGER, MEMBER }

    private final UUID id;
    private final UUID owner;
    private String name;
    private final long createdAt;
    private final Map<UUID, Role> members    = new HashMap<>();
    // playerUuid -> expiration epoch ms (-1 = pas d'expiration)
    private final Map<UUID, Long> invitations = new HashMap<>();

    public Company(UUID owner, String name) {
        this.id        = UUID.randomUUID();
        this.owner     = owner;
        this.name      = name;
        this.createdAt = System.currentTimeMillis();
        members.put(owner, Role.OWNER);
    }

    // ── Membres ──────────────────────────────────────────────────────────

    public boolean isMember(UUID uuid)  { return members.containsKey(uuid); }
    public boolean isOwner(UUID uuid)   { return owner.equals(uuid); }
    public boolean isManager(UUID uuid) {
        Role r = members.get(uuid);
        return r == Role.OWNER || r == Role.MANAGER;
    }
    public Role getRole(UUID uuid)      { return members.getOrDefault(uuid, null); }

    public void addMember(UUID uuid, Role role) {
        members.put(uuid, role);
        invitations.remove(uuid);
    }

    /** Retire un membre (impossible de retirer l'owner). */
    public boolean removeMember(UUID uuid) {
        if (owner.equals(uuid)) return false;
        return members.remove(uuid) != null;
    }

    // ── Invitations ───────────────────────────────────────────────────────

    /**
     * @param expiryHours heures avant expiration, 0 = jamais
     */
    public void invite(UUID uuid, long expiryHours) {
        long exp = expiryHours > 0 ? System.currentTimeMillis() + expiryHours * 3_600_000L : -1L;
        invitations.put(uuid, exp);
    }

    public boolean hasValidInvitation(UUID uuid) {
        Long exp = invitations.get(uuid);
        if (exp == null) return false;
        return exp == -1L || System.currentTimeMillis() <= exp;
    }

    /** Accepte l'invitation et ajoute le joueur comme MEMBER. Retourne false si invitation invalide. */
    public boolean acceptInvitation(UUID uuid) {
        if (!hasValidInvitation(uuid)) return false;
        addMember(uuid, Role.MEMBER);
        return true;
    }

    public void removeInvitation(UUID uuid) { invitations.remove(uuid); }

    // ── Getters ──────────────────────────────────────────────────────────

    public UUID getId()              { return id; }
    public UUID getOwner()           { return owner; }
    public String getName()          { return name; }
    public void setName(String name) { this.name = name; }
    public long getCreatedAt()       { return createdAt; }
    public Map<UUID, Role> getMembers()     { return members; }
    public Map<UUID, Long> getInvitations() { return invitations; }
}
