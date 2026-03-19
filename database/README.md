# 网络监测系统 - 数据库初始化脚本说明

## 📁 文件位置

```
network-monitor-parent/
└── database/
    └── init.sql    # 数据库初始化脚本
```

## 📋 脚本内容概览

该 SQL 脚本完整实现了网络监测系统所需的所有数据库对象，包括：

### 1. 数据库创建
- 创建 `network_monitor` 数据库
- 字符集：`utf8mb4`
- 排序规则：`utf8mb4_unicode_ci`

### 2. 数据表结构（5 张核心表）

#### 2.1 sys_traffic_monitor（流量监测表）
**用途**：存储所有 HTTP 流量数据

**核心字段**：
- `id`: 主键 ID（自增）
- `request_time`: 请求时间
- `source_ip`, `target_ip`: 源/目标 IP 地址
- `source_port`, `target_port`: 源/目标端口
- `http_method`: HTTP 方法（GET/POST/PUT/DELETE 等）
- `protocol`: 协议类型（HTTP/1.0、HTTP/1.1、HTTP/2、HTTPS 等）
- `request_uri`, `query_params`: 请求 URI 和查询参数
- `request_headers`, `request_body`: 请求头和请求体
- `response_status`, `response_body`, `response_time`: 响应信息
- `content_type`, `user_agent`, `cookie`: 其他请求信息
- `create_time`, `update_time`: 时间戳

**索引设计**：
- `idx_source_ip`: 源 IP 查询优化
- `idx_target_ip`: 目标 IP 查询优化
- `idx_request_time`: 时间范围查询优化
- `idx_http_method`: HTTP 方法筛选优化
- `idx_request_uri`: URI 查询优化（前缀索引 255）

#### 2.2 sys_attack_monitor（攻击监测表）
**用途**：存储所有检测到的攻击事件

**核心字段**：
- `id`: 主键 ID（自增）
- `traffic_id`: 关联流量 ID（外键关联）
- `attack_type`: 攻击类型（SQL_INJECTION/XSS/COMMAND_INJECTION/DDOS 等）
- `risk_level`: 风险等级（HIGH/MEDIUM/LOW）
- `confidence`: 攻击置信度（0-100）
- `rule_id`: 命中规则 ID
- `rule_content`: 命中规则内容
- `source_ip`, `target_ip`, `target_uri`: 攻击源和目标
- `attack_content`: 攻击内容（解码后）
- `handled`: 是否已处理（0-未处理，1-已处理）
- `handle_time`, `handle_remark`: 处理信息
- `create_time`, `update_time`: 时间戳

**索引设计**：
- `idx_traffic_id`: 关联流量查询
- `idx_source_ip`: 攻击源 IP 统计
- `idx_attack_type`: 攻击类型分析
- `idx_risk_level`: 风险等级筛选
- `idx_handled`: 处理状态筛选
- `idx_create_time`: 时间排序
- `idx_rule_id`: 规则命中统计

#### 2.3 sys_vulnerability_monitor（漏洞监测表）
**用途**：存储预设漏洞及验证状态

**核心字段**：
- `id`: 主键 ID（自增）
- `vuln_name`: 漏洞名称
- `vuln_type`: 漏洞类型（SQL 注入/XSS/命令注入等）
- `vuln_level`: 漏洞等级（CRITICAL/HIGH/MEDIUM/LOW）
- `vuln_path`: 预设漏洞接口路径
- `verify_status`: 验证状态（0-未验证，1-已验证可利用）
- `first_attack_time`, `last_attack_time`: 首次/最近攻击时间
- `attack_count`: 被攻击次数
- `attack_ids`: 关联攻击 ID 列表（逗号分隔）
- `description`: 漏洞描述
- `fix_suggestion`: 修复建议
- `create_time`, `update_time`: 时间戳

**索引设计**：
- `idx_vuln_type`: 漏洞类型筛选
- `idx_vuln_level`: 漏洞等级筛选
- `idx_verify_status`: 验证状态筛选
- `idx_vuln_path`: 漏洞路径匹配（前缀索引 255）

#### 2.4 sys_defense_monitor（防御日志表）
**用途**：存储所有防御操作日志

**核心字段**：
- `id`: 主键 ID（自增）
- `attack_id`: 关联攻击 ID
- `traffic_id`: 关联流量 ID
- `defense_type`: 防御类型（BLOCK_IP/RATE_LIMIT/BLOCK_REQUEST 等）
- `defense_action`: 防御动作（ADD/REMOVE/UPDATE）
- `defense_target`: 防御对象（IP 地址/规则 ID）
- `defense_reason`: 防御原因
- `expire_time`: 防御过期时间
- `execute_status`: 执行状态（0-失败，1-成功）
- `execute_result`: 执行结果信息
- `operator`: 操作人（SYSTEM/MANUAL）
- `create_time`, `update_time`: 时间戳

