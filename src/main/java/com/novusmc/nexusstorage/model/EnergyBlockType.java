package com.novusmc.nexusstorage.manager;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShieldDomeManager implements Listener {

    private final Main plugin;
    // Garde en mémoire l'emplacement de chaque générateur actif et le réseau auquel il appartient
    private final Map<Location, UUID> activeDomes = new HashMap<>();

    public ShieldDomeManager(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Tâche planifiée (appelée par exemple toutes les minutes ou toutes les heures)
     * pour consommer l'énergie du réseau et désactiver le dôme s'il n'y a plus de FE.
     */
    public void runShieldTick() {
        // La description indique 10 000 FE / heure. 
        // Si ta tâche tourne toutes les minutes (1200 ticks), la consommation est de ~166 FE par minute.
        double energyCostPerMinute = 10000.0 / 60.0; 

        activeDomes.entrySet().removeIf(entry -> {
            Location loc = entry.getKey();
            UUID ownerUUID = entry.getValue();
            
            NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(ownerUUID);
            
            // Vérification si le réseau existe et a assez d'énergie
            if (network == null || network.getEnergy() < energyCostPerMinute) {
                // Plus assez d'énergie ou réseau supprimé -> on éteint le dôme
                loc.getWorld().sendMessage("§c⚡ Un générateur de bouclier s'est éteint par manque d'énergie !");
                return true; // Supprime du dôme actif
            }

            // Consomme l'énergie
            network.setEnergy(network.getEnergy() - energyCostPerMinute);
            return false;
        });
    }

    /**
     * Gère la protection de la zone (200x200 blocs autour du générateur).
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isProtected(event.getBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c⚡ Cette zone est protégée par un bouclier énergétique Nexus !");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isProtected(event.getBlock().getLocation(), event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c⚡ Cette zone est protégée par un bouclier énergétique Nexus !");
        }
    }

    /**
     * Vérifie si une position est protégée par un dôme actif
     * et si le joueur qui interagit en est le propriétaire (ou a accès).
     */
    private boolean isProtected(Location blockLoc, Player player) {
        for (Map.Entry<Location, UUID> entry : activeDomes.entrySet()) {
            Location domeLoc = entry.getKey();
            UUID ownerUUID = entry.getValue();

            // Même monde et rayon de 100 blocs autour (pour faire une zone de 200x200)
            if (blockLoc.getWorld().equals(domeLoc.getWorld())) {
                double distanceX = Math.abs(blockLoc.getX() - domeLoc.getX());
                double distanceZ = Math.abs(blockLoc.getZ() - domeLoc.getZ());

                if (distanceX <= 100 && distanceZ <= 100) {
                    // Si le joueur est le propriétaire, il a le droit de construire/casser
                    if (player.getUniqueId().equals(ownerUUID)) {
                        return false;
                    }
                    
                    // Optionnel : Permettre aux membres du Nexus d'y accéder aussi
                    NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(ownerUUID);
                    if (network != null && network.getMembers().contains(player.getUniqueId())) {
                        return false;
                    }

                    return true; // Zone protégée pour les autres !
                }
            }
        }
        return false;
    }

    public void registerDome(Location loc, UUID owner) {
        activeDomes.put(loc, owner);
    }

    public void unregisterDome(Location loc) {
        activeDomes.remove(loc);
    }
}
