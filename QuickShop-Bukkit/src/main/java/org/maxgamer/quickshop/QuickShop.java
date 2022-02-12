/*
 * This file is a part of project QuickShop, the name is QuickShop.java
 *  Copyright (C) PotatoCraft Studio and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.maxgamer.quickshop;

import cc.carm.lib.easysql.EasySQL;
import cc.carm.lib.easysql.api.SQLManager;
import cc.carm.lib.easysql.hikari.HikariConfig;
import com.ghostchu.simplereloadlib.ReloadManager;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import de.leonhard.storage.LightningBuilder;
import de.leonhard.storage.Yaml;
import de.leonhard.storage.internal.settings.ConfigSettings;
import de.leonhard.storage.internal.settings.ReloadSettings;
import io.papermc.lib.PaperLib;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.Setter;
import me.minebuilders.clearlag.Clearlag;
import me.minebuilders.clearlag.listeners.ItemMergeListener;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.h2.Driver;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.maxgamer.quickshop.api.QuickShopAPI;
import org.maxgamer.quickshop.api.command.CommandManager;
import org.maxgamer.quickshop.api.compatibility.CompatibilityManager;
import org.maxgamer.quickshop.api.database.DatabaseHelper;
import org.maxgamer.quickshop.api.economy.AbstractEconomy;
import org.maxgamer.quickshop.api.economy.EconomyType;
import org.maxgamer.quickshop.api.event.QSConfigurationReloadEvent;
import org.maxgamer.quickshop.api.inventory.InventoryWrapperManager;
import org.maxgamer.quickshop.api.localization.text.TextManager;
import org.maxgamer.quickshop.api.shop.*;
import org.maxgamer.quickshop.command.SimpleCommandManager;
import org.maxgamer.quickshop.converter.HikariConverter;
import org.maxgamer.quickshop.database.SimpleDatabaseHelper;
import org.maxgamer.quickshop.economy.Economy_GemsEconomy;
import org.maxgamer.quickshop.economy.Economy_TNE;
import org.maxgamer.quickshop.economy.Economy_Vault;
import org.maxgamer.quickshop.inventory.InventoryWrapperRegistry;
import org.maxgamer.quickshop.listener.*;
import org.maxgamer.quickshop.listener.worldedit.WorldEditAdapter;
import org.maxgamer.quickshop.localization.text.SimpleTextManager;
import org.maxgamer.quickshop.metric.MetricListener;
import org.maxgamer.quickshop.nonquickshopstuff.com.rylinaux.plugman.util.PluginUtil;
import org.maxgamer.quickshop.papi.QuickShopPAPI;
import org.maxgamer.quickshop.permission.PermissionManager;
import org.maxgamer.quickshop.platform.Platform;
import org.maxgamer.quickshop.platform.paper.PaperPlatform;
import org.maxgamer.quickshop.platform.spigot.SpigotPlatform;
import org.maxgamer.quickshop.shop.*;
import org.maxgamer.quickshop.shop.inventory.BukkitInventoryWrapperManager;
import org.maxgamer.quickshop.util.Timer;
import org.maxgamer.quickshop.util.*;
import org.maxgamer.quickshop.util.compatibility.SimpleCompatibilityManager;
import org.maxgamer.quickshop.util.config.ConfigCommentUpdater;
import org.maxgamer.quickshop.util.config.ConfigProvider;
import org.maxgamer.quickshop.util.config.ConfigurationFixer;
import org.maxgamer.quickshop.util.envcheck.*;
import org.maxgamer.quickshop.util.matcher.item.BukkitItemMatcherImpl;
import org.maxgamer.quickshop.util.matcher.item.QuickShopItemMatcherImpl;
import org.maxgamer.quickshop.util.reporter.error.RollbarErrorReporter;
import org.maxgamer.quickshop.watcher.*;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;

public class QuickShop extends JavaPlugin implements QuickShopAPI {
    /**
     * The active instance of QuickShop
     * You shouldn't use this if you really need it.
     */
    @Deprecated
    private static QuickShop instance;
    /**
     * The manager to check permissions.
     */
    private static PermissionManager permissionManager;
    private static boolean loaded = false;
    /**
     * If running environment test
     */
    @Getter
    private static volatile boolean testing = false;
    /* Public QuickShop API */
    private final SimpleCompatibilityManager compatibilityTool = new SimpleCompatibilityManager(this);
    private final Map<String, Integer> limits = new HashMap<>(15);
    /**
     * The shop limites.
     */
    private final ConfigProvider configProvider = new ConfigProvider(this, new File(getDataFolder(), "config.yml"));
    private final List<BukkitTask> timerTaskList = new ArrayList<>(3);
    @Getter
    private final ReloadManager reloadManager = new ReloadManager();
    /* Public QuickShop API End */
    boolean onLoadCalled = false;
    private GameVersion gameVersion;
    private SimpleDatabaseHelper databaseHelper;
    private SimpleCommandManager commandManager;
    private ItemMatcher itemMatcher;
    private SimpleShopManager shopManager;
    private SimpleTextManager textManager;
    private boolean priceChangeRequiresFee = false;
    private final InventoryWrapperRegistry inventoryWrapperRegistry = new InventoryWrapperRegistry(this);
    @Getter
    private final InventoryWrapperManager inventoryWrapperManager = new BukkitInventoryWrapperManager();

    /**
     * The BootError, if it not NULL, plugin will stop loading and show setted errors when use /qs
     */
    @Nullable
    @Getter
    @Setter
    private BootError bootError;
    /**
     * Default database prefix, can overwrite by config
     */
    @Getter
    private String dbPrefix = "";
    /**
     * Whether we should use display items or not
     */
    private boolean display = true;
    @Getter
    private int displayItemCheckTicks;
    /**
     * The economy we hook into for transactions
     */
    @Getter
    private AbstractEconomy economy;
    /**
     * Whether or not to limit players shop amounts
     */
    private boolean limit = false;
    @Nullable
    @Getter
    private LogWatcher logWatcher;
    /**
     * bStats, good helper for metrics.
     */
    private Metrics metrics;
    /**
     * The plugin OpenInv (null if not present)
     */
    @Getter
    private Plugin openInvPlugin;
    /**
     * The plugin PlaceHolderAPI(null if not present)
     */
    @Getter
    private Plugin placeHolderAPI;
    /**
     * A util to call to check some actions permission
     */
    @Getter
    private PermissionChecker permissionChecker;
    /**
     * The error reporter to help devs report errors to Sentry.io
     */
    @Getter
    private RollbarErrorReporter sentryErrorReporter;
    /**
     * The server UniqueID, use to the ErrorReporter
     */
    @Getter
    private UUID serverUniqueID;
    private boolean setupDBonEnableding = false;
    /**
     * Rewrited shoploader, more faster.
     */
    @Getter
    private ShopLoader shopLoader;
    @Getter
    private DisplayAutoDespawnWatcher displayAutoDespawnWatcher;
    @Getter
    private OngoingFeeWatcher ongoingFeeWatcher;
    @Getter
    private SignUpdateWatcher signUpdateWatcher;
    @Getter
    private ShopContainerWatcher shopContainerWatcher;
    @Getter
    private @Deprecated
    DisplayDupeRemoverWatcher displayDupeRemoverWatcher;
    @Getter
    @Deprecated
    private boolean enabledAsyncDisplayDespawn;
    @Getter
    private Plugin blockHubPlugin;
    @Getter
    private Plugin lwcPlugin;
    @Getter
    private Cache shopCache;
    @Getter
    private boolean allowStack;
    @Getter
    private EnvironmentChecker environmentChecker;
    @Getter
    @Nullable
    private UpdateWatcher updateWatcher;
    @Getter
    private BuildInfo buildInfo;
    @Getter
    @Nullable
    private String currency = null;
    @Getter
    private CalendarWatcher calendarWatcher;
    @Getter
    private Plugin worldEditPlugin;
    @Getter
    private WorldEditAdapter worldEditAdapter;
    @Getter
    private ShopPurger shopPurger;
    private int loggingLocation = 0;
    @Getter
    private InteractionController interactionController;
    @Getter
    private SQLManager sqlManager;
    @Nullable
    private QuickShopPAPI quickShopPAPI;
    @Deprecated
    private Yaml configurationForCompatibility = null;
    @Getter
    private Platform platform;
    private BukkitAudiences audience;
    @Getter
    private ShopControlPanelManager shopControlPanelManager = new SimpleShopControlPanelManager(this);
    /**
     * Use for mock bukkit
     */
    public QuickShop() {
        super();
    }

    /**
     * Use for mock bukkit
     */
    protected QuickShop(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
        super(loader, description, dataFolder, file);
    }

    @NotNull
    public static QuickShop getInstance() {
        return instance;
    }

    @NotNull
    @Override
    public InventoryWrapperRegistry getInventoryWrapperRegistry() {
        return inventoryWrapperRegistry;
    }

    /**
     * Returns QS version, this method only exist on QuickShop forks If running other QuickShop forks,, result
     * may not is "Reremake x.x.x" If running QS official, Will throw exception.
     *
     * @return Plugin Version
     */
    public static String getVersion() {
        return QuickShop.instance.getDescription().getVersion();
    }

    /**
     * Get the permissionManager as static
     *
     * @return the permission Manager.
     */
    public static PermissionManager getPermissionManager() {
        return permissionManager;
    }

    /**
     * Return the QuickShop fork name.
     *
     * @return The fork name.
     */
    public static String getFork() {
        return "Hikari";
    }

    /**
     * Get the Player's Shop limit.
     *
     * @param p The player you want get limit.
     * @return int Player's shop limit
     */
    public int getShopLimit(@NotNull Player p) {
        int max = getConfig().getInt("limits.default");
        for (Entry<String, Integer> entry : limits.entrySet()) {
            if (entry.getValue() > max && getPermissionManager().hasPermission(p, entry.getKey())) {
                max = entry.getValue();
            }
        }
        return max;
    }

    /**
     * Load 3rdParty plugin support module.
     */
    private void load3rdParty() {
        // added for compatibility reasons with OpenInv - see
        // https://github.com/KaiKikuchi/QuickShop/issues/139
        if (getConfig().getBoolean("plugin.OpenInv")) {
            this.openInvPlugin = Bukkit.getPluginManager().getPlugin("OpenInv");
            if (this.openInvPlugin != null) {
                getLogger().info("Successfully loaded OpenInv support!");
            }
        }
        if (getConfig().getBoolean("plugin.PlaceHolderAPI")) {
            this.placeHolderAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (this.placeHolderAPI != null) {
                this.quickShopPAPI = new QuickShopPAPI();
                this.quickShopPAPI.register();
                getLogger().info("Successfully loaded PlaceHolderAPI support!");
            }
        }
        if (getConfig().getBoolean("plugin.BlockHub")) {
            this.blockHubPlugin = Bukkit.getPluginManager().getPlugin("BlockHub");
            if (this.blockHubPlugin != null) {
                getLogger().info("Successfully loaded BlockHub support!");
            }
        }
        if (getConfig().getBoolean("plugin.WorldEdit")) {
            //  GameVersion gameVersion = GameVersion.get(nmsVersion);
            this.worldEditPlugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
            if (this.worldEditPlugin != null) {
                this.worldEditAdapter = new WorldEditAdapter(this, (WorldEditPlugin) this.worldEditPlugin);
                this.worldEditAdapter.register();
                getLogger().info("Successfully loaded WorldEdit support!");
            }
        }

        if (getConfig().getBoolean("plugin.LWC")) {
            this.lwcPlugin = Bukkit.getPluginManager().getPlugin("LWC");
            if (this.lwcPlugin != null) {
                if (Util.isMethodAvailable("com.griefcraft.lwc.LWC", "findProtection", org.bukkit.Location.class)) {
                    getLogger().info("Successfully loaded LWC support!");
                } else {
                    getLogger().warning("Unsupported LWC version, please make sure you are using the modern version of LWC!");
                    this.lwcPlugin = null;
                }
            }
        }

        Bukkit.getPluginManager().registerEvents(this.compatibilityTool, this);
        compatibilityTool.searchAndRegisterPlugins();
        if (this.display) {
            //VirtualItem support
            if (AbstractDisplayItem.getNowUsing() == DisplayType.VIRTUALITEM) {
                getLogger().info("Using Virtual Item display, loading ProtocolLib support...");
                Plugin protocolLibPlugin = Bukkit.getPluginManager().getPlugin("ProtocolLib");
                if (protocolLibPlugin != null && protocolLibPlugin.isEnabled()) {
                    getLogger().info("Successfully loaded ProtocolLib support!");
                } else {
                    getLogger().warning("Failed to load ProtocolLib support, fallback to real item display");
                    getConfig().set("shop.display-type", 0);
                    saveConfiguration();
                }
            }
            if (AbstractDisplayItem.getNowUsing() == DisplayType.REALITEM) {
                getLogger().warning("You're using Real Display system and that may cause your server lagg, switch to Virtual Display system if you can!");
                if (Bukkit.getPluginManager().getPlugin("ClearLag") != null) {
                    try {
                        Clearlag clearlag = (Clearlag) Bukkit.getPluginManager().getPlugin("ClearLag");
                        for (RegisteredListener clearLagListener : ItemSpawnEvent.getHandlerList().getRegisteredListeners()) {
                            if (!clearLagListener.getPlugin().equals(clearlag)) {
                                continue;
                            }
                            if (clearLagListener.getListener().getClass().equals(ItemMergeListener.class)) {
                                ItemSpawnEvent.getHandlerList().unregister(clearLagListener.getListener());
                                getLogger().warning("+++++++++++++++++++++++++++++++++++++++++++");
                                getLogger().severe("Detected incompatible module of ClearLag-ItemMerge module, it will broken the QuickShop display, we already unregister this module listener!");
                                getLogger().severe("Please turn off it in the ClearLag config.yml or turn off the QuickShop display feature!");
                                getLogger().severe("If you didn't do that, this message will keep spam in your console every times you server boot up!");
                                getLogger().warning("+++++++++++++++++++++++++++++++++++++++++++");
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    // /**
//     * Logs the given string to qs.log, if QuickShop is configured to do so.
//     *
//     * @param s The string to log. It will be prefixed with the date and time.
//     */
//    public void log(@NotNull String s) {
//        Util.debugLog("[SHOP LOG] " + s);
//        if (this.getLogWatcher() == null) {
//            return;
//        }
//        this.getLogWatcher().log(s);
//    }

    public void logEvent(@NotNull Object eventObject) {
        if (this.getLogWatcher() == null) {
            return;
        }
        if (loggingLocation == 0) {
            this.getLogWatcher().log(JsonUtil.getGson().toJson(eventObject));
        } else {
            getDatabaseHelper().insertHistoryRecord(eventObject);
        }

    }

    /**
     * Tries to load the economy and its core. If this fails, it will try to use vault. If that fails,
     * it will return false.
     *
     * @return true if successful, false if the core is invalid or is not found, and vault cannot be
     * used.
     */

    public boolean loadEcon() {
        try {
            // EconomyCore core = new Economy_Vault();
            switch (EconomyType.fromID(getConfig().getInt("economy-type"))) {
                case UNKNOWN -> {
                    setupBootError(new BootError(this.getLogger(), "Can't load the Economy provider, invaild value in config.yml."), true);
                    return false;
                }
                case VAULT -> {
                    economy = new Economy_Vault(this);
                    Util.debugLog("Now using the Vault economy system.");
                    if (getConfig().getDouble("tax", 0.0d) > 0) {
                        try {
                            String taxAccount = getConfig().getString("tax-account", "tax");
                            if (!taxAccount.isEmpty()) {
                                OfflinePlayer tax;
                                if (Util.isUUID(taxAccount)) {
                                    tax = PlayerFinder.findOfflinePlayerByUUID(UUID.fromString(taxAccount));
                                } else {
                                    tax = PlayerFinder.findOfflinePlayerByName((Objects.requireNonNull(taxAccount)));
                                }
                                Economy_Vault vault = (Economy_Vault) economy;
                                if (vault.isValid()) {
                                    if (!Objects.requireNonNull(vault.getVault()).hasAccount(tax)) {
                                        try {
                                            Util.debugLog("Tax account not exists! Creating...");
                                            getLogger().warning("QuickShop detected tax account not exists, we're trying to create one. If you see any errors, please change tax-account in config.yml to server owner in-game username");
                                            if (vault.getVault().createPlayerAccount(tax)) {
                                                getLogger().info("Tax account created.");
                                            } else {
                                                getLogger().warning("Cannot to create tax-account,  please change tax-account in config.yml to server owner in-game username");
                                            }
                                        } catch (Exception ignored) {
                                        }
                                        if (!vault.getVault().hasAccount(tax)) {
                                            getLogger().warning("Tax account's player never played this server before and failed to create one, that may cause server lagg or economy system error, you should change that name. But if this warning not cause any issues, you can safety ignore this.");
                                        }
                                    }

                                }
                            }
                        } catch (Exception ignored) {
                            Util.debugLog("Failed to fix account issue.");
                        }
                    }
                }
                case GEMS_ECONOMY -> {
                    economy = new Economy_GemsEconomy(this);
                    Util.debugLog("Now using the GemsEconomy economy system.");
                }
                case TNE -> {
                    economy = new Economy_TNE(this);
                    Util.debugLog("Now using the TNE economy system.");
                }
                default -> Util.debugLog("No any economy provider selected.");
            }
            if (economy == null) {
                return false;
            }
            if (!economy.isValid()) {
                setupBootError(BuiltInSolution.econError(), false);
                return false;
            }
            economy = ServiceInjector.getEconomy(economy);
        } catch (Throwable e) {
            this.getSentryErrorReporter().ignoreThrow();
            getLogger().log(Level.WARNING, "Something going wrong when loading up economy system", e);
            getLogger().severe("QuickShop could not hook into a economy/Not found Vault or Reserve!");
            getLogger().severe("QuickShop CANNOT start!");
            setupBootError(BuiltInSolution.econError(), false);
            getLogger().severe("Plugin listeners was disabled, please fix the economy issue.");
            return false;
        }
        return true;
    }

    @Override
    public @NotNull FileConfiguration getConfig() throws UnsupportedOperationException {
        return configProvider.get();
    }

    @Deprecated
    public @NotNull Yaml getConfiguration() {
        return configurationForCompatibility == null ? configurationForCompatibility = LightningBuilder
                .fromFile(new File(getDataFolder(), "config.yml"))
                .addInputStreamFromResource("config.yml")
                .setReloadSettings(ReloadSettings.MANUALLY)
                .setConfigSettings(ConfigSettings.PRESERVE_COMMENTS)
                .createYaml() : configurationForCompatibility;
    }

    @ApiStatus.ScheduledForRemoval
    @Override
    @Deprecated
    public void saveConfig() {
        this.saveConfiguration();
        //configProvider.save();
    }

    public void saveConfiguration() {
        this.configProvider.save();
    }


    /**
     * Reloads QuickShops config
     */
    @Override
    @Deprecated
    public void reloadConfig() {
        this.reloadConfiguration();
    }

    public void reloadConfiguration() {
        configProvider.reload();
        // Load quick variables
        this.display = this.getConfig().getBoolean("shop.display-items");
        this.priceChangeRequiresFee = this.getConfig().getBoolean("shop.price-change-requires-fee");
        this.displayItemCheckTicks = this.getConfig().getInt("shop.display-items-check-ticks");
        this.allowStack = this.getConfig().getBoolean("shop.allow-stacks");
        this.currency = this.getConfig().getString("currency");
        this.loggingLocation = this.getConfig().getInt("logging.location");
        if (StringUtils.isEmpty(this.currency)) {
            this.currency = null;
        }
        if (this.getConfig().getBoolean("logging.enable")) {
            logWatcher = new LogWatcher(this, new File(getDataFolder(), "qs.log"));
        } else {
            logWatcher = null;
        }
        Bukkit.getPluginManager().callEvent(new QSConfigurationReloadEvent(this));
    }

    /**
     * Early than onEnable, make sure instance was loaded in first time.
     */
    @Override
    public final void onLoad() {
        instance = this;
        Util.setPlugin(this);
        this.onLoadCalled = true;
        getLogger().info("QuickShop " + getFork() + " - Early boot step - Booting up");
        //BEWARE THESE ONLY RUN ONCE
        this.buildInfo = new BuildInfo(getResource("BUILDINFO"));
        runtimeCheck(EnvCheckEntry.Stage.ON_LOAD);
        getLogger().info("Reading the configuration...");
        this.initConfiguration();
        this.bootError = null;
        getLogger().info("Initialing Unirest http request library...");
        Unirest.config()
                .followRedirects(true)
                .cacheResponses(true)
                .socketTimeout(10 * 1000)
                .connectTimeout(30 * 1000)
                .concurrency(10, 5)
                .setDefaultHeader("User-Agent", "Java QuickShop-" + getFork() + "/" + getDescription().getVersion())
                .followRedirects(true)
                .enableCookieManagement(true)
                .automaticRetries(true);
        getLogger().info("Loading messages translation over-the-air (this may need take a while).");
        this.textManager = new SimpleTextManager(this);
        textManager.load();
        getLogger().info("Registering InventoryWrapper...");
        this.inventoryWrapperRegistry.register(this,this.inventoryWrapperManager);
        getLogger().info("Loading up integration modules.");
        if (PaperLib.isPaper()) {
            this.platform = new PaperPlatform();
        } else if (PaperLib.isSpigot()) {
            this.platform = new SpigotPlatform();
        } else {
            throw new UnsupportedOperationException("Unsupported platform");
        }
        getLogger().info("QuickShop " + getFork() + " - Early boot step - Complete");
    }

    @Override
    public final void onDisable() {
        if (!this.platform.isServerStopping()) {
            getLogger().log(Level.WARNING, "/reload command is unsupported, don't expect any support from QuickShop support team after you execute this command.", new IllegalStateException("/reload command is unsupported, restart your server!"));
        }
        getLogger().info("QuickShop is finishing remaining work, this may need a while...");
        if (sentryErrorReporter != null) {
            getLogger().info("Shutting down error reporter...");
            sentryErrorReporter.unregister();
        }
        if (this.quickShopPAPI != null) {
            getLogger().info("Unregistering PlaceHolderAPI hooks...");
            this.quickShopPAPI.unregister();
        }
        try {
            if (getShopManager() != null) {
                getLogger().info("Unloading all loaded shops...");
                getShopManager().getLoadedShops().forEach(Shop::onUnload);
            }
        } catch (Exception ignored) {
        }
        if (worldEditAdapter != null) {
            getLogger().info("Unregistering adapters...");
            worldEditAdapter.unregister();
        }
        getLogger().info("Unregistering compatibility hooks...");
        compatibilityTool.unregisterAll();
        /* Remove all display items, and any dupes we can find */
        if (shopManager != null) {
            Util.debugLog("Cleaning up shop manager...");
            shopManager.clear();
        }
        if (AbstractDisplayItem.getNowUsing() == DisplayType.VIRTUALITEM) {
            getLogger().info("Cleaning up display manager...");
            VirtualDisplayItem.VirtualDisplayItemManager.unload();
        }
        if (this.getSqlManager() != null) {
            getLogger().info("Shutting down database connections...");
            EasySQL.shutdownManager(this.getSqlManager());
        }
        if (logWatcher != null) {
            getLogger().info("Stopping log watcher...");
            logWatcher.close();
        }
        getLogger().info("Shutting down scheduled timers...");
        Iterator<BukkitTask> taskIterator = timerTaskList.iterator();
        while (taskIterator.hasNext()) {
            BukkitTask task = taskIterator.next();
            if (!task.isCancelled()) {
                task.cancel();
            }
            taskIterator.remove();
        }
        if (calendarWatcher != null) {
            getLogger().info("Shutting down event calendar watcher...");
            calendarWatcher.stop();
        }
        /* Unload UpdateWatcher */
        if (this.updateWatcher != null) {
            getLogger().info("Shutting down update watcher...");
            this.updateWatcher.uninit();
        }
        getLogger().info("Cleanup scheduled tasks...");
        try {
            Bukkit.getScheduler().cancelTasks(this);
        } catch (Throwable ignored) {
        }
        getLogger().info("Cleanup listeners...");
        HandlerList.unregisterAll(this);
        getLogger().info("Unregistering plugin services...");
        getServer().getServicesManager().unregisterAll(this);
        getLogger().info("Shutting down Unirest instances...");
        Unirest.shutDown(true);
        getLogger().info("Finishing remains misc works...");
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this, "BungeeCord");
        getLogger().info("All shutdown work is finished.");
    }

    public void reload() {
        PluginManager pluginManager = getServer().getPluginManager();
        try {
            File file = Paths.get(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).toFile();
            //When quickshop was updated, we need to stop reloading
            if (getUpdateWatcher() != null) {
                File updatedJar = getUpdateWatcher().getUpdater().getUpdatedJar();
                if (updatedJar != null) {
                    throw new IllegalStateException("Failed to reload QuickShop! Please consider restarting the server. (Plugin was updated)");
                }
            }
            if (!file.exists()) {
                throw new IllegalStateException("Failed to reload QuickShop! Please consider restarting the server. (Failed to find plugin jar)");
            }
            Throwable throwable = PluginUtil.unload(this);
            if (throwable != null) {
                throw new IllegalStateException("Failed to reload QuickShop! Please consider restarting the server. (Plugin unloading has failed)", throwable);
            }
            Plugin plugin = pluginManager.loadPlugin(file);
            if (plugin != null) {
                plugin.onLoad();
                pluginManager.enablePlugin(plugin);
            } else {
                throw new IllegalStateException("Failed to reload QuickShop! Please consider restarting the server. (Plugin loading has failed)");
            }
        } catch (URISyntaxException | InvalidDescriptionException | InvalidPluginException e) {
            throw new RuntimeException("Failed to reload QuickShop! Please consider restarting the server.", e);
        }
    }

    private void initConfiguration() {
        /* Process the config */
        //noinspection ResultOfMethodCallIgnored
        getDataFolder().mkdirs();
        try {
            saveDefaultConfig();
        } catch (IllegalArgumentException resourceNotFoundException) {
            getLogger().severe("Failed to save config.yml from jar, The binary file of QuickShop may corrupted. Please re-download from our website.");
        }
        reloadConfiguration();
        if (getConfig().getInt("config-version", 0) == 0) {
            getConfig().set("config-version", 1);
        }
        /* It will generate a new UUID above updateConfig */
        this.serverUniqueID = UUID.fromString(Objects.requireNonNull(getConfig().getString("server-uuid", String.valueOf(UUID.randomUUID()))));
        try {
            updateConfig(getConfig().getInt("config-version"));
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Failed to update configuration", exception);
        }
    }

    private void runtimeCheck(@NotNull EnvCheckEntry.Stage stage) {
        testing = true;
        environmentChecker = new EnvironmentChecker(this);
        ResultReport resultReport = environmentChecker.run(stage);
        StringJoiner joiner = new StringJoiner("\n", "", "");
        if (resultReport.getFinalResult().ordinal() > CheckResult.WARNING.ordinal()) {
            for (Entry<EnvCheckEntry, ResultContainer> result : resultReport.getResults().entrySet()) {
                if (result.getValue().getResult().ordinal() > CheckResult.WARNING.ordinal()) {
                    joiner.add(String.format("- [%s/%s] %s", result.getValue().getResult().getDisplay(), result.getKey().name(), result.getValue().getResultMessage()));
                }
            }
        }
        // Check If we need kill the server or disable plugin

        switch (resultReport.getFinalResult()) {
            case DISABLE_PLUGIN:
                Bukkit.getPluginManager().disablePlugin(this);
                break;
            case STOP_WORKING:
                setupBootError(new BootError(this.getLogger(), joiner.toString()), true);
                PluginCommand command = getCommand("qs");
                if (command != null) {
                    Util.mainThreadRun(() -> command.setTabCompleter(this)); //Disable tab completer
                }
                break;
            case KILL_SERVER:
                getLogger().severe("[Security Risk Detected] QuickShop forcing crash the server for security, contact the developer for details.");
                String result = environmentChecker.getReportMaker().bake();
                File reportFile = writeSecurityReportToFile();
                URI uri = null;
                if (reportFile != null) {
                    uri = reportFile.toURI();
                }
                if (uri != null) {
                    getLogger().warning("[Security Risk Detected] To get more details, please check: " + uri);
                    try {
                        if (java.awt.Desktop.isDesktopSupported()) {
                            java.awt.Desktop dp = java.awt.Desktop.getDesktop();
                            if (dp.isSupported(java.awt.Desktop.Action.BROWSE)) {
                                dp.browse(uri);
                                getLogger().warning("[Security Risk Detected] A browser already open for you. ");
                            }
                        }
                    } catch (Throwable ignored) {
                        //If failed, write directly to console
                        getLogger().severe(result);
                    }
                } else {
                    //If write failed, write directly to console
                    getLogger().severe(result);
                }
                //Wait for a while for user and logger outputting
                try {
                    //10 seconds
                    Thread.yield();
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // Halt the process, kill the server
                Runtime.getRuntime().halt(-1);
            default:
                break;
        }
        testing = false;
    }

    private File writeSecurityReportToFile() {
        File file = new File(getDataFolder(), UUID.randomUUID() + ".security.letter.txt");
        try {
            Files.writeString(new File(getDataFolder(), UUID.randomUUID() + ".security.letter.txt").toPath(), environmentChecker.getReportMaker().bake());
            file = file.getCanonicalFile();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to write security report!", e);
            return null;
        }
        return file;
    }

    @Override
    public final void onEnable() {
        if (!this.onLoadCalled) {
            getLogger().severe("FATAL: onLoad not called and QuickShop trying patching them... Some Integrations will won't work or work incorrectly!");
            try {
                onLoad();
            } catch (Throwable ignored) {
            }
        }
        Timer enableTimer = new Timer(true);
        getLogger().info("QuickShop " + getFork());
        this.audience = BukkitAudiences.create(this);
        /* Check the running envs is support or not. */
        getLogger().info("Starting plugin self-test, please wait...");
        runtimeCheck(EnvCheckEntry.Stage.ON_ENABLE);
        getLogger().info("Reading the configuration...");
        this.initConfiguration();
        getLogger().info("Developers: " + Util.list2String(this.getDescription().getAuthors()));
        getLogger().info("Original author: Netherfoam, Timtower, KaiNoMood");
        getLogger().info("Let's start loading the plugin");
        getLogger().info("Chat processor selected: Hardcoded BungeeChat Lib");
        /* Process Metrics and Sentry error reporter. */
        metrics = new Metrics(this, 14281);

        try {
            if (!getConfig().getBoolean("auto-report-errors")) {
                Util.debugLog("Error reporter was disabled!");
            } else {
                sentryErrorReporter = new RollbarErrorReporter(this);
            }
        } catch (Throwable th) {
            getLogger().warning("Cannot load the Sentry Error Reporter: " + th.getMessage());
            getLogger().warning("Because our error reporter doesn't work, please report this error to developer, thank you!");
        }

        /* Initalize the Utils */
        this.loadItemMatcher();
        Util.initialize();
        try {
            MsgUtil.loadI18nFile();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error when loading translation", e);
        }
        MsgUtil.loadItemi18n();
        MsgUtil.loadEnchi18n();
        MsgUtil.loadPotioni18n();

        /* Load 3rd party supports */
        load3rdParty();

        //Load the database
        setupDBonEnableding = true;
        setupDatabase();

        setupDBonEnableding = false;

        /* Initalize the tools */
        // Create the shop manager.
        permissionManager = new PermissionManager(this);
        // This should be inited before shop manager
        if (this.display && getConfig().getBoolean("shop.display-auto-despawn")) {
            this.displayAutoDespawnWatcher = new DisplayAutoDespawnWatcher(this);
            //BUKKIT METHOD SHOULD ALWAYS EXECUTE ON THE SERVER MAIN THEAD
            timerTaskList.add(this.displayAutoDespawnWatcher.runTaskTimer(this, 20, getConfig().getInt("shop.display-check-time"))); // not worth async
        }

        getLogger().info("Registering commands...");
        /* PreInit for BootError feature */
        commandManager = new SimpleCommandManager(this);
        //noinspection ConstantConditions
        getCommand("qs").setExecutor(commandManager);
        //noinspection ConstantConditions
        getCommand("qs").setTabCompleter(commandManager);

        this.registerCustomCommands();

        this.shopManager = new SimpleShopManager(this);

        this.permissionChecker = new PermissionChecker(this);
        // Limit
        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        ConfigurationSection limitCfg = yamlConfiguration.getConfigurationSection("limits");
        if (limitCfg != null) {
            this.limit = limitCfg.getBoolean("use", false);
            limitCfg = limitCfg.getConfigurationSection("ranks");
            for (String key : Objects.requireNonNull(limitCfg).getKeys(true)) {
                limits.put(key, limitCfg.getInt(key));
            }
        }
        // Limit end
        if (getConfig().getInt("shop.finding.distance") > 100 && (getConfig().getBoolean("shop.finding.exclude-out-of-stock"))) {
            getLogger().severe("Shop find distance is too high with chunk loading feature turned on! It may cause lag! Pick a number under 100!");
        }

        if (getConfig().getBoolean("use-caching")) {
            this.shopCache = new Cache(this);
        } else {
            this.shopCache = null;
        }

        signUpdateWatcher = new SignUpdateWatcher();
        shopContainerWatcher = new ShopContainerWatcher();
        if (display && AbstractDisplayItem.getNowUsing() != DisplayType.VIRTUALITEM) {
            displayDupeRemoverWatcher = new DisplayDupeRemoverWatcher();
            timerTaskList.add(displayDupeRemoverWatcher.runTaskTimerAsynchronously(this, 0, 1));
        }
        /* Load all shops. */
        shopLoader = new ShopLoader(this);
        shopLoader.loadShops();

        getLogger().info("Registering listeners...");
        this.interactionController = new InteractionController(this);
        // Register events
        // Listeners (These don't)
        new BlockListener(this, this.shopCache).register();
        new PlayerListener(this).register();
        new WorldListener(this).register();
        // Listeners - We decide which one to use at runtime
        new ChatListener(this).register();
        new ChunkListener(this).register();
        new CustomInventoryListener(this).register();
        new ShopProtectionListener(this, this.shopCache).register();
        new PluginListener(this).register();
        new EconomySetupListener(this).register();
        new MetricListener(this).register();
        InternalListener internalListener = new InternalListener(this);
        internalListener.register();

        this.shopControlPanelManager.register(new SimpleShopControlPanel());

        if (this.display && AbstractDisplayItem.getNowUsing() != DisplayType.VIRTUALITEM) {
            if (getDisplayItemCheckTicks() > 0) {
                if (getConfig().getInt("shop.display-items-check-ticks") < 3000) {
                    getLogger().severe("Shop.display-items-check-ticks is too low! It may cause HUGE lag! Pick a number > 3000");
                }
                getLogger().info("Registering DisplayCheck task....");
                timerTaskList.add(getServer().getScheduler().runTaskTimer(this, () -> {
                    for (Shop shop : getShopManager().getLoadedShops()) {
                        //Shop may be deleted or unloaded when iterating
                        if (shop.isDeleted() || !shop.isLoaded()) {
                            continue;
                        }
                        shop.checkDisplay();
                    }
                }, 1L, getDisplayItemCheckTicks()));
            } else {
                if (getDisplayItemCheckTicks() != 0) {
                    getLogger().severe("Shop.display-items-check-ticks is invalid! Pick a number > 3000");
                } else {
                    getLogger().severe("Shop.display-items-check-ticks is zero, display check is disabled");
                }
            }
            new DisplayProtectionListener(this, this.shopCache).register();
            if (Bukkit.getPluginManager().getPlugin("ClearLag") != null) {
                new ClearLaggListener(this).register();
            }
        }
        if (getConfig().getBoolean("shop.lock")) {
            new LockListener(this, this.shopCache).register();
        }
        getLogger().info("Cleaning MsgUtils...");
        MsgUtil.loadTransactionMessages();
        MsgUtil.clean();
        if (this.getConfig().getBoolean("updater", true)) {
            updateWatcher = new UpdateWatcher();
            updateWatcher.init();
        }


        /* Delay the Ecoonomy system load, give a chance to let economy system regiser. */
        /* And we have a listener to listen the ServiceRegisterEvent :) */
        Util.debugLog("Loading economy system...");
        new BukkitRunnable() {
            @Override
            public void run() {
                loadEcon();
            }
        }.runTaskLater(this, 1);
        Util.debugLog("Registering watchers...");
        calendarWatcher = new CalendarWatcher(this);
        // shopVaildWatcher.runTaskTimer(this, 0, 20 * 60); // Nobody use it
        timerTaskList.add(signUpdateWatcher.runTaskTimer(this, 0, 10));
        timerTaskList.add(shopContainerWatcher.runTaskTimer(this, 0, 5)); // Nobody use it

        if (logWatcher != null) {
            timerTaskList.add(logWatcher.runTaskTimerAsynchronously(this, 10, 10));
            getLogger().info("Log actions is enabled, actions will log in the qs.log file!");
        }
        if (getConfig().getBoolean("shop.ongoing-fee.enable")) {
            ongoingFeeWatcher = new OngoingFeeWatcher(this);
            timerTaskList.add(ongoingFeeWatcher.runTaskTimerAsynchronously(this, 0, getConfig().getInt("shop.ongoing-fee.ticks")));
            getLogger().info("Ongoing fee feature is enabled.");
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                getLogger().info("Registering bStats metrics...");
                submitMeritcs();
            }
        }.runTask(this);
        if (loaded) {
            getServer().getPluginManager().callEvent(new QSConfigurationReloadEvent(this));
        } else {
            loaded = true;
        }
        calendarWatcher = new CalendarWatcher(this);
        calendarWatcher.start();
        this.shopPurger = new ShopPurger(this);
        if (getConfig().getBoolean("purge.at-server-startup")) {
            shopPurger.purge();
        }
        Util.debugLog("Now using display-type: " + AbstractDisplayItem.getNowUsing().name());
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getLogger().info("QuickShop Loaded! " + enableTimer.stopAndGetTimePassed() + " ms.");
    }

    private void loadItemMatcher() {
        ItemMatcher defItemMatcher = switch (getConfig().getInt("matcher.work-type")) {
            case 1 -> new BukkitItemMatcherImpl(this);
            case 0 -> new QuickShopItemMatcherImpl(this);
            default -> throw new IllegalStateException("Unexpected value: " + getConfig().getInt("matcher.work-type"));
        };
        this.itemMatcher = ServiceInjector.getItemMatcher(defItemMatcher);
    }

    /**
     * Setup the database
     *
     * @return The setup result
     */
    private boolean setupDatabase() {
        getLogger().info("Setting up database...");

        HikariConfig config = new HikariConfig();

        config.addDataSourceProperty("connection-timeout", "60000");
        config.addDataSourceProperty("validation-timeout", "3000");
        config.addDataSourceProperty("idle-timeout", "60000");
        config.addDataSourceProperty("login-timeout", "5");
        config.addDataSourceProperty("maxLifeTime", "60000");
        config.addDataSourceProperty("maximum-pool-size", "8");
        config.addDataSourceProperty("minimum-idle", "10");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useUnicode", "true");
        config.addDataSourceProperty("characterEncoding", "utf8");

        try {
            ConfigurationSection dbCfg = getConfig().getConfigurationSection("database");
            if (Objects.requireNonNull(dbCfg).getBoolean("mysql")) {
                // MySQL database - Required database be created first.
                dbPrefix = dbCfg.getString("prefix");
                if (dbPrefix == null || "none".equals(dbPrefix)) {
                    dbPrefix = "";
                }
                String user = dbCfg.getString("user");
                String pass = dbCfg.getString("password");
                String host = dbCfg.getString("host");
                String port = dbCfg.getString("port");
                String database = dbCfg.getString("database");
                boolean useSSL = dbCfg.getBoolean("usessl");
                config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL);
                config.setUsername(user);
                config.setPassword(pass);
                this.sqlManager = EasySQL.createManager(config);
            } else {
                // SQLite database - Doing this handles file creation
                Driver.load();
                config.setJdbcUrl("jdbc:h2:" + new File(this.getDataFolder(), "shops").getCanonicalFile().getAbsolutePath() + ";DB_CLOSE_DELAY=-1;MODE=MYSQL");
                this.sqlManager = EasySQL.createManager(config);
                this.sqlManager.executeSQL("SET MODE=MYSQL"); // Switch to MySQL mode
            }
            // Make the database up to date
            this.databaseHelper = new SimpleDatabaseHelper(this, this.sqlManager);
            this.databaseHelper.init(this.getDbPrefix());
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error when connecting to the database", e);
            if (setupDBonEnableding) {
                bootError = BuiltInSolution.databaseError();
            }
            return false;
        }
    }

    private void submitMeritcs() {
        if (!getConfig().getBoolean("disabled-metrics")) {
            String vaultVer;
            Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
            if (vault != null) {
                vaultVer = vault.getDescription().getVersion();
            } else {
                vaultVer = "Vault not found";
            }
            // Use internal Metric class not Maven for solve plugin name issues
            String economyType = AbstractEconomy.getNowUsing().name();
            if (getEconomy() != null) {
                economyType = this.getEconomy().getName();
            }
            String eventAdapter;
            if (getConfig().getInt("shop.protection-checking-handler") == 1) {
                eventAdapter = "QUICKSHOP";
            } else {
                eventAdapter = "BUKKIT";
            }
            // Version
            metrics.addCustomChart(new Metrics.SimplePie("server_version", Bukkit::getVersion));
            metrics.addCustomChart(new Metrics.SimplePie("bukkit_version", Bukkit::getBukkitVersion));
            metrics.addCustomChart(new Metrics.SimplePie("vault_version", () -> vaultVer));
            metrics.addCustomChart(new Metrics.SimplePie("use_display_items", () -> Util.boolean2Status(getConfig().getBoolean("shop.display-items"))));
            metrics.addCustomChart(new Metrics.SimplePie("use_locks", () -> Util.boolean2Status(getConfig().getBoolean("shop.lock"))));
            metrics.addCustomChart(new Metrics.SimplePie("use_sneak_action", () -> Util.boolean2Status(getConfig().getBoolean("shop.interact.sneak-to-create") || getConfig().getBoolean("shop.interact.sneak-to-trade") || getConfig().getBoolean("shop.interact.sneak-to-control"))));
            String finalEconomyType = economyType;
            metrics.addCustomChart(new Metrics.SimplePie("economy_type", () -> finalEconomyType));
            metrics.addCustomChart(new Metrics.SimplePie("use_display_auto_despawn", () -> String.valueOf(getConfig().getBoolean("shop.display-auto-despawn"))));
            metrics.addCustomChart(new Metrics.SimplePie("use_enhance_display_protect", () -> String.valueOf(getConfig().getBoolean("shop.enchance-display-protect"))));
            metrics.addCustomChart(new Metrics.SimplePie("use_enhance_shop_protect", () -> String.valueOf(getConfig().getBoolean("shop.enchance-shop-protect"))));
            metrics.addCustomChart(new Metrics.SimplePie("use_ongoing_fee", () -> String.valueOf(getConfig().getBoolean("shop.ongoing-fee.enable"))));
            metrics.addCustomChart(new Metrics.SimplePie("display_type", () -> AbstractDisplayItem.getNowUsing().name()));
            metrics.addCustomChart(new Metrics.SimplePie("itemmatcher_type", () -> this.getItemMatcher().getName()));
            metrics.addCustomChart(new Metrics.SimplePie("use_stack_item", () -> String.valueOf(this.isAllowStack())));
            metrics.addCustomChart(new Metrics.SimplePie("event_adapter", () -> eventAdapter));
            metrics.addCustomChart(new Metrics.SingleLineChart("shops_created_on_all_servers", () -> this.getShopManager().getAllShops().size()));
        } else {
            getLogger().info("You have disabled mertics, Skipping...");
        }

    }

    //TODO: Refactor it
    private void updateConfig(int selectedVersion) throws IOException {
        String serverUUID = getConfig().getString("server-uuid");
        if (serverUUID == null || serverUUID.isEmpty()) {
            UUID uuid = UUID.randomUUID();
            serverUUID = uuid.toString();
            getConfig().set("server-uuid", serverUUID);
        }

        if(selectedVersion < 157){
            new HikariConverter(this).upgrade();
            saveConfiguration();
            getLogger().info("Server will restart after 5 seconds, enjoy :)");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Runtime.getRuntime().halt(0);
        }


        if (getConfig().getInt("matcher.work-type") != 0 && GameVersion.get(platform.getMinecraftVersion()).name().contains("1_16")) {
            getLogger().warning("You are not using QS Matcher, it may meeting item comparing issue mentioned there: https://hub.spigotmc.org/jira/browse/SPIGOT-5063");
        }

        try (InputStreamReader buildInConfigReader = new InputStreamReader(new BufferedInputStream(Objects.requireNonNull(getResource("config.yml"))), StandardCharsets.UTF_8)) {
            if (new ConfigurationFixer(this, new File(getDataFolder(), "config.yml"), getConfig(), YamlConfiguration.loadConfiguration(buildInConfigReader)).fix()) {
                reloadConfiguration();
            }
        }

        //Check if server software support comment saving
        if (Util.isMethodAvailable("org.bukkit.configuration.MemoryConfiguration", "getInlineComments", String.class)) {
            //If so, clean the comment
            ConfigurationSection configurationSection = getConfig();
            List<String> emptyList = Collections.emptyList();
            for (String key : configurationSection.getKeys(true)) {
                configurationSection.setComments(key, emptyList);
                configurationSection.setInlineComments(key, emptyList);
            }
        }

        saveConfiguration();
        reloadConfiguration();


        //Re-add comment for config.yml
        try (InputStream inputStream = Objects.requireNonNull(getResource("config.yml"))) {
            new ConfigCommentUpdater(this, inputStream, new File(getDataFolder(), "config.yml")).updateComment();
        }

        //Delete old example configuration files
        Files.deleteIfExists(new File(getDataFolder(), "example.config.yml").toPath());
        Files.deleteIfExists(new File(getDataFolder(), "example-configuration.txt").toPath());
        Files.deleteIfExists(new File(getDataFolder(), "example-configuration.yml").toPath());

        try {
            if (new File(getDataFolder(), "messages.json").exists()) {
                Files.move(new File(getDataFolder(), "messages.json").toPath(), new File(getDataFolder(), "messages.json.outdated").toPath());
            }
            if (new File(getDataFolder(), "messages.json.outdated").exists()) {
                Files.write(new File(getDataFolder(), "about-messages.json.outdated.txt").toPath(), Collections.singletonList("Please read v4 migrate guide there, after migration you can delete messages.json.outdated and this file: https://github.com/PotatoCraft-Studio/QuickShop-Reremake/wiki/Migrate:-v4-to-v5"));
            }
        } catch (Exception ignore) {
        }

    }

    /**
     * Mark plugins stop working
     *
     * @param bootError           reason
     * @param unregisterListeners should we disable all listeners?
     */
    public void setupBootError(BootError bootError, boolean unregisterListeners) {
        this.bootError = bootError;
        if (unregisterListeners) {
            HandlerList.unregisterAll(this);
        }
        Bukkit.getScheduler().cancelTasks(this);
    }

    public void registerCustomCommands() {
        List<String> customCommands = getConfig().getStringList("custom-commands");
        PluginCommand quickShopCommand = getCommand("qs");
        if (quickShopCommand == null) {
            getLogger().warning("Failed to get QuickShop PluginCommand instance.");
            return;
        }
        List<String> aliases = quickShopCommand.getAliases();
        aliases.addAll(customCommands);
        quickShopCommand.setAliases(aliases);
        try {
            platform.registerCommand("qs",quickShopCommand);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to register command aliases", e);
            return;
        }
        Util.debugLog("Command alias successfully registered.");
    }

    public @NotNull TextManager text() {
        return this.textManager;
    }

    @Override
    public CompatibilityManager getCompatibilityManager() {
        return this.compatibilityTool;
    }

    @Override
    public ShopManager getShopManager() {
        return this.shopManager;
    }

    @Override
    public boolean isDisplayEnabled() {
        return this.display;
    }

    @Override
    public boolean isLimit() {
        return this.limit;
    }

    @Override
    public DatabaseHelper getDatabaseHelper() {
        return this.databaseHelper;
    }

    @Override
    public TextManager getTextManager() {
        return this.textManager;
    }

    @Override
    public ItemMatcher getItemMatcher() {
        return this.itemMatcher;
    }

    @Override
    public boolean isPriceChangeRequiresFee() {
        return this.priceChangeRequiresFee;
    }

    @Override
    public CommandManager getCommandManager() {
        return this.commandManager;
    }

    @Override
    public Map<String, Integer> getLimits() {
        return this.limits;
    }

    @Override
    public GameVersion getGameVersion() {
        if (gameVersion == null) {
            gameVersion = GameVersion.get(ReflectFactory.getNMSVersion());
        }
        return this.gameVersion;
    }
    @NotNull
    public BukkitAudiences getAudience() {
        return audience;
    }
}
