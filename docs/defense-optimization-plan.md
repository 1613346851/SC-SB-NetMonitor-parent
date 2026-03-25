# 网络监测系统防御联动优化方案

## 文档信息

| 项目   | 内容                               |
| ---- | -------------------------------- |
| 文档版本 | v1.0                             |
| 创建日期 | 2026-03-25                       |
| 适用系统 | network-monitor-parent           |
| 涉及服务 | gateway-service, monitor-service |

***

## 一、问题诊断

### 1.1 防御日志和黑名单记录丢失

#### 问题现象

- DDoS攻击测试后，网关服务返回403（说明封禁生效）
- 监测服务防御日志表中无记录
- 监测服务黑名单表中无记录

#### 根本原因分析

```
当前流程问题：

┌─────────────────────────────────────────────────────────────────┐
│ 监测服务 DefenseDecisionServiceImpl                              │
│ 1. 生成防御命令 ✓                                                │
│ 2. 推送命令到网关 ✓                                              │
│ 3. 记录防御日志 ✗ （缺失！）                                     │
│ 4. 发布黑名单同步事件 ✗ （缺失！）                               │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 网关 DefenseCommandController                                    │
│ 1. 接收防御命令 ✓                                                │
│ 2. 添加IP到黑名单缓存 ✓                                          │
│ 3. 标记IP为DEFENDED状态 ✓                                        │
│ 4. 未推送防御日志到监测服务 ✗                                    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 后续请求到达网关                                                 │
│ 1. IpBlacklistDefenseFilter 检查IP在黑名单中 ✓                  │
│ 2. skipDefenseLog = shouldSkipDefenseAction() = true            │
│ 3. 跳过防御日志推送 ✓ （正确行为，但首次日志已丢失）             │
│ 4. 返回403 ✓                                                    │
└─────────────────────────────────────────────────────────────────┘
```

**结论**：监测服务生成防御决策后，未记录防御日志和发布黑名单同步事件，导致数据库中无记录。

### 1.2 返回403而非429的原因

| 过滤器顺序 | 过滤器名称                    | 状态码     | 触发条件    |
| ----- | ------------------------ | ------- | ------- |
| 0     | IpBlacklistDefenseFilter | **403** | IP在黑名单中 |
| 10    | RequestRateLimitFilter   | **429** | 请求频率超限  |

**结论**：返回403说明IP已在黑名单中，这是正确的行为，说明封禁已生效。

### 1.3 配置单位不统一

| 服务   | 配置项                                          | 当前值 | 单位   |
| ---- | -------------------------------------------- | --- | ---- |
| 网关服务 | gateway.defense.rate-limit.default-threshold | 10  | 次/秒  |
| 监测服务 | ddos.threshold                               | 100 | 次/分钟 |

**问题**：单位不统一导致阈值配置混乱，难以协调联动。

### 1.4 DEFENDED状态行为问题

当前问题：

1. IP进入DEFENDED状态后，仍可能被限流过滤器处理
2. 限流过滤器可能推送重复的限流日志
3. 流量推送未根据状态优化

### 1.5 缺少连续限流触发封禁机制

当前流程：

- 限流触发 → 返回429 → 记录限流日志
- 无升级机制 → 无法自动升级为DDoS攻击封禁

***

## 二、流量统计方案选型分析

### 2.1 方案对比

| 方案      | 描述        | 优点        | 缺点           |
| ------- | --------- | --------- | ------------ |
| **方案A** | 流量表增加统计字段 | 查询方便，结构简单 | 需修改表结构，存储压力大 |
| **方案B** | 网关推送多条记录  | 无需改表      | 数据量大，网络IO高   |
| **方案C** | 网关聚合后推送   | 数据量小，减少压力 | 需新增聚合逻辑      |

### 2.2 行业标准分析

#### 主流WAF/安全网关实践

| 产品          | 流量处理方式 | 说明                |
| ----------- | ------ | ----------------- |
| Cloudflare  | 边缘聚合   | 在边缘节点聚合后上报        |
| AWS WAF     | 采样+聚合  | 高流量时采样，定期聚合上报     |
| Nginx + Lua | 共享内存聚合 | 使用shared dict聚合计数 |
| ModSecurity | 日志聚合   | 日志层面聚合后输出         |

#### 最佳实践原则

1. **边缘聚合**：在网关层进行聚合，减少中心化存储压力
2. **分级处理**：正常流量实时记录，攻击流量聚合统计
3. **状态感知**：根据IP状态决定处理策略
4. **资源优化**：减少网络IO和存储空间

### 2.3 推荐方案：方案C（网关聚合推送）

**选择理由**：

1. **符合行业标准**：主流WAF都采用边缘聚合方式
2. **性能优势**：
   - 减少网络传输量 90%+
   - 减少数据库写入压力 90%+
   - 降低监测服务处理负载
3. **扩展性好**：支持高并发场景，适合DDoS防护
4. **数据完整**：聚合记录保留关键统计信息

**实现方式**：

