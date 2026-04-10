-- ============================================================
-- 网络监测系统数据库完整初始化脚本
-- Database Initialization Script for Network Monitor System
-- 
-- 数据库版本：MySQL 8.0
-- 字符集：utf8mb4
-- 排序规则：utf8mb4_unicode_ci
-- 
-- 包含内容：
-- 1. 基础表结构（流量、攻击、漏洞、防御日志、规则）
-- 2. 系统配置表
-- 3. IP黑名单优化表结构（主表、历史表、全局防御日志表）
-- 4. 攻击事件聚合表（第三阶段新增）
-- 5. 初始化数据（规则、漏洞、系统配置）
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
  `event_id` VARCHAR(64) DEFAULT NULL COMMENT '关联攻击事件ID',
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
  `request_count` INT DEFAULT 1 COMMENT '请求次数(聚合统计)',
  `state_tag` VARCHAR(20) DEFAULT 'NORMAL' COMMENT 'IP状态标签',
  `state_value` INT DEFAULT 0 COMMENT '状态值(数字: 0-正常, 1-可疑, 2-攻击中, 3-已防御, 4-冷却期)',
  `confidence` INT DEFAULT 0 COMMENT '置信度(0-100)',
  `is_aggregated` TINYINT DEFAULT 0 COMMENT '是否为聚合记录(0-否,1-是)',
  `aggregate_start_time` DATETIME DEFAULT NULL COMMENT '聚合开始时间',
  `aggregate_end_time` DATETIME DEFAULT NULL COMMENT '聚合结束时间',
  `error_count` INT DEFAULT 0 COMMENT '错误次数(聚合统计)',
  `avg_processing_time` BIGINT DEFAULT NULL COMMENT '平均处理时间(毫秒)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_traffic_id` (`traffic_id`),
  KEY `idx_event_id` (`event_id`),
  KEY `idx_source_ip` (`source_ip`),
  KEY `idx_target_ip` (`target_ip`),
  KEY `idx_request_time` (`request_time`),
  KEY `idx_http_method` (`http_method`),
  KEY `idx_request_uri` (`request_uri`(255)),
  KEY `idx_state_tag` (`state_tag`),
  KEY `idx_state_value` (`state_value`),
  KEY `idx_is_aggregated` (`is_aggregated`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流量监测表';

-- ------------------------------------------------------------
-- 2.2 攻击监测表 (sys_attack_monitor)
-- 存储所有检测到的攻击事件
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_attack_monitor`;
CREATE TABLE `sys_attack_monitor` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `event_id` VARCHAR(64) DEFAULT NULL COMMENT '关联事件ID',
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
  KEY `idx_event_id` (`event_id`),
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

-- ------------------------------------------------------------
-- 2.5.1 检测白名单表 (sys_detection_whitelist)
-- 存储攻击检测白名单，用于减少误报
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_detection_whitelist`;
CREATE TABLE `sys_detection_whitelist` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `whitelist_type` VARCHAR(20) NOT NULL COMMENT '白名单类型(PATH-路径白名单，HEADER-请求头白名单，IP-IP白名单)',
  `whitelist_value` VARCHAR(255) NOT NULL COMMENT '白名单值(路径模式/请求头名称/IP地址)',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态 (0-禁用，1-启用)',
  `priority` INT DEFAULT 100 COMMENT '优先级 (数字越小优先级越高)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_whitelist_type` (`whitelist_type`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='检测白名单表';

-- ------------------------------------------------------------
-- 2.6 系统配置表 (sys_config)
-- 存储系统配置信息
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 2.7 IP黑名单主表 (sys_ip_blacklist)
-- 存储唯一IP地址信息，记录IP的当前最新状态
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_ip_blacklist`;
CREATE TABLE `sys_ip_blacklist` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `ip_address` VARCHAR(128) NOT NULL COMMENT 'IP地址(支持IPv4/IPv6)',
    `ip_location` VARCHAR(255) DEFAULT NULL COMMENT 'IP归属地(省市/国家)',
    `current_expire_time` DATETIME DEFAULT NULL COMMENT '当前封禁过期时间(永久封禁时为NULL)',
    `total_ban_count` INT DEFAULT 0 COMMENT '累计封禁次数',
    `first_ban_time` DATETIME DEFAULT NULL COMMENT '首次封禁时间',
    `last_ban_time` DATETIME DEFAULT NULL COMMENT '最近一次封禁时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `status` TINYINT DEFAULT 0 COMMENT '当前状态(0-正常，1-封禁中)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ip_address` (`ip_address`),
    KEY `idx_status` (`status`),
    KEY `idx_current_expire_time` (`current_expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IP黑名单主表';

-- ------------------------------------------------------------
-- 2.8 IP黑名单子表 (sys_ip_blacklist_history)
-- 存储单个IP的所有封禁/解封历史记录
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_ip_blacklist_history`;
CREATE TABLE `sys_ip_blacklist_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `blacklist_id` BIGINT NOT NULL COMMENT '关联黑名单主表ID',
    `attack_id` BIGINT DEFAULT NULL COMMENT '关联攻击表ID(sys_attack_monitor)',
    `traffic_id` BIGINT DEFAULT NULL COMMENT '关联流量表ID(sys_traffic_monitor)',
    `rule_id` BIGINT DEFAULT NULL COMMENT '关联触发规则ID(sys_monitor_rule)',
    `ban_type` VARCHAR(32) DEFAULT 'MANUAL' COMMENT '封禁类型(SYSTEM-自动封禁，MANUAL-人工封禁)',
    `ban_reason` VARCHAR(500) DEFAULT NULL COMMENT '封禁原因(SQL注入/DDOS等)',
    `ban_duration` BIGINT DEFAULT NULL COMMENT '封禁时长(秒，永久为NULL)',
    `expire_time` DATETIME DEFAULT NULL COMMENT '封禁过期时间',
    `process_status` TINYINT DEFAULT 1 COMMENT '处理状态(1-封禁中，2-已解封)',
    `operator` VARCHAR(64) DEFAULT NULL COMMENT '操作人(SYSTEM/管理员账号)',
    `ban_execute_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '封禁执行时间',
    `unban_execute_time` DATETIME DEFAULT NULL COMMENT '解封执行时间',
    `unban_reason` VARCHAR(255) DEFAULT NULL COMMENT '解封原因(误封/过期)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_blacklist_id` (`blacklist_id`),
    KEY `idx_attack_id` (`attack_id`),
    KEY `idx_traffic_id` (`traffic_id`),
    KEY `idx_rule_id` (`rule_id`),
    KEY `idx_process_status` (`process_status`),
    KEY `idx_expire_time` (`expire_time`),
    KEY `idx_ban_execute_time` (`ban_execute_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IP黑名单子表(封禁历史记录)';

-- ------------------------------------------------------------
-- 2.9 全局防御日志表 (sys_defense_log)
-- 记录所有类型的防御操作，作为系统全局安全审计日志
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_defense_log`;
CREATE TABLE `sys_defense_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `event_id` VARCHAR(64) DEFAULT NULL COMMENT '关联事件ID',
    `defense_type` VARCHAR(32) NOT NULL COMMENT '防御类型(BLOCK_IP-封禁IP，RATE_LIMIT-限流，BLOCK_REQUEST-拦截请求)',
    `defense_action` VARCHAR(20) DEFAULT NULL COMMENT '防御动作(ADD-添加，REMOVE-移除，UPDATE-更新)',
    `defense_target` VARCHAR(255) NOT NULL COMMENT '防御对象(IP/接口URI/规则ID)',
    `attack_id` BIGINT DEFAULT NULL COMMENT '关联攻击表ID',
    `traffic_id` BIGINT DEFAULT NULL COMMENT '关联流量表ID',
    `rule_id` BIGINT DEFAULT NULL COMMENT '关联检测规则ID',
    `defense_reason` VARCHAR(500) DEFAULT NULL COMMENT '防御原因',
    `expire_time` DATETIME DEFAULT NULL COMMENT '防御过期时间(封禁IP时使用)',
    `execute_status` TINYINT DEFAULT 0 COMMENT '执行状态(0-失败，1-成功)',
    `is_first` TINYINT DEFAULT 0 COMMENT '是否首次防御(0-否，1-是)',
    `execute_result` VARCHAR(500) DEFAULT NULL COMMENT '执行结果详情',
    `operator` VARCHAR(64) DEFAULT NULL COMMENT '操作人(SYSTEM-自动，MANUAL-人工)',
    `execute_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '防御执行时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_defense_type` (`defense_type`),
    KEY `idx_defense_action` (`defense_action`),
    KEY `idx_defense_target` (`defense_target`),
    KEY `idx_attack_id` (`attack_id`),
    KEY `idx_traffic_id` (`traffic_id`),
    KEY `idx_rule_id` (`rule_id`),
    KEY `idx_execute_status` (`execute_status`),
    KEY `idx_operator` (`operator`),
    KEY `idx_execute_time` (`execute_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局防御日志表';

-- ------------------------------------------------------------
-- 2.10 攻击事件聚合表 (sys_attack_event)
-- 存储聚合后的攻击事件，用于态势感知和趋势分析
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_attack_event`;
CREATE TABLE `sys_attack_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `event_id` VARCHAR(64) NOT NULL COMMENT '事件唯一标识(UUID)',
    `source_ip` VARCHAR(128) NOT NULL COMMENT '攻击源IP(规范化后)',
    `attack_type` VARCHAR(32) NOT NULL COMMENT '攻击类型',
    `risk_level` VARCHAR(20) NOT NULL COMMENT '风险等级',
    `start_time` DATETIME NOT NULL COMMENT '攻击开始时间',
    `end_time` DATETIME DEFAULT NULL COMMENT '攻击结束时间',
    `duration_seconds` INT DEFAULT 0 COMMENT '持续时间(秒)',
    `total_requests` INT DEFAULT 0 COMMENT '总请求数',
    `peak_rps` INT DEFAULT 0 COMMENT '峰值请求数/秒',
    `attack_count` INT DEFAULT 0 COMMENT '关键攻击节点数',
    `confidence_start` INT DEFAULT 0 COMMENT '初始置信度',
    `confidence_end` INT DEFAULT 0 COMMENT '最终置信度',
    `defense_action` VARCHAR(32) DEFAULT NULL COMMENT '防御动作',
    `defense_expire_time` DATETIME DEFAULT NULL COMMENT '防御过期时间',
    `defense_success` TINYINT DEFAULT NULL COMMENT '防御是否成功(0-失败，1-成功)',
    `status` TINYINT DEFAULT 0 COMMENT '状态(0-进行中，1-已结束)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_id` (`event_id`),
    KEY `idx_source_ip` (`source_ip`),
    KEY `idx_attack_type` (`attack_type`),
    KEY `idx_status` (`status`),
    KEY `idx_start_time` (`start_time`),
    KEY `idx_risk_level` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='攻击事件聚合表';

-- ------------------------------------------------------------
-- 2.11 系统用户表 (sys_user)
-- 存储系统管理员账号信息
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '登录账号',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密存储)',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '用户昵称',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号码',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱地址',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '账号状态(0-正常，1-禁用，2-锁定)',
    `login_fail_count` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip` VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP',
    `password_update_time` DATETIME DEFAULT NULL COMMENT '密码更新时间',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记(0-正常，1-已删除)',
    `create_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_status` (`status`),
    KEY `idx_del_flag` (`del_flag`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- ------------------------------------------------------------
-- 2.12 角色表 (sys_role)
-- 存储系统角色信息
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码',
    `role_desc` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '角色状态(0-正常，1-禁用)',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记(0-正常，1-已删除)',
    `create_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    KEY `idx_status` (`status`),
    KEY `idx_del_flag` (`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- ------------------------------------------------------------
-- 2.13 菜单权限表 (sys_menu)
-- 存储系统菜单和权限信息
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父权限ID',
    `menu_name` VARCHAR(50) NOT NULL COMMENT '菜单/权限名称',
    `menu_type` TINYINT NOT NULL DEFAULT 0 COMMENT '类型(0-目录，1-菜单，2-按钮)',
    `permission` VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
    `path` VARCHAR(255) DEFAULT NULL COMMENT '路由路径',
    `component` VARCHAR(255) DEFAULT NULL COMMENT '组件路径',
    `icon` VARCHAR(100) DEFAULT NULL COMMENT '菜单图标',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    `visible` TINYINT NOT NULL DEFAULT 0 COMMENT '是否可见(0-显示，1-隐藏)',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '菜单状态(0-正常，1-禁用)',
    `del_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记(0-正常，1-已删除)',
    `create_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_status` (`status`),
    KEY `idx_del_flag` (`del_flag`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单权限表';

-- ------------------------------------------------------------
-- 2.14 用户-角色关联表 (sys_user_role)
-- 存储用户与角色的关联关系
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';

-- ------------------------------------------------------------
-- 2.15 角色-菜单关联表 (sys_role_menu)
-- 存储角色与菜单权限的关联关系
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-菜单关联表';

-- ------------------------------------------------------------
-- 2.16 系统操作日志表 (sys_oper_log)
-- 存储系统操作日志
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '操作账号',
    `oper_type` VARCHAR(50) NOT NULL COMMENT '操作类型(LOGIN/LOGOUT/INSERT/UPDATE/DELETE/EXPORT等)',
    `oper_module` VARCHAR(100) DEFAULT NULL COMMENT '操作模块',
    `oper_content` VARCHAR(2000) DEFAULT NULL COMMENT '操作内容',
    `oper_method` VARCHAR(200) DEFAULT NULL COMMENT '操作方法',
    `oper_url` VARCHAR(500) DEFAULT NULL COMMENT '请求URL',
    `oper_ip` VARCHAR(50) DEFAULT NULL COMMENT '操作IP',
    `oper_location` VARCHAR(255) DEFAULT NULL COMMENT '操作地点',
    `oper_status` TINYINT NOT NULL DEFAULT 0 COMMENT '操作状态(0-成功，1-失败)',
    `error_msg` VARCHAR(2000) DEFAULT NULL COMMENT '错误信息',
    `oper_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    `cost_time` BIGINT DEFAULT NULL COMMENT '耗时(毫秒)',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`),
    KEY `idx_oper_type` (`oper_type`),
    KEY `idx_oper_status` (`oper_status`),
    KEY `idx_oper_time` (`oper_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统操作日志表';

-- ============================================================
-- 3. 创建视图
-- ============================================================

-- ------------------------------------------------------------
-- 3.1 攻击事件关联视图 (v_attack_detail)
-- 关联攻击、流量、防御日志信息
-- ------------------------------------------------------------
DROP VIEW IF EXISTS `v_attack_detail`;
CREATE VIEW `v_attack_detail` AS
SELECT 
    a.id AS attack_id,
    a.event_id,
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
    d.is_first AS defense_is_first,
    d.operator
FROM sys_attack_monitor a
LEFT JOIN sys_traffic_monitor t ON a.traffic_id = t.id
LEFT JOIN sys_defense_log d ON a.id = d.attack_id;

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

-- ------------------------------------------------------------
-- 3.3 攻击事件详情视图 (v_attack_event_detail)
-- 关联攻击事件与攻击监测记录
-- ------------------------------------------------------------
DROP VIEW IF EXISTS `v_attack_event_detail`;
CREATE VIEW `v_attack_event_detail` AS
SELECT 
    e.id AS event_table_id,
    e.event_id,
    e.source_ip,
    e.attack_type,
    e.risk_level,
    e.start_time,
    e.end_time,
    e.duration_seconds,
    e.total_requests,
    e.peak_rps,
    e.attack_count,
    e.confidence_start,
    e.confidence_end,
    e.defense_action,
    e.defense_success,
    e.status AS event_status,
    COUNT(m.id) AS monitor_record_count
FROM sys_attack_event e
LEFT JOIN sys_attack_monitor m ON e.event_id = m.event_id
GROUP BY e.id, e.event_id, e.source_ip, e.attack_type, e.risk_level,
         e.start_time, e.end_time, e.duration_seconds, e.total_requests,
         e.peak_rps, e.attack_count, e.confidence_start, e.confidence_end,
         e.defense_action, e.defense_success, e.status;

-- ------------------------------------------------------------
-- 2.10 扫描目标配置表 (sys_scan_target)
-- 存储扫描目标服务配置
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_scan_target`;
CREATE TABLE `sys_scan_target` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '目标ID',
    `target_name` VARCHAR(100) NOT NULL COMMENT '目标名称',
    `target_url` VARCHAR(255) NOT NULL COMMENT '目标URL',
    `target_type` VARCHAR(20) NOT NULL COMMENT '目标类型(PRODUCTION/TEST/DEMO)',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `enabled` TINYINT DEFAULT 1 COMMENT '是否启用(0-禁用，1-启用)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='扫描目标配置表';

-- ------------------------------------------------------------
-- 2.11 扫描接口配置表 (sys_scan_interface)
-- 存储扫描接口配置
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_scan_interface`;
CREATE TABLE `sys_scan_interface` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '接口ID',
    `target_id` BIGINT NOT NULL COMMENT '关联目标ID',
    `interface_name` VARCHAR(100) NOT NULL COMMENT '接口名称',
    `interface_path` VARCHAR(255) NOT NULL COMMENT '接口路径',
    `http_method` VARCHAR(10) NOT NULL COMMENT 'HTTP方法',
    `vuln_type` VARCHAR(50) NOT NULL COMMENT '漏洞类型',
    `risk_level` VARCHAR(20) NOT NULL COMMENT '风险等级',
    `params_config` TEXT DEFAULT NULL COMMENT '参数配置(JSON)',
    `payload_config` TEXT DEFAULT NULL COMMENT 'Payload配置(JSON)',
    `match_rules` TEXT DEFAULT NULL COMMENT '匹配规则(JSON)',
    `enabled` TINYINT DEFAULT 1 COMMENT '是否启用(0-禁用，1-启用)',
    `priority` INT DEFAULT 100 COMMENT '扫描优先级',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_target_id` (`target_id`),
    KEY `idx_vuln_type` (`vuln_type`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='扫描接口配置表';

-- ------------------------------------------------------------
-- 2.12 Payload库表 (sys_payload_library)
-- 存储Payload库配置
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_payload_library`;
CREATE TABLE `sys_payload_library` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Payload ID',
    `vuln_type` VARCHAR(50) NOT NULL COMMENT '漏洞类型',
    `payload_level` VARCHAR(20) NOT NULL COMMENT 'Payload级别(BASIC/ADVANCED/CUSTOM)',
    `payload_content` TEXT NOT NULL COMMENT 'Payload内容',
    `match_keywords` VARCHAR(500) DEFAULT NULL COMMENT '匹配关键词(逗号分隔)',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `risk_level` VARCHAR(20) DEFAULT NULL COMMENT '风险等级',
    `references` VARCHAR(255) DEFAULT NULL COMMENT '参考链接',
    `enabled` TINYINT DEFAULT 1 COMMENT '是否启用(0-禁用，1-启用)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_vuln_type` (`vuln_type`),
    KEY `idx_payload_level` (`payload_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Payload库表';

-- ============================================================
-- 4. 初始化基础数据
-- ============================================================

-- ------------------------------------------------------------
-- 4.1 初始化攻击检测规则
-- ------------------------------------------------------------

-- SQL 注入检测规则（针对靶场测试用例优化）
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('SQL 注入检测 - UNION 关键字', 'SQL_INJECTION', '(?i)\\bUNION\\b.*\\bSELECT\\b', '检测 UNION SELECT 注入攻击', 'HIGH', 1, 10),
('SQL 注入检测 - OR 1=1', 'SQL_INJECTION', '(?i)\\bOR\\b\\s+[\'"]?1[\'"]?\\s*=\\s*[\'"]?1[\'"]?', '检测 OR 1=1 注入攻击', 'HIGH', 1, 10),
('SQL 注入检测 - DROP TABLE', 'SQL_INJECTION', '(?i)\\bDROP\\b.*\\bTABLE\\b', '检测 DROP TABLE 恶意注入', 'HIGH', 1, 5),
('SQL 注入检测 - SLEEP 函数', 'SQL_INJECTION', '(?i)\\bSLEEP\\b\\s*\\(', '检测基于时间盲注的 SLEEP 函数', 'MEDIUM', 1, 20),
('SQL 注入检测 - BENCHMARK 函数', 'SQL_INJECTION', '(?i)\\bBENCHMARK\\b\\s*\\(', '检测基于时间盲注的 BENCHMARK 函数', 'MEDIUM', 1, 20),
('SQL 注入检测 - 注释符号', 'SQL_INJECTION', '(--|#|/\\*)', '检测 SQL 注释符号', 'LOW', 1, 50),
('SQL 注入检测 - 十六进制编码', 'SQL_INJECTION', '0x[0-9a-fA-F]+', '检测十六进制编码的注入', 'MEDIUM', 1, 30),
('SQL 注入检测 - 堆叠查询', 'SQL_INJECTION', ';\\s*(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER)\\b', '检测堆叠查询攻击', 'HIGH', 1, 15),
('SQL 注入检测 - 布尔盲注', 'SQL_INJECTION', '(?i)\\bAND\\b\\s+[\'"]?\\d+[\'"]?\\s*=\\s*[\'"]?\\d+[\'"]?', '检测布尔盲注攻击', 'MEDIUM', 1, 25);

-- XSS 攻击检测规则（针对靶场测试用例优化）
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('XSS 检测 - script 标签', 'XSS', '(?i)<\\s*script[^>]*>', '检测 script 标签注入', 'HIGH', 1, 10),
('XSS 检测 - javascript 协议', 'XSS', '(?i)javascript\\s*:', '检测 javascript 协议注入', 'HIGH', 1, 10),
('XSS 检测 - onerror 事件', 'XSS', '(?i)\\bonerror\\b\\s*=', '检测 onerror 事件处理器', 'MEDIUM', 1, 20),
('XSS 检测 - onload 事件', 'XSS', '(?i)\\bonload\\b\\s*=', '检测 onload 事件处理器', 'MEDIUM', 1, 20),
('XSS 检测 - onclick 事件', 'XSS', '(?i)\\bonclick\\b\\s*=', '检测 onclick 事件处理器', 'MEDIUM', 1, 20),
('XSS 检测 - alert 函数', 'XSS', '(?i)\\balert\\b\\s*\\(', '检测 alert 弹窗函数', 'MEDIUM', 1, 30),
('XSS 检测 - document.cookie', 'XSS', '(?i)document\\.cookie', '检测 Cookie 窃取尝试', 'HIGH', 1, 15),
('XSS 检测 - img 标签注入', 'XSS', '(?i)<\\s*img[^>]+onerror', '检测 img 标签 onerror 注入', 'MEDIUM', 1, 25),
('XSS 检测 - SVG 标签注入', 'XSS', '(?i)<\\s*svg[^>]*onload', '检测 SVG 标签 onload 注入', 'MEDIUM', 1, 25),
('XSS 检测 - eval 函数', 'XSS', '(?i)\\beval\\b\\s*\\(', '检测 eval 函数调用', 'HIGH', 1, 20);

-- 命令注入检测规则（针对靶场测试用例优化，减少误报）
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('命令注入 - 管道符', 'COMMAND_INJECTION', '\\|\\s*(cat|ls|pwd|whoami|wget|curl|nc|bash|sh|cmd|type|dir|find|ping|tasklist)\\b', '检测管道符命令注入', 'HIGH', 1, 10),
('命令注入 - 分号分隔', 'COMMAND_INJECTION', ';\\s*(cat|ls|pwd|whoami|wget|curl|nc|bash|sh|cmd|type|dir|find|ping|tasklist)\\b', '检测分号分隔的命令注入', 'HIGH', 1, 10),
('命令注入 - 反引号执行', 'COMMAND_INJECTION', '`[^`]+`', '检测反引号命令执行', 'HIGH', 1, 5),
('命令注入 - $() 执行', 'COMMAND_INJECTION', '\\$\\([^)]+\\)', '检测 $() 命令执行', 'HIGH', 1, 5),
('命令注入 - 常见命令带参数', 'COMMAND_INJECTION', '(?i)\\b(cat|ls|pwd|whoami|wget|curl|nc|bash|sh|ping|tasklist)\\s+[\\w\\-./]+', '检测常见系统命令带参数', 'MEDIUM', 1, 30),
('命令注入 - Windows 命令执行', 'COMMAND_INJECTION', '(?i)(cmd\\s*/c|cmd\\.exe|powershell\\s*-|\\|\\s*cmd\\b)', '检测 Windows 命令执行模式', 'HIGH', 1, 25),
('命令注入 - 参数注入', 'COMMAND_INJECTION', '(?i)(cmd|command|exec|execute|ping|tasklist|dir)\\s*=[^&]*\\b(cat|ls|whoami|wget|curl|nc|bash|sh|cmd|type|dir|find|ping|tasklist)\\b', '检测参数中的命令注入', 'HIGH', 1, 20);

-- 路径遍历检测规则（针对靶场测试用例优化）
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('路径遍历 - 父目录引用', 'PATH_TRAVERSAL', '\\.\\./|\\.\\.\\\\', '检测父目录引用 (../ 或 ..\\)', 'HIGH', 1, 10),
('路径遍历 - 绝对路径', 'PATH_TRAVERSAL', '(?i)/etc/passwd|/etc/shadow', '检测 Linux 敏感文件路径', 'HIGH', 1, 5),
('路径遍历 - Windows 敏感文件', 'PATH_TRAVERSAL', '(?i)c:\\\\windows', '检测 Windows 系统路径', 'MEDIUM', 1, 20),
('路径遍历 - URL 编码绕过', 'PATH_TRAVERSAL', '%2e%2e%2f|%2e%2e/', '检测 URL 编码的路径遍历', 'HIGH', 1, 15),
('路径遍历 - 参数注入', 'PATH_TRAVERSAL', '(?i)(filename|file|path)\\s*=[^&]*\\.\\./', '检测参数中的路径遍历', 'HIGH', 1, 12);

-- 文件包含检测规则（针对靶场测试用例优化）
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('文件包含 - PHP include', 'FILE_INCLUSION', '(?i)\\b(include|require|include_once|require_once)\\b\\s*\\(', '检测 PHP 文件包含函数', 'HIGH', 1, 10),
('文件包含 - data 协议', 'FILE_INCLUSION', '(?i)data://', '检测 data 协议文件包含', 'HIGH', 1, 10),
('文件包含 - php://协议', 'FILE_INCLUSION', '(?i)php://', '检测 php:// 协议', 'HIGH', 1, 10),
('文件包含 - file://协议', 'FILE_INCLUSION', '(?i)file://', '检测 file:// 协议', 'HIGH', 1, 10),
('文件包含 - classpath 协议', 'FILE_INCLUSION', '(?i)classpath:', '检测 classpath 协议文件包含', 'HIGH', 1, 10),
('文件包含 - 参数注入', 'FILE_INCLUSION', '(?i)(path|file|include)\\s*=[^&]*(file://|classpath:|\\.\\./)', '检测参数中的文件包含', 'HIGH', 1, 15);

-- DDoS 攻击检测规则（基于频率）
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('DDoS 检测 - 高频请求', 'DDOS', 'FREQUENCY_THRESHOLD', '基于请求频率的 DDoS 检测（需配合计数器）', 'HIGH', 1, 1);

-- ------------------------------------------------------------
-- 4.1.2 初始化检测白名单
-- 减少误报，跳过对特定路径和请求头的检测
-- ------------------------------------------------------------

-- 路径白名单：靶场静态页面路径（精确匹配，不使用通配符）
INSERT INTO `sys_detection_whitelist` (`whitelist_type`, `whitelist_value`, `description`, `enabled`, `priority`) VALUES
('PATH', '/target/page/index', '靶场首页', 1, 10),
('PATH', '/target/page/', '靶场首页（根路径）', 1, 10),
('PATH', '/target/page/cmd-vuln', 'CMD命令注入测试页面', 1, 10),
('PATH', '/target/page/sql-vuln', 'SQL注入测试页面', 1, 10),
('PATH', '/target/page/xss-vuln', 'XSS测试页面', 1, 10),
('PATH', '/target/page/ddos-vuln', 'DDoS测试页面', 1, 10),
('PATH', '/target/page/path-traversal-vuln', '路径遍历测试页面', 1, 10),
('PATH', '/target/page/file-include-vuln', '文件包含测试页面', 1, 10),
('PATH', '/target/page/ssrf-vuln', 'SSRF测试页面', 1, 10),
('PATH', '/target/page/xxe-vuln', 'XXE测试页面', 1, 10),
('PATH', '/target/page/deserial-vuln', '反序列化测试页面', 1, 10),
('PATH', '/target/page/csrf-vuln', 'CSRF测试页面', 1, 10),
('PATH', '/target/sql/safe-query', 'SQL注入安全对比接口', 1, 15),
('PATH', '/target/xss/safe-submit-comment', 'XSS安全对比接口', 1, 15),
('PATH', '/target/xss/list-comments', 'XSS评论列表接口', 1, 15),
('PATH', '/target/path/safe-read', '路径遍历安全对比接口', 1, 15),
('PATH', '/target/path/list-files', '路径遍历文件列表接口', 1, 15),
('PATH', '/target/file/safe-include', '文件包含安全对比接口', 1, 15),
('PATH', '/target/file/list-allowed', '文件包含允许列表接口', 1, 15),
('PATH', '/static/*', '静态资源路径', 1, 20),
('PATH', '/favicon.ico', '网站图标', 1, 30),
('PATH', '/actuator/*', 'Spring Boot Actuator端点', 1, 40);

-- 请求头白名单：标准HTTP请求头，不应被检测
INSERT INTO `sys_detection_whitelist` (`whitelist_type`, `whitelist_value`, `description`, `enabled`, `priority`) VALUES
('HEADER', 'Accept', 'HTTP内容协商请求头', 1, 10),
('HEADER', 'Accept-Language', '语言偏好请求头', 1, 20),
('HEADER', 'Accept-Encoding', '编码偏好请求头', 1, 30),
('HEADER', 'Accept-Charset', '字符集偏好请求头', 1, 40),
('HEADER', 'Cache-Control', '缓存控制请求头', 1, 50),
('HEADER', 'Connection', '连接控制请求头', 1, 60),
('HEADER', 'Content-Type', '内容类型请求头', 1, 70),
('HEADER', 'Content-Length', '内容长度请求头', 1, 80),
('HEADER', 'Host', '主机请求头', 1, 90),
('HEADER', 'Origin', '跨域来源请求头', 1, 100),
('HEADER', 'Referer', '来源页面请求头', 1, 110),
('HEADER', 'User-Agent', '用户代理请求头', 1, 120),
('HEADER', 'X-Forwarded-For', '代理转发请求头', 1, 130),
('HEADER', 'X-Real-IP', '真实IP请求头', 1, 140),
('HEADER', 'X-Request-ID', '请求ID请求头', 1, 150),
('HEADER', 'X-Forwarded-Proto', '转发协议请求头', 1, 160);

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
-- 4.3 初始化扫描配置数据
-- ------------------------------------------------------------

-- 初始化扫描目标（靶场服务）
INSERT INTO `sys_scan_target` (`target_name`, `target_url`, `target_type`, `description`, `enabled`) VALUES
('靶场服务', 'http://127.0.0.1:9001', 'TEST', '漏洞测试靶场服务，包含SQL注入、XSS、命令注入等多种漏洞测试接口', 1);

-- 初始化扫描接口配置（靶场服务的各个漏洞测试接口）
INSERT INTO `sys_scan_interface` (`target_id`, `interface_name`, `interface_path`, `http_method`, `vuln_type`, `risk_level`, `params_config`, `payload_config`, `match_rules`, `enabled`, `priority`) VALUES
(1, 'SQL注入测试接口', '/target/sql/query', 'GET', 'SQL_INJECTION', 'HIGH', 
 '{"id": {"type": "string", "required": true, "testValues": ["1", "1 OR 1=1", "1; SELECT 1"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["OR 1=1", "statement_results", "executed_sql"], "responsePatterns": ["SQL注入漏洞", "多语句执行成功"]}',
 1, 10),

(1, 'XSS反射型测试接口', '/target/xss/search', 'GET', 'XSS', 'MEDIUM',
 '{"keyword": {"type": "string", "required": true, "testValues": ["test", "<script>alert(1)</script>"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["scan_xss_quick", "scan_xss_full"], "responsePatterns": ["反射型XSS漏洞", "搜索成功"]}',
 1, 20),

(1, 'XSS DOM型测试接口', '/target/xss/profile', 'GET', 'XSS', 'MEDIUM',
 '{"username": {"type": "string", "required": true, "testValues": ["test", "<svg/onload=alert(1)>"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["scan_xss_quick", "scan_xss_full"], "responsePatterns": ["DOM型XSS漏洞", "获取资料成功"]}',
 1, 30),

(1, '命令注入测试接口', '/target/cmd/execute', 'GET', 'COMMAND_INJECTION', 'CRITICAL',
 '{"cmd": {"type": "string", "required": true, "testValues": ["ping 127.0.0.1 -n 2", "whoami"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["Pinging", "ping statistics", "\\\\", "root"], "responsePatterns": ["命令执行结果", "纯命令注入漏洞触发成功"]}',
 1, 5),

(1, '路径遍历测试接口', '/target/path/read', 'GET', 'PATH_TRAVERSAL', 'HIGH',
 '{"filename": {"type": "string", "required": true, "testValues": ["test.txt", "../../application.yml"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["server:", "<project", "root:"], "responsePatterns": ["路径遍历漏洞", "文件读取成功"]}',
 1, 15),

(1, '文件包含测试接口', '/target/file/include', 'GET', 'FILE_INCLUSION', 'HIGH',
 '{"path": {"type": "string", "required": true, "testValues": ["test.properties", "config/test.properties"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["db.password", "文件包含漏洞", "文件加载成功"], "responsePatterns": ["文件包含漏洞", "文件加载成功"]}',
 1, 25),

(1, 'SSRF测试接口', '/target/ssrf/request', 'GET', 'SSRF', 'HIGH',
 '{"url": {"type": "string", "required": true, "testValues": ["http://127.0.0.1:9001/target/ddos/status"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["DDoS被攻击目标状态", "SSRF漏洞", "请求成功"], "responsePatterns": ["SSRF漏洞", "请求成功（漏洞接口）"]}',
 1, 35),

(1, 'XXE测试接口', '/target/xxe/parse', 'POST', 'XXE', 'HIGH',
 '{"xmlBody": {"type": "xml", "required": true}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["has_external_entity", "test_password_123", "XXE漏洞"], "responsePatterns": ["XXE漏洞", "has_external_entity"]}',
 1, 40),

(1, 'CSRF测试接口', '/target/csrf/update-name', 'POST', 'CSRF', 'MEDIUM',
 '{"userId": {"type": "string", "required": true}, "nickname": {"type": "string", "required": true}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["CSRF漏洞", "昵称修改成功"], "responsePatterns": ["CSRF漏洞", "昵称修改成功（漏洞接口）"]}',
 1, 45);

-- 初始化Payload库（基础Payload）
INSERT INTO `sys_payload_library` (`vuln_type`, `payload_level`, `payload_content`, `match_keywords`, `description`, `risk_level`, `enabled`) VALUES
-- SQL注入Payload
('SQL_INJECTION', 'BASIC', '1 OR 1=1', 'OR 1=1', '布尔型恒真注入探测', 'HIGH', 1),
('SQL_INJECTION', 'ADVANCED', '1; SELECT 1', 'statement_results', '堆叠语句执行探测', 'HIGH', 1),
('SQL_INJECTION', 'ADVANCED', '1 UNION SELECT 1,2,3,4,5', 'UNION', '联合查询注入探测', 'HIGH', 1),
('SQL_INJECTION', 'ADVANCED', '1'' AND ''1''=''1', 'AND', '字符串型注入探测', 'MEDIUM', 1),
('SQL_INJECTION', 'ADVANCED', '1; DROP TABLE users--', 'DROP', '危险操作注入探测', 'CRITICAL', 1),
('SQL_INJECTION', 'ADVANCED', '1 AND SLEEP(3)', 'SLEEP', '时间盲注探测', 'MEDIUM', 1),

-- XSS Payload
('XSS', 'BASIC', '<svg/onload=alert(''scan_xss_quick'')>', 'scan_xss_quick', 'SVG 事件回显探测', 'MEDIUM', 1),
('XSS', 'ADVANCED', '<img src=x onerror=alert(''scan_xss_full'')>', 'scan_xss_full', 'IMG onerror 回显探测', 'MEDIUM', 1),
('XSS', 'ADVANCED', '<script>alert(''scan_xss_script'')</script>', 'scan_xss_script', 'Script 标签探测', 'HIGH', 1),
('XSS', 'ADVANCED', ''\"><script>alert(''scan_xss_attr'')</script>', 'scan_xss_attr', '属性注入探测', 'HIGH', 1),
('XSS', 'ADVANCED', '<body onload=alert(''scan_xss_body'')>', 'scan_xss_body', 'Body 事件探测', 'MEDIUM', 1),
('XSS', 'ADVANCED', '<iframe src=''javascript:alert(\"scan_xss_iframe\")''>', 'scan_xss_iframe', 'Iframe 注入探测', 'HIGH', 1),

-- 命令注入Payload（Windows）
('COMMAND_INJECTION', 'BASIC', 'ping 127.0.0.1 -n 2', 'Pinging,ping statistics', 'Ping 命令探测', 'HIGH', 1),
('COMMAND_INJECTION', 'ADVANCED', 'whoami', '\\\\,root', '用户身份探测', 'HIGH', 1),
('COMMAND_INJECTION', 'ADVANCED', 'dir', 'bytes,total', '目录列表探测', 'MEDIUM', 1),
('COMMAND_INJECTION', 'ADVANCED', 'tasklist,ps aux', 'PID', '进程列表探测', 'MEDIUM', 1),

-- 路径遍历Payload
('PATH_TRAVERSAL', 'BASIC', '../../application.yml', 'server:', '读取服务配置文件', 'HIGH', 1),
('PATH_TRAVERSAL', 'ADVANCED', '../../../../../pom.xml', '<project', '读取项目构建文件', 'HIGH', 1),
('PATH_TRAVERSAL', 'ADVANCED', '....//....//....//etc/passwd', 'root:', 'Linux 密码文件读取', 'CRITICAL', 1),
('PATH_TRAVERSAL', 'ADVANCED', '..\\..\\..\\..\\windows\\win.ini', '[fonts]', 'Windows 系统文件读取', 'CRITICAL', 1);

-- ------------------------------------------------------------
-- 4.4 初始化系统配置
-- 按监测服务配置、网关配置的顺序排列
-- ------------------------------------------------------------

-- ============================================================
-- 一、监测服务配置（不需要同步到网关）
-- ============================================================

-- ------------------------------------------------------------
-- 1.1 基础配置项
-- ------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('blacklist.default.expire.seconds', '86400', '黑名单默认过期时间（秒）'),
('alert.push.interval', '5000', '告警推送间隔（毫秒）'),
('alert.heartbeat.interval', '10000', '告警心跳间隔（毫秒）');

-- ------------------------------------------------------------
-- 1.2 防御策略配置项
-- 不同风险等级的封禁时长配置
-- ------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('defense.blacklist.default-duration-seconds', '1800', '黑名单默认持续时间(秒)'),
('defense.blacklist.high-risk-duration-seconds', '3600', '高风险攻击封禁时长(秒)'),
('defense.blacklist.critical-risk-duration-seconds', '86400', '严重风险攻击封禁时长(秒)'),
('defense.blacklist.auto-unban.enabled', 'true', '是否启用自动解封'),
('defense.blacklist.repeat-offender.multiplier', '2', '重复违规封禁时长倍数'),
('defense.blacklist.repeat-offender.max-multiplier', '8', '最大封禁时长倍数');

-- ------------------------------------------------------------
-- 1.3 告警配置项
-- ------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('alert.enabled', 'true', '是否启用告警功能'),
('alert.suppress.duration-seconds', '300', '告警抑制时长(秒)'),
('alert.aggregate.window-seconds', '60', '告警聚合时间窗口(秒)'),
('alert.email.enabled', 'false', '是否启用邮件通知'),
('alert.email.smtp.host', '', 'SMTP服务器地址'),
('alert.email.smtp.port', '465', 'SMTP端口'),
('alert.email.smtp.username', '', 'SMTP用户名'),
('alert.email.smtp.password', '', 'SMTP密码'),
('alert.email.smtp.ssl-enabled', 'true', '是否启用SSL'),
('alert.email.from-address', '', '发件人地址'),
('alert.email.to-addresses', '', '收件人地址(逗号分隔)'),
('alert.feishu.enabled', 'false', '是否启用飞书通知'),
('alert.feishu.webhook-url', '', '飞书机器人Webhook地址'),
('alert.feishu.secret', '', '飞书机器人签名密钥'),
('alert.sound.enabled', 'true', '是否启用告警声音提示'),
('alert.sound.level-threshold', 'HIGH', '触发声音提示的最低告警级别');

-- ------------------------------------------------------------
-- 1.4 监测服务专用配置项
-- ------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('ddos.peak-rps.record.enabled', 'true', '是否记录峰值RPS'),
('traffic.push.aggregate-interval-ms', '5000', '聚合推送统计周期(毫秒)');

-- ============================================================
-- 二、网关配置（需要同步到网关，共110项）
-- ============================================================

-- ------------------------------------------------------------
-- 3.1 网关核心配置（gateway.*）
-- ------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('gateway.defense.blacklist.enabled', 'true', '网关-黑名单防御开关'),
('gateway.defense.rate-limit.enabled', 'true', '网关-限流防御开关'),
('gateway.defense.malicious-request.enabled', 'true', '网关-恶意请求拦截开关'),
('gateway.defense.rate-limit.default-threshold', '30', '网关-默认限流阈值(次/秒)'),
('gateway.defense.rate-limit.window-size', '1000', '网关-限流时间窗口(毫秒)'),
('gateway.defense.blacklist.default-expire-seconds', '600', '网关-黑名单默认过期时间(秒)'),
('gateway.defense.malicious.user-agents', 'sqlmap,nessus,nmap,burp suite,zaproxy,nikto,w3af,arachni,skipfish,wvs,dirb,gobuster,ffuf,hydra,medusa', '网关-恶意User-Agent列表(逗号分隔)'),
('gateway.defense.malicious.uri-patterns', '/admin,/manager,/console,/wp-admin,/phpmyadmin,/mysql,/dbadmin,/webdav,/.git/config,/.env,/config/database.yml,/backup,/dump,/export,/download', '网关-恶意URI模式列表(逗号分隔)'),
('gateway.cache.traffic-expire-ms', '3600000', '网关-流量缓存过期时间(毫秒)'),
('gateway.cache.blacklist-expire-ms', '600000', '网关-黑名单缓存过期时间(毫秒)'),
('gateway.cache.cleanup-interval-ms', '60000', '网关-缓存清理间隔(毫秒)'),
('gateway.attack-state.cooldown-duration-ms', '300000', '网关-冷却持续时间(毫秒)'),
('gateway.attack-state.state-expire-ms', '600000', '网关-攻击状态过期时间(毫秒)'),
('gateway.request.max-body-size', '102400', '网关-最大请求体大小(字节)'),
('gateway.request.abnormal-response-threshold-ms', '3000', '网关-异常响应时间阈值(毫秒)'),
('gateway.defense.rate-limit.peak-threshold', '60', '业务高峰期限流阈值(次/秒)'),
('gateway.defense.ban.duration-base-ms', '300000', '封禁基础时长(毫秒)'),
('gateway.defense.ban.duration-multiplier', '6', '重复违规封禁时长倍数');

-- ------------------------------------------------------------
-- 3.2 DDoS检测配置（ddos.*）
-- ------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('ddos.threshold', '50', 'DDoS检测阈值(次/秒)'),
('ddos.detection.window-ms', '1000', 'DDoS检测时间窗口(毫秒)'),
('ddos.rate-limit-trigger-count', '5', '连续限流触发封禁阈值(次)'),
('ddos.rate-limit-trigger-window-seconds', '30', '连续限流检测时间窗口(秒)'),
('ddos.slow-attack.threshold-rps', '5', 'DDoS慢速攻击检测阈值'),
('ddos.global-attack.related-ip-threshold', '5', 'DDoS分布式攻击关联IP阈值');

-- ------------------------------------------------------------
-- 3.3 状态机配置（state.*）
-- ------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('state.normal-to-suspicious.threshold-rps', '30', 'NORMAL到SUSPICIOUS的RPS阈值(次/秒)'),
('state.normal-to-suspicious.window-ms', '1000', 'NORMAL到SUSPICIOUS的检测窗口(毫秒)'),
('state.normal-to-suspicious.slide-step-ms', '100', '滑动窗口步进(毫秒)'),
('state.suspicious-to-attacking.duration-ms', '5000', 'SUSPICIOUS持续多久转为ATTACKING(毫秒)'),
('state.suspicious-to-attacking.min-requests', '50', 'SUSPICIOUS期间最小请求数'),
('state.suspicious-to-attacking.uri-diversity-threshold', '3', 'URI多样性阈值(不同URI数量)'),
('state.suspicious-to-normal.quiet-duration-ms', '10000', 'SUSPICIOUS静止多久恢复NORMAL(毫秒)'),
('state.suspicious.timeout-ms', '30000', 'SUSPICIOUS状态超时时间(毫秒)'),
('state.defended-to-cooldown.quiet-duration-ms', '30000', 'DEFENDED静止多久进入COOLDOWN(毫秒)'),
('state.cooldown.base-duration-ms', '180000', 'COOLDOWN基础时长(毫秒)'),
('state.cooldown.max-duration-ms', '600000', 'COOLDOWN最大时长(毫秒)'),
('state.cooldown.attack-intensity-multiplier', '0.5', '攻击强度系数'),
('state.cooldown.timeout-ms', '600000', 'COOLDOWN状态超时时间(毫秒)'),
('state.cooldown-to-attacking.threshold-rps', '20', 'COOLDOWN期间重新攻击的RPS阈值'),
('state.slow-attack.duration-ms', '60000', '慢速攻击判定持续时间(毫秒)'),
('state.slow-attack.threshold-rps', '5', '慢速攻击RPS阈值'),
('state.global-attack.related-ip-threshold', '5', '触发分布式攻击判定的关联IP数量阈值'),
('state.global-attack.network-mask', '24', '关联IP网络掩码'),
('state.manual-reset.log-required', 'true', '人工重置是否必须记录日志'),
('state.manual-reset.operator-required', 'true', '人工重置是否必须填写操作人');

-- ------------------------------------------------------------
-- 3.4 冷却配置（cooldown.*）
-- ------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('cooldown.dynamic.enabled', 'true', '是否启用动态冷却时长'),
('cooldown.base-duration-ms', '180000', '冷却基础时长(毫秒)'),
('cooldown.max-duration-ms', '600000', '冷却最大时长(毫秒)'),
('cooldown.intensity-multiplier', '0.5', '攻击强度系数'),
('cooldown.history-multiplier', '1.5', '历史攻击次数系数'),
('cooldown.history-max-multiplier', '5', '历史攻击次数最大系数');

-- ------------------------------------------------------------
-- 3.5 置信度配置（confidence.*）
-- ------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('confidence.base-score', '30', '置信度基础分'),
('confidence.frequency.max-score', '25', '频率异常最高分'),
('confidence.frequency.per-exceed-score', '5', '每超过阈值1倍的得分'),
('confidence.diversity.max-score', '20', '多样性最高分'),
('confidence.diversity.per-uri-score', '3', '每个不同URI的得分'),
('confidence.persistence.max-score', '15', '持续时间最高分'),
('confidence.persistence.per-10s-score', '3', '每持续10秒的得分'),
('confidence.pattern.max-score', '10', '攻击模式匹配最高分'),
('confidence.pattern.partial-score', '5', '攻击模式部分匹配得分'),
('confidence.normal-behavior.max-deduction', '20', '正常行为最高抵扣'),
('confidence.normal-behavior.no-history-deduction', '5', '无历史攻击记录抵扣'),
('confidence.normal-behavior.normal-requests-deduction', '15', '历史正常请求多抵扣'),
('confidence.smooth.strategy', 'ONLY_UP', '置信度平滑策略'),
('confidence.smooth.alpha', '0.4', '滑动平均系数'),
('confidence.history.max-score', '10', '历史行为最高分'),
('confidence.history.no-attack-deduction', '5', '无历史攻击记录抵扣'),
('confidence.history.has-attack-score', '10', '历史有攻击记录得分'),
('confidence.history.normal-rate-deduction', '10', '近1小时正常请求占比>90%抵扣'),
('confidence.slow-attack.max-score', '10', '慢速攻击最高分'),
('confidence.slow-attack.per-minute-score', '5', '慢速攻击每分钟得分'),
('confidence.global-attack.max-score', '10', '分布式攻击最高分'),
('confidence.global-attack.per-ip-score', '2', '每个关联IP得分'),
('confidence.no-decrease.enabled', 'true', '是否启用置信度只升不降策略'),
('confidence.min-value', '10', '置信度最小值'),
('confidence.blocked.rate-limit-score', '5', '被限流后的置信度得分'),
('confidence.blocked.blacklist-score', '10', '被加入黑名单后的置信度得分'),
('confidence.blocked.max-daily-score', '30', '每日最大封禁得分');

-- ------------------------------------------------------------
-- 3.6 流量推送配置（traffic.*）
-- ------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('traffic.push.normal.strategy', 'realtime', 'NORMAL状态推送策略'),
('traffic.push.suspicious.strategy', 'aggregate', 'SUSPICIOUS状态推送策略'),
('traffic.push.attacking.strategy', 'aggregate', 'ATTACKING状态推送策略'),
('traffic.push.defended.strategy', 'aggregate', 'DEFENDED状态推送策略'),
('traffic.push.cooldown.strategy', 'sampling', 'COOLDOWN状态推送策略'),
('traffic.push.batch-interval-ms', '5000', '批量推送间隔(毫秒)'),
('traffic.push.sampling-rate', '10', '采样推送比例(1/N)'),
('traffic.push.enabled', 'true', '是否启用流量推送'),
('traffic.push.interval-ms', '3000', '流量推送周期(毫秒)'),
('traffic.push.interval-low-ms', '10000', '低谷期流量推送间隔(毫秒)'),
('traffic.push.retry.max-count', '3', '推送失败最大重试次数'),
('traffic.push.retry.delay-ms', '1000', '重试延迟基础时间(毫秒)'),
('traffic.push.retry.max-queue-size', '10000', '重试队列最大大小'),
('traffic.push.retry-interval-ms', '500,1000,2000', '推送重试间隔序列(毫秒)'),
('traffic.push.memory.max-usage-percent', '80', '内存使用上限百分比'),
('traffic.push.memory.force-flush-threshold', '90', '强制推送内存阈值百分比'),
('traffic.push.degradation.enabled', 'true', '是否启用降级模式'),
('traffic.push.degradation.local-cache-size', '50000', '降级模式本地缓存大小'),
('traffic.push.degradation.health-check-interval-ms', '30000', '下游服务健康检查间隔(毫秒)'),
('traffic.push.fallback-enabled', 'true', '是否启用推送失败降级'),
('traffic.sample.max-per-uri', '3', '每个URI模式保留的最大样本数'),
('traffic.sample.max-total', '20', '单次推送保留的最大样本总数'),
('traffic.sample.abnormal-priority', 'true', '是否优先保留异常样本'),
('traffic.sample.desensitize-enabled', 'true', '是否启用样本脱敏'),
('traffic.aggregate.uri-pattern-depth', '2', 'URI模式聚合深度'),
('traffic.aggregate.max-uri-groups', '50', '单次推送最大URI分组数'),
('traffic.aggregate.batch-threshold', '10', '批量聚合推送阈值'),
('traffic.queue.single-ip-capacity', '50', '单个IP流量队列容量上限'),
('traffic.queue.global-capacity', '1000', '全局流量队列容量上限'),
('traffic.queue.overflow-strategy', 'DROP_OLDEST_SAMPLE', '队列溢出策略'),
('traffic.business-peak.enabled', 'true', '是否启用业务高峰模式'),
('traffic.business-peak.threshold-multiplier', '2', '业务高峰期RPS阈值倍数'),
('traffic.business-peak.time-ranges', '09:00-12:00,14:00-18:00', '业务高峰时段');

-- ------------------------------------------------------------
-- 4.4 初始化角色数据
-- 内置三个默认角色：超级管理员、安全管理员、审计管理员
-- ------------------------------------------------------------
INSERT INTO `sys_role` (`role_name`, `role_code`, `role_desc`, `status`, `create_by`, `remark`) VALUES
('超级管理员', 'SUPER_ADMIN', '拥有系统所有权限', 0, 'system', '系统内置角色，不可删除'),
('安全管理员', 'SECURITY_ADMIN', '负责安全监控和防御操作', 0, 'system', '系统内置角色，不可删除'),
('审计管理员', 'AUDIT_ADMIN', '仅拥有日志查看权限', 0, 'system', '系统内置角色，不可删除');

-- ------------------------------------------------------------
-- 4.5 初始化菜单权限数据
-- ------------------------------------------------------------
-- 一级菜单
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `path`, `component`, `icon`, `sort_order`, `visible`, `status`, `create_by`) VALUES
(1, 0, '系统概览', 1, 'dashboard', '/', '/dashboard', 'dashboard', 1, 0, 0, 'system'),
(2, 0, '流量监测', 0, 'traffic', '/traffic', NULL, 'monitor', 2, 0, 0, 'system'),
(3, 0, '攻击监测', 0, 'attack', '/attack', NULL, 'warning', 3, 0, 0, 'system'),
(4, 0, '防御管理', 0, 'defense', '/defense', NULL, 'security', 4, 0, 0, 'system'),
(5, 0, '漏洞监测', 1, 'vulnerability', '/vulnerability', '/vulnerability', 'bug', 5, 0, 0, 'system'),
(6, 0, '数据报表', 1, 'report', '/report', '/report', 'chart', 6, 0, 0, 'system'),
(7, 0, '系统管理', 0, 'system', '/system', NULL, 'setting', 7, 0, 0, 'system');

-- 二级菜单 - 流量监测
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `path`, `component`, `icon`, `sort_order`, `visible`, `status`, `create_by`) VALUES
(21, 2, '流量列表', 1, 'traffic:list', '/traffic/list', '/traffic-list', 'list', 1, 0, 0, 'system');

-- 二级菜单 - 攻击监测
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `path`, `component`, `icon`, `sort_order`, `visible`, `status`, `create_by`) VALUES
(31, 3, '攻击列表', 1, 'attack:list', '/attack/list', '/attack-list', 'list', 1, 0, 0, 'system');

-- 二级菜单 - 防御管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `path`, `component`, `icon`, `sort_order`, `visible`, `status`, `create_by`) VALUES
(41, 4, '黑名单管理', 1, 'blacklist:list', '/defense/blacklist', '/blacklist-manage', 'lock', 1, 0, 0, 'system'),
(42, 4, '防御日志', 1, 'defense:log', '/defense/log', '/defense-log', 'file-text', 2, 0, 0, 'system'),
(43, 4, '规则管理', 1, 'rule:list', '/defense/rule', '/rule-manage', 'tool', 3, 0, 0, 'system');

-- 二级菜单 - 系统管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `path`, `component`, `icon`, `sort_order`, `visible`, `status`, `create_by`) VALUES
(71, 7, '系统配置', 1, 'config:list', '/system/config', '/sys-config', 'setting', 1, 0, 0, 'system'),
(72, 7, '用户管理', 1, 'user:list', '/system/user', '/user-manage', 'user', 2, 0, 0, 'system'),
(73, 7, '角色管理', 1, 'role:list', '/system/role', '/role-manage', 'team', 3, 0, 0, 'system'),
(74, 7, '菜单管理', 1, 'menu:list', '/system/menu', '/menu-manage', 'menu', 4, 0, 0, 'system'),
(75, 7, '操作日志', 1, 'operlog:list', '/system/log', '/oper-log', 'history', 5, 0, 0, 'system');

-- 按钮权限 - 黑名单管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `path`, `sort_order`, `visible`, `status`, `create_by`) VALUES
(411, 41, '新增黑名单', 2, 'blacklist:add', NULL, 1, 0, 0, 'system'),
(412, 41, '编辑黑名单', 2, 'blacklist:edit', NULL, 2, 0, 0, 'system'),
(413, 41, '删除黑名单', 2, 'blacklist:delete', NULL, 3, 0, 0, 'system'),
(414, 41, '解封IP', 2, 'blacklist:unblock', NULL, 4, 0, 0, 'system');

-- 按钮权限 - 规则管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `path`, `sort_order`, `visible`, `status`, `create_by`) VALUES
(431, 43, '新增规则', 2, 'rule:add', NULL, 1, 0, 0, 'system'),
(432, 43, '编辑规则', 2, 'rule:edit', NULL, 2, 0, 0, 'system'),
(433, 43, '删除规则', 2, 'rule:delete', NULL, 3, 0, 0, 'system'),
(434, 43, '启用/禁用规则', 2, 'rule:toggle', NULL, 4, 0, 0, 'system');

-- 按钮权限 - 用户管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `path`, `sort_order`, `visible`, `status`, `create_by`) VALUES
(721, 72, '新增用户', 2, 'user:add', NULL, 1, 0, 0, 'system'),
(722, 72, '编辑用户', 2, 'user:edit', NULL, 2, 0, 0, 'system'),
(723, 72, '删除用户', 2, 'user:delete', NULL, 3, 0, 0, 'system'),
(724, 72, '重置密码', 2, 'user:resetPwd', NULL, 4, 0, 0, 'system'),
(725, 72, '分配角色', 2, 'user:assignRole', NULL, 5, 0, 0, 'system');

-- 按钮权限 - 角色管理
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `path`, `sort_order`, `visible`, `status`, `create_by`) VALUES
(731, 73, '新增角色', 2, 'role:add', NULL, 1, 0, 0, 'system'),
(732, 73, '编辑角色', 2, 'role:edit', NULL, 2, 0, 0, 'system'),
(733, 73, '删除角色', 2, 'role:delete', NULL, 3, 0, 0, 'system'),
(734, 73, '分配权限', 2, 'role:assignPerm', NULL, 4, 0, 0, 'system');

