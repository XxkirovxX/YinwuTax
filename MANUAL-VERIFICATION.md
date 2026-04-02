# YinwuTax 本地手工验证清单

## 1. 验证目标

本清单用于本地验证 `YinwuTax` 在以下服务端核心上的运行表现：

- `Spigot`
- `Paper`
- `Folia`

重点验证以下能力：

- 插件加载与依赖探测
- 定时结算
- 所得税 / 人头税 / 财富税
- 免税机制
- 管理命令
- `PlaceholderAPI`
- 重载与数据持久化
- `Folia` 下的基础线程安全表现

## 2. 测试前准备

每个核心都准备一套独立测试目录，避免互相污染数据。

建议目录：

- `servers/spigot/`
- `servers/paper/`
- `servers/folia/`

每套服务端准备：

- 对应核心的服务端 `jar`
- `plugins/YinwuTax.jar`
- `plugins/VaultUnlocked.jar`
- `plugins/iConomyUnlocked.jar`
- `plugins/PlaceholderAPI.jar`

建议至少准备 4 个测试账号：

- `admin`
- `user_a`
- `user_b`
- `alt_a`

其中：

- `user_a` 和 `alt_a` 尽量使用同一出口 IP
- `user_b` 使用不同 IP 更容易验证人头税差异

## 3. 首次启动检查

在 `Spigot`、`Paper`、`Folia` 上分别执行：

1. 启动服务端。
2. 确认控制台没有 `YinwuTax` 启动报错。
3. 确认生成这些文件：
   - `plugins/YinwuTax/config.yml`
   - `plugins/YinwuTax/messages.yml`
   - `plugins/YinwuTax/storage.yml`
   - `plugins/YinwuTax/tax-data.yml`
4. 执行 `/yinwutax status <player>`，确认命令可用。
5. 如安装了 `PlaceholderAPI`，执行 `/papi ecloud` 相关命令不是必须，但至少确认插件未因 PAPI 缺失/存在而报错。

通过标准：

- 插件成功启用
- 无明显异常堆栈
- 默认配置与数据文件自动生成

## 4. 所得税验证

### 4.1 基础梯度税率

目标：确认先按收入梯度得出基础税率。

步骤：

1. 把 `income-tax.period` 临时调短，例如 `1m`。
2. 配置至少 3 档所得税梯度。
3. 让 `user_a` 在一个周期内获得低档收入。
4. 让 `user_b` 在一个周期内获得高档收入。
5. 等待结算周期到达，或执行手动结算命令。

检查点：

- `user_a` 结算税率命中低档
- `user_b` 结算税率命中高档
- 税额 = 周期收入 × 基础税率

### 4.2 转账收入计税

目标：确认普通转账收入计入应税收入。

步骤：

1. 让 `user_b` 向 `user_a` 执行转账。
2. 在结算前查看 `user_a` 的收入状态。
3. 到达结算周期后观察税额。

检查点：

- `user_a` 的这笔收款被计入周期总收入
- `user_b` 的付款不会被作为正收入计入

### 4.3 小号互转计税

目标：确认同 IP / 同人小号互转不被排除。

步骤：

1. 用 `user_a` 向 `alt_a` 转账。
2. 再由 `alt_a` 向 `user_a` 转账。
3. 到达结算周期。

检查点：

- 两边收到的钱都被算作各自收入
- 不存在“小号互转自动免税”现象

### 4.4 管理员操作排除

目标：确认 `set`、`reset`、管理员手动操作不算收入。

步骤：

1. 用管理员对 `user_a` 执行 `money grant`。
2. 用管理员对 `user_a` 执行 `money set`。
3. 用管理员对 `user_a` 执行 `money reset`。
4. 观察周期结算前后的收入结果。

检查点：

- 这些管理员操作不会计入应税收入
- 普通玩家交易仍正常计税

## 5. 人头税验证

### 5.1 默认 IP 关联倍率

目标：确认先算基础所得税率，再乘人头税附加倍率。

步骤：

1. 让 `user_a` 和 `alt_a` 在同一 IP 登录。
2. 给 `user_a` 制造一笔明确收入。
3. 记录其基础所得税率。
4. 触发结算。

检查点：

- 最终所得税率 = `baseIncomeTaxRate * (1 + headcountExtraRate * extraAccounts)`
- 是先算梯度，再乘倍率
- 不应出现“先把收入乘倍率再找税档”的行为

### 5.2 人工覆写

目标：确认申诉/覆写命令生效。

步骤：

