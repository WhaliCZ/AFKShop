package cz.yourserver.afkshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * Freeze owner: cancel move if in shop. Close shop on quit or block position change.
 */
public class PlayerListener implements Listener {

    private final AFKShopPlugin plugin;

    public PlayerListener(AFKShopPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Optional<AfkShop> shop = plugin.getShopManager().getShopByOwner(player.getUniqueId());
        if (shop.isEmpty()) return;

        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            event.setCancelled(true);
            return;
        }

        if (shop.get().hasMoved(to)) {
            double earned = shop.get().getEarnings();
            plugin.getShopManager().closeShop(shop.get(), true);
            player.sendMessage(plugin.getMessage("shop-stopped", "amount", plugin.getEconomyManager().format(earned)));
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Optional<AfkShop> shop = plugin.getShopManager().getShopByOwner(player.getUniqueId());
        shop.ifPresent(afkShop -> plugin.getShopManager().closeShop(afkShop, true));
    }
}
