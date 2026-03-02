package cz.yourserver.afkshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

/**
 * Vault economy: withdraw buyer, deposit owner. Uses OfflinePlayer by UUID.
 */
public class EconomyManager {

    private final JavaPlugin plugin;
    private Economy economy;

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    public boolean setupEconomy() {
        var rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean hasEconomy() {
        return economy != null;
    }

    public double getBalance(UUID playerUuid) {
        if (economy == null) return 0;
        OfflinePlayer op = plugin.getServer().getOfflinePlayer(playerUuid);
        return economy.getBalance(op);
    }

    public boolean has(UUID playerUuid, double amount) {
        return getBalance(playerUuid) >= amount;
    }

    /**
     * Withdraw from buyer. Returns true if successful.
     */
    public boolean withdraw(UUID playerUuid, double amount) {
        if (economy == null || amount <= 0) return false;
        OfflinePlayer op = plugin.getServer().getOfflinePlayer(playerUuid);
        var result = economy.withdrawPlayer(op, amount);
        return result.transactionSuccess();
    }

    /**
     * Deposit to owner (e.g. shop owner earnings).
     */
    public boolean deposit(UUID playerUuid, double amount) {
        if (economy == null || amount <= 0) return false;
        OfflinePlayer op = plugin.getServer().getOfflinePlayer(playerUuid);
        var result = economy.depositPlayer(op, amount);
        return result.transactionSuccess();
    }

    public String format(double amount) {
        return economy != null ? economy.format(amount) : String.valueOf(amount);
    }
}
