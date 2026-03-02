package cz.yourserver.afkshop;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ShopManager {

    private static final int INVISIBILITY_DURATION_TICKS = 20 * 60 * 60 * 24;

    private final AFKShopPlugin plugin;
    private final GUIListener guiListener;
    private final Map<UUID, AfkShop> shopByOwner;

    public ShopManager(AFKShopPlugin plugin, GUIListener guiListener) {
        this.plugin = Objects.requireNonNull(plugin);
        this.guiListener = Objects.requireNonNull(guiListener);
        this.shopByOwner = new HashMap<>();
    }

    public AFKShopPlugin getPlugin() {
        return plugin;
    }

    public int getMaxShops() {
        return plugin.getConfig().getInt("max-shops", 50);
    }

    public String getRegionName() {
        return plugin.getConfig().getString("region-name", "afk_market");
    }

    public boolean isInAfkMarketRegion(Location location) {
        if (location == null || location.getWorld() == null) return false;
        String worldName = plugin.getConfig().getString("region.world", "world");
        if (!location.getWorld().getName().equals(worldName)) return false;
        int x1 = plugin.getConfig().getInt("region.x1", -100);
        int y1 = plugin.getConfig().getInt("region.y1", 0);
        int z1 = plugin.getConfig().getInt("region.z1", -100);
        int x2 = plugin.getConfig().getInt("region.x2", 100);
        int y2 = plugin.getConfig().getInt("region.y2", 255);
        int z2 = plugin.getConfig().getInt("region.z2", 100);
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
                && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
    }

    public Optional<AfkShop> createShop(Player player) {
        if (player == null) return Optional.empty();
        UUID uuid = player.getUniqueId();
        if (shopByOwner.containsKey(uuid)) return Optional.empty();
        if (shopByOwner.size() >= getMaxShops()) return Optional.empty();

        Location loc = player.getLocation();
        if (!isInAfkMarketRegion(loc)) return Optional.empty();

        // Create hologram above player
        List<ArmorStand> stands = plugin.getHologramManager().createHologram(loc, player.getName());

        // Spawn visible ArmorStand as player model (player head as helmet)
        ArmorStand shopStand = spawnShopStand(player);

        // Make player invisible
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, INVISIBILITY_DURATION_TICKS, 1, false, false, false));

        AfkShop shop = new AfkShop(uuid, player.getName(), loc.clone(), stands, null, shopStand);
        shopByOwner.put(uuid, shop);

        // Set freeze location after 5 ticks (player has settled)
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                shop.setFreezeLocation(player.getLocation().clone()), 5L);

        Inventory inv = buildEditorInventory();
        shop.setEditorInventory(inv);

        // Open inventory after 1 tick
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                player.openInventory(inv), 1L);

        return Optional.of(shop);
    }

    private ArmorStand spawnShopStand(Player player) {
        Location loc = player.getLocation().clone();
        ArmorStand stand = player.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setPersistent(false);
            as.setVisible(true);
            as.setSmall(false);
            as.setMarker(false);
            // Set player head as helmet
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
            as.setHelmet(head);
        });
        return stand;
    }

    public Inventory buildEditorInventory() {
        Inventory inv = plugin.getServer().createInventory(null, AfkShop.EDITOR_SIZE,
                plugin.getMessage("shop-editor-title"));

        // Fill item slots 0-44 with placeholder glass panes
        ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        placeholder.editMeta(meta -> {
            meta.displayName(Component.text("§7Vlož item"));
            meta.lore(List.of(Component.text("§7Přetáhni sem item z inventáře"),
                    Component.text("§7Shift+klik = nastav cenu")));
        });
        for (int i = 0; i < AfkShop.EDITOR_ITEM_SLOTS; i++) {
            inv.setItem(i, placeholder.clone());
        }

        // Slot 45 - Confirm / open shop (green wool)
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        confirm.editMeta(meta -> {
            meta.displayName(Component.text("§a✔ Obchod je otevřen"));
            meta.lore(List.of(Component.text("§7Obchod je aktivní"),
                    Component.text("§7/afkshop stop = ukončit")));
        });
        inv.setItem(45, confirm);

        // Slot 47 - Spacer
        ItemStack spacer = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        spacer.editMeta(meta -> meta.displayName(Component.text(" ")));
        for (int i = 46; i <= 48; i++) inv.setItem(i, spacer.clone());
        for (int i = 50; i <= 52; i++) inv.setItem(i, spacer.clone());

        // Slot 49 - Help (book)
        ItemStack help = new ItemStack(Material.BOOK);
        help.editMeta(meta -> {
            meta.displayName(Component.text("§eNápověda"));
            meta.lore(List.of(
                    Component.text("§71. Přetáhni item do slotu"),
                    Component.text("§72. Shift+klik na item = nastav cenu"),
                    Component.text("§73. Zákazníci kliknou na tvůj hologram"),
                    Component.text("§7"),
                    Component.text("§c/afkshop stop §7= ukončit obchod")
            ));
        });
        inv.setItem(49, help);

        // Slot 53 - Close shop (red wool)
        ItemStack closeBtn = new ItemStack(Material.RED_WOOL);
        closeBtn.editMeta(meta -> {
            meta.displayName(Component.text("§c✖ Zavřít obchod"));
            meta.lore(List.of(Component.text("§7Ukončí obchod a vyplatí výdělek")));
        });
        inv.setItem(53, closeBtn);

        return inv;
    }

    public Optional<AfkShop> getShopByOwner(UUID ownerUuid) {
        return Optional.ofNullable(shopByOwner.get(ownerUuid));
    }

    public Optional<AfkShop> getShopByHologramStand(Entity entity) {
        if (entity == null) return Optional.empty();
        for (AfkShop shop : shopByOwner.values()) {
            if (shop.hasHologramStand(entity)) return Optional.of(shop);
        }
        return Optional.empty();
    }

    public Optional<AfkShop> getShopByShopStand(Entity entity) {
        if (entity == null) return Optional.empty();
        for (AfkShop shop : shopByOwner.values()) {
            if (shop.getShopStand() != null && shop.getShopStand().equals(entity)) return Optional.of(shop);
        }
        return Optional.empty();
    }

    public Optional<AfkShop> getShopByEditorInventory(Inventory inv) {
        if (inv == null) return Optional.empty();
        for (AfkShop shop : shopByOwner.values()) {
            if (shop.getEditorInventory() == inv) return Optional.of(shop);
        }
        return Optional.empty();
    }

    public void closeShop(AfkShop shop, boolean payEarnings) {
        if (shop == null) return;
        UUID ownerUuid = shop.getOwnerUuid();

        // Remove invisibility from owner
        Player owner = plugin.getServer().getPlayer(ownerUuid);
        if (owner != null && owner.isOnline()) {
            owner.removePotionEffect(PotionEffectType.INVISIBILITY);
        }

        // Remove holograms and shop stand
        List<ArmorStand> stands = shop.getHologramStands();
        shop.removeFromWorld();
        plugin.getHologramManager().removeStands(stands);

        // Close editor for all viewers
        if (shop.getEditorInventory() != null && !shop.getEditorInventory().getViewers().isEmpty()) {
            new ArrayList<>(shop.getEditorInventory().getViewers()).forEach(h -> {
                if (h instanceof Player p) p.closeInventory();
            });
        }

        shopByOwner.remove(ownerUuid);

        if (payEarnings && shop.getEarnings() > 0) {
            plugin.getEconomyManager().deposit(ownerUuid, shop.getEarnings());
            plugin.getDatabase().deleteEarnings(ownerUuid);
        } else if (shop.getEarnings() > 0) {
            plugin.getDatabase().saveEarningsAsync(ownerUuid, shop.getEarnings());
        }
    }

    public void closeAllShops() {
        for (AfkShop shop : shopByOwner.values().toArray(new AfkShop[0])) {
            closeShop(shop, true);
        }
    }

    public int getActiveShopCount() {
        return shopByOwner.size();
    }

    public Inventory buildBuyerInventory(AfkShop shop) {
        if (shop == null || shop.getEditorInventory() == null) return null;
        Inventory inv = plugin.getServer().createInventory(null, AfkShop.EDITOR_SIZE,
                plugin.getMessage("shop-buyer-title", "name", shop.getOwnerName()));
        ItemStack[] contents = shop.getEditorContentsCopy();
        if (contents != null) {
            for (int i = 0; i < contents.length && i < AfkShop.EDITOR_ITEM_SLOTS; i++) {
                ItemStack item = contents[i];
                if (item != null && !item.getType().isAir() && item.getType() != Material.GRAY_STAINED_GLASS_PANE) {
                    inv.setItem(i, GUIListener.applyPriceDisplay(plugin, item.clone()));
                }
            }
        }
        return inv;
    }

    public void openBuyerInventory(Player player, AfkShop shop) {
        Inventory buyerInv = buildBuyerInventory(shop);
        if (buyerInv != null) {
            guiListener.registerBuyerInventory(buyerInv, shop);
            player.openInventory(buyerInv);
        }
    }
}
