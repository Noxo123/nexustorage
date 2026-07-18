package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShieldDomeManager implements Listener {

    private final Main plugin;
    // Stocke l'emplacement des dômes actifs et l'UUID du réseau associé
    private final Map<Location, UUID> activeDomes = new HashMap<>();
    private final int RADIUS = 100; // 100 blocs de rayon = zone de 200x200

    public ShieldDomeManager(Main plugin) {
        this.plugin = plugin;
        startEnergyDrainTask();
    }

    /**
     * Tâche qui s'exécute toutes les minutes pour consommer l'énergie
     */
    private void startEnergyDrainTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Consommation pour 1 minute : 10000 / 60 = ~166 FE
                double energyToDrain = 10000.0 / 60.0; 

                activeDomes.entrySet().removeIf(entry -> {
                    Location loc = entry.getKey();
                    UUID networkId = entry.getValue();
                    NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(networkId);

                    // Si le réseau n'existe plus ou n'a plus assez d'énergie
                    if (network == null || network.getEnergy() < energyToDrain) {
                        // Éteindre le dôme (visuel ou message)
                        loc.getWorld().sendMessage(loc, "§c[Nexus] Un dôme de protection s'est éteint par manque d'énergie !");
                        return true; // Retire le dôme de la liste des dômes actifs
                    }

                    // Sinon, on consomme l'énergie
                    network.setEnergy(network.getEnergy() - energyToDrain);
                    plugin.getAccessManager().saveNetworkMeta(network); // Sauvegarde
                    return false;
                });
            }
        }.runTaskTimer(plugin, 20L * 60L, 20L * 60L); // S'exécute toutes les minutes (1200 ticks)
    }

    /**
     * Vérifie si une position donnée est protégée par un dôme actif
     */
    public boolean isProtected(Location loc) {
        for (Location domeLoc : activeDomes.keySet()) {
            if (!domeLoc.getWorld().equals(loc.getWorld())) continue;
            
            // Calcul de la zone 200x200 (format carré pour éviter les calculs de sphère complexes)
            double deltaX = Math.abs(loc.getX() - domeLoc.getX());
            double deltaZ = Math.abs(loc.getZ() - domeLoc.getZ());
            
            if (deltaX <= RADIUS && deltaZ <= RADIUS) {
                return true;
            }
        }
        return false;
    }

    // --- ÉVÉNEMENTS DE PROTECTION ---

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // Si le bloc est dans une zone protégée et que le joueur n'est pas admin/owner
        if (isProtected(event.getBlock().getLocation())) {
            if (!player.hasPermission("nexusstorage.bypass.dome")) {
                event.setCancelled(true);
                player.sendMessage("§cCette zone est protégée par un dôme énergétique du Nexus.");
            }
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        // Annule l'explosion des blocs si elle a lieu dans ou touche un dôme
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    public void registerDome(Location loc, UUID networkId) {
        activeDomes.put(loc, networkId);
    }

    public void unregisterDome(Location loc) {
        activeDomes.remove(loc);
    }
}
