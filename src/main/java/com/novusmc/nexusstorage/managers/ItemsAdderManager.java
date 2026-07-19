package com.novusmc.nexusstorage.managers;

import com.novusmc.nexusstorage.Main;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;

public class ItemsAdderManager {

    private final Main plugin;

    private boolean pluginPresent;
    private boolean enabled;

    private FileConfiguration iaConfig;


    public ItemsAdderManager(Main plugin) {
        this.plugin = plugin;

        saveDefaultIAConfig();
        reload();
    }



    private void saveDefaultIAConfig() {

        File file = new File(plugin.getDataFolder(), "itemsadder.yml");

        if (!file.exists()) {

            try (InputStream in = plugin.getResource("itemsadder.yml")) {

                if (in != null) {

                    file.getParentFile().mkdirs();

                    java.nio.file.Files.copy(
                            in,
                            file.toPath()
                    );
                }

            } catch (IOException e) {

                plugin.getLogger()
                        .log(Level.WARNING,
                        "Impossible de copier itemsadder.yml",
                        e);
            }
        }
    }




    public void reload() {

        File file = new File(
                plugin.getDataFolder(),
                "itemsadder.yml"
        );


        iaConfig = YamlConfiguration.loadConfiguration(file);



        this.pluginPresent =
                plugin.getServer()
                .getPluginManager()
                .getPlugin("ItemsAdder") != null;



        this.enabled =
                plugin.getConfig()
                .getBoolean(
                "integrations.itemsadder.enabled",
                false)
                && pluginPresent;



        if(enabled){

            plugin.getLogger()
                    .info(
                    "ItemsAdderManager actif."
                    );
        }
    }







    /**
     * Donne un ItemStack avec :
     * - modèle ItemsAdder
     * - NBT NexusStorage
     */
    public ItemStack resolve(String key){


        String iaId =
                iaConfig.getString(
                key + ".itemsadder",
                "default"
                );


        String fallback =
                iaConfig.getString(
                key + ".fallback",
                "STONE"
                );



        ItemStack item = null;



        // ITEMSADDER

        if(enabled
        && !iaId.equalsIgnoreCase("default")
        && !iaId.isBlank()) {


            try {


                Class<?> clazz =
                        Class.forName(
                        "dev.lone.itemsadder.api.CustomStack"
                        );



                Object customStack =
                        clazz.getMethod(
                        "getInstance",
                        String.class
                        )
                        .invoke(null, iaId);



                if(customStack != null){

                    item =
                    (ItemStack)
                    clazz.getMethod(
                    "getItemStack"
                    )
                    .invoke(customStack);

                }



            }catch(Exception e){

                plugin.getLogger()
                .warning(
                "Impossible de charger ItemsAdder : "
                + iaId
                );
            }
        }





        // FALLBACK

        if(item == null){


            try{

                item =
                new ItemStack(
                Material.valueOf(
                fallback.toUpperCase()
                ));


            }catch(Exception e){

                item =
                new ItemStack(
                Material.STONE
                );
            }
        }






        // AJOUT NBT NEXUS

        applyNexusNBT(item,key);



        return item;
    }







    private void applyNexusNBT(
            ItemStack item,
            String key){


        if(item == null)
            return;



        ItemMeta meta =
                item.getItemMeta();


        if(meta == null)
            return;




        switch(key.toLowerCase()){


            case "nexus-core":


                meta.getPersistentDataContainer()
                .set(
                plugin.getNexusCoreKey(),
                PersistentDataType.BOOLEAN,
                true
                );

                break;



            case "nexus-tablet":


                meta.getPersistentDataContainer()
                .set(
                plugin.getNexusTabletKey(),
                PersistentDataType.BOOLEAN,
                true
                );

                break;



            case "nexus-connected-block":


                meta.getPersistentDataContainer()
                .set(
                plugin.getNexusConnectedBlockKey(),
                PersistentDataType.BOOLEAN,
                true
                );

                break;




            case "nexus-chest-link":


                meta.getPersistentDataContainer()
                .set(
                plugin.getChestLinkKey(),
                PersistentDataType.BOOLEAN,
                true
                );

                break;
        }



        item.setItemMeta(meta);
    }









    /**
     * Vérifie un item dans la main
     */
    public boolean matches(
            ItemStack item,
            String key){


        if(item == null
        || !item.hasItemMeta())
            return false;



        ItemMeta meta =
                item.getItemMeta();



        return switch(key.toLowerCase()){


            case "nexus-core" ->
                    meta.getPersistentDataContainer()
                    .has(
                    plugin.getNexusCoreKey(),
                    PersistentDataType.BOOLEAN);



            case "nexus-tablet" ->
                    meta.getPersistentDataContainer()
                    .has(
                    plugin.getNexusTabletKey(),
                    PersistentDataType.BOOLEAN);



            case "nexus-connected-block" ->
                    meta.getPersistentDataContainer()
                    .has(
                    plugin.getNexusConnectedBlockKey(),
                    PersistentDataType.BOOLEAN);



            case "nexus-chest-link" ->
                    meta.getPersistentDataContainer()
                    .has(
                    plugin.getChestLinkKey(),
                    PersistentDataType.BOOLEAN);



            default -> false;
        };
    }









    public Material resolveMaterial(String key){


        String fallback =
                iaConfig.getString(
                key+".fallback",
                "STONE"
                );


        try{

            return Material.valueOf(
            fallback.toUpperCase()
            );


        }catch(Exception e){

            return Material.STONE;
        }
    }







    public boolean matches(
            String key,
            Material material){


        return resolveMaterial(key)
                == material;
    }







    public boolean isConnectedBlock(
            Material material){

        return matches(
        "nexus-connected-block",
        material);
    }




    public boolean isNexusAccessBlock(
            Material material){

        return matches(
        "nexus-block-normal",
        material)
        ||
        matches(
        "nexus-block-wifi",
        material);
    }






    public boolean isEnabled(){
        return enabled;
    }


    public boolean isPluginPresent(){
        return pluginPresent;
    }


    public FileConfiguration getIAConfig(){
        return iaConfig;
    }

}
