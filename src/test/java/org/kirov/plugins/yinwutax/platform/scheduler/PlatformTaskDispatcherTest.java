package org.kirov.plugins.yinwutax.platform.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/**
 * 调度后端的探测与选择逻辑测试。
 *
 * <p>全部用例都不需要真实服务端：能力探测与后端构造都通过公开 API 类型驱动，这样才能验证
 * "非 Folia 服务端回落到 Bukkit 后端" 这条在 Spigot 上最关键的路径。
 */
class PlatformTaskDispatcherTest {

    @Test
    void toTicksNeverReturnsZero() {
        assertEquals(1L, TaskDispatcher.toTicks(Duration.ZERO));
        assertEquals(1L, TaskDispatcher.toTicks(Duration.ofMillis(1)));
        assertEquals(20L, TaskDispatcher.toTicks(Duration.ofSeconds(1)));
    }

    @Test
    void resolvesToBukkitBackendWhenServerHasNoFoliaSchedulers() {
        SchedulerBackendResolver resolver = new SchedulerBackendResolver(fakePlugin(null, null));

        assertEquals(SchedulerResolution.SchedulerBackendKind.BUKKIT, resolver.resolution().kind());
        assertTrue(resolver.backend() instanceof BukkitSchedulerBackend);
    }

    @Test
    void resolvesToFoliaBackendWhenSchedulersAreAvailable() {
        SchedulerBackendResolver resolver = new SchedulerBackendResolver(
            fakePlugin(new RecordingGlobalRegionScheduler(), FakeSchedulers.async(new AtomicInteger()))
        );

        assertEquals(SchedulerResolution.SchedulerBackendKind.FOLIA, resolver.resolution().kind());
        assertTrue(resolver.backend() instanceof FoliaSchedulerBackend);
    }

    @Test
    void resolvesToFoliaBrokenWhenOnlyOneSchedulerIsAvailable() {
        SchedulerBackendResolver resolver = new SchedulerBackendResolver(
            fakePlugin(new RecordingGlobalRegionScheduler(), null)
        );

        assertEquals(SchedulerResolution.SchedulerBackendKind.FOLIA_BROKEN, resolver.resolution().kind());
        assertTrue(resolver.resolution().failureReason().isPresent());
        assertTrue(resolver.backend() instanceof BukkitSchedulerBackend);
    }

    @Test
    void scheduleGlobalRepeatingUsesFoliaSchedulerWithNonZeroDelays() {
        RecordingGlobalRegionScheduler scheduler = new RecordingGlobalRegionScheduler();
        TaskDispatcher dispatcher = new PlatformTaskDispatcher(
            fakePlugin(scheduler, FakeSchedulers.async(new AtomicInteger()))
        );

        dispatcher.scheduleGlobalRepeating(
            new TaskContext("autosave", Duration.ZERO, Duration.ofMillis(1)),
            scheduler::recordTaskRun
        );

        assertEquals(1L, scheduler.initialDelayTicks);
        assertEquals(1L, scheduler.periodTicks);
        assertEquals(1, scheduler.taskRuns);
        assertTrue(scheduler.createdTasks.get(0).isRepeatingTask());
    }

    @Test
    void runAsyncOnFoliaUsesAsyncSchedulerInsteadOfBukkit() {
        AtomicInteger asyncRuns = new AtomicInteger();
        TaskDispatcher dispatcher = new PlatformTaskDispatcher(
            fakePlugin(new RecordingGlobalRegionScheduler(), FakeSchedulers.async(asyncRuns))
        );

        dispatcher.runAsync(asyncRuns::incrementAndGet);

        assertEquals(1, asyncRuns.get());
    }

    @Test
    void cancelCancelsStoredTask() {
        RecordingGlobalRegionScheduler scheduler = new RecordingGlobalRegionScheduler();
        TaskDispatcher dispatcher = new PlatformTaskDispatcher(
            fakePlugin(scheduler, FakeSchedulers.async(new AtomicInteger()))
        );

        dispatcher.scheduleGlobalRepeating(
            new TaskContext("income-tax", Duration.ofSeconds(1), Duration.ofSeconds(60)),
            () -> {
            }
        );
        dispatcher.cancel("income-tax");

        assertTrue(scheduler.createdTasks.get(0).cancelled());
    }

    @Test
    void closeCancelsAllStoredTasks() {
        RecordingGlobalRegionScheduler scheduler = new RecordingGlobalRegionScheduler();
        TaskDispatcher dispatcher = new PlatformTaskDispatcher(
            fakePlugin(scheduler, FakeSchedulers.async(new AtomicInteger()))
        );

        dispatcher.scheduleGlobalRepeating(new TaskContext("income-tax", Duration.ofSeconds(1), Duration.ofSeconds(60)), () -> {
        });
        dispatcher.scheduleGlobalRepeating(new TaskContext("wealth-tax", Duration.ofSeconds(1), Duration.ofSeconds(60)), () -> {
        });

        dispatcher.close();

        assertEquals(2, scheduler.createdTasks.size());
        assertTrue(scheduler.createdTasks.stream().allMatch(RecordingScheduledTask::cancelled));
    }

