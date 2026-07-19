package com.novusmc.nexusstorage.model;

import org.bukkit.Material;

/**
 * Tous les blocs du système d'énergie Nexus (style RF/FE).
 *
 * IMPORTANT: les rôles (Role) sont exactement ceux attendus par EnergyManager :
 *   CORE, SOURCE, STORAGE, CABLE, INTERFACE, FURNACE, SHIELD, REGULATOR, MONITOR
 *
 * Chaque bloc a un matériau distinct pour être identifiable en jeu
 * (détecté via PDC nexus_energy_type, pas le Material seul).
 * maxStorage = capacité de stockage propre au bloc (0 si inapplicable).
 */
public enum EnergyBlockType {

    // ── Générateurs (SOURCE) ────────────────────────────────────────────
    SOLAR_PANEL_BASIC(
            Material.DAYLIGHT_DETECTOR, Role.SOURCE, 0,
            "&e&lPanneau Solaire Basique",
            "&7Génère &e20 FE&7/cycle en journée.",
            "&7Nécessite le ciel dégagé au-dessus."
    ),
    SOLAR_PANEL_ADVANCED(
            Material.TINTED_GLASS, Role.SOURCE, 0,
            "&6&lPanneau Solaire Avancé",
            "&7Génère &e60 FE&7/cycle en journée.",
            "&7Nécessite Y ≥ 100 et ciel dégagé."
    ),

    // ── Stockage (STORAGE) ───────────────────────────────────────────────
    CAPACITOR_BASIC(
            Material.COPPER_BLOCK, Role.STORAGE, 10_000L,
            "&b&lCondensateur Basique",
            "&7Stocke jusqu'à &e10 000 FE."
    ),
    CAPACITOR_ADVANCED(
            Material.REINFORCED_DEEPSLATE, Role.STORAGE, 100_000L,
            "&5&lCondensateur Avancé",
            "&7Stocke jusqu'à &e100 000 FE."
    ),

    // ── Nœud central (CORE) ─────────────────────────────────────────────
    ENERGY_CORE(
            Material.BEACON, Role.CORE, 0,
            "&d&l⚡ Nexus Energy Core",
            "&7Ancre le réseau physique de câbles",
            "&7à ton réseau Nexus.",
            "&7Un seul suffit par grappe."
    ),

    // ── Câbles (CABLE) ───────────────────────────────────────────────────
    CABLE_BASIC(
            Material.LIGHT_GRAY_CONCRETE, Role.CABLE, 0,
            "&f&lCâble Énergétique Basique",
            "&7Transporte l'énergie.",
            "&7Légère perte par bloc."
    ),
    CABLE_INSULATED(
            Material.GRAY_CONCRETE, Role.CABLE, 0,
            "&f&lCâble Isolé",
            "&7Transporte l'énergie sans déperdition."
    ),

    // ── Interface items (INTERFACE) ──────────────────────────────────────
    INTERFACE_BLOCK(
            Material.CHISELED_COPPER, Role.INTERFACE, 0,
            "&a&lInterface Réseau",
            "&7Transfère les items d'un coffre adjacent",
            "&7vers ton stockage Nexus.",
            "&7Consomme &e5 FE&7/item."
    ),

    // ── Four électrique (FURNACE) ─────────────────────────────────────
    ELECTRIC_FURNACE(
            Material.BLAST_FURNACE, Role.FURNACE, 0,
            "&6&l⚡ Four Électrique",
            "&7Accélère la cuisson en utilisant",
            "&7l'énergie du réseau.",
            "&7Consomme &e75 FE&7/tick."
    ),

    // ── Régulateur (REGULATOR) ───────────────────────────────────────────
    REDSTONE_REGULATOR(
            Material.COMPARATOR, Role.REGULATOR, 0,
            "&c&lRégulateur Redstone",
            "&7Coupe les Interfaces si l'énergie",
            "&7tombe sous le seuil configuré.",
            "&7Clic : +5% | Shift+Clic : -5%"
    ),

    // ── Moniteur (MONITOR) ───────────────────────────────────────────────
    ENERGY_MONITOR(
            Material.OBSERVER, Role.MONITOR, 0,
            "&f&lMoniteur Énergétique",
            "&7Clic droit pour voir les statistiques",
            "&7en temps réel du réseau."
    ),

    // ── Dôme de protection (SHIELD) ──────────────────────────────────────
    SHIELD_DOME(
            Material.SEA_LANTERN, Role.SHIELD, 0,
            "&5&l⚡ Générateur de Bouclier",
            "&7Protège une zone de 200×200 blocs.",
            "&7Consomme &e250 FE&7/10s."
    );

    /**
     * Rôles alignés sur la logique du BFS de EnergyManager.
     * Ne pas changer les noms sans mettre à jour EnergyManager en parallèle.
     */
    public enum Role {
        SOURCE,    // génère de l'énergie
        STORAGE,   // stocke de l'énergie
        CORE,      // nœud d'ancrage (obligatoire, 1 par réseau)
        CABLE,     // transporte l'énergie
        INTERFACE, // transfère des items → stockage Nexus
        FURNACE,   // four alimenté électriquement
        REGULATOR, // coupe les interfaces si énergie faible
        MONITOR,   // affiche les stats
        SHIELD     // dôme de protection
    }

    private final Material material;
    private final Role     role;
    private final long     maxStorage;
    private final String   displayName;
    private final String[] lore;

    EnergyBlockType(Material material, Role role, long maxStorage,
                    String displayName, String... lore) {
        this.material    = material;
        this.role        = role;
        this.maxStorage  = maxStorage;
        this.displayName = displayName;
        this.lore        = lore;
    }

    public Material getMaterial()  { return material; }
    public Role     getRole()      { return role; }
    public long     getMaxStorage(){ return maxStorage; }
    public String   getDisplayName(){ return displayName; }
    public String[] getLore()      { return lore; }

    /** Clé utilisée dans itemsadder.yml (ex: "energy_solar_panel_basic"). */
    public String configKey() { return "energy-" + name().toLowerCase().replace('_', '-'); }
}
