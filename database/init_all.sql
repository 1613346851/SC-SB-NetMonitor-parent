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
('文件包含 - data 协议', 'FILE_INCLUSION', '(?i)data://', '检测 data 协议文件包含', 'HIGH', 1, 10),
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
-- 4.3 初始化系统配置
-- ------------------------------------------------------------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('ddos.threshold', '100', 'DDoS 检测阈值（次/分钟）'),
('blacklist.default.expire.seconds', '86400', '黑名单默认过期时间（秒）'),
('alert.enabled', 'true', '是否启用告警通知'),
('alert.push.interval', '5000', '告警推送间隔（毫秒）'),
('alert.heartbeat.interval', '10000', '告警心跳间隔（毫秒）');

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
