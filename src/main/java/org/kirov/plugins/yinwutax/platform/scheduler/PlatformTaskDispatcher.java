package org.kirov.plugins.yinwutax.platform.scheduler;

import org.bukkit.plugin.Plugin;

/**
 * {@link TaskDispatcher} 的平台实现：在构造阶段解析一次服务端能力，之后只做委托。
 *
 * <p>解析结果只可能是 Bukkit 后端或 Folia 后端；Folia 存在但不可用时会在解析阶段显式
 * 记录日志，不会静默把 Folia 任务塞给 Bukkit 调度器。
 */
public final class PlatformTaskDispatcher implements TaskDispatcher {

    private final SchedulerBackend backend;

    public PlatformTaskDispatcher(Plugin plugin) {
        this.backend = new SchedulerBackendResolver(plugin).backend();
    }

    PlatformTaskDispatcher(SchedulerBackend backend) {
        this.backend = backend;
    }

    @Override
    public void runGlobal(Runnable task) {
        backend.runGlobal(task);
    }

    @Override
    public void runAsync(Runnable task) {
        backend.runAsync(task);
    }

    @Override
    public void scheduleGlobalDelayed(TaskContext context, Runnable task) {
        backend.scheduleGlobalDelayed(context, task);
    }

    @Override
    public void scheduleGlobalRepeating(TaskContext context, Runnable task) {
        backend.scheduleGlobalRepeating(context, task);
    }

    @Override
    public void cancel(String key) {
        backend.cancel(key);
    }

    @Override
    public void close() {
        backend.close();
    }
}