-- ------------------------------------------------------------
-- 4.6 初始化角色权限关联
-- 超级管理员拥有所有权限
-- ------------------------------------------------------------
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `sys_menu` WHERE del_flag = 0;

-- 安全管理员权限（监控、防御操作）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 1), (2, 2), (2, 21), (2, 3), (2, 31), (2, 4), (2, 41), (2, 42), (2, 43),
(2, 411), (2, 412), (2, 413), (2, 414), (2, 431), (2, 432), (2, 433), (2, 434),
(2, 5), (2, 6);

-- 审计管理员权限（仅查看）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 1), (3, 2), (3, 21), (3, 3), (3, 31), (3, 4), (3, 41), (3, 42), (3, 43),
(3, 5), (3, 6), (3, 75);

-- ------------------------------------------------------------
-- 4.7 管理员账号初始化说明
-- 
-- 管理员账号由应用程序在首次启动时自动创建：
-- 1. 如果配置了 admin.init.password，则使用配置的密码
-- 2. 如果未配置，则自动生成12位随机密码并打印到启动日志
-- 
-- 配置方式（application.yml）：
-- admin:
--   init:
--     password: your-secure-password
-- 
-- 首次启动后请查看应用日志获取初始密码
-- 生产环境请务必在首次登录后修改密码
-- ------------------------------------------------------------

