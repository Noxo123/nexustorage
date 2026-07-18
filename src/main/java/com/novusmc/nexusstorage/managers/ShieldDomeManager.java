package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
    private final Map<Location, UUID> activeDomes = new HashMap<>();
    private final int RADIUS = 100; 

    public ShieldDomeManager(Main plugin) {
        this.plugin = plugin;
        startEnergyDrainTask();
    }

    private void startEnergyDrainTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                double energyToDrain = 10000.0 / 60.0; 

                activeDomes.entrySet().removeIf(entry -> {
                    Location loc = entry.getKey();
                    UUID networkId = entry.getValue();
                    NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(networkId);

                    // TODO: Si ton système utilise un autre manager pour l'énergie (ex: plugin.getEnergyManager().getEnergy(networkId))
                    // Ajuste la condition ci-dessous selon ton architecture exacte.
                    
                    // Simulation ou bypass temporaire si getEnergy() n'est pas dans NexusNetwork
                    boolean hasEnoughEnergy = true; 
                    
                    /* 
                    // Décommente et ajuste si tu as ajouté l'énergie dans NexusNetwork :
                    if (network == null || network.getEnergy() < energyToDrain) {
                        hasEnoughEnergy = false;
                    } else {
                        network.setEnergy(network.getEnergy() - energyToDrain);
                        plugin.getAccessManager().saveNetworkMeta(network);
                    }
                    */

                    if (!hasEnoughEnergy || network == null) {
                        // Correction du sendMessage : Broadcast propre à tout le serveur ou log console
                        Bukkit.broadcastMessage("§c[Nexus] Un dôme de protection à la position X: " + loc.getBlockX() + " Z: " + loc.getBlockZ() + " s'est éteint !");
                        return true; 
                    }

                    return false;
                });
            }
        }.runTaskTimer(plugin, 20L * 60L, 20L * 60L); 
    }

    public boolean isProtected(Location loc) {
        for (Location domeLoc : activeDomes.keySet()) {
            if (!domeLoc.getWorld().equals(loc.getWorld())) continue;
            
            double deltaX = Math.abs(loc.getX() - domeLoc.getX());
            double deltaZ = Math.abs(loc.getZ() - domeLoc.getZ());
            
            if (deltaX <= RADIUS && deltaZ <= RADIUS) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isProtected(event.getBlock().getLocation())) {
            if (!player.hasPermission("nexusstorage.bypass.dome")) {
                event.setCancelled(true);
                player.sendMessage("§cCette zone est protégée par un dôme énergétique du Nexus.");
            }
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    public void registerDome(Location loc, UUID networkId) {
        activeDomes.put(loc, networkId);
    }

    public void unregisterDome(Location loc) {
        activeDomes.remove(loc);
    }
}
