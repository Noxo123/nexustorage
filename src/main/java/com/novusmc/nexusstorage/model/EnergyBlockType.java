package com.novusmc.nexusstorage.model;

import org.bukkit.Material;

/**
 * Tous les blocs du systeme d'energie Nexus.
 * ELECTRIC_FURNACE et SHIELD_DOME ont leur propre role (FURNACE, SHIELD)
 * pour ne pas etre confondus avec les INTERFACE_BLOCK dans les boucles.
 */
public enum EnergyBlockType {

    SOLAR_PANEL_BASIC(Material.SEA_LANTERN, Role.SOURCE, "&e&lNexus Solar Panel",
            "&7Genere de l'energie le jour", "&7si le ciel est visible au-dessus."),

    SOLAR_PANEL_ADVANCED(Material.END_ROD, Role.SOURCE, "&6&lNexus Solar Panel II",
            "&7Version avancee : plus de sortie,", "&7necessite Y > 100 et ciel degage."),

    CAPACITOR_BASIC(Material.COPPER_BLOCK, Role.STORAGE, "&b&lNexus Capacitor",
            "&7Stocke l'energie du reseau physique.", "&7Capacite de base."),

    CAPACITOR_ADVANCED(Material.NETHERITE_BLOCK, Role.STORAGE, "&5&lNexus Capacitor II",
            "&7Capacitor haute capacite."),

    CABLE_BASIC(Material.IRON_BARS, Role.CABLE, "&f&lNexus Energy Cable",
            "&7Transporte l'energie.", "&7Legere perte par bloc."),

    CABLE_INSULATED(Material.CHAIN, Role.CABLE, "&f&lNexus Insulated Cable",
            "&7Cable isole : perte quasi nulle,", "&7meilleur debit."),

    INTERFACE_BLOCK(Material.HOPPER, Role.INTERFACE, "&a&lNexus Interface",
            "&7Consomme de l'energie pour transferer",
            "&7des items d'un coffre adjacent vers ton stockage Nexus."),

    // Role distinct FURNACE : detecte separement dans EnergyManager.runElectricFurnaces()
    ELECTRIC_FURNACE(Material.FURNACE, Role.FURNACE, "&6&l⚡ Electric Furnace",
            "&7Consomme de l'energie du reseau",
            "&7pour cuire des aliments ou fondre des minerais",
            "&7a tres grande vitesse."),

    ENERGY_CORE(Material.BEACON, Role.CORE, "&d&l⚡ Nexus Energy Core",
            "&7Ancre le reseau physique de cables",
            "&7a ton reseau Nexus. Un seul suffit", "&7par grappe de cables."),

    REDSTONE_REGULATOR(Material.OBSERVER, Role.REGULATOR, "&c&lNexus Regulator",
            "&7Coupe automatiquement les Interfaces",
            "&7si l'energie stockee passe sous un seuil.",
            "&7Clique pour augmenter le seuil,", "&7shift-clique pour le baisser."),

    ENERGY_MONITOR(Material.LECTERN, Role.MONITOR, "&f&lNexus Energy Monitor",
            "&7Clique droit pour voir les statistiques",
            "&7en temps reel du reseau d'energie."),

    // Role distinct SHIELD : detecte separement dans ShieldDomeManager
    SHIELD_DOME(Material.CRYING_OBSIDIAN, Role.SHIELD, "&5&l⚡ Shield Generator",
            "&7Generateur de dome energetique.",
            "&7Consomme 10000 FE/heure pour proteger",
            "&7une zone de 200x200 blocs.");

    public enum Role { SOURCE, STORAGE, CABLE, INTERFACE, FURNACE, SHIELD, CORE, REGULATOR, MONITOR }

    private final Material material;
    private final Role role;
    private final String displayName;
    private final String[] lore;

    EnergyBlockType(Material material, Role role, String displayName, String... lore) {
        this.material = material;
        this.role = role;
        this.displayName = displayName;
        this.lore = lore;
    }

    public Material getMaterial()   { return material; }
    public Role getRole()           { return role; }
    public String getDisplayName()  { return displayName; }
    public String[] getLore()       { return lore; }
    public String configKey()       { return name().toLowerCase(); }
}
