package org.kirov.plugins.yinwutax.platform.scheduler;

/**
 * 调度后端契约，仅在 scheduler 包内部使用。
 *
 * <p>刻意不出现任何 Paper/Folia 调度器类型：Paper/Folia 的类型只允许出现在后端实现与
 * {@link SchedulerProbe} 内部。
 */
interface SchedulerBackend {

    void runGlobal(Runnable task);

    void runAsync(Runnable task);

    void scheduleGlobalDelayed(TaskContext context, Runnable task);

    void scheduleGlobalRepeating(TaskContext context, Runnable task);

    void cancel(String key);

    void close();
}