```sql
-- 流量表增加聚合字段（方案C优化版）
ALTER TABLE `sys_traffic_monitor` ADD COLUMN `request_count` INT DEFAULT 1 COMMENT '请求次数(聚合统计)';
ALTER TABLE `sys_traffic_monitor` ADD COLUMN `state_tag` VARCHAR(20) DEFAULT 'NORMAL' COMMENT 'IP状态标签';
ALTER TABLE `sys_traffic_monitor` ADD COLUMN `is_aggregated` TINYINT DEFAULT 0 COMMENT '是否为聚合记录';
ALTER TABLE `sys_traffic_monitor` ADD COLUMN `aggregate_start_time` DATETIME DEFAULT NULL COMMENT '聚合开始时间';
ALTER TABLE `sys_traffic_monitor` ADD COLUMN `aggregate_end_time` DATETIME DEFAULT NULL COMMENT '聚合结束时间';
```

***

## 三、优化方案详细设计

### 3.1 配置统一方案

#### 3.1.1 统一时间单位为"次/秒"

```sql
-- DDoS防护配置
('ddos.threshold', '20', 'DDoS检测阈值(次/秒)'),
('ddos.detection.window-ms', '1000', 'DDoS检测时间窗口(毫秒)'),
('ddos.rate-limit-trigger-count', '3', '连续限流触发封禁阈值(次)'),
('ddos.rate-limit-trigger-window-seconds', '60', '连续限流检测时间窗口(秒)'),

-- 防御策略配置
('defense.blacklist.default-duration-seconds', '1800', '黑名单默认持续时间(秒)'),
('defense.blacklist.high-risk-duration-seconds', '3600', '高风险攻击封禁时长(秒)'),
('defense.blacklist.critical-risk-duration-seconds', '86400', '严重风险攻击封禁时长(秒)'),

-- 流量推送策略配置
('traffic.push.normal.strategy', 'realtime', 'NORMAL状态推送策略: realtime/sampling'),
('traffic.push.suspicious.strategy', 'sampling', 'SUSPICIOUS状态推送策略'),
('traffic.push.attacking.strategy', 'batch', 'ATTACKING状态推送策略'),
('traffic.push.defended.strategy', 'skip', 'DEFENDED状态推送策略: skip/sampling'),
('traffic.push.batch-interval-ms', '5000', '批量推送间隔(毫秒)'),
('traffic.push.sampling-rate', '10', '采样推送比例(1/N)');
```

#### 3.1.2 配置项分组

| 分组     | 前缀              | 说明          |
| ------ | --------------- | ----------- |
| DDoS防护 | ddos.\*         | DDoS检测和封禁配置 |
| 防御策略   | defense.\*      | 防御动作配置      |
| 流量推送   | traffic.push.\* | 流量数据处理策略    |
| 网关配置   | gateway.\*      | 网关防御开关和阈值   |

### 3.2 防御日志记录修复方案

#### 3.2.1 监测服务修改

**文件**：`monitor-service/src/main/java/com/network/monitor/service/impl/DefenseDecisionServiceImpl.java`

**修改内容**：

```java
@Override
public DefenseCommandDTO generateDefenseDecision(AttackMonitorDTO attackDTO) {
    // ... 现有逻辑 ...
    
    if (commandDTO != null) {
        boolean success = gatewayApiClient.pushDefenseCommand(commandDTO);
        if (success) {
            // 【新增】记录防御日志
            recordDefenseLog(commandDTO, event, attackDTO);
            
            // 【新增】发布黑名单同步事件
            if (commandDTO.getDefenseType() == DefenseCommandDTO.DefenseType.BLACKLIST) {
                publishBlacklistSyncEvent(commandDTO, event);
            }
            
            attackStateCache.markAsDefended(sourceIp, eventId);
            // ... 其他逻辑 ...
        }
    }
}

private void recordDefenseLog(DefenseCommandDTO commandDTO, AttackEventEntity event, AttackMonitorDTO attackDTO) {
    DefenseLogEntity log = new DefenseLogEntity();
    log.setEventId(commandDTO.getEventId());
    log.setDefenseType(convertDefenseType(commandDTO.getDefenseType()));
    log.setDefenseTarget(commandDTO.getSourceIp());
    log.setDefenseReason(commandDTO.getDescription());
    log.setDefenseAction("ADD_BLACKLIST");
    log.setExecuteStatus(1);
    log.setIsFirst(1);  // 标记为首次防御
    log.setOperator("SYSTEM");
    log.setAttackId(attackDTO.getAttackId());
    log.setTrafficId(attackDTO.getTrafficId());
    
    if (commandDTO.getExpireTimestamp() != null) {
        log.setExpireTime(LocalDateTime.ofInstant(
            Instant.ofEpochMilli(commandDTO.getExpireTimestamp()),
            ZoneId.systemDefault()
        ));
    }
    
    defenseLogMapper.insert(log);
    log.info("记录防御日志成功：eventId={}, defenseType={}, target={}", 
        commandDTO.getEventId(), log.getDefenseType(), log.getDefenseTarget());
}

private void publishBlacklistSyncEvent(DefenseCommandDTO commandDTO, AttackEventEntity event) {
    LocalDateTime expireTime = null;
    if (commandDTO.getExpireTimestamp() != null) {
        expireTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(commandDTO.getExpireTimestamp()),
            ZoneId.systemDefault()
        );
    }
    
    BlacklistSyncEvent syncEvent = BlacklistSyncEvent.add(
        this,
        commandDTO.getSourceIp(),
        commandDTO.getDescription(),
        expireTime,
        "SYSTEM"
    );
    eventPublisher.publishEvent(syncEvent);
    log.info("发布黑名单同步事件：ip={}, reason={}", commandDTO.getSourceIp(), commandDTO.getDescription());
}
```

