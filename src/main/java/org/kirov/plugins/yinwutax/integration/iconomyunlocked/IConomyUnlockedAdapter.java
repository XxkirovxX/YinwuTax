package org.kirov.plugins.yinwutax.integration.iconomyunlocked;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.kirov.plugins.yinwutax.integration.income.IncomeObservationStrategy;
import org.kirov.plugins.yinwutax.tax.income.IncomeTaxService;

// 通过反射接 iConomyUnlocked，保证目标插件缺失时主插件仍能启动。
public class IConomyUnlockedAdapter implements IncomeObservationStrategy, Listener {

    private static final String ICONOMY_PLUGIN_NAME = "iConomyUnlocked";
    private static final String ACCOUNT_UPDATE_EVENT = "io.github.townyadvanced.iconomy.events.AccountUpdateEvent";

    private final JavaPlugin plugin;
    private final IncomeTaxService incomeTaxService;
    private final Map<String, Integer> pendingAdminGrantExclusions = new ConcurrentHashMap<>();
    private final boolean excludeAdminOperations;
    private final boolean excludeSetReset;

    private boolean active;

    public IConomyUnlockedAdapter(
        JavaPlugin plugin,
        IncomeTaxService incomeTaxService,
        boolean excludeAdminOperations,
        boolean excludeSetReset
    ) {
        this.plugin = plugin;
        this.incomeTaxService = incomeTaxService;
        this.excludeAdminOperations = excludeAdminOperations;
        this.excludeSetReset = excludeSetReset;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isPrecise() {
        return active;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void start() {
        Plugin iconomyPlugin = Bukkit.getPluginManager().getPlugin(ICONOMY_PLUGIN_NAME);
        if (iconomyPlugin == null || !iconomyPlugin.isEnabled()) {
            active = false;
            return;
        }

        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(ACCOUNT_UPDATE_EVENT);
            // 这里不直接硬依赖事件类，而是在运行时反射注册，避免缺依赖时类加载失败。
            EventExecutor executor = (listener, event) -> {
                try {
                    handleAccountUpdate(event);
                } catch (ReflectiveOperationException exception) {
                    throw new EventException(exception);
                }
            };

            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR, executor, plugin, true);
            Bukkit.getPluginManager().registerEvents(this, plugin);
            active = true;
        } catch (ClassNotFoundException exception) {
            active = false;
        }
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(this);
        active = false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        trackAdminGrant(event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        trackAdminGrant(event.getCommand());
    }

    private void trackAdminGrant(String rawCommand) {
        String normalized = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        String[] args = normalized.split("\\s+");
        if (args.length < 3) {
            return;
        }

        if (!args[0].equalsIgnoreCase("money")) {
            return;
        }

        String subcommand = args[1].toLowerCase(Locale.ROOT);
        // 这里只跳过该账号接下来一次“正向入账”事件，用来过滤管理员修正，
        // 不影响之后真正的玩家收入继续计税。
        if ("grant".equals(subcommand) && excludeAdminOperations) {
            String accountName = args[2].toLowerCase(Locale.ROOT);
            pendingAdminGrantExclusions.merge(accountName, 1, Integer::sum);
            return;
        }

        if (excludeSetReset && ("set".equals(subcommand) || "reset".equals(subcommand))) {
            pendingAdminGrantExclusions.merge(args[2].toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
    }

    private void handleAccountUpdate(Event event) throws ReflectiveOperationException {
        Method getAccountName = event.getClass().getMethod("getAccountName");
        Method getAccountUUID = event.getClass().getMethod("getAccountUUID");
        Method getPrevious = event.getClass().getMethod("getPrevious");
        Method getBalance = event.getClass().getMethod("getBalance");

        String accountName = ((String) getAccountName.invoke(event)).toLowerCase(Locale.ROOT);
        UUID accountId = (UUID) getAccountUUID.invoke(event);
        BigDecimal previous = BigDecimal.valueOf(((Number) getPrevious.invoke(event)).doubleValue());
        BigDecimal balance = BigDecimal.valueOf(((Number) getBalance.invoke(event)).doubleValue());
        BigDecimal delta = balance.subtract(previous);

        // 只有余额正增长才视为收入；支出或无变化都不进入所得税账本。
        if (delta.signum() <= 0) {
            return;
        }

        if (consumeAdminGrantExclusion(accountName)) {
            return;
        }

        incomeTaxService.recordIncome(accountId, delta);
    }

    private boolean consumeAdminGrantExclusion(String accountName) {
        Integer count = pendingAdminGrantExclusions.get(accountName);
        if (count == null || count <= 0) {
            return false;
        }

        // 排除标记只消费一次，防止后续真实收入也被一起吞掉。
        if (count == 1) {
            pendingAdminGrantExclusions.remove(accountName);
        } else {
            pendingAdminGrantExclusions.put(accountName, count - 1);
        }
        return true;
    }
}
