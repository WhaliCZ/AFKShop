package cz.yourserver.afkshop;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class AfkShop {

    public static final int EDITOR_ITEM_SLOTS = 45;
    public static final int BOTTOM_BAR_START = 45;
    public static final int EDITOR_SIZE = 54;

    private final UUID ownerUuid;
    private final String ownerName;
    private final Location shopLocation;
    private final List<ArmorStand> hologramStands;
    private ArmorStand shopStand;
    private Location ownerFreezeLocation;
    private Inventory editorInventory;
    private double earnings;

    public AfkShop(UUID ownerUuid, String ownerName, Location shopLocation,
                   List<ArmorStand> hologramStands, Location ownerFreezeLocation,
                   ArmorStand shopStand) {
        this.ownerUuid = Objects.requireNonNull(ownerUuid);
        this.ownerName = Objects.requireNonNull(ownerName);
        this.shopLocation = shopLocation != null ? shopLocation.clone() : null;
        this.hologramStands = new ArrayList<>(hologramStands);
        this.ownerFreezeLocation = ownerFreezeLocation != null ? ownerFreezeLocation.clone() : null;
        this.shopStand = shopStand;
        this.earnings = 0.0;
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public Location getShopLocation() { return shopLocation != null ? shopLocation.clone() : null; }
    public ArmorStand getShopStand() { return shopStand; }
    public void setShopStand(ArmorStand stand) { this.shopStand = stand; }

    public List<ArmorStand> getHologramStands() { return new ArrayList<>(hologramStands); }

    public boolean hasHologramStand(Entity entity) {
        return entity != null && hologramStands.contains(entity);
    }

    public Location getOwnerFreezeLocation() {
        return ownerFreezeLocation != null ? ownerFreezeLocation.clone() : null;
    }

    public void setFreezeLocation(Location loc) {
        this.ownerFreezeLocation = loc != null ? loc.clone() : null;
    }

    public Inventory getEditorInventory() { return editorInventory; }
    public void setEditorInventory(Inventory editorInventory) { this.editorInventory = editorInventory; }
    public double getEarnings() { return earnings; }
    public void addEarnings(double amount) { this.earnings += amount; }

    public boolean isOwner(Player player) {
        return player != null && ownerUuid.equals(player.getUniqueId());
    }

    public boolean hasMoved(Location current) {
        if (ownerFreezeLocation == null || current == null) return false;
        if (!Objects.equals(ownerFreezeLocation.getWorld(), current.getWorld())) return true;
        return ownerFreezeLocation.getBlockX() != current.getBlockX()
                || ownerFreezeLocation.getBlockY() != current.getBlockY()
                || ownerFreezeLocation.getBlockZ() != current.getBlockZ();
    }

    public void removeFromWorld() {
        for (ArmorStand stand : hologramStands) {
            if (stand.isValid()) stand.remove();
        }
        hologramStands.clear();
        if (shopStand != null && shopStand.isValid()) {
            shopStand.remove();
            shopStand = null;
        }
    }

    public ItemStack[] getEditorContentsCopy() {
        if (editorInventory == null) return null;
        int size = Math.min(EDITOR_ITEM_SLOTS, editorInventory.getSize());
        ItemStack[] out = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            ItemStack item = editorInventory.getItem(i);
            out[i] = item != null && !item.getType().isAir() ? item.clone() : null;
        }
        return out;
    }
}
