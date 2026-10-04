package org.kirov.plugins.yinwutax.platform.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bukkit/Spigot 调度后端：只在服务端确认没有 Folia 调度器时使用。
 *
 * <p>这里可以直接调用 {@link BukkitScheduler}，因为 {@code org.bukkit.scheduler} 属于所有
 * Bukkit 系服务端都实现的公共 API。
 */
final class BukkitSchedulerBackend implements SchedulerBackend {

    private final Plugin plugin;
    private final BukkitScheduler scheduler;
    private final Map<String, BukkitTask> trackedTasks = new ConcurrentHashMap<>();

    BukkitSchedulerBackend(Plugin plugin) {
        this(plugin, plugin.getServer().getScheduler());
    }

    BukkitSchedulerBackend(Plugin plugin, BukkitScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    @Override
    public void runGlobal(Runnable task) {
        scheduler.runTask(plugin, task);
    }

    @Override
    public void runAsync(Runnable task) {
        scheduler.runTaskAsynchronously(plugin, task);
    }

    @Override
    public void scheduleGlobalDelayed(TaskContext context, Runnable task) {
        cancel(context.key());
        BukkitTask scheduledTask = scheduler.runTaskLater(plugin, task, TaskDispatcher.toTicks(context.initialDelay()));
        trackedTasks.put(context.key(), scheduledTask);
    }

    @Override
    public void scheduleGlobalRepeating(TaskContext context, Runnable task) {
        cancel(context.key());
        BukkitTask scheduledTask = scheduler.runTaskTimer(
            plugin,
            task,
            TaskDispatcher.toTicks(context.initialDelay()),
            TaskDispatcher.toTicks(context.period())
        );
        trackedTasks.put(context.key(), scheduledTask);
    }

    @Override
    public void cancel(String key) {
        BukkitTask scheduledTask = trackedTasks.remove(key);
        if (scheduledTask != null) {
            scheduledTask.cancel();
        }
    }

    @Override
    public void close() {
        for (String key : trackedTasks.keySet()) {
            cancel(key);
        }

        scheduler.cancelTasks(plugin);
    }
}
