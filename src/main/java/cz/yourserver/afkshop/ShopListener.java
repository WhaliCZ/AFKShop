package cz.yourserver.afkshop;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.Objects;
import java.util.Optional;

public class ShopListener implements Listener {

    private final AFKShopPlugin plugin;

    public ShopListener(AFKShopPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteractEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;

        Player player = event.getPlayer();

        // Check if it's a shop stand (visible ArmorStand = player model)
        Optional<AfkShop> shopByStand = plugin.getShopManager().getShopByShopStand(stand);
        if (shopByStand.isPresent()) {
            event.setCancelled(true);
            AfkShop shop = shopByStand.get();
            if (shop.isOwner(player)) {
                player.openInventory(shop.getEditorInventory());
            } else {
                plugin.getShopManager().openBuyerInventory(player, shop);
            }
            return;
        }

        // Check if it's a hologram stand
        Optional<AfkShop> shopByHolo = plugin.getShopManager().getShopByHologramStand(stand);
        if (shopByHolo.isPresent()) {
            event.setCancelled(true);
            AfkShop shop = shopByHolo.get();
            if (shop.isOwner(player)) {
                player.openInventory(shop.getEditorInventory());
            } else {
                plugin.getShopManager().openBuyerInventory(player, shop);
            }
        }
    }
}