**索引设计**：
- `idx_attack_id`: 关联攻击查询
- `idx_traffic_id`: 关联流量查询
- `idx_defense_type`: 防御类型统计
- `idx_execute_status`: 执行状态筛选
- `idx_operator`: 操作人统计
- `idx_create_time`: 时间排序

#### 2.5 sys_monitor_rule（攻击规则表）
**用途**：存储攻击检测规则

**核心字段**：
- `id`: 主键 ID（自增）
- `rule_name`: 规则名称
- `attack_type`: 攻击类型（SQL 注入/XSS/命令注入/DDoS 等）
- `rule_content`: 规则内容（正则表达式/关键词）
- `description`: 规则描述
- `risk_level`: 风险等级（HIGH/MEDIUM/LOW）
- `enabled`: 启用状态（0-禁用，1-启用）
- `priority`: 规则优先级（数字越小优先级越高）
- `hit_count`: 命中次数统计
- `last_hit_time`: 最后命中时间
- `create_time`, `update_time`: 时间戳

**索引设计**：
- `idx_attack_type`: 攻击类型分类
- `idx_risk_level`: 风险等级筛选
- `idx_enabled`: 启用状态筛选
- `idx_priority`: 优先级排序

### 3. 视图（2 个）

#### 3.1 v_attack_detail（攻击事件关联视图）
关联攻击、流量、防御日志信息，提供全链路数据追溯能力。

**包含字段**：
- 攻击信息：attack_id, traffic_id, attack_type, risk_level, confidence 等
- 流量信息：traffic_request_time, traffic_source_ip, target_ip, http_method 等
- 防御信息：defense_id, defense_type, defense_status, operator 等

#### 3.2 v_vulnerability_stat（漏洞攻击统计视图）
统计漏洞被攻击情况，提供漏洞验证状态和实际攻击次数对比。

**包含字段**：
- 漏洞信息：vuln_id, vuln_name, vuln_type, vuln_level, verify_status
- 统计信息：attack_count, first_attack_time, last_attack_time, actual_attack_count

### 4. 触发器（1 个）

#### trg_update_vuln_stat_after_attack
**触发时机**：INSERT ON sys_attack_monitor

**功能**：当新增攻击记录时，自动更新匹配的漏洞统计信息
- 自动匹配目标 URI 包含漏洞路径的攻击
- 更新攻击次数、首次/最近攻击时间
- 当攻击次数≥1 时，自动标记为"已验证可利用"

### 5. 初始化数据

#### 5.1 攻击检测规则（27 条）

**SQL 注入检测规则（7 条）**：
- UNION SELECT 注入检测
- OR 1=1 注入检测
- DROP TABLE 恶意注入检测
- SLEEP/BENCHMARK时间盲注检测
- SQL 注释符号检测
- 十六进制编码注入检测

**XSS 攻击检测规则（8 条）**：
- script 标签注入检测
- javascript 协议注入检测
- onerror/onload/onclick事件处理器检测
- alert 弹窗函数检测
- document.cookie Cookie 窃取检测
- img 标签 onerror 注入检测

**命令注入检测规则（6 条）**：
- 管道符/分号分隔命令注入检测
- 反引号/$() 命令执行检测
- 常见系统命令检测（Linux/Windows）

**路径遍历检测规则（4 条）**：
- 父目录引用（../ 或 ..\）检测
- Linux/Windows敏感文件路径检测
- URL 编码绕过检测

**文件包含检测规则（4 条）**：
- PHP 文件包含函数检测
- data://、php://、file:// 协议检测

**DDoS 攻击检测规则（1 条）**：
- 基于请求频率的 DDoS 检测（需配合计数器）

#### 5.2 预设漏洞信息（8 条）

