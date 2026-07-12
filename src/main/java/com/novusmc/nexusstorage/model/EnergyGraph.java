package com.novusmc.nexusstorage.model;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represente un reseau physique connecte (BFS depuis un Energy Core) :
 * un ensemble de cables, sources, capacitors, interfaces, regulateurs et
 * moniteurs qui forment une seule grappe dans le monde.
 */
public class EnergyGraph {

    private final Location coreLocation;
    private UUID ownerId;

    private final List<Location> cables = new ArrayList<>();
    private final List<Location> sources = new ArrayList<>();
    private final List<Location> storages = new ArrayList<>();
    private final List<Location> interfaces = new ArrayList<>();
    private final List<Location> regulators = new ArrayList<>();
    private final List<Location> monitors = new ArrayList<>();

    private long totalCapacity;
    private long totalStored;
    private double lastProduction;
    private double lastConsumption;
    private boolean interfacesPaused;

    public EnergyGraph(Location coreLocation, UUID ownerId) {
        this.coreLocation = coreLocation;
        this.ownerId = ownerId;
    }

    public Location getCoreLocation() {
        return coreLocation;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public List<Location> getCables() {
        return cables;
    }

    public List<Location> getSources() {
        return sources;
    }

    public List<Location> getStorages() {
        return storages;
    }

    public List<Location> getInterfaces() {
        return interfaces;
    }

    public List<Location> getRegulators() {
        return regulators;
    }

    public List<Location> getMonitors() {
        return monitors;
    }

    public long getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(long totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    public long getTotalStored() {
        return totalStored;
    }

    public void setTotalStored(long totalStored) {
        this.totalStored = totalStored;
    }

    public double getLastProduction() {
        return lastProduction;
    }

    public void setLastProduction(double lastProduction) {
        this.lastProduction = lastProduction;
    }

    public double getLastConsumption() {
        return lastConsumption;
    }

    public void setLastConsumption(double lastConsumption) {
        this.lastConsumption = lastConsumption;
    }

    public boolean isInterfacesPaused() {
        return interfacesPaused;
    }

    public void setInterfacesPaused(boolean interfacesPaused) {
        this.interfacesPaused = interfacesPaused;
    }

    public double getFillPercent() {
        if (totalCapacity <= 0) return 0;
        return (totalStored * 100.0) / totalCapacity;
    }

    public int machineCount() {
        return sources.size() + storages.size() + interfaces.size() + regulators.size() + monitors.size() + 1;
    }
}
