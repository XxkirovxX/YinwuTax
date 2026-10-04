package org.kirov.plugins.yinwutax.platform.scheduler;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.Optional;

import org.bukkit.plugin.Plugin;

/**
 * 在插件启用期间只探测一次服务端的 Folia 调度能力。
 *
 * <p>探测完全基于反射，且只允许出现在本包内：任何一个 Paper/Folia 调度器类型都不会
 * 出现在公开契约里，因此在 Spigot 等不具备 Folia API 的服务端上，本类只会得到
 * "缺少方法" 的结论，而不是 {@link NoSuchMethodError}。
 *
 * <p>三个必须遵守的反射细节：
 *
 * <ul>
 *   <li>{@code findVirtual} 要求方法描述符<b>完全匹配</b>：返回类型与参数类型都必须取自
 *       真实方法，不能一律按 {@code void}/{@code Object} 去查，因此这里统一用
 *       {@code Method#getReturnType()} 与 {@code Method#getParameterTypes()} 构造句柄；</li>
 *   <li>同名方法在不同调度器上参数个数可能不同（{@code AsyncScheduler#runDelayed}
 *       比 {@code GlobalRegionScheduler#runDelayed} 多一个 {@code TimeUnit}）；</li>
 *   <li>服务端给出的调度器实现类通常不是 public，所以句柄声明在公开 API 类型上。</li>
 * </ul>
 */
final class SchedulerProbe {

    private SchedulerProbe() {
    }

    static SchedulerCapabilities probe(Plugin plugin) {
        Object server = plugin.getServer();

        Optional<Object> globalScheduler = fetchScheduler(server, "getGlobalRegionScheduler");
        Optional<Object> asyncScheduler = fetchScheduler(server, "getAsyncScheduler");

        if (globalScheduler.isEmpty() && asyncScheduler.isEmpty()) {
            // 非 Folia 服务端：预期结果，不算错误。
            return SchedulerCapabilities.unavailable();
        }

        // 调度器类型存在就说明这是 Folia 系服务端；此时任何方法缺失都属于错误状态，
        // 上抛给解析器显式记录，而不是静默回落到 Bukkit。
        return new SchedulerCapabilities(
            globalScheduler.map(scheduler -> bindGlobal(scheduler, SchedulerApiTypes.GLOBAL_REGION_SCHEDULER)),
            asyncScheduler.map(scheduler -> bindAsync(scheduler, SchedulerApiTypes.ASYNC_SCHEDULER))
        );
    }

    private static Optional<Object> fetchScheduler(Object server, String methodName) {
        Class<?> serverType = SchedulerApiTypes.SERVER;
        Method method;
        try {
            method = serverType.getMethod(methodName);
        } catch (NoSuchMethodException absent) {
            // Spigot 等非 Folia 服务端没有这个方法。
            return Optional.empty();
        }

        MethodHandle handle = bind(method, server, "Server#" + methodName);
        try {
            return Optional.ofNullable(handle.invoke());
        } catch (Throwable failure) {
            throw new IllegalStateException("Failed to invoke Server#" + methodName, unwrap(failure));
        }
    }

    private static SchedulerCapabilities.SchedulerMethods bindGlobal(Object scheduler, Class<?> api) {
        return new SchedulerCapabilities.SchedulerMethods(
            requireHandle(bindable(api, "run", 2, scheduler)),
            requireHandle(bindable(api, "runDelayed", 3, scheduler)),
            requireHandle(bindable(api, "runAtFixedRate", 4, scheduler)),
            requireHandle(bindable(api, "cancelTasks", 1, scheduler)),
            0
        );
    }

    /**
     * 异步调度器的三个方法都多一个 {@code TimeUnit} 参数，一次性任务方法名在各版本间也有差异。
     */
    private static SchedulerCapabilities.SchedulerMethods bindAsync(Object scheduler, Class<?> api) {
        MethodHandle runNow = bindable(api, "runNow", 2, scheduler).orElse(null);
        MethodHandle oneShot = runNow != null
            ? runNow
            : requireHandle(bindable(api, "run", 2, scheduler));

        return new SchedulerCapabilities.SchedulerMethods(
            oneShot,
            requireHandle(bindable(api, "runDelayed", 4, scheduler)),
            requireHandle(bindable(api, "runAtFixedRate", 5, scheduler)),
            requireHandle(bindable(api, "cancelTasks", 1, scheduler)),
            1
        );
    }

    private static Optional<MethodHandle> bindable(
        Class<?> api,
        String methodName,
        int parameterCount,
        Object scheduler
    ) {
        return SchedulerApiTypes.resolveMethod(api, methodName, parameterCount)
            .flatMap(method -> SchedulerApiTypes.bindable(method, scheduler));
    }

    private static MethodHandle requireHandle(Optional<MethodHandle> handle) {
        return handle.orElseThrow(() -> new IllegalStateException("Required scheduler method is missing"));
    }

    private static MethodHandle bind(Method method, Object receiver, String description) {
        return SchedulerApiTypes.bindable(method, receiver).orElseThrow(
            () -> new IllegalStateException(description + " is present but cannot be bound")
        );
    }

    private static Throwable unwrap(Throwable failure) {
        return failure.getCause() != null ? failure.getCause() : failure;
    }
}