    private static Plugin fakePlugin(GlobalRegionScheduler globalScheduler, Object asyncScheduler) {
        Object server = Proxy.newProxyInstance(
            Server.class.getClassLoader(),
            new Class<?>[] {Server.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getGlobalRegionScheduler" -> globalScheduler;
                case "getAsyncScheduler" -> asyncScheduler;
                case "getLogger" -> java.util.logging.Logger.getLogger("YinwuTaxTest");
                case "getName" -> "YinwuTax";
                case "toString" -> "FakeServer";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> null;
            }
        );

        return (Plugin) Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getServer" -> server;
                case "getLogger" -> java.util.logging.Logger.getLogger("YinwuTaxTest");
                case "getName" -> "YinwuTax";
                case "toString" -> "FakePlugin";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> null;
            }
        );
    }

    /**
     * 异步调度器用动态代理模拟：它的一次性任务方法名与参数形态随服务端版本变化，
     * 代理可以按名响应，并像真实实现一样回调 {@code Consumer}/{@code Runnable}。
     */
    private static final class FakeSchedulers {

        private FakeSchedulers() {
        }

        private static Object async(AtomicInteger runs) {
            return Proxy.newProxyInstance(
                SchedulerApiTypes.ASYNC_SCHEDULER.getClassLoader(),
                new Class<?>[] {SchedulerApiTypes.ASYNC_SCHEDULER},
                (proxy, method, arguments) -> {
                    if (!"cancelTasks".equals(method.getName()) && arguments.length > 1) {
                        Object callback = arguments[1];
                        RecordingScheduledTask scheduledTask = new RecordingScheduledTask(null, false);
                        if (callback instanceof Runnable runnable) {
                            runnable.run();
                        } else if (callback instanceof Consumer) {
                            @SuppressWarnings("unchecked")
                            Consumer<ScheduledTask> consumer = (Consumer<ScheduledTask>) callback;
                            consumer.accept(scheduledTask);
                        }
                        return scheduledTask;
                    }

                    return null;
                }
            );
        }
    }

    private static final class RecordingGlobalRegionScheduler implements GlobalRegionScheduler {

        private final List<RecordingScheduledTask> createdTasks = new ArrayList<>();
        private long initialDelayTicks;
        private long periodTicks;
        private int taskRuns;

        @Override
        public void execute(Plugin plugin, Runnable run) {
            run.run();
        }

        @Override
        public ScheduledTask run(Plugin plugin, Consumer<ScheduledTask> task) {
            return record(plugin, false, task);
        }

        @Override
        public ScheduledTask runDelayed(Plugin plugin, Consumer<ScheduledTask> task, long delayTicks) {
            this.initialDelayTicks = delayTicks;
            return record(plugin, false, task);
        }

        @Override
        public ScheduledTask runAtFixedRate(
            Plugin plugin,
            Consumer<ScheduledTask> task,
            long initialDelayTicks,
            long periodTicks
        ) {
            this.initialDelayTicks = initialDelayTicks;
            this.periodTicks = periodTicks;
            return record(plugin, true, task);
        }

        @Override
        public void cancelTasks(Plugin plugin) {
            createdTasks.forEach(RecordingScheduledTask::cancel);
        }

        private ScheduledTask record(Plugin plugin, boolean repeating, Consumer<ScheduledTask> task) {
            RecordingScheduledTask scheduledTask = new RecordingScheduledTask(plugin, repeating);
            createdTasks.add(scheduledTask);
            task.accept(scheduledTask);
            return scheduledTask;
        }

        private void recordTaskRun() {
            taskRuns++;
        }
    }

    private static final class RecordingScheduledTask implements ScheduledTask {

        private final Plugin plugin;
        private final boolean repeating;
        private boolean cancelled;

        private RecordingScheduledTask(Plugin plugin, boolean repeating) {
            this.plugin = plugin;
            this.repeating = repeating;
        }

        @Override
        public Plugin getOwningPlugin() {
            return plugin;
        }

        @Override
        public boolean isRepeatingTask() {
            return repeating;
        }

        @Override
        public CancelledState cancel() {
            if (cancelled) {
                return CancelledState.CANCELLED_ALREADY;
            }

            cancelled = true;
            return CancelledState.CANCELLED_BY_CALLER;
        }

        @Override
        public ExecutionState getExecutionState() {
            return cancelled ? ExecutionState.CANCELLED : ExecutionState.IDLE;
        }

        private boolean cancelled() {
            return cancelled;
        }
    }
}
