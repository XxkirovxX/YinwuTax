# 更新日志

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/) 命名版本号。

## [1.0.3]

### 修复

- 修复服务端未安装 `VaultUnlocked` 时执行 `/yinwutax status` 抛出
  `NoClassDefFoundError: net/milkbowl/vault2/economy/Economy`（表现为 "Command exception"）的问题。
  该错误属于 `Error`，业务代码的 `catch (Exception)` 无法捕获，因此此前会直接冒到命令层。

### 变更

- 经济网关改为完全反射实现（`ReflectiveEconomyGateway` + `EconomyGatewayFactory`），
  类文件中不再出现任何 Vault 类型引用；按 `VaultUnlocked` → 经典 `Vault` → 无经济插件 的顺序探测，
  选定结果会打印一行日志说明实际使用的 provider。
- 未安装经济插件时改为**安全降级**：插件正常启用，余额读作 0，税额为 0，
  并在控制台明确提示需要安装哪个依赖，而不是让命令报错或定时结算崩溃。
- 命令层的经济数值读取增加兜底，个别数值读取失败只显示 0 并记录警告，不再中断整条命令。
- 移除旧的 `VaultUnlockedEconomyAdapter`（硬引用 Vault 类型的实现）。

## [1.0.2]

### 新增

- Maven Wrapper（`mvnw` / `mvnw.cmd`），构建不再依赖本机预装 Maven。
- GitHub Actions 构建流程：JDK 21 上在 Linux 与 Windows 双平台执行 `clean verify`，并上传插件 jar。
- `LICENSE`（GNU General Public License v3.0）、`.gitattributes`、`CHANGELOG.md`。
- `TaskDispatcher` 增加 `runAsync` 与 `scheduleGlobalDelayed`，异步调度路径可用。
- README 补齐运行环境、安装、构建、命令与权限、占位符、配置文件与兼容性说明。

### 变更

- 调度层按 [调度器兼容设计](docs/superpowers/specs/2026-04-27-scheduler-compat-design.md) 重构：
  - 新增 `SchedulerBackendResolver` / `SchedulerProbe` / `SchedulerCapabilities` / `SchedulerResolution`，在插件启用时一次性探测服务端调度能力。
  - 拆出 `FoliaSchedulerBackend` 与 `BukkitSchedulerBackend` 两个执行后端。
  - Folia 调度器全部改为反射访问，Paper/Folia 类型不再出现在公开契约中。
  - 探测到 Folia 调度器但方法无法绑定（或只具备其中一个调度器）时，显式记录警告后再回落到 Bukkit 调度，不再静默回落。
- `plugin.yml` 移除 AI 工具署名，补充 `website`。
- `pom.xml` 补充许可证、开发者与 SCM 元数据。

### 修复

- 修复在 Spigot/Bukkit 服务端启用时因直接调用 `Server#getGlobalRegionScheduler()` 而抛出 `NoSuchMethodError`、插件无法启动的问题。
- 修复 Folia 后端定时任务无法取消的问题（`ScheduledTask#cancel()` 的方法句柄此前解析失败并被静默忽略）。

## [1.0.1]

- 包含所得税、人头税、财富税与免税机制，支持 `Paper` / `Folia`。
