package org.kirov.plugins.yinwutax.bootstrap;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.kirov.plugins.yinwutax.config.YinwuTaxConfig;
import org.kirov.plugins.yinwutax.integration.EconomyGateway;
import org.kirov.plugins.yinwutax.integration.EconomyGatewayFactory;
import org.kirov.plugins.yinwutax.integration.iconomyunlocked.IConomyUnlockedAdapter;
import org.kirov.plugins.yinwutax.platform.scheduler.PlatformTaskDispatcher;
import org.kirov.plugins.yinwutax.platform.scheduler.TaskContext;
import org.kirov.plugins.yinwutax.platform.scheduler.TaskDispatcher;
import org.kirov.plugins.yinwutax.storage.StorageFacade;
import org.kirov.plugins.yinwutax.storage.TaxDataSnapshot;
import org.kirov.plugins.yinwutax.storage.yaml.YamlStorageFacade;
import org.kirov.plugins.yinwutax.tax.core.TaxCollectionExecutor;
import org.kirov.plugins.yinwutax.tax.core.TaxCoordinator;
import org.kirov.plugins.yinwutax.tax.exemption.ExemptionService;
import org.kirov.plugins.yinwutax.tax.exemption.ExemptionStore;
import org.kirov.plugins.yinwutax.tax.headcount.HeadcountOverrideStore;
import org.kirov.plugins.yinwutax.tax.headcount.HeadcountTaxService;
import org.kirov.plugins.yinwutax.tax.headcount.IpHistoryTracker;
import org.kirov.plugins.yinwutax.tax.income.IncomeBracketResolver;
import org.kirov.plugins.yinwutax.tax.income.IncomeTaxService;
import org.kirov.plugins.yinwutax.tax.wealth.WealthBracketResolver;
import org.kirov.plugins.yinwutax.tax.wealth.WealthTaxService;
import org.kirov.plugins.yinwutax.velocity.VelocityBridge;

// 统一负责服务装配，避免主类膨胀，也方便重载时整体重建依赖。
public class ServiceRegistry implements AutoCloseable {

    private final JavaPlugin plugin;
    private final YinwuTaxConfig config;
    private final StorageFacade storage;
    private final TaxDataSnapshot snapshot;
    private final TaskDispatcher taskDispatcher;
    private final EconomyGateway economyGateway;
    private final IncomeTaxService incomeTaxService;
    private final HeadcountOverrideStore headcountOverrideStore;
    private final HeadcountTaxService headcountTaxService;
    private final ExemptionStore exemptionStore;
    private final ExemptionService exemptionService;
    private final IpHistoryTracker ipHistoryTracker;
    private final WealthTaxService wealthTaxService;
    private final TaxCoordinator taxCoordinator;
    private final IConomyUnlockedAdapter iconomyUnlockedAdapter;
    private final VelocityBridge velocityBridge;

