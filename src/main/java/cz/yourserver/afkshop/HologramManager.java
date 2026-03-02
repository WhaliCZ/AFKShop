package cz.yourserver.afkshop;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates and removes holograms (ArmorStands) above a location.
 */
public class HologramManager {

    private final JavaPlugin plugin;
    private final List<ArmorStand> allStands;

    public HologramManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.allStands = new ArrayList<>();
    }

    /**
     * Create hologram lines above the given block location. Offset from config.
     * Returns list of ArmorStands (from bottom to top).
     */
    public List<ArmorStand> createHologram(Location blockLocation, String ownerName) {
        double offset = plugin.getConfig().getDouble("hologram-offset", 1.5);
        World world = blockLocation.getWorld();
        if (world == null) return List.of();

        double x = blockLocation.getBlockX() + 0.5;
        double y = blockLocation.getBlockY() + offset;
        double z = blockLocation.getBlockZ() + 0.5;

        String line1 = ownerName + "'s Shop";
        String line2 = "Click to open";

        List<ArmorStand> stands = new ArrayList<>();
        stands.add(spawnLine(world, x, y + 0.5, z, line2));
        stands.add(spawnLine(world, x, y, z, line1));
        synchronized (allStands) {
            allStands.addAll(stands);
        }
        return stands;
    }

    private ArmorStand spawnLine(World world, double x, double y, double z, String text) {
        Location loc = new Location(world, x, y, z);
        ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setPersistent(false);
        stand.setVisible(false);
        stand.setMarker(false);
        stand.setSmall(false);
        return stand;
    }

    /**
     * Remove stands from tracking (and from world if still valid). Call when a shop closes.
     */
    public void removeStands(List<ArmorStand> stands) {
        if (stands == null) return;
        synchronized (allStands) {
            for (ArmorStand s : stands) {
                if (s.isValid()) s.remove();
            }
            allStands.removeAll(stands);
        }
    }

    /**
     * Remove all hologram stands created by this manager (e.g. on disable).
     */
    public void removeAll() {
        synchronized (allStands) {
            for (ArmorStand s : allStands) {
                if (s.isValid()) s.remove();
            }
            allStands.clear();
        }
    }
}
