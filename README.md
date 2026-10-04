# YinwuTax

[![Build](https://github.com/XxkirovxX/YinwuTax/actions/workflows/build.yml/badge.svg)](https://github.com/XxkirovxX/YinwuTax/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-orange.svg)](pom.xml)

YinwuTax 是一款面向 `Bukkit`、`Paper`、`Folia` 服务端的 Minecraft 税务插件，基于 `VaultUnlocked` 与 `iConomyUnlocked` 构建，提供可配置、可扩展的服务器税收系统。

## 插件简介

YinwuTax 旨在为服务器提供更加清晰、灵活的税务管理能力。插件围绕经济系统运行，支持按配置自动结算税单，并兼顾后续扩展需求，例如 `PlaceholderAPI` 支持与 `Velocity` 桥接预留。

## 插件功能

- **个人所得税**：按真实时间周期统计玩家收入，并根据配置好的梯度税率自动结算。
- **IP 人头税**：基于历史同 IP 账号数量追加附加税率，并支持人工申诉与覆写调整。
- **财富税**：按真实时间周期以玩家当前余额为基础征税，支持独立梯度配置。
- **免税机制**：可为玩家发放免税次数，每次仅对当前一张税单做整单免除。

## 运行环境

| 项目 | 要求 |
| --- | --- |
| 服务端 | `Paper` / `Folia` 1.21.x；`Spigot` 1.21.x（调度层自动回落到 Bukkit 调度器） |
| Java | 21 及以上 |
| 必需依赖 | `VaultUnlocked`、`iConomyUnlocked` |
| 可选依赖 | `PlaceholderAPI`（缺失时插件照常启动，仅不注册占位符） |

## 安装

1. 将 `VaultUnlocked`、`iConomyUnlocked` 放入服务端 `plugins/` 目录。
2. 将构建产物 `target/yinwutax-<version>.jar` 重命名为 `YinwuTax.jar` 后放入 `plugins/` 目录。
3. 启动服务端，插件会生成 `plugins/YinwuTax/` 配置目录。
4. **务必修改** `plugins/YinwuTax/config.yml` 中的 `storage.ip-hash-salt`（默认值 `change-me` 只适合本地测试），否则同 IP 关联统计可被反推。
5. 使用 `/yinwutax reload` 使配置生效。

## 构建

需要 JDK 21 与 Maven。推荐直接使用仓库自带的 Maven Wrapper，无需预装 Maven：

```bash
# Linux / macOS
./mvnw -B clean package

# Windows PowerShell / CMD
.\mvnw.cmd -B clean package
```

若本机已有 Maven，也可以直接执行：

```bash
mvn -B clean package
```

产物说明：

- `target/yinwutax-<version>.jar`：经过 shade 处理、可直接投入使用的插件包。
- `target/original-yinwutax-<version>.jar`：shade 之前的原始包，仅用于排查问题。

只跑测试：

```bash
./mvnw -B test
```

### 依赖下载与离线构建

首次构建需要联网：Maven 会从 `pom.xml` 中声明的仓库（PaperMC、CodeMC、ExtendedClip 等）下载依赖。

本机若存在 `.mvn/repository/` 目录（开发机上用于集中缓存的依赖副本，**未纳入版本库**，因为体积约 95 MB），可以让 Maven 直接复用它：

```bash
./mvnw -B -Dmaven.repo.local=.mvn/repository clean package
```

例如国内网络访问 PaperMC 仓库不稳定时，可以先在能联网的环境执行一次 `dependency:go-offline` 填充该目录，之后离线构建：

```bash
./mvnw -B -Dmaven.repo.local=.mvn/repository dependency:go-offline
./mvnw -B -o -Dmaven.repo.local=.mvn/repository clean package
```

需要注意 `paper-api` 使用的是 `1.21.11-R0.1-SNAPSHOT`，上游清理该快照后在线构建会解析到新版本；需要完全可复现时请把 `pom.xml` 中的 `paper.version` 固定到具体构建号。

## 命令与权限

| 命令 | 说明 | 权限节点 |
| --- | --- | --- |
| `/yinwutax` | 查看自己的税务状态 | `yinwutax.command.status`（仅查看他人时需要） |
| `/yinwutax status <玩家>` | 查看指定玩家税务状态 | `yinwutax.command.status` |
| `/yinwutax reload` | 重载配置并重建服务 | `yinwutax.command.reload` |
| `/yinwutax exempt grant <玩家> <次数>` | 发放免税次数 | `yinwutax.command.exempt` |
| `/yinwutax exempt take <玩家> <次数>` | 收回免税次数 | `yinwutax.command.exempt` |
| `/yinwutax headcount set <玩家> <数量>` | 设置人头税附加账户数覆写值 | `yinwutax.command.headcount` |
| `/yinwutax headcount clear <玩家>` | 清除人头税覆写值 | `yinwutax.command.headcount` |
| `/yinwutax settle income` | 手动触发所得税结算 | `yinwutax.command.settle` |
| `/yinwutax settle wealth` | 手动触发财富税结算 | `yinwutax.command.settle` |

命令别名：`/ytax`。`yinwutax.admin` 拥有全部管理权限。上述权限默认均为 `op`。

## PlaceholderAPI 占位符

| 占位符 | 含义 |
| --- | --- |
| `%yinwutax_income_rate%` | 当前所得税率（含人头税附加） |
| `%yinwutax_wealth_rate%` | 当前财富税率 |
| `%yinwutax_exemptions%` | 剩余免税次数 |
| `%yinwutax_last_tax_amount%` | 最近一次税额 |
| `%yinwutax_linked_accounts%` | 关联账户数量 |

## 配置文件

| 文件 | 说明 |
| --- | --- |
| `config.yml` | 税种开关、结算周期、税档梯度、人头税附加比例、免税上限、IP 哈希盐、Velocity 开关 |
| `storage.yml` | 存储说明文件，解释运行时数据文件用途 |
| `messages.yml` | 默认文案参考。**当前版本不读取该文件**，前台消息以代码内文案为准 |
| `tax-data.yml` | 运行时数据（收入记录、免税次数、人头税覆写、哈希后的 IP 关联历史），由插件自动生成 |

时长格式示例：`30m`、`12h`、`7d`。税率使用小数，例如 `0.03` 表示 3%。税档按 `brackets` 下的书写顺序依次生效，建议从低到高填写。

## 兼容性说明

- 插件在启动时只探测一次服务端的 Folia 调度能力：具备 `GlobalRegionScheduler` 与 `AsyncScheduler` 时走 Folia 调度，否则回落到 Bukkit 调度器；探测到的 Folia 调度器不可用时会显式记录警告再回落。
- `Velocity` 桥接目前仅为预留实现（启用后只输出一条日志），不参与跨服数据同步。
- 修改 `messages.yml` 目前不会改变前台文案，文案配置化尚未接入。

## 开发与验证

- 手工验证清单见 [`MANUAL-VERIFICATION.md`](MANUAL-VERIFICATION.md)，覆盖 Spigot / Paper / Folia 三套核心的功能验证步骤。
- 调度层兼容改造的设计说明见 [`docs/superpowers/specs/2026-04-27-scheduler-compat-design.md`](docs/superpowers/specs/2026-04-27-scheduler-compat-design.md)。

## 作者信息

[YinwuTax]为中国正版Minecraft公益服务器[YinwuRealm](www.yinwurealm.org)插件，开发者为服务器运维团队成员[Xx_Kirov_xX](https://namemc.com/profile/Xx_Kirov_xX.1)

## 开源协议

本项目基于 [GNU General Public License v3.0](LICENSE) 发布，版权归 Xx_Kirov_xX 及 YinwuTax 贡献者所有。
