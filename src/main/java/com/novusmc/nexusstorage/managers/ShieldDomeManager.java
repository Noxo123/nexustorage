package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.NexusNetwork;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
        startDomeEffectsAndVisualsTask();
    }

    private void startEnergyDrainTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // TODO: Logique de drain d'énergie (actuellement forcé à faux/vrai selon vos besoins)
                activeDomes.entrySet().removeIf(entry -> {
                    Location loc = entry.getKey();
                    UUID networkId = entry.getValue();
                    NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(networkId);

                    boolean hasEnoughEnergy = true; 

                    if (!hasEnoughEnergy || network == null) {
                        Bukkit.broadcastMessage("§c[Nexus] Un dôme de protection à la position X: " + loc.getBlockX() + " Z: " + loc.getBlockZ() + " s'est éteint !");
                        
                        // Nettoyage visuel pour tous les joueurs à l'extinction
                        for (Player p : loc.getWorld().getPlayers()) {
                            p.setWorldBorder(loc.getWorld().getWorldBorder());
                        }
                        return true; 
                    }
                    return false;
                });
            }
        }.runTaskTimer(plugin, 20L * 60L, 20L * 60L); 
    }

    private void startDomeEffectsAndVisualsTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeDomes.isEmpty()) return;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location pLoc = player.getLocation();
                    Location nearestDome = null;
                    double closestDistance = Double.MAX_VALUE;

                    for (Location domeLoc : activeDomes.keySet()) {
                        if (!domeLoc.getWorld().equals(pLoc.getWorld())) continue;

                        double dist = pLoc.distance(domeLoc);
                        if (dist < closestDistance) {
                            closestDistance = dist;
                            nearestDome = domeLoc;
                        }
                    }

                    if (nearestDome == null) continue;

                    double deltaX = Math.abs(pLoc.getX() - nearestDome.getX());
                    double deltaZ = Math.abs(pLoc.getZ() - nearestDome.getZ());

                    // Si le joueur est dans la zone carrée (ou utilisez la distance pour un cercle)
                    if (deltaX <= RADIUS && deltaZ <= RADIUS) {
                        // 1. Effets de potion
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 0, true, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 0, true, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 0, true, false, true));

                        // 2. Gestion de la WorldBorder factice si proche du bord
                        if (deltaX >= (RADIUS - 15) || deltaZ >= (RADIUS - 15)) {
                            org.bukkit.WorldBorder fakeBorder = Bukkit.createWorldBorder();
                            fakeBorder.setCenter(nearestDome.getX(), nearestDome.getZ());
                            fakeBorder.setSize(RADIUS * 2.0);
                            fakeBorder.setWarningDistance(5); 
                            player.setWorldBorder(fakeBorder);
                        } else {
                            player.setWorldBorder(player.getWorld().getWorldBorder());
                        }

                        // 3. AFFICHAGE DU DÔME EN VERRE TRAVERSABLE (Faux blocs envoyés au client)
                        // On affiche uniquement une portion du dôme proche des yeux du joueur pour éviter le lag
                        showFakeGlassDome(player, nearestDome);

                    } else {
                        player.setWorldBorder(player.getWorld().getWorldBorder());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // S'exécute toutes les 2 secondes
    }

    /**
     * Envoie des faux blocs de verre au joueur pour dessiner le contour du dôme sans bloquer ses mouvements.
     */
    private void showFakeGlassDome(Player player, Location domeCenter) {
        Location pLoc = player.getLocation();
        int playerY = pLoc.getBlockY();
        
        // On ne génère les blocs que dans un rayon de 16 blocs autour du joueur pour optimiser les performances
        int viewRadius = 16; 
        
        for (int x = pLoc.getBlockX() - viewRadius; x <= pLoc.getBlockX() + viewRadius; x++) {
            for (int z = pLoc.getBlockZ() - viewRadius; z <= pLoc.getBlockZ() + viewRadius; z++) {
                
                // Vérifie si ces coordonnées X/Z touchent exactement la limite du dôme (carré)
                boolean isAtXBorder = Math.abs(x - domeCenter.getBlockX()) == RADIUS;
                boolean isAtZBorder = Math.abs(z - domeCenter.getBlockZ()) == RADIUS;
                
                // Si on est sur la bordure du dôme
                if ((isAtXBorder && Math.abs(z - domeCenter.getBlockZ()) <= RADIUS) || 
                    (isAtZBorder && Math.abs(x - domeCenter.getBlockX()) <= RADIUS)) {
                    
                    // On affiche le mur de verre sur une hauteur de 5 blocs autour de la hauteur du joueur
                    for (int y = playerY - 2; y <= playerY + 4; y++) {
                        Location blockLoc = new Location(domeCenter.getWorld(), x, y, z);
                        
                        // Si le vrai bloc est de l'air, on le remplace visuellement par du verre teinté (ex: bleu)
                        if (blockLoc.getBlock().getType() == Material.AIR) {
                            player.sendBlockChange(blockLoc, Material.BLUE_STAINED_GLASS.createBlockData());
                        }
                    }
                }
            }
        }
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
        for (Player p : loc.getWorld().getPlayers()) {
            p.setWorldBorder(loc.getWorld().getWorldBorder());
        }
    }
}
