package org.kirov.plugins.yinwutax.platform.scheduler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 解析 Paper/Folia 的公开 API 类型，缺失时退化为占位类型。
 *
 * <p>类型解析集中在这里，是为了让 {@link SchedulerProbe} 的类文件不出现对这些类型的
 * <b>常量池引用</b>：在 Spigot 等不具备 Folia API 的服务端上，直接引用会让类解析阶段
 * 就抛 {@link NoClassDefFoundError}，而这里只会得到 "类型不存在" 的结论。
 *
 * <p>静态常量放在嵌套类里，只有真正被访问时才触发初始化，因此并不存在提前加载的问题。
 */
final class SchedulerApiTypes {

    /** 缺失时的占位类型，保证后续方法查找稳定失败而不是抛错。 */
    interface MissingSchedulerApi {
    }

    static final Class<?> SERVER = resolve("org.bukkit.Server");
    static final Class<?> GLOBAL_REGION_SCHEDULER = resolve(
        "io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler"
    );
    static final Class<?> ASYNC_SCHEDULER = resolve("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
    static final Class<?> SCHEDULED_TASK = resolve(
        "io.papermc.paper.threadedregions.scheduler.ScheduledTask"
    );

    static final MethodHandles.Lookup PUBLIC_LOOKUP = MethodHandles.publicLookup();

    private SchedulerApiTypes() {
    }

    /**
     * 按名字与参数个数查找公开 API 方法。
     *
     * <p>之所以带参数个数，是因为不同服务端的同名方法参数个数不同（例如
     * {@code AsyncScheduler#runDelayed} 比 {@code GlobalRegionScheduler#runDelayed}
     * 多一个 {@code TimeUnit}）。
     */
    static Optional<Method> resolveMethod(Class<?> api, String methodName, int parameterCount) {
        for (Method method : api.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == parameterCount) {
                return Optional.of(method);
            }
        }

        return Optional.empty();
    }

    /** 把方法绑定到实例上；实现类不是 public 也没关系，句柄声明在公开 API 类型上。 */
    static Optional<MethodHandle> bindable(Method method, Object instance) {
        return unreflect(method).map(handle -> handle.bindTo(instance));
    }

    /**
     * 只做 {@code unreflect}，不绑定接收者：句柄保留「接收者作为第一个参数」的签名。
     */
    static Optional<MethodHandle> unreflect(Method method) {
        try {
            return Optional.of(PUBLIC_LOOKUP.unreflect(method));
        } catch (IllegalAccessException denied) {
            return Optional.empty();
        }
    }

    private static Class<?> resolve(String typeName) {
        try {
            return Class.forName(typeName, false, SchedulerApiTypes.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            return MissingSchedulerApi.class;
        }
    }
}