### 3.3 DEFENDED状态优化方案

#### 3.3.1 过滤器执行顺序优化

```
优化后的过滤器执行流程：

┌─────────────────────────────────────────────────────────────────┐
│ 1. TrafficCollectGlobalFilter (优先级: -100)                    │
│    - 采集流量数据                                                │
│    - 根据IP状态决定推送策略：                                    │
│      • NORMAL: 实时推送                                         │
│      • SUSPICIOUS/COOLDOWN: 采样推送                            │
│      • ATTACKING: 批量推送                                      │
│      • DEFENDED: 跳过推送                                       │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 2. IpBlacklistDefenseFilter (优先级: 0)                         │
│    - 检查IP是否在黑名单中                                        │
│    - 如果在黑名单，直接返回403                                   │
│    - DEFENDED状态不推送日志（首次日志已在监测服务记录）          │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 【新增】DefenseStateCheckFilter (优先级: 5)                     │
│    - 检查IP是否处于DEFENDED状态                                  │
│    - 如果是DEFENDED状态，跳过后续限流检测                        │
│    - 直接放行到黑名单过滤器（已在上面处理）                      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ 3. RequestRateLimitFilter (优先级: 10)                          │
│    - 检查IP状态，DEFENDED状态跳过                                │
│    - 检查请求频率是否超过阈值                                    │
│    - 如果超过阈值：                                              │
│      • 返回429                                                  │
│      • 记录限流次数                                              │
│      • 检查是否达到连续限流封禁阈值                              │
└─────────────────────────────────────────────────────────────────┘
```

#### 3.3.2 网关限流过滤器优化

**文件**：`gateway-service/src/main/java/com/network/gateway/filter/defense/RequestRateLimitFilter.java`

**修改内容**：

```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String sourceIp = ServerWebExchangeUtil.extractSourceIp(exchange.getRequest());
    
    // 【新增】检查IP状态，DEFENDED状态跳过限流检测
    if (attackStateCache.isInDefendedState(sourceIp)) {
        log.debug("IP处于DEFENDED状态，跳过限流检测: ip={}", sourceIp);
        return chain.filter(exchange);
    }
    
    // 检查限流
    if (isRateLimited(sourceIp)) {
        // 记录限流次数
        int rateLimitCount = attackStateCache.incrementRateLimitCount(sourceIp);
        
        // 【新增】检查是否达到连续限流封禁阈值
        if (shouldTriggerBlacklist(sourceIp, rateLimitCount)) {
            // 推送DDoS攻击事件到监测服务
            pushDDoSEvent(sourceIp, rateLimitCount);
        }
        
        return buildRateLimitResponse(exchange, sourceIp);
    }
    
    return chain.filter(exchange);
}

private boolean shouldTriggerBlacklist(String ip, int rateLimitCount) {
    int triggerCount = configCache.getIntValue("ddos.rate-limit-trigger-count", 3);
    return rateLimitCount >= triggerCount;
}

private void pushDDoSEvent(String ip, int rateLimitCount) {
    AttackMonitorDTO attackDTO = new AttackMonitorDTO();
    attackDTO.setSourceIp(ip);
    attackDTO.setAttackType("DDOS");
    attackDTO.setRiskLevel("HIGH");
    attackDTO.setConfidence(85);
    attackDTO.setDescription(String.format("连续触发限流%d次，自动升级为DDoS攻击", rateLimitCount));
    
    trafficClient.pushAttackEvent(attackDTO);
    log.warn("IP连续触发限流，推送DDoS事件: ip={}, count={}", ip, rateLimitCount);
}
```

### 3.4 流量推送优化方案

#### 3.4.1 推送策略枚举

```java
public enum TrafficPushStrategy {
    REALTIME,    // 实时推送（NORMAL状态）
    SAMPLING,    // 采样推送（SUSPICIOUS/COOLDOWN状态）
    BATCH,       // 批量推送（ATTACKING状态）
    SKIP         // 跳过推送（DEFENDED状态）
}
```

#### 3.4.2 流量聚合缓存

