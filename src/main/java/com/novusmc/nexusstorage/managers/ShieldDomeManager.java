package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import com.novusmc.nexusstorage.model.EnergyGraph;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ShieldDomeManager implements Listener {

    private final Main plugin;
    private final Map<Location, Dome> domes = new HashMap<>();

    private static final int RADIUS = 100;
    private static final long ENERGY_COST = 250;

    public ShieldDomeManager(Main plugin) {
        this.plugin = plugin;

        startEnergyTask();
        startVisualTask();
    }

    /*
        Structure d'un dôme
     */
    private static class Dome {
        Location center;
        UUID network;

        Dome(Location center, UUID network) {
            this.center = center.clone();
            this.network = network;
        }
    }

    /*
        Activation via Nexus Core
     */
    public void activate(Location core, UUID network) {
        domes.put(core.clone(), new Dome(core, network));

        core.getWorld().playSound(
                core,
                Sound.BLOCK_BEACON_ACTIVATE,
                2,
                1
        );

        Bukkit.broadcastMessage(
                "§b[Nexus] §fDôme énergétique activé."
        );
    }

    public void deactivate(Location loc) {
        domes.remove(loc);

        loc.getWorld().playSound(
                loc,
                Sound.BLOCK_BEACON_DEACTIVATE,
                2,
                1
        );
    }

    /*
        Consommation énergie
     */
    private void startEnergyTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<Location, Dome>> iterator = domes.entrySet().iterator();

                while (iterator.hasNext()) {
                    Dome dome = iterator.next().getValue();

                    EnergyGraph graph = plugin.getEnergyManager().getGraphContaining(dome.center);

                    if (graph == null) {
                        iterator.remove();
                        continue;
                    }

                    boolean power = plugin.getEnergyManager().consumeEnergy(graph, ENERGY_COST);

                    if (!power) {
                        iterator.remove();
                        Bukkit.broadcastMessage(
                                "§c[Nexus] Dôme désactivé : énergie insuffisante."
                        );
                    }
                }
            }
        }.runTaskTimer(
                plugin,
                20L * 10,
                20L * 10
        );
    }

    /*
        Particules sphère
     */
    private void startVisualTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Dome dome : domes.values()) {
                    drawSphere(dome.center);

                    for (Player p : dome.center.getWorld().getPlayers()) {
                        if (isInside(p.getLocation(), dome.center)) {
                            p.spawnParticle(
                                    Particle.GLOW,
                                    p.getLocation().add(0, 1, 0),
                                    5,
                                    .3, .5, .3,
                                    0
                            );
                        }
                    }
                }
            }
        }.runTaskTimer(
                plugin,
                0,
                10
        );
    }

    private void drawSphere(Location center) {
        World world = center.getWorld();
        int points = 120;

        for (int i = 0; i < points; i++) {
            double theta = Math.random() * Math.PI * 2;
            double phi = Math.acos(2 * Math.random() - 1);

            double x = RADIUS * Math.sin(phi) * Math.cos(theta);
            double y = RADIUS * Math.cos(phi);
            double z = RADIUS * Math.sin(phi) * Math.sin(theta);

            Location particle = center.clone().add(x, y, z);

            world.spawnParticle(
                    Particle.END_ROD,
                    particle,
                    1,
                    0, 0, 0,
                    0
            );
        }
    }

    /*
        Vérification sphère
     */
    private boolean isInside(Location target, Location center) {
        if (!target.getWorld().equals(center.getWorld())) {
            return false;
        }
        return target.distanceSquared(center) <= (RADIUS * RADIUS);
    }

    public boolean isProtected(Location loc) {
        for (Dome dome : domes.values()) {
            if (isInside(loc, dome.center)) {
                return true;
            }
        }
        return false;
    }

    /*
        Anti grief
     */
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (isProtected(e.getBlock().getLocation())
                && !e.getPlayer().hasPermission("nexusstorage.dome.bypass")) {

            e.setCancelled(true);
            e.getPlayer().sendMessage("§cBouclier Nexus actif.");
        }
    }

    /*
        Anti TNT / Creeper
     */
    @EventHandler
    public void explosion(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> isProtected(b.getLocation()));
    }

    @EventHandler
    public void creeper(CreeperPowerEvent e) {
        if (isProtected(e.getEntity().getLocation())) {
            e.setCancelled(true);
        }
    }

    /*
        Protection mobs
     */
    @EventHandler
    public void entityDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;

        if (e.getDamager() instanceof Monster
                && isProtected(e.getEntity().getLocation())) {
            e.setCancelled(true);
        }
    }

    /*
        Nettoyage
     */
    public void clear() {
        domes.clear();
    }

    public Map<Location, Dome> getDomes() {
        return Collections.unmodifiableMap(domes);
    }
}
