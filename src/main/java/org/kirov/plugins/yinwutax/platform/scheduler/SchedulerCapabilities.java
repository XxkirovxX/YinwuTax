package org.kirov.plugins.yinwutax.platform.scheduler;

import java.lang.invoke.MethodHandle;
import java.util.Optional;

/**
 * 一次性能探测的结果，只以方法句柄描述服务端实际提供的调度能力。
 *
 * <p>本类型不出现任何 Paper/Folia 调度器类型，因此可以在 Spigot 等不具备 Folia API
 * 的服务端上正常加载。
 */
record SchedulerCapabilities(
    Optional<SchedulerMethods> global,
    Optional<SchedulerMethods> async
) {

    static SchedulerCapabilities unavailable() {
        return new SchedulerCapabilities(Optional.empty(), Optional.empty());
    }

    /** 全部 Folia 调度器方法均已绑定成功，可以走 Folia 后端。 */
    boolean foliaReady() {
        return global.isPresent() && async.isPresent();
    }

    /**
     * 单个调度器已绑定的方法句柄集合。
     *
     * <p>{@code runDelayed} / {@code runAtFixedRate} 一定以 {@code (Plugin, Consumer)} 开头，
     * 之后是否还多一个 {@code TimeUnit} 参数取决于具体调度器，由
     * {@link #trailingArgumentCount()} 描述。
     *
     * @param oneShot 一次性任务，签名 {@code (Plugin, Consumer)}，返回 {@code ScheduledTask}
     * @param delayed 延迟一次性任务，签名 {@code (Plugin, Consumer, ...)}，返回 {@code ScheduledTask}
     * @param repeating 固定周期重复任务，签名 {@code (Plugin, Consumer, ...)}，返回 {@code ScheduledTask}
     * @param cancelTasks 取消该插件在对应调度器上的全部任务
     * @param trailingArgumentCount {@code delayed}/{@code repeating} 在 {@code (Plugin, Consumer)}
     *     之后还需要补的参数个数：{@code GlobalRegionScheduler} 按 tick 计，为 0；
     *     {@code AsyncScheduler} 是「数值 + TimeUnit」，为 1
     */
    record SchedulerMethods(
        MethodHandle oneShot,
        MethodHandle delayed,
        MethodHandle repeating,
        MethodHandle cancelTasks,
        int trailingArgumentCount
    ) {
    }
}