```java
@Component
public class TrafficAggregateCache {
    
    private final ConcurrentHashMap<String, TrafficAggregate> aggregateMap = new ConcurrentHashMap<>();
    
    public TrafficAggregate getOrAdd(TrafficMonitorDTO traffic) {
        String aggregateKey = buildAggregateKey(traffic);
        return aggregateMap.computeIfAbsent(aggregateKey, k -> {
            TrafficAggregate aggregate = new TrafficAggregate();
            aggregate.setSourceIp(traffic.getSourceIp());
            aggregate.setRequestUri(traffic.getRequestUri());
            aggregate.setHttpMethod(traffic.getHttpMethod());
            aggregate.setUserAgent(traffic.getUserAgent());
            aggregate.setStartTime(LocalDateTime.now());
            return aggregate;
        });
    }
    
    private String buildAggregateKey(TrafficMonitorDTO traffic) {
        return String.format("%s|%s|%s|%s",
            traffic.getSourceIp(),
            traffic.getRequestUri(),
            traffic.getHttpMethod(),
            traffic.getUserAgent() != null ? traffic.getUserAgent().hashCode() : ""
        );
    }
}

@Data
public class TrafficAggregate {
    private String sourceIp;
    private String requestUri;
    private String httpMethod;
    private String userAgent;
    private LocalDateTime startTime;
    private AtomicInteger count = new AtomicInteger(0);
    private AtomicInteger errorCount = new AtomicInteger(0);
    
    public void increment() {
        count.incrementAndGet();
    }
    
    public void incrementError() {
        errorCount.incrementAndGet();
    }
}
```

#### 3.4.3 流量推送逻辑

```java
public void pushTraffic(TrafficMonitorDTO traffic) {
    IpStateEntry state = attackStateCache.getStateEntry(traffic.getSourceIp());
    
    switch (state.getState()) {
        case NORMAL:
            strategy = TrafficPushStrategy.REALTIME;
            break;
        case SUSPICIOUS:
        case COOLDOWN:
            strategy = TrafficPushStrategy.SAMPLING;
            break;
        case ATTACKING:
            strategy = TrafficPushStrategy.BATCH;
            break;
        case DEFENDED:
            strategy = TrafficPushStrategy.SKIP;
            break;
    }
    
    switch (strategy) {
        case REALTIME:
            doRealtimePush(traffic);
            break;
        case SAMPLING:
            doSamplingPush(traffic);
            break;
        case BATCH:
            doBatchPush(traffic);
            break;
        case SKIP:
            log.debug("DEFENDED状态，跳过流量推送: ip={}", traffic.getSourceIp());
            break;
    }
}

private void doSamplingPush(TrafficMonitorDTO traffic) {
    TrafficAggregate aggregate = aggregateCache.getOrAdd(traffic);
    int count = aggregate.increment();
    
    int samplingRate = configCache.getIntValue("traffic.push.sampling-rate", 10);
    if (count % samplingRate == 1) {
        traffic.setRequestCount(count);
        traffic.setStateTag(state.name());
        trafficClient.pushTraffic(traffic);
    }
}

private void doBatchPush(TrafficMonitorDTO traffic) {
    batchQueue.add(traffic);
}

@Scheduled(fixedRateString = "${traffic.push.batch-interval-ms:5000}")
public void flushBatchQueue() {
    if (batchQueue.isEmpty()) {
        return;
    }
    
    List<TrafficMonitorDTO> batch = new ArrayList<>();
    while (!batchQueue.isEmpty()) {
        batch.add(batchQueue.poll());
    }
    
    Map<String, List<TrafficMonitorDTO>> groupedByIp = batch.stream()
        .collect(Collectors.groupingBy(TrafficMonitorDTO::getSourceIp));
    
    for (Map.Entry<String, List<TrafficMonitorDTO>> entry : groupedByIp.entrySet()) {
        TrafficMonitorDTO aggregated = aggregateTrafficList(entry.getValue());
        trafficClient.pushTraffic(aggregated);
    }
}
```

### 3.5 连续限流触发封禁方案

#### 3.5.1 状态机扩展

```java
// IpAttackStateEntry 新增字段
private int rateLimitCount;           // 限流触发次数
private long rateLimitWindowStart;    // 限流计数窗口开始时间

public int incrementRateLimitCount() {
    long now = System.currentTimeMillis();
    int windowSeconds = configCache.getIntValue("ddos.rate-limit-trigger-window-seconds", 60);
    
    if (now - rateLimitWindowStart > windowSeconds * 1000) {
        rateLimitCount = 1;
        rateLimitWindowStart = now;
    } else {
        rateLimitCount++;
    }
    
    return rateLimitCount;
}
```

#### 3.5.2 自动升级逻辑

```
限流触发流程：

请求到达 → 限流检测 → 超过阈值？
                           ↓ 是
                    返回429，记录限流次数
                           ↓
                    检查连续限流次数 >= 3？
                           ↓ 是
                    推送DDoS攻击事件到监测服务
                           ↓
                    监测服务生成防御决策
                           ↓
                    推送BLACKLIST命令到网关
                           ↓
                    网关执行封禁，标记DEFENDED
```

***

## 四、数据库变更方案

