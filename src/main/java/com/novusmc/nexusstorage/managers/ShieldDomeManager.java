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

// CORRECTION : Le nom de la classe doit correspondre au nom du fichier "ShieldDomeManager.java"
public class ShieldDomeManager implements Listener {

    private final Main plugin;
    private final Map<Location, UUID> activeDomes = new HashMap<>();
    private final int RADIUS = 100; 

    // CORRECTION : Le constructeur doit avoir EXACTEMENT le même nom que la classe ci-dessus
    public ShieldDomeManager(Main plugin) {
        this.plugin = plugin;
        startEnergyDrainTask();
        startDomeEffectsAndVisualsTask();
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

                    boolean hasEnoughEnergy = true; 

                    if (!hasEnoughEnergy || network == null) {
                        Bukkit.broadcastMessage("§c[Nexus] Un dôme de protection à la position X: " + loc.getBlockX() + " Z: " + loc.getBlockZ() + " s'est éteint !");
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

                    if (deltaX <= RADIUS && deltaZ <= RADIUS) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 0, true, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 0, true, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 0, true, false, true));

                        if (deltaX >= (RADIUS - 15) || deltaZ >= (RADIUS - 15)) {
                            org.bukkit.WorldBorder fakeBorder = Bukkit.createWorldBorder();
                            fakeBorder.setCenter(nearestDome.getX(), nearestDome.getZ());
                            fakeBorder.setSize(RADIUS * 2.0);
                            fakeBorder.setWarningDistance(5); 
                            
                            player.setWorldBorder(fakeBorder);
                        } else {
                            player.setWorldBorder(player.getWorld().getWorldBorder());
                        }
                    } else {
                        player.setWorldBorder(player.getWorld().getWorldBorder());
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 40L);
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
