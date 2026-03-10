-- ============================================================
-- 网络监测系统数据库初始化脚本
-- Database Initialization Script for Network Monitor System
-- 
-- 数据库版本：MySQL 8.0
-- 字符集：utf8mb4
-- 排序规则：utf8mb4_unicode_ci
-- ============================================================

-- 设置客户端字符集
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 创建数据库
-- ============================================================
DROP DATABASE IF EXISTS `network_monitor`;
CREATE DATABASE `network_monitor` 
DEFAULT CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

USE `network_monitor`;

-- ============================================================
-- 2. 创建数据表
-- ============================================================

-- ------------------------------------------------------------
-- 2.1 流量监测表 (sys_traffic_monitor)
-- 存储所有 HTTP 流量数据
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_traffic_monitor`;
CREATE TABLE `sys_traffic_monitor` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `traffic_id` VARCHAR(64) DEFAULT NULL COMMENT '原始流量 ID（网关生成）',
  `request_time` DATETIME NOT NULL COMMENT '请求时间',
  `source_ip` VARCHAR(50) NOT NULL COMMENT '源 IP 地址',
  `target_ip` VARCHAR(50) NOT NULL COMMENT '目标 IP 地址',
  `source_port` INT DEFAULT NULL COMMENT '源端口',
  `target_port` INT DEFAULT NULL COMMENT '目标端口',
  `http_method` VARCHAR(10) DEFAULT NULL COMMENT 'HTTP 方法 (GET/POST/PUT/DELETE 等)',
  `protocol` VARCHAR(20) DEFAULT NULL COMMENT '协议类型 (HTTP/1.0、HTTP/1.1、HTTP/2、HTTPS 等)',
  `request_uri` VARCHAR(2048) DEFAULT NULL COMMENT '请求 URI',
  `query_params` TEXT DEFAULT NULL COMMENT '查询参数',
  `request_headers` TEXT DEFAULT NULL COMMENT '请求头 (JSON 格式)',
  `request_body` LONGTEXT DEFAULT NULL COMMENT '请求体',
  `response_status` INT DEFAULT NULL COMMENT '响应状态码',
  `response_body` LONGTEXT DEFAULT NULL COMMENT '响应体',
  `response_time` BIGINT DEFAULT NULL COMMENT '响应时间 (毫秒)',
  `content_type` VARCHAR(255) DEFAULT NULL COMMENT '内容类型',
  `user_agent` VARCHAR(1024) DEFAULT NULL COMMENT 'User-Agent',
  `cookie` TEXT DEFAULT NULL COMMENT 'Cookie 信息',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_traffic_id` (`traffic_id`),
  KEY `idx_source_ip` (`source_ip`),
  KEY `idx_target_ip` (`target_ip`),
  KEY `idx_request_time` (`request_time`),
  KEY `idx_http_method` (`http_method`),
  KEY `idx_request_uri` (`request_uri`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流量监测表';

-- ------------------------------------------------------------
-- 2.2 攻击监测表 (sys_attack_monitor)
-- 存储所有检测到的攻击事件
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_attack_monitor`;
CREATE TABLE `sys_attack_monitor` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `traffic_id` BIGINT DEFAULT NULL COMMENT '关联流量 ID',
  `attack_type` VARCHAR(50) NOT NULL COMMENT '攻击类型 (SQL_INJECTION/XSS/COMMAND_INJECTION/DDOS 等)',
  `risk_level` VARCHAR(20) NOT NULL COMMENT '风险等级 (HIGH/MEDIUM/LOW)',
  `confidence` INT DEFAULT NULL COMMENT '攻击置信度 (0-100)',
  `rule_id` BIGINT DEFAULT NULL COMMENT '命中规则 ID',
  `rule_content` TEXT DEFAULT NULL COMMENT '命中规则内容',
  `source_ip` VARCHAR(50) NOT NULL COMMENT '源 IP 地址',
  `target_ip` VARCHAR(50) DEFAULT NULL COMMENT '目标 IP 地址',
  `target_uri` VARCHAR(2048) DEFAULT NULL COMMENT '目标 URI',
  `attack_content` TEXT DEFAULT NULL COMMENT '攻击内容 (解码后)',
  `handled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已处理 (0-未处理，1-已处理)',
  `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
  `handle_remark` VARCHAR(512) DEFAULT NULL COMMENT '处理备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_traffic_id` (`traffic_id`),
  KEY `idx_source_ip` (`source_ip`),
  KEY `idx_attack_type` (`attack_type`),
  KEY `idx_risk_level` (`risk_level`),
  KEY `idx_handled` (`handled`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_rule_id` (`rule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='攻击监测表';

-- ------------------------------------------------------------
-- 2.3 漏洞监测表 (sys_vulnerability_monitor)
-- 存储预设漏洞及验证状态
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_vulnerability_monitor`;
CREATE TABLE `sys_vulnerability_monitor` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `vuln_name` VARCHAR(255) NOT NULL COMMENT '漏洞名称',
  `vuln_type` VARCHAR(50) DEFAULT NULL COMMENT '漏洞类型 (SQL 注入/XSS/命令注入等)',
  `vuln_level` VARCHAR(20) DEFAULT NULL COMMENT '漏洞等级 (CRITICAL/HIGH/MEDIUM/LOW)',
  `vuln_path` VARCHAR(1024) DEFAULT NULL COMMENT '预设漏洞接口路径',
  `verify_status` TINYINT NOT NULL DEFAULT 0 COMMENT '验证状态 (0-未验证，1-已验证可利用)',
  `first_attack_time` DATETIME DEFAULT NULL COMMENT '首次被攻击时间',
  `last_attack_time` DATETIME DEFAULT NULL COMMENT '最近被攻击时间',
  `attack_count` INT NOT NULL DEFAULT 0 COMMENT '被攻击次数',
  `attack_ids` VARCHAR(2048) DEFAULT NULL COMMENT '关联攻击 ID 列表 (逗号分隔)',
  `description` TEXT DEFAULT NULL COMMENT '漏洞描述',
  `fix_suggestion` TEXT DEFAULT NULL COMMENT '修复建议',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_vuln_type` (`vuln_type`),
  KEY `idx_vuln_level` (`vuln_level`),
  KEY `idx_verify_status` (`verify_status`),
  KEY `idx_vuln_path` (`vuln_path`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='漏洞监测表';

-- ------------------------------------------------------------
-- 2.4 防御日志表 (sys_defense_monitor)
-- 存储所有防御操作日志
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_defense_monitor`;
CREATE TABLE `sys_defense_monitor` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `attack_id` BIGINT DEFAULT NULL COMMENT '关联攻击 ID',
  `traffic_id` BIGINT DEFAULT NULL COMMENT '关联流量 ID',
  `defense_type` VARCHAR(50) NOT NULL COMMENT '防御类型 (BLOCK_IP/RATE_LIMIT/BLOCK_REQUEST 等)',
  `defense_action` VARCHAR(20) DEFAULT NULL COMMENT '防御动作 (ADD/REMOVE/UPDATE)',
  `defense_target` VARCHAR(255) DEFAULT NULL COMMENT '防御对象 (IP 地址/规则 ID)',
  `defense_reason` VARCHAR(512) DEFAULT NULL COMMENT '防御原因',
  `expire_time` DATETIME DEFAULT NULL COMMENT '防御过期时间',
  `execute_status` TINYINT NOT NULL DEFAULT 0 COMMENT '执行状态 (0-失败，1-成功)',
  `execute_result` TEXT DEFAULT NULL COMMENT '执行结果信息',
  `operator` VARCHAR(50) DEFAULT NULL COMMENT '操作人 (SYSTEM/MANUAL)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_attack_id` (`attack_id`),
  KEY `idx_traffic_id` (`traffic_id`),
  KEY `idx_defense_type` (`defense_type`),
  KEY `idx_execute_status` (`execute_status`),
  KEY `idx_operator` (`operator`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='防御日志表';

-- ------------------------------------------------------------
-- 2.5 攻击规则表 (sys_monitor_rule)
-- 存储攻击检测规则
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_monitor_rule`;
CREATE TABLE `sys_monitor_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `rule_name` VARCHAR(255) NOT NULL COMMENT '规则名称',
  `attack_type` VARCHAR(50) NOT NULL COMMENT '攻击类型 (SQL 注入/XSS/命令注入/DDoS 等)',
  `rule_content` TEXT NOT NULL COMMENT '规则内容 (正则表达式/关键词)',
  `description` VARCHAR(1024) DEFAULT NULL COMMENT '规则描述',
  `risk_level` VARCHAR(20) NOT NULL COMMENT '风险等级 (HIGH/MEDIUM/LOW)',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态 (0-禁用，1-启用)',
  `priority` INT DEFAULT 100 COMMENT '规则优先级 (数字越小优先级越高)',
  `hit_count` INT NOT NULL DEFAULT 0 COMMENT '命中次数统计',
  `last_hit_time` DATETIME DEFAULT NULL COMMENT '最后命中时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_attack_type` (`attack_type`),
  KEY `idx_risk_level` (`risk_level`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='攻击规则表';

-- ============================================================
-- 3. 创建视图（可选）
-- ============================================================

-- ------------------------------------------------------------
-- 3.1 攻击事件关联视图 (v_attack_detail)
-- 关联攻击、流量、防御日志信息
-- ------------------------------------------------------------
DROP VIEW IF EXISTS `v_attack_detail`;
CREATE VIEW `v_attack_detail` AS
SELECT 
    a.id AS attack_id,
    a.traffic_id,
    a.attack_type,
    a.risk_level,
    a.confidence,
    a.source_ip AS attack_source_ip,
    a.target_uri,
    a.handled,
    a.handle_time,
    a.create_time AS attack_time,
    t.request_time AS traffic_request_time,
    t.source_ip AS traffic_source_ip,
    t.target_ip,
    t.http_method,
    t.request_uri,
    t.response_status,
    d.id AS defense_id,
    d.defense_type,
    d.execute_status AS defense_status,
    d.operator
FROM sys_attack_monitor a
LEFT JOIN sys_traffic_monitor t ON a.traffic_id = t.id
LEFT JOIN sys_defense_monitor d ON a.id = d.attack_id;

-- ------------------------------------------------------------
-- 3.2 漏洞攻击统计视图 (v_vulnerability_stat)
-- 统计漏洞被攻击情况
-- ------------------------------------------------------------
DROP VIEW IF EXISTS `v_vulnerability_stat`;
CREATE VIEW `v_vulnerability_stat` AS
SELECT 
    v.id AS vuln_id,
    v.vuln_name,
    v.vuln_type,
    v.vuln_level,
    v.verify_status,
    v.attack_count,
    v.first_attack_time,
    v.last_attack_time,
    COUNT(a.id) AS actual_attack_count
FROM sys_vulnerability_monitor v
LEFT JOIN sys_attack_monitor a ON FIND_IN_SET(a.id, v.attack_ids)
GROUP BY v.id, v.vuln_name, v.vuln_type, v.vuln_level, v.verify_status, 
         v.attack_count, v.first_attack_time, v.last_attack_time;

-- ============================================================
-- 4. 初始化基础数据
-- ============================================================

-- ------------------------------------------------------------
-- 4.1 初始化攻击检测规则
-- ------------------------------------------------------------

-- SQL 注入检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('SQL 注入检测 - UNION 关键字', 'SQL_INJECTION', '(?i)\\bUNION\\b.*\\bSELECT\\b', '检测 UNION SELECT 注入攻击', 'HIGH', 1, 10),
('SQL 注入检测 - OR 1=1', 'SQL_INJECTION', '(?i)\\bOR\\b\\s+1\\s*=\\s*1', '检测 OR 1=1 注入攻击', 'HIGH', 1, 10),
('SQL 注入检测 - DROP TABLE', 'SQL_INJECTION', '(?i)\\bDROP\\b.*\\bTABLE\\b', '检测 DROP TABLE 恶意注入', 'HIGH', 1, 5),
('SQL 注入检测 - SLEEP 函数', 'SQL_INJECTION', '(?i)\\bSLEEP\\b\\s*\\(', '检测基于时间盲注的 SLEEP 函数', 'MEDIUM', 1, 20),
('SQL 注入检测 - BENCHMARK 函数', 'SQL_INJECTION', '(?i)\\bBENCHMARK\\b\\s*\\(', '检测基于时间盲注的 BENCHMARK 函数', 'MEDIUM', 1, 20),
('SQL 注入检测 - 注释符号', 'SQL_INJECTION', '(--|#|/\\*)', '检测 SQL 注释符号', 'LOW', 1, 50),
('SQL 注入检测 - 十六进制编码', 'SQL_INJECTION', '0x[0-9a-fA-F]+', '检测十六进制编码的注入', 'MEDIUM', 1, 30);

-- XSS 攻击检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('XSS 检测 - script 标签', 'XSS', '(?i)<\\s*script[^>]*>', '检测 script 标签注入', 'HIGH', 1, 10),
('XSS 检测 - javascript 协议', 'XSS', '(?i)javascript\\s*:', '检测 javascript 协议注入', 'HIGH', 1, 10),
('XSS 检测 - onerror 事件', 'XSS', '(?i)\\bonerror\\b\\s*=', '检测 onerror 事件处理器', 'MEDIUM', 1, 20),
('XSS 检测 - onload 事件', 'XSS', '(?i)\\bonload\\b\\s*=', '检测 onload 事件处理器', 'MEDIUM', 1, 20),
('XSS 检测 - onclick 事件', 'XSS', '(?i)\\bonclick\\b\\s*=', '检测 onclick 事件处理器', 'MEDIUM', 1, 20),
('XSS 检测 - alert 函数', 'XSS', '(?i)\\balert\\b\\s*\\(', '检测 alert 弹窗函数', 'MEDIUM', 1, 30),
('XSS 检测 - document.cookie', 'XSS', '(?i)document\\.cookie', '检测 Cookie 窃取尝试', 'HIGH', 1, 15),
('XSS 检测 - img 标签注入', 'XSS', '(?i)<\\s*img[^>]+onerror', '检测 img 标签 onerror 注入', 'MEDIUM', 1, 25);

-- 命令注入检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('命令注入 - 管道符', 'COMMAND_INJECTION', '\\|\\s*\\w+', '检测管道符命令注入', 'HIGH', 1, 10),
('命令注入 - 分号分隔', 'COMMAND_INJECTION', ';\\s*\\w+', '检测分号分隔的命令注入', 'HIGH', 1, 10),
('命令注入 - 反引号执行', 'COMMAND_INJECTION', '`[^`]+`', '检测反引号命令执行', 'HIGH', 1, 5),
('命令注入 - $() 执行', 'COMMAND_INJECTION', '\\$\\([^)]+\\)', '检测 $() 命令执行', 'HIGH', 1, 5),
('命令注入 - 常见命令', 'COMMAND_INJECTION', '(?i)\\b(cat|ls|pwd|whoami|wget|curl|nc|bash|sh)\\b', '检测常见系统命令', 'MEDIUM', 1, 30),
('命令注入 - Windows 命令', 'COMMAND_INJECTION', '(?i)\\b(cmd|powershell|systeminfo|ipconfig)\\b', '检测 Windows 系统命令', 'MEDIUM', 1, 30);

-- 路径遍历检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('路径遍历 - 父目录引用', 'PATH_TRAVERSAL', '\\.\\./|\\.\\.\\\\', '检测父目录引用 (../ 或 ..\\)', 'HIGH', 1, 10),
('路径遍历 - 绝对路径', 'PATH_TRAVERSAL', '(?i)/etc/passwd|/etc/shadow', '检测 Linux 敏感文件路径', 'HIGH', 1, 5),
('路径遍历 - Windows 敏感文件', 'PATH_TRAVERSAL', '(?i)c:\\\\windows', '检测 Windows 系统路径', 'MEDIUM', 1, 20),
('路径遍历 - URL 编码绕过', 'PATH_TRAVERSAL', '%2e%2e%2f|%2e%2e/', '检测 URL 编码的路径遍历', 'HIGH', 1, 15);

-- 文件包含检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('文件包含 - PHP include', 'FILE_INCLUSION', '(?i)\\b(include|require|include_once|require_once)\\b\\s*\\(', '检测 PHP 文件包含函数', 'HIGH', 1, 10),
('文件 包含 - data 协议', 'FILE_INCLUSION', '(?i)data://', '检测 data 协议文件包含', 'HIGH', 1, 10),
('文件包含 - php://协议', 'FILE_INCLUSION', '(?i)php://', '检测 php:// 协议', 'HIGH', 1, 10),
('文件包含 - file://协议', 'FILE_INCLUSION', '(?i)file://', '检测 file:// 协议', 'HIGH', 1, 10);

-- DDoS 攻击检测规则（基于频率）
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('DDoS 检测 - 高频请求', 'DDOS', 'FREQUENCY_THRESHOLD', '基于请求频率的 DDoS 检测（需配合计数器）', 'HIGH', 1, 1);

-- ------------------------------------------------------------
-- 4.2 初始化预设漏洞信息
-- ------------------------------------------------------------
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `description`, `fix_suggestion`) VALUES
('SQL 注入漏洞 - 登录绕过', 'SQL_INJECTION', 'HIGH', '/api/login', 0, 
 '用户登录接口存在 SQL 注入漏洞，攻击者可通过注入恶意 SQL 语句绕过身份验证',
 '使用参数化查询或预编译语句，避免直接拼接 SQL 语句；对用户输入进行严格的验证和过滤'),

('XSS 漏洞 - 搜索框反射', 'XSS', 'MEDIUM', '/api/search', 0, 
 '搜索功能存在反射型 XSS 漏洞，用户输入未正确转义直接输出到页面',
 '对用户输入进行 HTML 实体转义；设置 Content-Type 响应头；使用 CSP 策略'),

('命令注入漏洞 - 文件下载', 'COMMAND_INJECTION', 'CRITICAL', '/api/download', 0, 
 '文件下载功能存在命令注入漏洞，攻击者可执行任意系统命令',
 '避免直接调用系统命令；使用白名单验证文件名；使用安全的文件操作 API'),

('路径遍历漏洞 - 文件读取', 'PATH_TRAVERSAL', 'HIGH', '/api/file/read', 0, 
 '文件读取功能存在路径遍历漏洞，攻击者可读取任意文件',
 '限制文件访问目录；对文件路径进行规范化处理；使用白名单验证文件名'),

('文件包含漏洞 - 远程文件', 'FILE_INCLUSION', 'CRITICAL', '/api/template/load', 0, 
 '模板加载功能存在文件包含漏洞，攻击者可包含远程恶意文件',
 '禁用 allow_url_include；使用白名单验证文件名；避免用户输入直接影响文件路径'),

('未授权访问 - 管理接口', 'UNAUTHORIZED_ACCESS', 'HIGH', '/admin/*', 0, 
 '管理接口未进行身份验证，攻击者可直接访问敏感功能',
 '添加身份验证中间件；实施基于角色的访问控制；对敏感操作进行二次验证'),

('信息泄露 - 错误详情', 'INFORMATION_DISCLOSURE', 'LOW', '/api/*', 0, 
 'API 接口返回详细错误信息，可能泄露系统敏感信息',
 '使用统一的错误处理机制；不返回详细堆栈信息；记录错误日志到服务器'),

('SSRF 漏洞 - 内网探测', 'SSRF', 'HIGH', '/api/fetch', 0, 
 'URL 抓取功能存在 SSRF 漏洞，攻击者可探测内网服务',
 '限制协议类型（仅允许 HTTP/HTTPS）；禁用重定向；使用白名单验证目标地址');

-- ------------------------------------------------------------
-- 4.3 初始化系统配置（可选）
-- ------------------------------------------------------------
-- 如需添加系统配置表，可取消以下注释
/*
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
  `config_value` TEXT DEFAULT NULL COMMENT '配置值',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '配置描述',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('ddos.threshold', '100', 'DDoS 检测阈值（次/分钟）'),
('blacklist.default.expire.seconds', '86400', '黑名单默认过期时间（秒）'),
('alert.enabled', 'true', '是否启用告警通知'),
('alert.push.interval', '5000', '告警推送间隔（毫秒）'),
('alert.heartbeat.interval', '10000', '告警心跳间隔（毫秒）');
*/

-- ============================================================
-- 5. 创建触发器（可选）
-- ============================================================

-- ------------------------------------------------------------
-- 5.1 攻击记录自动更新漏洞统计触发器
-- ------------------------------------------------------------
DROP TRIGGER IF EXISTS `trg_update_vuln_stat_after_attack`;
DELIMITER $$
CREATE TRIGGER `trg_update_vuln_stat_after_attack`
AFTER INSERT ON `sys_attack_monitor`
FOR EACH ROW
BEGIN
    DECLARE v_vuln_path VARCHAR(1024);
    
    -- 查找匹配的预设漏洞
    SELECT vuln_path INTO v_vuln_path
    FROM sys_vulnerability_monitor
    WHERE NEW.target_uri LIKE CONCAT('%', vuln_path, '%')
    LIMIT 1;
    
    -- 如果找到匹配的漏洞，更新统计信息
    IF v_vuln_path IS NOT NULL THEN
        UPDATE sys_vulnerability_monitor
        SET attack_count = attack_count + 1,
            first_attack_time = COALESCE(first_attack_time, NEW.create_time),
            last_attack_time = NEW.create_time,
            verify_status = CASE WHEN attack_count >= 1 THEN 1 ELSE verify_status END
        WHERE vuln_path = v_vuln_path;
    END IF;
END$$
DELIMITER ;

-- ============================================================
-- 6. 恢复外键检查
-- ============================================================
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 7. 数据验证查询（可选）
-- ============================================================

-- 验证表创建情况
SELECT 
    TABLE_NAME,
    TABLE_COMMENT,
    TABLE_ROWS,
    CREATE_TIME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'network_monitor'
ORDER BY TABLE_NAME;

-- 验证规则初始化情况
SELECT 
    attack_type,
    COUNT(*) AS rule_count,
    SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END) AS enabled_count
FROM sys_monitor_rule
GROUP BY attack_type;

-- 验证漏洞初始化情况
SELECT 
    vuln_level,
    COUNT(*) AS vuln_count
FROM sys_vulnerability_monitor
GROUP BY vuln_level;

-- ============================================================
-- 数据库初始化完成
-- ============================================================
SELECT '数据库初始化完成！' AS status;
SELECT CONCAT('数据库：', DATABASE()) AS database_info;
SELECT CONCAT('表数量：', COUNT(*)) AS table_count 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'network_monitor';