### 4.1 配置表新增配置项

**文件**：`database/init_all.sql`

```sql
-- ============================================================
-- 4.3.2 DDoS防护配置
-- ============================================================
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('ddos.threshold', '20', 'DDoS检测阈值(次/秒)'),
('ddos.detection.window-ms', '1000', 'DDoS检测时间窗口(毫秒)'),
('ddos.rate-limit-trigger-count', '3', '连续限流触发封禁阈值(次)'),
('ddos.rate-limit-trigger-window-seconds', '60', '连续限流检测时间窗口(秒)');

-- ============================================================
-- 4.3.3 防御策略配置
-- ============================================================
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('defense.blacklist.default-duration-seconds', '1800', '黑名单默认持续时间(秒)'),
('defense.blacklist.high-risk-duration-seconds', '3600', '高风险攻击封禁时长(秒)'),
('defense.blacklist.critical-risk-duration-seconds', '86400', '严重风险攻击封禁时长(秒)');

-- ============================================================
-- 4.3.4 流量推送策略配置
-- ============================================================
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('traffic.push.normal.strategy', 'realtime', 'NORMAL状态推送策略: realtime/sampling'),
('traffic.push.suspicious.strategy', 'sampling', 'SUSPICIOUS状态推送策略'),
('traffic.push.attacking.strategy', 'batch', 'ATTACKING状态推送策略'),
('traffic.push.defended.strategy', 'skip', 'DEFENDED状态推送策略: skip/sampling'),
('traffic.push.batch-interval-ms', '5000', '批量推送间隔(毫秒)'),
('traffic.push.sampling-rate', '10', '采样推送比例(1/N)');
```

### 4.2 流量表结构变更

```sql
-- ============================================================
-- 2.1 流量监测表结构优化
-- 新增聚合统计字段
-- ============================================================
ALTER TABLE `sys_traffic_monitor` ADD COLUMN `request_count` INT DEFAULT 1 COMMENT '请求次数(聚合统计)' AFTER `cookie`;
ALTER TABLE `sys_traffic_monitor` ADD COLUMN `state_tag` VARCHAR(20) DEFAULT 'NORMAL' COMMENT 'IP状态标签' AFTER `request_count`;
ALTER TABLE `sys_traffic_monitor` ADD COLUMN `is_aggregated` TINYINT DEFAULT 0 COMMENT '是否为聚合记录' AFTER `state_tag`;
ALTER TABLE `sys_traffic_monitor` ADD COLUMN `aggregate_start_time` DATETIME DEFAULT NULL COMMENT '聚合开始时间' AFTER `is_aggregated`;
ALTER TABLE `sys_traffic_monitor` ADD COLUMN `aggregate_end_time` DATETIME DEFAULT NULL COMMENT '聚合结束时间' AFTER `aggregate_start_time`;

-- 添加索引
ALTER TABLE `sys_traffic_monitor` ADD INDEX `idx_state_tag` (`state_tag`);
ALTER TABLE `sys_traffic_monitor` ADD INDEX `idx_is_aggregated` (`is_aggregated`);
```

### 4.3 删除旧配置

```sql
-- 删除旧的DDoS配置（单位不统一）
DELETE FROM `sys_config` WHERE `config_key` = 'ddos.threshold';
```

***

## 五、执行阶段规划

### 阶段一：核心问题修复（高优先级）

**目标**：修复防御日志和黑名单记录丢失问题

| 序号  | 任务                                  | 涉及文件            | 预计工时 |
| --- | ----------------------------------- | --------------- | ---- |
| 1.1 | 修改DefenseDecisionServiceImpl，增加日志记录 | monitor-service | 1h   |
| 1.2 | 增加黑名单同步事件发布逻辑                       | monitor-service | 0.5h |
| 1.3 | 单元测试验证                              | monitor-service | 0.5h |

**验收标准**：

- DDoS攻击后，防御日志表有记录
- 黑名单表有记录
- 网关返回403

### 阶段二：DEFENDED状态优化（高优先级）

**目标**：优化DEFENDED状态下的过滤器行为

| 序号  | 任务                                    | 涉及文件            | 预计工时 |
| --- | ------------------------------------- | --------------- | ---- |
| 2.1 | 修改RequestRateLimitFilter，DEFENDED状态跳过 | gateway-service | 0.5h |
| 2.2 | 新增DefenseStateCheckFilter             | gateway-service | 1h   |
| 2.3 | 优化TrafficCollectGlobalFilter推送逻辑      | gateway-service | 1h   |
| 2.4 | 集成测试                                  | 全部              | 0.5h |

**验收标准**：

- DEFENDED状态不再触发限流
- DEFENDED状态不再推送限流日志
- 流量推送根据状态分级处理

### 阶段三：配置统一（中优先级）

**目标**：统一配置单位，完善系统配置页面