| 漏洞名称 | 类型 | 等级 | 路径 | 描述 |
|---------|------|------|------|------|
| SQL 注入漏洞 - 登录绕过 | SQL_INJECTION | HIGH | /api/login | 登录接口 SQL 注入 |
| XSS 漏洞 - 搜索框反射 | XSS | MEDIUM | /api/search | 搜索功能 XSS |
| 命令注入漏洞 - 文件下载 | COMMAND_INJECTION | CRITICAL | /api/download | 文件下载命令注入 |
| 路径遍历漏洞 - 文件读取 | PATH_TRAVERSAL | HIGH | /api/file/read | 文件读取路径遍历 |
| 文件包含漏洞 - 远程文件 | FILE_INCLUSION | CRITICAL | /api/template/load | 模板加载文件包含 |
| 未授权访问 - 管理接口 | UNAUTHORIZED_ACCESS | HIGH | /admin/* | 管理接口未授权 |
| 信息泄露 - 错误详情 | INFORMATION_DISCLOSURE | LOW | /api/* | 错误信息泄露 |
| SSRF 漏洞 - 内网探测 | SSRF | HIGH | /api/fetch | URL 抓取 SSRF |

## 🚀 使用方法

### 方式一：命令行执行
```bash
# 使用 MySQL 客户端执行
mysql -u root -p < database/init.sql

# 或登录 MySQL 后执行
mysql -u root -p
source database/init.sql
```

### 方式二：MySQL Workbench
1. 打开 MySQL Workbench
2. 连接数据库
3. File → Open SQL Script
4. 选择 `database/init.sql`
5. 点击 Execute

### 方式三：Navicat
1. 打开 Navicat
2. 连接数据库
3. 工具 → 运行 SQL 文件
4. 选择 `database/init.sql`
5. 开始执行

## ✅ 验证安装

执行以下 SQL 验证数据库初始化是否成功：

```sql
-- 1. 查看数据库
SHOW DATABASES LIKE 'network_monitor';

-- 2. 查看表数量
SELECT COUNT(*) AS table_count 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'network_monitor';
-- 预期结果：5

-- 3. 查看规则数量
SELECT COUNT(*) AS rule_count FROM sys_monitor_rule;
-- 预期结果：27

-- 4. 查看漏洞数量
SELECT COUNT(*) AS vuln_count FROM sys_vulnerability_monitor;
-- 预期结果：8

-- 5. 查看规则分类统计
SELECT attack_type, COUNT(*) AS count 
FROM sys_monitor_rule 
GROUP BY attack_type;

-- 6. 查看漏洞等级分布
SELECT vuln_level, COUNT(*) AS count 
FROM sys_vulnerability_monitor 
GROUP BY vuln_level;
```

## 📊 数据库设计特点

### 1. 字段类型选择
- **主键**：使用 `BIGINT` 自增，支持海量数据
- **时间字段**：使用 `DATETIME`，精确到秒
- **IP 地址**：使用 `VARCHAR(50)`，兼容 IPv6
- **文本内容**：使用 `TEXT`/`LONGTEXT`，支持大文本
- **状态字段**：使用 `TINYINT`，节省空间

### 2. 索引优化策略
- **单列索引**：高频查询字段单独建立索引
- **前缀索引**：长文本字段使用前缀索引（如 URI 前 255 字符）
- **时间索引**：所有时间范围查询字段建立索引
- **状态索引**：筛选类字段建立索引（如 handled, enabled）

### 3. 字符集设计
- 使用 `utf8mb4` 字符集，支持 Emoji 等特殊字符
- 排序规则使用 `utf8mb4_unicode_ci`，支持多语言

### 4. 时间戳管理
- 所有表包含 `create_time` 和 `update_time`
- `update_time` 使用 `ON UPDATE CURRENT_TIMESTAMP` 自动更新

### 5. 外键设计
- **未使用物理外键**：避免外键约束影响性能
- **使用逻辑外键**：通过字段关联（如 traffic_id, attack_id）
- **应用层维护**：在 Service 层保证数据一致性

## 🔧 扩展建议

### 1. 分区表（可选）
如果数据量巨大，可考虑对流量表和攻击表进行分区：

```sql
-- 按月分区示例
ALTER TABLE sys_traffic_monitor
PARTITION BY RANGE (YEAR(request_time) * 100 + MONTH(request_time)) (
    PARTITION p202401 VALUES LESS THAN (202402),
    PARTITION p202402 VALUES LESS THAN (202403),
    ...
);
```

### 2. 全文索引（可选）
如果需要对请求体、响应体进行全文搜索：

```sql
ALTER TABLE sys_traffic_monitor
ADD FULLTEXT INDEX ft_request_body (request_body);
```

### 3. 系统配置表（可选）
如需动态配置系统参数，可取消 init.sql 中 sys_config 表的注释。

## ⚠️ 注意事项

1. **执行顺序**：脚本会先删除已存在的数据库，请谨慎执行
2. **权限要求**：需要 MySQL 的 CREATE、DROP、INSERT 权限
3. **字符集**：确保 MySQL 服务器支持 utf8mb4 字符集
4. **引擎**：所有表使用 InnoDB 引擎，支持事务
5. **触发器**：触发器会自动更新漏洞统计，无需手动维护

## 📝 版本历史

- **v1.0** (2024): 初始版本，包含 5 张核心表、2 个视图、1 个触发器、27 条规则、8 个预设漏洞

## 📞 技术支持

如有问题，请检查：
1. MySQL 版本是否为 8.0+
2. 字符集是否正确
3. 执行日志是否有错误信息
4. 表结构是否与 Entity 类匹配

---

**数据库初始化脚本已完成！可以开始使用网络监测系统了！** 🎉
