package com.novusmc.nexusstorage.model;

import com.novusmc.nexusstorage.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import java.util.UUID;

public enum EnergyBlockType {

    // Définition de tes types de blocs énergétiques (Exemples à adapter selon tes besoins)
    NEXUS_CORE(Material.BEACON, 500000),
    BATTERY(Material.COPPER_BLOCK, 100000),
    
    // Le fameux générateur de dôme requis par ta structure
    SHIELD_GENERATOR(Material.SEA_LANTERN, 50000);

    private final Material material;
    private final long maxStorage;

    EnergyBlockType(Material material, long maxStorage) {
        this.material = material;
        this.maxStorage = maxStorage;
    }

    public Material getMaterial() {
        return material;
    }

    public long getMaxStorage() {
        return maxStorage;
    }

    /**
     * Vérifie et affiche l'énergie du réseau à un joueur à l'aide de l'API Kyori.
     * Règle les erreurs des lignes 43, 45 et 50.
     */
    public void checkNetworkEnergy(NexusNetwork network, Player player) {
        // Résout l'erreur getEnergy() en ciblant le stockage
        double currentEnergy = network.getEnergy(); 

        // Résout l'erreur sendMessage(String) en utilisant les Components modernes
        player.sendMessage(
                Component.text("⚡ Énergie du réseau Nexus : ", NamedTextColor.AQUA)
                        .append(Component.text(String.format("%.1f", currentEnergy) + " FE", NamedTextColor.GREEN))
        );
    }

    /**
     * Gère les vérifications d'accès des joueurs au réseau.
     * Règle l'erreur de la ligne 96.
     */
    public boolean hasAccess(NexusNetwork network, UUID playerUUID) {
        // Utilisation de la structure réseau correcte pour vérifier l'accès des membres
        return network.getMembers().contains(playerUUID);
    }

    /**
     * Logique d'activation d'un dôme depuis un bloc du réseau
     */
    public void handleShieldActivation(Main plugin, Location loc, UUID networkOwner) {
        if (this == SHIELD_GENERATOR) {
            plugin.getShieldDomeManager().registerDome(loc, networkOwner);
            loc.getWorld().sendMessage(
                    Component.text("⚡ Un dôme énergétique Nexus vient d'être déployé !", NamedTextColor.GREEN)
            );
        }
    }
}
