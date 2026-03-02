package cz.yourserver.afkshop;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class GUIListener implements Listener {

    private static final String PRICE_KEY = "afkshop_price";

    private final AFKShopPlugin plugin;
    private final Map<Inventory, AfkShop> buyerInventoryToShop;
    private final Map<UUID, Integer> awaitingPriceSlotByPlayer;

    public GUIListener(AFKShopPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.buyerInventoryToShop = new HashMap<>();
        this.awaitingPriceSlotByPlayer = new HashMap<>();
    }

    public static NamespacedKey getPriceKey(JavaPlugin plugin) {
        return new NamespacedKey(plugin, PRICE_KEY);
    }

    public static double getPrice(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Double value = meta.getPersistentDataContainer().get(getPriceKey(plugin), PersistentDataType.DOUBLE);
        return value != null ? value : 0;
    }

    public static void setPrice(JavaPlugin plugin, ItemStack item, double price) {
        if (item == null || item.getType().isAir()) return;
        item.editMeta(meta -> meta.getPersistentDataContainer().set(getPriceKey(plugin), PersistentDataType.DOUBLE, price));
    }

    public static ItemStack applyPriceDisplay(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType().isAir()) return item;
        double price = getPrice(plugin, item);
        String formatted = plugin instanceof AFKShopPlugin p ? p.getEconomyManager().format(price) : String.valueOf(price);
        ItemStack copy = item.clone();
        copy.editMeta(meta -> {
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.text("Cena: " + formatted).color(NamedTextColor.GOLD));
            lore.add(Component.text("Klikni pro nákup").color(NamedTextColor.GRAY));
            meta.lore(lore);
        });
        return copy;
    }

    private boolean isPlaceholder(ItemStack item) {
        if (item == null || item.getType() != Material.GRAY_STAINED_GLASS_PANE) return false;
        if (!item.hasItemMeta()) return false;
        Component displayName = item.getItemMeta().displayName();
        if (displayName == null) return false;
        String plain = PlainTextComponentSerializer.plainText().serialize(displayName);
        return plain.contains("Vlož item");
    }

    private boolean isUIItem(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        return m == Material.BLACK_STAINED_GLASS_PANE
                || m == Material.LIME_WOOL
                || m == Material.RED_WOOL
                || m == Material.BOOK;
    }

    public void registerBuyerInventory(Inventory buyerInv, AfkShop shop) {
        buyerInventoryToShop.put(buyerInv, shop);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getInventory();
        Inventory clicked = event.getClickedInventory();
        int slot = event.getRawSlot();

        if (clicked == null) return;

        Optional<AfkShop> editorShop = plugin.getShopManager().getShopByEditorInventory(top);
        if (editorShop.isPresent()) {
            handleEditorClick(event, player, editorShop.get(), top, clicked, slot);
            return;
        }

        AfkShop buyerShop = buyerInventoryToShop.get(top);
        if (buyerShop != null) {
            handleBuyerClick(event, player, buyerShop, top, clicked, slot);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!isAwaitingPrice(player.getUniqueId())) return;
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        if (event.isAsynchronous()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> onChatPrice(player.getUniqueId(), message));
        } else {
            onChatPrice(player.getUniqueId(), message);
        }
    }

    private void handleEditorClick(InventoryClickEvent event, Player player, AfkShop shop,
                                    Inventory top, Inventory clicked, int slot) {

        // ── Bottom bar (slots 45-53): always cancel except Close button ──
        if (slot >= AfkShop.BOTTOM_BAR_START && slot < AfkShop.EDITOR_SIZE) {
            event.setCancelled(true);
            if (slot == 53) {
                // Red wool = close shop
                double earned = shop.getEarnings();
                plugin.getShopManager().closeShop(shop, true);
                player.sendMessage(plugin.getMessage("shop-stopped",
                        "amount", plugin.getEconomyManager().format(earned)));
            }
            return;
        }

        // ── Player clicking in their own bottom inventory ──
        if (clicked != top) {
            // Only block shift-click that would move item into UI bar
            if (event.isShiftClick()) {
                // Calculate where item would land - if it could land in UI bar, cancel
                // Simplest safe approach: allow shift-click from player inventory into editor
                // (it will go to first available slot 0-44 which is fine)
                // But we need to cancel if it would replace a UI item
                // Actually let Bukkit handle it - it fills from slot 0 upward
                // The UI bar slots 45-53 are filled so shift-click won't land there
                // Allow normally
            }
            return;
        }

        // ── Item area (slots 0-44) ──
        if (slot < 0 || slot >= AfkShop.EDITOR_ITEM_SLOTS) {
            event.setCancelled(true);
            return;
        }

        ItemStack current = top.getItem(slot);
        ItemStack cursor = event.getCursor();

        // Shift+click on real item → open price prompt
        if (event.isShiftClick()
                && current != null
                && !current.getType().isAir()
                && !isPlaceholder(current)) {
            event.setCancelled(true);
            awaitingPriceSlotByPlayer.put(player.getUniqueId(), slot);
            player.sendMessage(plugin.getMessage("click-to-set-price"));
            player.closeInventory();
            return;
        }

        // Shift+click on placeholder → cancel (don't take it)
        if (event.isShiftClick() && isPlaceholder(current)) {
            event.setCancelled(true);
            return;
        }

        // Player placing item from cursor into slot with placeholder → allow, remove placeholder
        if (cursor != null && !cursor.getType().isAir() && isPlaceholder(current)) {
            // Allow placement: Bukkit will swap cursor with placeholder
            // But we don't want placeholder going to player inv
            // So: manually set the slot to cursor item and clear cursor
            event.setCancelled(true);
            ItemStack toPlace = cursor.clone();
            player.setItemOnCursor(null);
            top.setItem(slot, toPlace);
            return;
        }

        // Player trying to take placeholder → cancel
        if (isPlaceholder(current)) {
            event.setCancelled(true);
            return;
        }

        // Player taking real item from slot → allow (they can take their own items back)
        // Player placing item on top of real item → allow (normal inventory behavior)
    }

    private void handleBuyerClick(InventoryClickEvent event, Player player, AfkShop shop,
                                   Inventory top, Inventory clicked, int slot) {
        event.setCancelled(true);
        if (clicked != top || slot < 0 || slot >= AfkShop.EDITOR_ITEM_SLOTS) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) return;

        double price = getPrice(plugin, item);
        if (price <= 0) return;

        if (!plugin.getEconomyManager().hasEconomy()) {
            player.sendMessage(plugin.getMessage("no-economy"));
            return;
        }
        if (!plugin.getEconomyManager().has(player.getUniqueId(), price)) {
            player.sendMessage(plugin.getMessage("not-enough-money",
                    "amount", plugin.getEconomyManager().format(price)));
            return;
        }
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), price)) {
            player.sendMessage(plugin.getMessage("not-enough-money",
                    "amount", plugin.getEconomyManager().format(price)));
            return;
        }

        shop.addEarnings(price);
        plugin.getDatabase().saveEarningsAsync(shop.getOwnerUuid(), shop.getEarnings());

        // Give item to buyer (without price meta and lore)
        ItemStack toGive = item.clone();
        toGive.setAmount(1);
        removePriceMeta(plugin, toGive);
        toGive.editMeta(meta -> {
            List<Component> lore = meta.lore();
            if (lore != null) {
                List<Component> filtered = lore.stream()
                        .filter(c -> {
                            String s = PlainTextComponentSerializer.plainText().serialize(c);
                            return !s.contains("Cena:") && !s.contains("Klikni pro nákup");
                        })
                        .toList();
                meta.lore(filtered.isEmpty() ? null : filtered);
            }
        });
        player.getInventory().addItem(toGive);

        // Update editor inventory
        ItemStack inEditor = shop.getEditorInventory().getItem(slot);
        if (inEditor != null && inEditor.getAmount() > 1) {
            inEditor.setAmount(inEditor.getAmount() - 1);
        } else {
            // Replace with placeholder in editor
            shop.getEditorInventory().setItem(slot, null);
        }

        // Update buyer inventory view
        int nowInSlot = item.getAmount() - 1;
        if (nowInSlot <= 0) {
            top.setItem(slot, null);
        } else {
            ItemStack updated = item.clone();
            updated.setAmount(nowInSlot);
            top.setItem(slot, updated);
        }

        String itemName = toGive.hasItemMeta() && toGive.getItemMeta().hasDisplayName()
                ? PlainTextComponentSerializer.plainText().serialize(toGive.getItemMeta().displayName())
                : toGive.getType().name();
        player.sendMessage(plugin.getMessage("item-purchased",
                "amount", "1",
                "item", itemName,
                "price", plugin.getEconomyManager().format(price)));
    }

    private static void removePriceMeta(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        item.editMeta(meta -> meta.getPersistentDataContainer().remove(getPriceKey(plugin)));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        buyerInventoryToShop.remove(event.getInventory());
    }

    public void onChatPrice(UUID playerUuid, String message) {
        Integer slot = awaitingPriceSlotByPlayer.remove(playerUuid);
        if (slot == null) return;
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player == null) return;
        Optional<AfkShop> shop = plugin.getShopManager().getShopByOwner(playerUuid);
        if (shop.isEmpty()) return;
        Inventory editor = shop.get().getEditorInventory();
        if (editor == null || slot < 0 || slot >= editor.getSize()) return;

        double price;
        try {
            price = Double.parseDouble(message.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getMessage("click-to-set-price"));
            player.openInventory(editor);
            return;
        }
        if (price < 0) price = 0;
        ItemStack item = editor.getItem(slot);
        if (item != null && !item.getType().isAir() && !isPlaceholder(item)) {
            setPrice(plugin, item, price);
            editor.setItem(slot, item);
            player.sendMessage("§aCena §6" + plugin.getEconomyManager().format(price) + "§a nastavena!");
        }
        player.openInventory(editor);
    }

    public boolean isAwaitingPrice(UUID playerUuid) {
        return awaitingPriceSlotByPlayer.containsKey(playerUuid);
    }
}
