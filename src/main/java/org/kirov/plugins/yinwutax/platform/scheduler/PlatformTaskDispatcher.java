package org.kirov.plugins.yinwutax.platform.scheduler;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class PlatformTaskDispatcher implements TaskDispatcher {

    private final JavaPlugin plugin;
    private final Map<String, Object> scheduledTasks = new ConcurrentHashMap<>();

    public PlatformTaskDispatcher(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runGlobal(Runnable task) {
        if (tryRunFoliaGlobal(task)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void scheduleGlobalRepeating(TaskContext context, Runnable task) {
        cancel(context.key());
        if (tryScheduleFoliaGlobal(context, task)) {
            return;
        }

        BukkitTask scheduled = Bukkit.getScheduler().runTaskTimer(
            plugin,
            task,
            TaskDispatcher.toTicks(context.initialDelay()),
            TaskDispatcher.toTicks(context.period())
        );
        scheduledTasks.put(context.key(), scheduled);
    }

    @Override
    public void cancel(String key) {
        Object task = scheduledTasks.remove(key);
        if (task instanceof BukkitTask bukkitTask) {
            bukkitTask.cancel();
            return;
        }

        if (task != null) {
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    @Override
    public void close() {
        for (String key : scheduledTasks.keySet()) {
            cancel(key);
        }
    }

    private boolean tryRunFoliaGlobal(Runnable task) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            Method runMethod = scheduler.getClass().getMethod("run", JavaPlugin.class, Consumer.class);
            runMethod.invoke(scheduler, plugin, (Consumer<Object>) ignored -> task.run());
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean tryScheduleFoliaGlobal(TaskContext context, Runnable task) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            Method runAtFixedRate = scheduler.getClass().getMethod(
                "runAtFixedRate",
                JavaPlugin.class,
                Consumer.class,
                long.class,
                long.class
            );

            Object scheduled = runAtFixedRate.invoke(
                scheduler,
                plugin,
                (Consumer<Object>) ignored -> task.run(),
                TaskDispatcher.toTicks(context.initialDelay()),
                TaskDispatcher.toTicks(context.period())
            );
            scheduledTasks.put(context.key(), scheduled);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