| 序号  | 任务                            | 涉及文件              | 预计工时 |
| --- | ----------------------------- | ----------------- | ---- |
| 3.1 | 修改init\_all.sql，新增配置项         | database          | 0.5h |
| 3.2 | 修改DDoSDetectServiceImpl，使用新配置 | monitor-service   | 0.5h |
| 3.3 | 完善系统配置页面，分组展示                 | monitor-service前端 | 1h   |
| 3.4 | 配置缓存同步逻辑                      | monitor-service   | 0.5h |

**验收标准**：

- 所有阈值单位统一为"次/秒"
- 系统配置页面展示完整
- 配置修改后实时生效

### 阶段四：连续限流封禁（中优先级）

**目标**：实现连续限流触发自动封禁

| 序号  | 任务                              | 涉及文件            | 预计工时 |
| --- | ------------------------------- | --------------- | ---- |
| 4.1 | 修改IpAttackStateEntry，增加限流计数字段   | gateway-service | 0.5h |
| 4.2 | 修改RequestRateLimitFilter，增加升级逻辑 | gateway-service | 1h   |
| 4.3 | 新增DDoS事件推送接口                    | monitor-service | 0.5h |
| 4.4 | 集成测试                            | 全部              | 0.5h |

**验收标准**：

- 连续触发限流3次后自动封禁
- 防御日志记录完整
- 黑名单记录正确

### 阶段五：流量推送优化（低优先级）

**目标**：实现流量聚合推送，优化性能

| 序号  | 任务                         | 涉及文件            | 预计工时 |
| --- | -------------------------- | --------------- | ---- |
| 5.1 | 新增TrafficAggregateCache    | gateway-service | 1h   |
| 5.2 | 修改TrafficMonitorDTO，增加聚合字段 | gateway-service | 0.5h |
| 5.3 | 实现分级推送策略                   | gateway-service | 1.5h |
| 5.4 | 修改流量表结构                    | database        | 0.5h |
| 5.5 | 修改监测服务流量接收逻辑               | monitor-service | 1h   |
| 5.6 | 性能测试                       | 全部              | 1h   |

**验收标准**：

- NORMAL状态实时推送
- SUSPICIOUS状态采样推送
- ATTACKING状态批量推送
- DEFENDED状态跳过推送
- 数据库存储量减少50%+

***

## 六、风险评估与回滚方案

### 6.1 风险评估

| 风险项        | 风险等级 | 影响范围 | 应对措施            |
| ---------- | ---- | ---- | --------------- |
| 数据库变更失败    | 中    | 全系统  | 先在测试环境验证，准备回滚脚本 |
| 防御逻辑变更导致误封 | 高    | 业务访问 | 增加白名单机制，监控封禁日志  |
| 流量推送失败     | 低    | 数据统计 | 增加重试机制，本地缓存     |
| 配置同步延迟     | 低    | 防御效果 | 增加配置版本校验        |

### 6.2 回滚方案

```sql
-- 回滚脚本：删除新增配置
DELETE FROM `sys_config` WHERE `config_key` IN (
    'ddos.threshold',
    'ddos.detection.window-ms',
    'ddos.rate-limit-trigger-count',
    'ddos.rate-limit-trigger-window-seconds',
    'defense.blacklist.default-duration-seconds',
    'defense.blacklist.high-risk-duration-seconds',
    'defense.blacklist.critical-risk-duration-seconds',
    'traffic.push.normal.strategy',
    'traffic.push.suspicious.strategy',
    'traffic.push.attacking.strategy',
    'traffic.push.defended.strategy',
    'traffic.push.batch-interval-ms',
    'traffic.push.sampling-rate'
);

-- 回滚脚本：删除新增字段
ALTER TABLE `sys_traffic_monitor` DROP COLUMN `request_count`;
ALTER TABLE `sys_traffic_monitor` DROP COLUMN `state_tag`;
ALTER TABLE `sys_traffic_monitor` DROP COLUMN `is_aggregated`;
ALTER TABLE `sys_traffic_monitor` DROP COLUMN `aggregate_start_time`;
ALTER TABLE `sys_traffic_monitor` DROP COLUMN `aggregate_end_time`;

-- 恢复旧配置
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('ddos.threshold', '100', 'DDoS 检测阈值（次/分钟）');
```

***

## 七、测试验证方案

### 7.1 功能测试用例

| 用例编号   | 测试场景         | 预期结果                 |
| ------ | ------------ | -------------------- |
| TC-001 | DDoS攻击测试     | 防御日志有记录，黑名单有记录，返回403 |
| TC-002 | 连续限流测试       | 连续3次限流后自动封禁          |
| TC-003 | DEFENDED状态请求 | 跳过限流检测，直接返回403       |
| TC-004 | 流量推送测试       | 各状态推送策略正确执行          |
| TC-005 | 配置修改测试       | 配置修改后实时生效            |

### 7.2 性能测试指标

| 指标       | 优化前    | 优化后目标       |
| -------- | ------ | ----------- |
| 流量推送TPS  | 1000   | 10000+      |
| 数据库写入QPS | 1000   | 200+ (聚合后)  |
| 防御响应时间   | <100ms | <50ms       |
| 内存占用     | 基准     | +10% (聚合缓存) |