1. 给 `user_a` 设置人头税覆写值。
2. 再次触发所得税结算。
3. 清除覆写。
4. 再结算一次。

检查点：

- 覆写后使用人工值
- 清除后恢复按历史同 IP 账号数量计算

## 6. 财富税验证

目标：确认按余额档位计税，而不是按收入计税。

步骤：

1. 把 `wealth-tax.period` 临时改为 `1m`。
2. 分别让 `user_a`、`user_b` 保持不同余额档位。
3. 到达结算周期。

检查点：

- 税率命中余额档位
- 税额 = 当前余额 × 财富税税率
- 与本周期是否有收入无关

## 7. 免税机制验证

### 7.1 整单免除

目标：确认免税是整单直接免除，不是部分减免。

步骤：

1. 给 `user_a` 发放 1 次免税次数。
2. 给 `user_a` 制造一张明确可见的税单。
3. 执行结算。

检查点：

- 本张税单直接不扣钱
- 不是“先扣后返”
- 不是“只减一部分”

### 7.2 一次只免一张

目标：确认一张免税次数只覆盖一张税单。

步骤：

1. 给 `user_a` 只发放 1 次免税次数。
2. 让其同时满足所得税和财富税的征税条件。
3. 触发一次完整结算流程。

检查点：

- 只会免掉其中当前执行的那一张税单
- 免税次数消耗后变为 0
- 另一张税单不能继续共享这一次免税

## 8. 命令验证

分别验证这些命令：

- `/yinwutax status`
- `/yinwutax status <player>`
- `/yinwutax reload`
- `/yinwutax exempt grant <player> <count>`
- `/yinwutax exempt take <player> <count>`
- `/yinwutax headcount set <player> <count>`
- `/yinwutax headcount clear <player>`
- `/yinwutax settle income`
- `/yinwutax settle wealth`

检查点：

- 有权限时可以执行
- 无权限时被拒绝
- 非法数字参数会提示错误
- 功能关闭时相关命令会提示已禁用

## 9. PlaceholderAPI 验证

如果安装了 `PlaceholderAPI`，至少验证这些占位符：

- `%yinwutax_income_rate%`
- `%yinwutax_wealth_rate%`
- `%yinwutax_exemptions%`
- `%yinwutax_last_tax_amount%`
- `%yinwutax_linked_accounts%`

建议方法：

1. 用一个测试记分板插件或聊天占位符输出。
2. 在执行转账、发放免税、设置人头税覆写后再次读取。

检查点：

- 占位符能解析
- 数值会随状态变化更新

## 10. 重载与持久化验证

### 10.1 重载

步骤：

1. 修改 `config.yml` 某个明显参数，例如税率。
2. 执行 `/yinwutax reload`。
3. 再触发相关税单。

检查点：

- 新配置生效
- 不出现重复结算
- 不出现重复监听导致的收入重复累计

### 10.2 关服重启持久化

步骤：

1. 制造以下状态：
   - 历史 IP 关联
   - 人头税覆写
   - 剩余免税次数
   - 最近一次税额
2. 正常停服。
3. 重启服务端。

检查点：

- 以上数据仍存在
- 不会全部重置

## 11. Folia 专项检查

仅在 `Folia` 上额外验证：

1. 多个玩家同时在线并频繁转账。
2. 同时执行：
   - 登录/下线
   - 手动结算
   - `/yinwutax reload`
3. 观察控制台。

检查点：

- 不出现线程违规异常
- 不出现明显并发修改异常
- 不出现重复扣税 / 漏扣税

## 12. 边界场景

至少覆盖：

- 零收入玩家
- 负余额玩家
- 离线玩家被结算
- 多个账号共享同 IP
- 覆写后恢复默认
- 功能关闭后命令行为
- `PlaceholderAPI` 缺失时插件仍能启动
- `iConomyUnlocked` 缺失时插件是否能降级运行

## 13. 建议记录模板

每个核心都记录：

- 核心类型：`Spigot` / `Paper` / `Folia`
- 核心版本：
- `YinwuTax` 版本：
- 依赖插件版本：
- 是否通过：
- 失败现象：
- 控制台关键日志：
- 复现步骤：

## 14. 最终验收标准

以下条件同时满足才算本地手工验证通过：

1. `Spigot`、`Paper`、`Folia` 三套核心都能正常启动插件。
2. 所得税、人头税、财富税、免税机制都能按规则工作。
3. 命令权限、参数校验、重载、持久化表现正常。
4. `PlaceholderAPI` 存在时占位符可读，缺失时插件不崩。
5. `Folia` 下没有明显线程安全报错。
