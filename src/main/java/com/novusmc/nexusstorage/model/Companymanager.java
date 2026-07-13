package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.Company;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gère la création, la gestion et la persistance des entreprises.
 * Les entreprises permettent à plusieurs joueurs de partager un réseau Nexus avec différents rôles.
 */
public class CompanyManager {
    private final Main plugin;
    private final Map<UUID, Company> companiesById = new ConcurrentHashMap<>();
    private final Map<String, UUID> companiesByName = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToCompany = new ConcurrentHashMap<>(); // player UUID -> company UUID
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File companiesDataFile;

    public CompanyManager(Main plugin) {
        this.plugin = plugin;
        this.companiesDataFile = new File(plugin.getDataFolder(), "companies.json");
        loadAllCompanies();
    }

    /**
     * Crée une nouvelle entreprise.
     * @param owner UUID du propriétaire
     * @param name Nom de l'entreprise
     * @return La company créée, ou null si le nom est déjà utilisé
     */
    public Company createCompany(UUID owner, String name) {
        if (companiesByName.containsKey(name.toLowerCase())) {
            return null; // Nom déjà utilisé
        }

        Company company = new Company(owner, name);
        companiesById.put(company.getId(), company);
        companiesByName.put(name.toLowerCase(), company.getId());
        playerToCompany.put(owner, company.getId());
        
        saveCompany(company);
        return company;
    }

    /**
     * Récupère une entreprise par son ID.
     */
    public Company getCompanyById(UUID companyId) {
        return companiesById.get(companyId);
    }

    /**
     * Récupère une entreprise par son nom.
     */
    public Company getCompanyByName(String name) {
        UUID id = companiesByName.get(name.toLowerCase());
        return id != null ? companiesById.get(id) : null;
    }

    /**
     * Obtient l'entreprise d'un joueur.
     */
    public Company getPlayerCompany(UUID playerUuid) {
        UUID companyId = playerToCompany.get(playerUuid);
        return companyId != null ? companiesById.get(companyId) : null;
    }

    /**
     * Envoie une invitation à un joueur pour rejoindre une entreprise.
     */
    public boolean invitePlayer(Company company, UUID playerUuid, Player inviter) {
        if (!company.isManager(inviter.getUniqueId())) {
            return false; // Pas de permission
        }

        if (company.isMember(playerUuid)) {
            return false; // Déjà membre
        }

        if (playerToCompany.containsKey(playerUuid)) {
            return false; // Déjà dans une autre entreprise
        }

        long expiryHours = plugin.getConfig().getLong("companies.invitation-expiry-hours", 72);
        company.sendInvitation(playerUuid, expiryHours);
        saveCompany(company);
        return true;
    }

    /**
     * Accepte l'invitation pour une entreprise.
     */
    public boolean acceptInvitation(UUID playerUuid, String companyName) {
        Company company = getCompanyByName(companyName);
        if (company == null) {
            return false;
        }

        if (!company.hasValidInvitation(playerUuid)) {
            return false;
        }

        if (playerToCompany.containsKey(playerUuid)) {
            return false; // Déjà dans une autre entreprise
        }

        company.acceptInvitation(playerUuid);
        playerToCompany.put(playerUuid, company.getId());
        saveCompany(company);
        return true;
    }

    /**
     * Refuse/annule une invitation.
     */
    public void declineInvitation(UUID playerUuid, String companyName) {
        Company company = getCompanyByName(companyName);
        if (company != null) {
            company.cancelInvitation(playerUuid);
            saveCompany(company);
        }
    }

    /**
     * Ajoute un membre à une entreprise (sans invitation).
     */
    public void addMember(Company company, UUID memberUuid, Company.CompanyRole role) {
        company.addMember(memberUuid, role);
        playerToCompany.put(memberUuid, company.getId());
        saveCompany(company);
    }

    /**
     * Retire un membre de l'entreprise.
     */
    public void removeMember(Company company, UUID memberUuid) {
        if (company.isOwner(memberUuid)) {
            return; // Impossible de retirer le propriétaire
        }
        company.removeMember(memberUuid);
        playerToCompany.remove(memberUuid);
        saveCompany(company);
    }

    /**
     * Dissocie une entreprise (la désactive).
     */
    public void dissolveCompany(Company company) {
        company.setActive(false);
        companiesByName.remove(company.getName().toLowerCase());
        companiesById.remove(company.getId());
        
        for (UUID member : company.getMembers().keySet()) {
            playerToCompany.remove(member);
        }
        
        saveCompany(company);
    }

    /**
     * Sauvegarde une entreprise dans le fichier JSON.
     */
    private void saveCompany(Company company) {
        try {
            // Pour cette démo, on sauvegarde tout le fichier
            // En production, implémenter une DB plus robuste
            saveAllCompanies();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la sauvegarde de l'entreprise " + company.getName(), e);
        }
    }

    /**
     * Sauvegarde toutes les entreprises.
     */
    public void saveAllCompanies() {
        try {
            if (!companiesDataFile.exists()) {
                companiesDataFile.getParentFile().mkdirs();
                companiesDataFile.createNewFile();
            }

            Map<String, Object> data = new HashMap<>();
            Map<String, String> companiesData = new HashMap<>();
            
            for (Company company : companiesById.values()) {
                companiesData.put(company.getId().toString(), gson.toJson(company));
            }
            
            data.put("companies", companiesData);
            
            // Utiliser un fichier texte brut avec Gson
            String json = gson.toJson(data);
            java.nio.file.Files.write(companiesDataFile.toPath(), json.getBytes());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la sauvegarde des entreprises", e);
        }
    }

    /**
     * Charge toutes les entreprises du fichier JSON.
     */
    private void loadAllCompanies() {
        try {
            if (!companiesDataFile.exists()) {
                return;
            }

            String content = new String(java.nio.file.Files.readAllBytes(companiesDataFile.toPath()));
            if (content.isBlank()) {
                return;
            }

            Map<String, Object> data = gson.fromJson(content, Map.class);
            if (data == null) {
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, String> companiesData = (Map<String, String>) data.get("companies");
            if (companiesData != null) {
                for (String jsonStr : companiesData.values()) {
                    try {
                        Company company = gson.fromJson(jsonStr, Company.class);
                        if (company != null && company.isActive()) {
                            companiesById.put(company.getId(), company);
                            companiesByName.put(company.getName().toLowerCase(), company.getId());
                            
                            for (UUID member : company.getMembers().keySet()) {
                                playerToCompany.put(member, company.getId());
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Erreur lors du chargement d'une entreprise", e);
                    }
                }
            }

            plugin.getLogger().info("Chargé " + companiesById.size() + " entreprise(s).");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la lecture des entreprises", e);
        }
    }

    /**
     * Obtient toutes les entreprises existantes.
     */
    public Collection<Company> getAllCompanies() {
        return new ArrayList<>(companiesById.values());
    }

    /**
     * Obtient les invitations en attente pour un joueur.
     */
    public Map<String, Company> getPendingInvitations(UUID playerUuid) {
        Map<String, Company> pending = new HashMap<>();
        for (Company company : companiesById.values()) {
            if (company.hasValidInvitation(playerUuid)) {
                pending.put(company.getName(), company);
            }
        }
        return pending;
    }
}