***

## 八、附录

### 8.1 相关文件清单

| 文件路径                                                | 修改类型 | 说明           |
| --------------------------------------------------- | ---- | ------------ |
| database/init\_all.sql                              | 修改   | 新增配置项和表结构    |
| monitor-service/.../DefenseDecisionServiceImpl.java | 修改   | 增加日志记录       |
| monitor-service/.../DDoSDetectServiceImpl.java      | 修改   | 使用新配置        |
| gateway-service/.../RequestRateLimitFilter.java     | 修改   | DEFENDED状态跳过 |
| gateway-service/.../TrafficCollectGlobalFilter.java | 修改   | 分级推送         |
| gateway-service/.../TrafficAggregateCache.java      | 新增   | 流量聚合缓存       |

### 8.2 参考资料

1. OWASP ModSecurity Core Rule Set
2. Cloudflare DDoS Protection Architecture
3. AWS WAF Best Practices
4. Nginx Rate Limiting Guide

***

## 九、阶段任务执行清单

### 阶段一：核心问题修复（高优先级）

**目标**：修复防御日志和黑名单记录丢失问题

- [x] 1.1 修改 `DefenseDecisionServiceImpl.java`，增加 `recordDefenseLog()` 方法
- [x] 1.2 修改 `DefenseDecisionServiceImpl.java`，增加 `publishBlacklistSyncEvent()` 方法
- [x] 1.3 在 `generateDefenseDecision()` 方法中调用新增方法
- [x] 1.4 注入 `DefenseLogMapper` 和 `ApplicationEventPublisher` 依赖
- [x] 1.5 编写单元测试验证日志记录功能
- [x] 1.6 编写单元测试验证事件发布功能
- [x] 1.7 集成测试：DDoS攻击后验证防御日志表有记录
- [x] 1.8 集成测试：DDoS攻击后验证黑名单表有记录

### 阶段二：DEFENDED状态优化（高优先级）

**目标**：优化DEFENDED状态下的过滤器行为

- [x] 2.1 修改 `RequestRateLimitFilter.java`，增加DEFENDED状态检查
- [x] 2.2 在限流检测前判断 `attackStateCache.isInDefendedState()`
- [x] 2.3 新增 `DefenseStateCheckFilter.java` 过滤器
- [x] 2.4 设置过滤器优先级为5（在黑名单过滤器之后，限流过滤器之前）
- [x] 2.5 修改 `TrafficCollectGlobalFilter.java`，实现分级推送策略
- [x] 2.6 增加 `getPushStrategy()` 方法根据IP状态返回策略
- [x] 2.7 集成测试：DEFENDED状态不再触发限流
- [x] 2.8 集成测试：DEFENDED状态不再推送限流日志

### 阶段三：配置统一（中优先级）

**目标**：统一配置单位，完善系统配置页面

- [x] 3.1 修改 `database/init_all.sql`，删除旧的 `ddos.threshold` 配置
- [x] 3.2 新增DDoS防护配置项（阈值、窗口、触发次数等）
- [x] 3.3 新增防御策略配置项（封禁时长配置）
- [x] 3.4 新增流量推送策略配置项
- [x] 3.5 修改 `DDoSDetectServiceImpl.java`，使用新的配置键
- [x] 3.6 修改检测逻辑，使用毫秒级时间窗口
- [x] 3.7 完善 `sys-config.html` 系统配置页面
- [x] 3.8 增加配置分组展示（DDoS防护、防御策略、流量推送）
- [x] 3.9 修改 `SysConfigCache.java`，增加新配置项的缓存支持
- [x] 3.10 测试：配置修改后实时生效

### 阶段四：连续限流封禁（中优先级）

**目标**：实现连续限流触发自动封禁

- [x] 4.1 修改 `IpAttackStateEntry.java`，增加 `rateLimitCount` 字段
- [x] 4.2 增加 `rateLimitWindowStart` 字段用于时间窗口计算
- [x] 4.3 增加 `incrementRateLimitCount()` 方法，支持窗口重置
- [x] 4.4 修改 `RequestRateLimitFilter.java`，增加限流计数逻辑
- [x] 4.5 增加 `shouldTriggerBlacklist()` 方法判断是否触发封禁
- [x] 4.6 增加 `pushDDoSEvent()` 方法推送DDoS事件到监测服务
- [x] 4.7 新增监测服务接口接收DDoS事件
- [x] 4.8 修改 `MonitorServiceDefenseClient.java`，增加推送攻击事件方法
- [x] 4.9 集成测试：连续触发限流3次后自动封禁
- [x] 4.10 集成测试：验证防御日志记录完整

### 阶段五：流量推送优化（低优先级）

**目标**：实现流量聚合推送，优化性能

