package cz.yourserver.afkshop;

import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class AFKShopPlugin extends JavaPlugin {

    private static final String MESSAGES_PATH = "messages.";

    public String getMessage(String key, String... replacements) {
        String path = MESSAGES_PATH + key;
        String msg = getConfig().getString(path, "&cMissing message: " + key);
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        if (replacements != null && replacements.length >= 2) {
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                msg = msg.replace("%" + replacements[i] + "%", replacements[i + 1]);
            }
        }
        return msg;
    }

    private static AFKShopPlugin instance;
    private Database database;
    private EconomyManager economyManager;
    private HologramManager hologramManager;
    private ShopManager shopManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        database = new Database(this);
        database.initialize();

        economyManager = new EconomyManager(this);
        if (!economyManager.setupEconomy()) {
            getLogger().warning("Vault/Economy not found. Economy features disabled.");
        }

        hologramManager = new HologramManager(this);
        GUIListener guiListener = new GUIListener(this);
        shopManager = new ShopManager(this, guiListener);

        var afkShopCommand = new AfkShopCommand(this);
        getCommand("afkshop").setExecutor(afkShopCommand);
        getCommand("afkshop").setTabCompleter(afkShopCommand);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getServer().getPluginManager().registerEvents(guiListener, this);
    }

    @Override
    public void onDisable() {
        if (shopManager != null) {
            shopManager.closeAllShops();
        }
        if (hologramManager != null) {
            hologramManager.removeAll();
        }
        if (database != null) {
            database.close();
        }
        instance = null;
    }

    public static AFKShopPlugin getInstance() {
        return instance;
    }

    public Database getDatabase() {
        return database;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }
}
