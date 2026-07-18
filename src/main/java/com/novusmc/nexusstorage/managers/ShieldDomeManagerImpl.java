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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShieldDomeManagerImpl implements ShieldDomeManager, Listener {
    // Reste du code inchangé...

    private final Main plugin;
    private final Map<Location, UUID> activeDomes = new HashMap<>();
    private static final int RADIUS = 100; // Rayon de 100 blocs (Zone de 200x200)

    public ShieldDomeManagerImpl(Main plugin) {
        this.plugin = plugin;
        startEnergyDrainTask();
        startDomeEffectsAndVisualsTask();
    }

    private void startEnergyDrainTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                double energyToDrain = 10000.0 / 60.0; // Consommation par minute

                activeDomes.entrySet().removeIf(entry -> {
                    Location loc = entry.getKey();
                    UUID networkId = entry.getValue();
                    NexusNetwork network = plugin.getNexusManager().getNetworkIfExists(networkId);

                    // ToDo: Relier à ton système d'énergie réel
                    boolean hasEnoughEnergy = true;

                    if (!hasEnoughEnergy || network == null) {
                        Bukkit.broadcastMessage("§c[Nexus] Un dôme de protection à la position X: " 
                                + loc.getBlockX() + " Z: " + loc.getBlockZ() + " s'est éteint faute d'énergie !");
                        
                        // Retirer la fausse worldborder aux joueurs du monde lors de l'extinction
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

    /**
     * Tâche récurrente (toutes les 2-3 secondes) qui gère les effets appliqués 
     * aux joueurs et l'affichage de la WorldBorder factice.
     */
    private void startDomeEffectsAndVisualsTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeDomes.isEmpty()) return;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location pLoc = player.getLocation();
                    Location nearestDome = null;
                    double closestDistance = Double.MAX_VALUE;

                    // 1. Recherche du dôme le plus proche dans le même monde
                    for (Location domeLoc : activeDomes.keySet()) {
                        if (!domeLoc.getWorld().equals(pLoc.getWorld())) continue;

                        double dist = pLoc.distance(domeLoc);
                        if (dist < closestDistance) {
                            closestDistance = dist;
                            nearestDome = domeLoc;
                        }
                    }

                    if (nearestDome == null) continue;

                    // 2. Vérification si le joueur est à l'intérieur des limites du dôme (Boite de collision carrée)
                    double deltaX = Math.abs(pLoc.getX() - nearestDome.getX());
                    double deltaZ = Math.abs(pLoc.getZ() - nearestDome.getZ());

                    if (deltaX <= RADIUS && deltaZ <= RADIUS) {
                        // --- EFFETS STYLE BEACON ---
                        // Durée de 8 secondes (160 ticks) pour éviter les clignotements, amplificateur 0 = Niveau 1
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 0, true, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 0, true, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 0, true, false, true));

                        // --- EFFET VISUEL : WORLDBORDER COMMUNE ---
                        // Si le joueur s'approche à moins de 15 blocs de la frontière intérieure du dôme, 
                        // on lui affiche la fausse bordure pour qu'il la voie briller en rouge/bleu.
                        if (deltaX >= (RADIUS - 15) || deltaZ >= (RADIUS - 15)) {
                            org.bukkit.WorldBorder fakeBorder = Bukkit.createWorldBorder();
                            fakeBorder.setCenter(nearestDome.getX(), nearestDome.getZ());
                            fakeBorder.setSize(RADIUS * 2.0);
                            fakeBorder.setWarningDistance(5); // Fait briller l'écran si trop près
                            
                            player.setWorldBorder(fakeBorder);
                        } else {
                            // S'il est bien au centre, on réinitialise pour ne pas encombrer sa vue
                            player.setWorldBorder(player.getWorld().getWorldBorder());
                        }
                    } else {
                        // Si le joueur vient de sortir du dôme ou est loin, on lui remet la vraie bordure du monde
                        player.setWorldBorder(player.getWorld().getWorldBorder());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // S'exécute toutes les 2 secondes (40 ticks)
    }

    @Override
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

    @Override
    public void registerDome(Location loc, UUID networkId) {
        activeDomes.put(loc, networkId);
    }

    @Override
    public void unregisterDome(Location loc) {
        activeDomes.remove(loc);
        // Clean up la bordure des joueurs dans ce monde
        for (Player p : loc.getWorld().getPlayers()) {
            p.setWorldBorder(loc.getWorld().getWorldBorder());
        }
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
}