- [x] 5.1 新增 `TrafficAggregate.java` 聚合数据类
- [x] 5.2 新增 `TrafficAggregateCache.java` 聚合缓存组件
- [x] 5.3 实现 `buildAggregateKey()` 方法生成聚合键
- [x] 5.4 修改 `TrafficMonitorDTO.java`，增加聚合字段
  - [x] `requestCount` 请求次数
  - [x] `stateTag` IP状态标签
  - [x] `isAggregated` 是否聚合记录
  - [x] `aggregateStartTime` 聚合开始时间
  - [x] `aggregateEndTime` 聚合结束时间
- [x] 5.5 修改 `TrafficCollectGlobalFilter.java`，实现推送策略
  - [x] `doRealtimePush()` 实时推送
  - [x] `doSamplingPush()` 采样推送
  - [x] `doBatchPush()` 批量推送
- [x] 5.6 新增定时任务 `flushBatchQueue()` 刷新批量队列
- [x] 5.7 修改 `database/init_all.sql`，流量表增加聚合字段
- [x] 5.8 修改 `TrafficMonitorEntity.java`，增加对应字段
- [x] 5.9 修改 `TrafficMonitorMapper.xml`，支持聚合字段映射
- [x] 5.10 修改监测服务流量接收接口，支持聚合记录
- [x] 5.11 单元测试：流量聚合功能测试
- [x] 5.12 单元测试：聚合缓存测试

### 阶段五后续问题修复（2026-03-25）

**问题发现**：DDoS测试后，攻击监测页面没有记录，IP黑名单列表没有记录，聚合流量推送失败

**问题分析**：
1. 聚合流量推送失败：`convertAggregateToDTO()` 方法缺少必填字段 `targetIp`
2. 攻击监测页面没有记录：`AttackEventReceiveController.receiveDDoSEvent()` 没有保存攻击记录到数据库
3. IP黑名单列表没有记录：`BlacklistManageServiceImpl.addToBlacklist()` 只写入缓存，没有写入 `sys_ip_blacklist` 表

**修复内容**：
- [x] 5.13 修复 `TrafficCollectGlobalFilter.convertAggregateToDTO()` 添加 `targetIp` 和 `responseStatus` 字段
- [x] 5.14 修复 `AttackEventReceiveController.receiveDDoSEvent()` 添加攻击记录保存逻辑
- [x] 5.15 修复 `BlacklistManageServiceImpl.addToBlacklist()` 调用 `IpBlacklistService` 写入数据库
- [x] 5.16 修复 `BlacklistManageServiceImpl.removeFromBlacklist()` 调用 `IpBlacklistService` 更新数据库
- [x] 5.17 修改 `AttackMonitorDTO.java` 添加 `attackId` 和 `description` 字段

### 阶段五后续问题修复（二）（2026-03-25）

**问题发现**：
1. DDoS聚合请求只统计了一条记录，DEFENDED状态跳过了流量记录
2. 限流拉黑逻辑需要优化：连续5次限流后第6次直接拉黑，不执行限流
3. IP黑名单列表主记录显示"手动添加"，需要区分系统自动和手动添加

**修复内容**：
- [x] 5.18 修改 `TrafficCollectGlobalFilter.getPushStrategy()` DEFENDED状态使用BATCH策略而非SKIP
- [x] 5.19 修改 `RequestRateLimitFilter.filter()` 添加连续限流后直接拉黑逻辑
- [x] 5.20 新增 `RequestRateLimitFilter.handleDirectBlacklist()` 处理直接拉黑
- [x] 5.21 新增 `DefenseResponseUtil.buildForbiddenResponse()` 构建403响应
- [x] 5.22 修改 `BlacklistInfoDTO.java` 添加 `banType` 和 `banTypeText` 字段
- [x] 5.23 修改 `BlacklistManageController.java` 添加封禁类型查询和显示
- [x] 5.24 修改前端 `traffic-list.html` 添加IP状态、聚合、请求次数列
- [x] 5.25 修改前端 `traffic-list.js` 添加聚合字段渲染逻辑

***

## 十、执行进度跟踪

| 阶段               | 状态    | 开始时间 | 完成时间 | 备注     |
| ---------------- | ----- | ---- | ---- | ------ |
| 阶段一：核心问题修复       | ✅ 已完成 | 2026-03-25 | 2026-03-25 | 所有任务已完成，单元测试和集成测试通过 |
| 阶段二：DEFENDED状态优化 | ✅ 已完成 | 2026-03-25 | 2026-03-25 | 所有任务已完成，单元测试和集成测试通过 |
| 阶段三：配置统一         | ✅ 已完成 | 2026-03-25 | 2026-03-25 | 所有任务已完成，配置项统一，系统配置页面完善 |
| 阶段四：连续限流封禁       | ✅ 已完成 | 2026-03-25 | 2026-03-25 | 所有任务已完成，连续限流触发自动封禁功能实现 |
| 阶段五：流量推送优化       | ✅ 已完成 | 2026-03-25 | 2026-03-25 | 所有任务已完成，流量聚合推送功能实现，性能优化 |

**状态说明**：

- ⬜ 未开始
- 🔄 进行中
- ✅ 已完成
- ⏸️ 暂停
- ❌ 取消

