package cz.yourserver.afkshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AfkShopCommand implements CommandExecutor, TabCompleter {

    private final AFKShopPlugin plugin;

    public AfkShopCommand(AFKShopPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        // /afkshop stop
        if (args.length > 0 && "stop".equalsIgnoreCase(args[0])) {
            Optional<AfkShop> shop = plugin.getShopManager().getShopByOwner(player.getUniqueId());
            if (shop.isEmpty()) {
                player.sendMessage(plugin.getMessage("no-shop"));
                return true;
            }
            double earned = shop.get().getEarnings();
            plugin.getShopManager().closeShop(shop.get(), true);
            player.sendMessage(plugin.getMessage("shop-stopped",
                    "amount", plugin.getEconomyManager().format(earned)));
            return true;
        }

        // /afkshop setregion pos1|pos2|info
        if (args.length >= 2 && "setregion".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("afkshop.admin")) {
                player.sendMessage(plugin.getMessage("no-permission"));
                return true;
            }
            handleSetRegion(player, args[1]);
            return true;
        }

        // /afkshop (open shop)
        if (!player.hasPermission("afkshop.use")) {
            player.sendMessage(plugin.getMessage("no-permission"));
            return true;
        }

        if (plugin.getShopManager().getShopByOwner(player.getUniqueId()).isPresent()) {
            player.sendMessage(plugin.getMessage("already-have-shop"));
            return true;
        }

        if (!plugin.getShopManager().isInAfkMarketRegion(player.getLocation())) {
            player.sendMessage(plugin.getMessage("not-in-region",
                    "region", plugin.getShopManager().getRegionName()));
            return true;
        }

        if (plugin.getShopManager().getActiveShopCount() >= plugin.getShopManager().getMaxShops()) {
            player.sendMessage(plugin.getMessage("max-shops-reached",
                    "max", String.valueOf(plugin.getShopManager().getMaxShops())));
            return true;
        }

        if (!plugin.getEconomyManager().hasEconomy()) {
            player.sendMessage(plugin.getMessage("no-economy"));
            return true;
        }

        Optional<AfkShop> created = plugin.getShopManager().createShop(player);
        if (created.isEmpty()) {
            player.sendMessage(plugin.getMessage("max-shops-reached",
                    "max", String.valueOf(plugin.getShopManager().getMaxShops())));
            return true;
        }

        player.sendMessage(plugin.getMessage("shop-created"));
        return true;
    }

    private void handleSetRegion(Player player, String sub) {
        switch (sub.toLowerCase()) {
            case "pos1" -> {
                int x = player.getLocation().getBlockX();
                int y = player.getLocation().getBlockY();
                int z = player.getLocation().getBlockZ();
                String world = player.getWorld().getName();
                plugin.getConfig().set("region.world", world);
                plugin.getConfig().set("region.x1", x);
                plugin.getConfig().set("region.y1", y);
                plugin.getConfig().set("region.z1", z);
                plugin.saveConfig();
                player.sendMessage(plugin.getMessage("region-pos1-set",
                        "x", String.valueOf(x),
                        "y", String.valueOf(y),
                        "z", String.valueOf(z)));
            }
            case "pos2" -> {
                int x = player.getLocation().getBlockX();
                int y = player.getLocation().getBlockY();
                int z = player.getLocation().getBlockZ();
                plugin.getConfig().set("region.x2", x);
                plugin.getConfig().set("region.y2", y);
                plugin.getConfig().set("region.z2", z);
                plugin.saveConfig();
                player.sendMessage(plugin.getMessage("region-pos2-set",
                        "x", String.valueOf(x),
                        "y", String.valueOf(y),
                        "z", String.valueOf(z)));
            }
            case "info" -> {
                String world = plugin.getConfig().getString("region.world", "world");
                int x1 = plugin.getConfig().getInt("region.x1", -100);
                int y1 = plugin.getConfig().getInt("region.y1", 0);
                int z1 = plugin.getConfig().getInt("region.z1", -100);
                int x2 = plugin.getConfig().getInt("region.x2", 100);
                int y2 = plugin.getConfig().getInt("region.y2", 255);
                int z2 = plugin.getConfig().getInt("region.z2", 100);
                player.sendMessage(plugin.getMessage("region-info",
                        "world", world,
                        "x1", String.valueOf(x1),
                        "y1", String.valueOf(y1),
                        "z1", String.valueOf(z1),
                        "x2", String.valueOf(x2),
                        "y2", String.valueOf(y2),
                        "z2", String.valueOf(z2)));
            }
            default -> player.sendMessage("§ePoužití: /afkshop setregion <pos1|pos2|info>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Stream.of("stop", "setregion")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && "setregion".equalsIgnoreCase(args[0])) {
            return Stream.of("pos1", "pos2", "info")
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
