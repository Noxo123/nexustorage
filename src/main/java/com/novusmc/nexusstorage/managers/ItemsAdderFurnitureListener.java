package com.novusmc.nexusstorage.listeners;

import com.novusmc.nexusstorage.Main;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class ItemsAdderFurnitureListener implements Listener {

    private final Main plugin;

    private final NamespacedKey chestLinkKey;

    public ItemsAdderFurnitureListener(Main plugin) {
        this.plugin = plugin;
        this.chestLinkKey = new NamespacedKey(plugin, "nexus-chest-link");
    }


    /**
     * Détection quand un joueur pose le meuble ItemsAdder
     */
    @EventHandler
    public void onPlace(PlayerInteractEvent event) {

        if (event.getItem() == null)
            return;

        ItemStack item = event.getItem();

        try {

            CustomStack customStack = CustomStack.byItemStack(item);

            if (customStack == null)
                return;


            String id = customStack.getNamespacedID();


            // Vérifie que c'est ton meuble
            if (!id.equalsIgnoreCase("novus:chest_link"))
                return;


            // Cherche le dernier ITEM_DISPLAY créé
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {

                Entity nearest = event.getPlayer()
                        .getWorld()
                        .getNearbyEntities(
                                event.getClickedBlock().getLocation().add(0.5,1,0.5),
                                1,
                                1,
                                1
                        )
                        .stream()
                        .filter(e -> e instanceof ItemDisplay)
                        .findFirst()
                        .orElse(null);


                if (!(nearest instanceof ItemDisplay display))
                    return;


                display.getPersistentDataContainer().set(
                        chestLinkKey,
                        PersistentDataType.BOOLEAN,
                        true
                );


                plugin.getLogger().info(
                        "Chest Link ItemsAdder enregistré : "
                        + display.getUniqueId()
                );


            }, 2L);


        } catch (Exception ignored) {
        }
    }



    /**
     * Vérification si une entité est un Nexus Chest Link
     */
    public boolean isChestLink(Entity entity) {

        return entity.getPersistentDataContainer()
                .has(chestLinkKey, PersistentDataType.BOOLEAN);
    }
}
