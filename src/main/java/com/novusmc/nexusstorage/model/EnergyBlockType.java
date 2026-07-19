package com.novusmc.nexusstorage.model;

import com.novusmc.nexusstorage.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public enum EnergyBlockType {

    // --- Générateurs ---
    SOLAR_PANEL_BASIC(Material.DAYLIGHT_DETECTOR, 10000, "Panneau Solaire Basique", "Génère de l'énergie en journée.", BlockRole.GENERATOR),
    SOLAR_PANEL_ADVANCED(Material.DAYLIGHT_DETECTOR, 50000, "Panneau Solaire Avancé", "Génère beaucoup plus d'énergie.", BlockRole.GENERATOR),

    // --- Stockage / Capacités ---
    CAPACITOR_BASIC(Material.COPPER_BLOCK, 100000, "Condensateur Basique", "Stocke de l'énergie Nexus.", BlockRole.STORAGE),
    CAPACITOR_ADVANCED(Material.REINFORCED_DEEPSLATE, 1000000, "Condensateur Avancé", "Grande capacité de stockage.", BlockRole.STORAGE),
    ENERGY_CORE(Material.BEACON, 10000000, "Cœur Énergétique Nexus", "Le centre névralgique de votre stockage.", BlockRole.STORAGE),

    // --- Câbles ---
    CABLE_BASIC(Material.LIGHT_GRAY_CONCRETE, 0, "Câble Énergétique Basique", "Transporte l'énergie du réseau.", BlockRole.CABLE),
    CABLE_INSULATED(Material.GRAY_CONCRETE, 0, "Câble Isolé", "Transporte l'énergie sans déperdition.", BlockRole.CABLE),

    // --- Machines / Consommateurs / Utilitaires ---
    INTERFACE_BLOCK(Material.CHISELED_COPPER, 5000, "Interface Réseau", "Permet d'interagir avec le stockage.", BlockRole.CONSUMER),
    ELECTRIC_FURNACE(Material.BLAST_FURNACE, 20000, "Four Électrique", "Cuit les éléments en utilisant les FE du réseau.", BlockRole.CONSUMER),
    REDSTONE_REGULATOR(Material.COMPARATOR, 0, "Régulateur Redstone", "Émet un signal selon l'énergie disponible.", BlockRole.UTILITY),
    ENERGY_MONITOR(Material.OBSERVER, 0, "Moniteur Énergétique", "Affiche les stats du réseau.", BlockRole.UTILITY),
    
    // --- Système de Bouclier ---
    SHIELD_DOME(Material.SEA_LANTERN, 50000, "Générateur de Bouclier", "Protège une zone de 200x200 contre les intrus.", BlockRole.CONSUMER);

    private final Material material;
    private final long maxStorage;
    private final String displayName;
    private final List<String> lore;
    private final BlockRole role;

    EnergyBlockType(Material material, long maxStorage, String displayName, String description, BlockRole role) {
        this.material = material;
        this.maxStorage = maxStorage;
        this.displayName = displayName;
        this.lore = Arrays.asList("§7" + description, "§8Capacité max: §a" + maxStorage + " FE");
        this.role = role;
    }

    public Material getMaterial() {
        return material;
    }

    public long getMaxStorage() {
        return maxStorage;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public BlockRole getRole() {
        return role;
    }

    /**
     * Vérifie et affiche l'énergie disponible sur le réseau.
     * Correction de getEnergy() selon ton modèle exact (ex: via le stockage interne).
     */
    public void checkNetworkEnergy(NexusNetwork network, Player player) {
        // Si ton NexusNetwork stocke la valeur dans une variable, on l'utilise directement ici
        long currentEnergy = network.getStoredEnergy(); 

        player.sendMessage(
                Component.text("⚡ Énergie du réseau Nexus : ", NamedTextColor.AQUA)
                        .append(Component.text(currentEnergy + " / " + network.getMaxEnergy() + " FE", NamedTextColor.GREEN))
        );
    }

    /**
     * Vérification de la sécurité / accès des joueurs.
     * Utilise network.getAccessMap() ou getMembers() pour corriger l'erreur de la Map.
     */
    public boolean hasAccess(NexusNetwork network, UUID playerUUID) {
        // Si ton réseau utilise une map d'AccessLevel, on vérifie si le joueur y est présent
        return network.getAccessMap() != null && network.getAccessMap().containsKey(playerUUID);
    }

    /**
     * Logique d'activation du dôme de protection.
     */
    public void handleShieldActivation(Main plugin, Location loc, UUID networkOwner) {
        if (this == SHIELD_DOME) {
            // Ajustement de la signature : si ton ShieldDomeManager attend (owner, location) au lieu de (location, owner)
            plugin.getShieldDomeManager().registerDome(networkOwner, loc);
            
            loc.getWorld().sendMessage(
                    Component.text("⚡ Un dôme énergétique Nexus vient d'être déployé !", NamedTextColor.GREEN)
            );
        }
    }

    /**
     * Enum interne pour définir les rôles des blocs de ton réseau énergétique.
     */
    public enum BlockRole {
        GENERATOR,
        STORAGE,
        CABLE,
        CONSUMER,
        UTILITY
    }
}
