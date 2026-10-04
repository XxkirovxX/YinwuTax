package org.kirov.plugins.yinwutax.platform.scheduler;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;

/**
 * Folia 调度后端：一律通过反射绑定到 {@code GlobalRegionScheduler} 与 {@code AsyncScheduler}。
 *
 * <p>反射是刻意的：编译基线是 Paper API，但同一个 jar 还要能在 Spigot 上加载，因此
 * Paper/Folia 类型不允许在公开契约里出现。
 */
final class FoliaSchedulerBackend implements SchedulerBackend {

    private final Plugin plugin;
    private final MethodHandle globalOneShot;
    private final MethodHandle globalDelayed;
    private final MethodHandle globalRepeating;
    private final MethodHandle asyncOneShot;
    private final MethodHandle globalCancelAll;
    private final MethodHandle asyncCancelAll;
    private final MethodHandle taskCancel;
    private final int globalTrailingArgumentCount;
    private final Map<String, Object> trackedTasks = new ConcurrentHashMap<>();

    FoliaSchedulerBackend(Plugin plugin, SchedulerCapabilities capabilities) {
        this.plugin = plugin;

        SchedulerCapabilities.SchedulerMethods global = capabilities.global().orElseThrow(
            () -> new IllegalArgumentException("Folia global scheduler capabilities are missing")
        );
        SchedulerCapabilities.SchedulerMethods async = capabilities.async().orElseThrow(
            () -> new IllegalArgumentException("Folia async scheduler capabilities are missing")
        );

        this.globalOneShot = global.oneShot();
        this.globalDelayed = global.delayed();
        this.globalRepeating = global.repeating();
        this.globalCancelAll = global.cancelTasks();
        this.globalTrailingArgumentCount = global.trailingArgumentCount();
        this.asyncOneShot = async.oneShot();
        this.asyncCancelAll = async.cancelTasks();
        this.taskCancel = resolveTaskCancel();
    }

    @Override
    public void runGlobal(Runnable task) {
        invokeOneShot(globalOneShot, asConsumer(task));
    }

    @Override
    public void runAsync(Runnable task) {
        // 一次性异步任务不登记句柄：没有需要跟随插件生命周期取消的长期状态。
        invokeOneShot(asyncOneShot, asConsumer(task));
    }

    @Override
    public void scheduleGlobalDelayed(TaskContext context, Runnable task) {
        cancel(context.key());
        track(
            context.key(),
            invokeGlobal(
                globalDelayed,
                asConsumer(task),
                TaskDispatcher.toTicks(context.initialDelay())
            )
        );
    }

    @Override
    public void scheduleGlobalRepeating(TaskContext context, Runnable task) {
        cancel(context.key());
        track(
            context.key(),
            invokeGlobal(
                globalRepeating,
                asConsumer(task),
                TaskDispatcher.toTicks(context.initialDelay()),
                TaskDispatcher.toTicks(context.period())
            )
        );
    }

    @Override
    public void cancel(String key) {
        cancelTracked(key);
    }

    @Override
    public void close() {
        for (String key : trackedTasks.keySet()) {
            cancelTracked(key);
        }

        invokeQuietly(globalCancelAll);
        invokeQuietly(asyncCancelAll);
    }

    private void invokeOneShot(MethodHandle handle, Consumer<Object> task) {
        try {
            handle.invoke(plugin, task);
        } catch (Throwable failure) {
            throw asRuntimeException(failure);
        }
    }

    /**
     * 调用全局调度器的 delayed/repeating 方法。是否补 {@code TimeUnit} 取决于探测结果。
     */
    private Object invokeGlobal(MethodHandle handle, Consumer<Object> task, long... tickArguments) {
        Long[] tickBoxes = new Long[tickArguments.length];
        for (int index = 0; index < tickArguments.length; index++) {
            tickBoxes[index] = tickArguments[index];
        }

        Object[] arguments = globalTrailingArgumentCount == 0
            ? prepend(plugin, task, tickBoxes)
            : prepend(plugin, task, append(tickBoxes, TimeUnit.MILLISECONDS));

        try {
            return handle.invokeWithArguments(arguments);
        } catch (Throwable failure) {
            throw asRuntimeException(failure);
        }
    }

    private static Object[] prepend(Object first, Object second, Object[] rest) {
        Object[] arguments = new Object[2 + rest.length];
        arguments[0] = first;
        arguments[1] = second;
        System.arraycopy(rest, 0, arguments, 2, rest.length);
        return arguments;
    }

    private static Object[] append(Object[] values, Object extra) {
        Object[] result = new Object[values.length + 1];
        System.arraycopy(values, 0, result, 0, values.length);
        result[values.length] = extra;
        return result;
    }

    private void track(String key, Object scheduledTask) {
        if (scheduledTask == null) {
            // 调度器没有返回任务句柄：后续无法取消该任务，显式记录而不是静默丢弃。
            plugin.getLogger().warning("Scheduler returned no task handle for '" + key + "'; it cannot be cancelled.");
            return;
        }

        trackedTasks.put(key, scheduledTask);
    }

    private void cancelTracked(String key) {
        Object scheduledTask = trackedTasks.remove(key);
        if (scheduledTask == null) {
            return;
        }

        if (taskCancel == null) {
            return;
        }

        try {
            // 句柄未绑定，签名是 (ScheduledTask)CancelledState，因此把任务实例作为参数传入。
            taskCancel.invoke(scheduledTask);
        } catch (Throwable failure) {
            // 任务可能已经自行结束，或服务端正在关停；取消失败不影响插件状态，但需要可诊断。
            plugin.getLogger().fine(() -> "Failed to cancel task '" + key + "': " + failure);
        }
    }

    /**
     * 解析 {@code ScheduledTask#cancel()} 的方法句柄（未绑定，签名 {@code (ScheduledTask)}）；
     * 服务端没有该类型时返回 {@code null}。
     */
    private static MethodHandle resolveTaskCancel() {
        return SchedulerApiTypes.resolveMethod(SchedulerApiTypes.SCHEDULED_TASK, "cancel", 0)
            .flatMap(method -> SchedulerApiTypes.unreflect(method))
            .orElse(null);
    }

    private void invokeQuietly(MethodHandle handle) {
        try {
            handle.invoke(plugin);
        } catch (Throwable failure) {
            plugin.getLogger().fine(() -> "Failed to cancel remaining scheduler tasks: " + failure);
        }
    }

    private Consumer<Object> asConsumer(Runnable task) {
        return scheduledTask -> task.run();
    }

    private RuntimeException asRuntimeException(Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException && failure.getCause() != null
            ? failure.getCause()
            : failure;
        return cause instanceof RuntimeException runtimeException
            ? runtimeException
            : new IllegalStateException("Failed to schedule task", cause);
    }
}