    private ServiceRegistry(
        JavaPlugin plugin,
        YinwuTaxConfig config,
        StorageFacade storage,
        TaxDataSnapshot snapshot,
        TaskDispatcher taskDispatcher,
        EconomyGateway economyGateway,
        IncomeTaxService incomeTaxService,
        HeadcountOverrideStore headcountOverrideStore,
        HeadcountTaxService headcountTaxService,
        ExemptionStore exemptionStore,
        ExemptionService exemptionService,
        IpHistoryTracker ipHistoryTracker,
        WealthTaxService wealthTaxService,
        TaxCoordinator taxCoordinator,
        IConomyUnlockedAdapter iconomyUnlockedAdapter,
        VelocityBridge velocityBridge
    ) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.snapshot = snapshot;
        this.taskDispatcher = taskDispatcher;
        this.economyGateway = economyGateway;
        this.incomeTaxService = incomeTaxService;
        this.headcountOverrideStore = headcountOverrideStore;
        this.headcountTaxService = headcountTaxService;
        this.exemptionStore = exemptionStore;
        this.exemptionService = exemptionService;
        this.ipHistoryTracker = ipHistoryTracker;
        this.wealthTaxService = wealthTaxService;
        this.taxCoordinator = taxCoordinator;
        this.iconomyUnlockedAdapter = iconomyUnlockedAdapter;
        this.velocityBridge = velocityBridge;
    }

    public static ServiceRegistry bootstrap(JavaPlugin plugin) {
        YinwuTaxConfig config = YinwuTaxConfig.from(plugin.getConfig());
        StorageFacade storage = new YamlStorageFacade(plugin, config.storage().dataFileName());
        TaxDataSnapshot snapshot = storage.load();
        TaskDispatcher dispatcher = new PlatformTaskDispatcher(plugin);
        EconomyGateway economyGateway = EconomyGatewayFactory.create(plugin);

        // 各个税务模块共用同一份快照，命令、定时任务和持久化都围绕这份内存视图工作。
        IncomeTaxService incomeTaxService = new IncomeTaxService(
            new IncomeBracketResolver(config.incomeTax().brackets()),
            storage,
            snapshot
        );
        HeadcountOverrideStore headcountOverrideStore = new HeadcountOverrideStore(snapshot.getHeadcountOverrides());
        HeadcountTaxService headcountTaxService = new HeadcountTaxService(
            headcountOverrideStore,
            config.headcountTax().perAccountExtraRate()
        );
        ExemptionStore exemptionStore = new ExemptionStore(snapshot.getExemptions());
        ExemptionService exemptionService = new ExemptionService(exemptionStore);
        IpHistoryTracker ipHistoryTracker = new IpHistoryTracker(snapshot, config.storage().ipHashSalt());
        WealthTaxService wealthTaxService = new WealthTaxService(
            new WealthBracketResolver(config.wealthTax().brackets()),
            economyGateway,
            storage,
            snapshot
        );
        TaxCollectionExecutor collectionExecutor = new TaxCollectionExecutor(economyGateway, exemptionService, config.exemption().enabled());
        TaxCoordinator taxCoordinator = new TaxCoordinator(
            economyGateway,
            incomeTaxService,
            headcountTaxService,
            ipHistoryTracker,
            wealthTaxService,
            collectionExecutor,
            config.headcountTax().enabled()
        );
        IConomyUnlockedAdapter iconomyUnlockedAdapter = new IConomyUnlockedAdapter(
            plugin,
            incomeTaxService,
            config.incomeTax().excludeAdminOperations(),
            config.incomeTax().excludeSetReset()
        );
        VelocityBridge velocityBridge = new VelocityBridge(plugin, config.velocity().enabled());

        return new ServiceRegistry(
            plugin,
            config,
            storage,
            snapshot,
            dispatcher,
            economyGateway,
            incomeTaxService,
            headcountOverrideStore,
            headcountTaxService,
            exemptionStore,
            exemptionService,
            ipHistoryTracker,
            wealthTaxService,
            taxCoordinator,
            iconomyUnlockedAdapter,
            velocityBridge
        );
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(ipHistoryTracker, plugin);
        iconomyUnlockedAdapter.start();
        velocityBridge.start();

        // 所得税和财富税各自独立调度，方便分别开关和单独调整周期。
        if (config.incomeTax().enabled()) {
            taskDispatcher.scheduleGlobalRepeating(
                new TaskContext("income-tax", config.incomeTax().period(), config.incomeTax().period()),
                this::runIncomeSettlement
            );
        }

        if (config.wealthTax().enabled()) {
            taskDispatcher.scheduleGlobalRepeating(
                new TaskContext("wealth-tax", config.wealthTax().period(), config.wealthTax().period()),
                this::runWealthSettlement
            );
        }

        taskDispatcher.scheduleGlobalRepeating(
            new TaskContext("autosave", java.time.Duration.ofMinutes(5), java.time.Duration.ofMinutes(5)),
            this::persist
        );
    }

    public void reload() {
        persist();
    }

    public void grantExemptions(UUID playerId, int count) {
        exemptionService.grant(playerId, count);
        persist();
    }

    public void takeExemptions(UUID playerId, int count) {
        exemptionService.take(playerId, count);
        persist();
    }

    public void setHeadcountOverride(UUID playerId, int extraAccounts) {
        headcountOverrideStore.setOverrideExtraAccounts(playerId, extraAccounts);
        persist();
    }

    public void clearHeadcountOverride(UUID playerId) {
        headcountOverrideStore.clearOverride(playerId);
        persist();
    }

    public int runIncomeSettlement() {
        int settled = taxCoordinator.settleIncomeTaxes();
        persist();
        return settled;
    }

    public int runWealthSettlement() {
        int settled = taxCoordinator.settleWealthTaxes();
        persist();
        return settled;
    }

    public BigDecimal getCurrentIncomeRate(UUID playerId) {
        BigDecimal baseRate = incomeTaxService.getCurrentBaseRate(playerId);
        return headcountTaxService.resolveFinalIncomeTaxRate(playerId, baseRate, ipHistoryTracker.getLinkedAccountCount(playerId));
    }

    public BigDecimal getCurrentWealthRate(UUID playerId) {
        return wealthTaxService.getCurrentRate(playerId);
    }

    public int getRemainingExemptions(UUID playerId) {
        return exemptionService.remaining(playerId);
    }

    public BigDecimal getLastTaxAmount(UUID playerId) {
        return snapshot.getLastTaxAmounts().getOrDefault(playerId, BigDecimal.ZERO);
    }

    public int getLinkedAccountCount(UUID playerId) {
        return ipHistoryTracker.getLinkedAccountCount(playerId);
    }

    public Set<UUID> getLinkedAccounts(UUID playerId) {
        return ipHistoryTracker.getLinkedAccounts(playerId);
    }

    public EconomyGateway getEconomyGateway() {
        return economyGateway;
    }

    public YinwuTaxConfig getConfigModel() {
        return config;
    }

    public void persist() {
        // 命令侧使用各自的 store 做增删改，这里在落盘前统一回写到共享快照。
        snapshot.getExemptions().clear();
        snapshot.getExemptions().putAll(exemptionStore.export());
        snapshot.getHeadcountOverrides().clear();
        snapshot.getHeadcountOverrides().putAll(headcountOverrideStore.export());
        storage.save(snapshot);
    }

    @Override
    public void close() {
        iconomyUnlockedAdapter.stop();
        HandlerList.unregisterAll(ipHistoryTracker);
        velocityBridge.stop();
        taskDispatcher.close();
        persist();
    }
}