-- ------------------------------------------------------------
-- 4.8 告警相关表结构（第三阶段新增）
-- ------------------------------------------------------------

-- 告警表
DROP TABLE IF EXISTS `sys_alert`;
CREATE TABLE `sys_alert` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '告警ID',
    `alert_id` VARCHAR(64) NOT NULL COMMENT '告警唯一标识(UUID)',
    `event_id` VARCHAR(64) DEFAULT NULL COMMENT '关联事件ID',
    `attack_id` BIGINT DEFAULT NULL COMMENT '关联攻击记录ID',
    `source_ip` VARCHAR(128) NOT NULL COMMENT '攻击源IP',
    `attack_type` VARCHAR(50) NOT NULL COMMENT '攻击类型',
    `alert_level` VARCHAR(20) NOT NULL COMMENT '告警级别(CRITICAL/HIGH/MEDIUM/LOW)',
    `alert_title` VARCHAR(255) NOT NULL COMMENT '告警标题',
    `alert_content` TEXT DEFAULT NULL COMMENT '告警详情',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态(0-待处理，1-已确认，2-已忽略)',
    `is_suppressed` TINYINT DEFAULT 0 COMMENT '是否被抑制(0-否，1-是)',
    `suppress_until` DATETIME DEFAULT NULL COMMENT '抑制截止时间',
    `notify_channels` VARCHAR(100) DEFAULT NULL COMMENT '通知渠道(EMAIL/FEISHU，逗号分隔)',
    `notify_status` TINYINT DEFAULT 0 COMMENT '通知状态(0-未发送，1-已发送，2-发送失败)',
    `notify_time` DATETIME DEFAULT NULL COMMENT '通知发送时间',
    `confirm_by` VARCHAR(64) DEFAULT NULL COMMENT '确认人',
    `confirm_time` DATETIME DEFAULT NULL COMMENT '确认时间',
    `ignore_reason` VARCHAR(500) DEFAULT NULL COMMENT '忽略原因',
    `ignore_by` VARCHAR(64) DEFAULT NULL COMMENT '忽略人',
    `ignore_time` DATETIME DEFAULT NULL COMMENT '忽略时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alert_id` (`alert_id`),
    KEY `idx_event_id` (`event_id`),
    KEY `idx_attack_id` (`attack_id`),
    KEY `idx_source_ip` (`source_ip`),
    KEY `idx_alert_level` (`alert_level`),
    KEY `idx_status` (`status`),
    KEY `idx_is_suppressed` (`is_suppressed`),
    KEY `idx_notify_status` (`notify_status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警表';

-- 告警规则表
DROP TABLE IF EXISTS `sys_alert_rule`;
CREATE TABLE `sys_alert_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '规则ID',
    `rule_name` VARCHAR(100) NOT NULL COMMENT '规则名称',
    `rule_code` VARCHAR(50) NOT NULL COMMENT '规则编码',
    `attack_type` VARCHAR(50) DEFAULT NULL COMMENT '攻击类型(为空表示所有类型)',
    `risk_level` VARCHAR(20) DEFAULT NULL COMMENT '风险等级条件(HIGH/MEDIUM/LOW)',
    `alert_level` VARCHAR(20) NOT NULL COMMENT '触发告警级别(CRITICAL/HIGH/MEDIUM/LOW)',
    `threshold_count` INT DEFAULT 1 COMMENT '触发阈值(次数)',
    `threshold_window_seconds` INT DEFAULT 60 COMMENT '阈值时间窗口(秒)',
    `suppress_duration_seconds` INT DEFAULT 300 COMMENT '抑制时长(秒)',
    `notify_channels` VARCHAR(100) DEFAULT 'EMAIL,FEISHU' COMMENT '通知渠道',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态(0-禁用，1-启用)',
    `priority` INT DEFAULT 100 COMMENT '规则优先级(数字越小优先级越高)',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '规则描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_code` (`rule_code`),
    KEY `idx_attack_type` (`attack_type`),
    KEY `idx_risk_level` (`risk_level`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警规则表';

-- ------------------------------------------------------------
-- 4.8.15 配置版本管理表
-- 用于配置同步机制
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_config_version`;
CREATE TABLE `sys_config_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `version` VARCHAR(32) NOT NULL COMMENT '配置版本号（格式：vYYYYMMDDNNN）',
    `change_type` VARCHAR(20) NOT NULL COMMENT '变更类型（ADD/UPDATE/DELETE）',
    `change_count` INT DEFAULT 0 COMMENT '变更配置项数量',
    `change_detail` TEXT DEFAULT NULL COMMENT '变更详情（JSON格式）',
    `operator` VARCHAR(64) DEFAULT NULL COMMENT '操作人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配置版本表';

-- ------------------------------------------------------------
-- 4.8.16 网关同步状态表
-- 记录网关配置同步状态
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_config_sync_status`;
CREATE TABLE `sys_config_sync_status` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `service_name` VARCHAR(50) NOT NULL COMMENT '服务名称',
    `service_instance` VARCHAR(100) NOT NULL COMMENT '服务实例标识',
    `sync_version` VARCHAR(32) NOT NULL COMMENT '已同步的配置版本',
    `sync_time` DATETIME NOT NULL COMMENT '同步时间',
    `sync_status` VARCHAR(20) NOT NULL COMMENT '同步状态（SUCCESS/FAILED）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_service_instance` (`service_name`, `service_instance`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网关同步状态表';

-- ------------------------------------------------------------
-- 4.10 初始化告警规则
-- ------------------------------------------------------------
INSERT INTO `sys_alert_rule` (`rule_name`, `rule_code`, `attack_type`, `risk_level`, `alert_level`, `threshold_count`, `threshold_window_seconds`, `suppress_duration_seconds`, `notify_channels`, `enabled`, `priority`, `description`) VALUES
('高危攻击告警', 'HIGH_RISK_ATTACK', NULL, 'HIGH', 'HIGH', 1, 60, 300, 'EMAIL,FEISHU', 1, 10, '检测到高危攻击时立即告警'),
('严重攻击告警', 'CRITICAL_ATTACK', NULL, 'CRITICAL', 'CRITICAL', 1, 60, 600, 'EMAIL,FEISHU', 1, 5, '检测到严重攻击时立即告警'),
('DDoS攻击告警', 'DDOS_ATTACK', 'DDOS', NULL, 'HIGH', 1, 30, 180, 'EMAIL,FEISHU', 1, 15, '检测到DDoS攻击时告警'),
('SQL注入攻击告警', 'SQL_INJECTION_ATTACK', 'SQL_INJECTION', NULL, 'HIGH', 1, 60, 300, 'EMAIL,FEISHU', 1, 20, '检测到SQL注入攻击时告警'),
('XSS攻击告警', 'XSS_ATTACK', 'XSS', NULL, 'MEDIUM', 3, 60, 180, 'FEISHU', 1, 30, '检测到XSS攻击时告警'),
('命令注入攻击告警', 'COMMAND_INJECTION_ATTACK', 'COMMAND_INJECTION', NULL, 'CRITICAL', 1, 60, 600, 'EMAIL,FEISHU', 1, 10, '检测到命令注入攻击时告警'),
('暴力破解告警', 'BRUTE_FORCE_ATTACK', 'BRUTE_FORCE', NULL, 'MEDIUM', 5, 60, 300, 'FEISHU', 1, 25, '检测到暴力破解行为时告警');

-- ------------------------------------------------------------
-- 4.11 告警相关菜单权限
-- ------------------------------------------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `path`, `component`, `icon`, `sort_order`, `visible`, `status`, `create_by`) VALUES
(8, 0, '告警管理', 0, 'alert', '/alert', NULL, 'bell', 8, 0, 0, 'system');

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `path`, `component`, `icon`, `sort_order`, `visible`, `status`, `create_by`) VALUES
(81, 8, '告警列表', 1, 'alert:list', '/alert/list', '/alert-list', 'list', 1, 0, 0, 'system'),
(82, 8, '告警配置', 1, 'alert:config', '/alert/config', '/alert-config', 'setting', 2, 0, 0, 'system');

INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `permission`, `path`, `sort_order`, `visible`, `status`, `create_by`) VALUES
(811, 81, '确认告警', 2, 'alert:confirm', NULL, 1, 0, 0, 'system'),
(812, 81, '忽略告警', 2, 'alert:ignore', NULL, 2, 0, 0, 'system'),
(813, 81, '批量确认', 2, 'alert:batch-confirm', NULL, 3, 0, 0, 'system'),
(821, 82, '编辑邮件配置', 2, 'alert:config:email', NULL, 1, 0, 0, 'system'),
(822, 82, '编辑飞书配置', 2, 'alert:config:feishu', NULL, 2, 0, 0, 'system'),
(823, 82, '测试通知', 2, 'alert:config:test', NULL, 3, 0, 0, 'system');

-- 安全管理员告警权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 8), (2, 81), (2, 82), (2, 811), (2, 812), (2, 813), (2, 821), (2, 822), (2, 823);

-- 审计管理员告警权限（仅查看）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(3, 8), (3, 81);

-- ============================================================
-- 5. 完成提示
-- ============================================================
SET FOREIGN_KEY_CHECKS = 1;

SELECT '========================================' AS '';
SELECT '数据库初始化完成！' AS '提示';
SELECT '========================================' AS '';
SELECT CONCAT('数据库：', DATABASE()) AS database_info;
SELECT '核心表：sys_traffic_monitor, sys_attack_monitor, sys_attack_event, sys_defense_log' AS '核心表';
SELECT '黑名单表：sys_ip_blacklist, sys_ip_blacklist_history' AS '黑名单表';
SELECT '系统表：sys_user, sys_role, sys_menu, sys_config' AS '系统表';
SELECT CONCAT('表数量：', COUNT(*)) AS table_count 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'network_monitor';
