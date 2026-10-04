# 更新日志

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/) 命名版本号。

## [未发布]

### 新增

- 补齐 Maven Wrapper（`mvnw` / `mvnw.cmd`）与 `.mvn/maven.config`，让构建不依赖本机预装 Maven，并复用仓库内的依赖缓存。
- 新增 `LICENSE`（GNU General Public License v3.0）与 `.gitattributes`。
- `TaskDispatcher` 增加 `runAsync` 与 `scheduleGlobalDelayed`，异步调度路径可用。
- README 补齐运行环境、安装、构建、命令与权限、占位符、配置文件与兼容性说明。

### 变更

- 调度层按 [调度器兼容设计](docs/superpowers/specs/2026-04-27-scheduler-compat-design.md) 重构：
  - 新增 `SchedulerBackendResolver` / `SchedulerProbe` / `SchedulerCapabilities` / `SchedulerResolution`，在插件启用时一次性探测服务端调度能力。
  - 拆出 `FoliaSchedulerBackend` 与 `BukkitSchedulerBackend` 两个执行后端。
  - Folia 调度器全部改为反射访问，Paper/Folia 类型不再出现在公开契约中，插件可重新在 Spigot 上启动。
  - 探测到 Folia 调度器但方法无法绑定（或只具备其中一个调度器）时，显式记录警告后再回落到 Bukkit 调度，不再静默回落。
- `plugin.yml` 移除 AI 工具署名，补充 `website`。
- `pom.xml` 补充许可证、开发者与 SCM 元数据。

### 修复

- 修复在 Spigot/Bukkit 服务端启用时因直接调用 `Server#getGlobalRegionScheduler()` 而抛出 `NoSuchMethodError`、插件无法启动的问题。

## [1.0.1]

- 当前发布版本，包含所得税、人头税、财富税与免税机制，支持 `Paper` / `Folia`。
