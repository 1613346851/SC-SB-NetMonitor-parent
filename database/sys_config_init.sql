-- ============================================================
-- 系统配置表初始化脚本
-- ============================================================

-- 删除已存在的表（如果存在）
DROP TABLE IF EXISTS `sys_config`;

-- 创建系统配置表
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

-- 初始化系统配置数据
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('ddos.threshold', '100', 'DDoS 检测阈值（次/分钟）'),
('blacklist.default.expire.seconds', '86400', '黑名单默认过期时间（秒）'),
('alert.enabled', 'true', '是否启用告警通知'),
('alert.push.interval', '5000', '告警推送间隔（毫秒）'),
('alert.heartbeat.interval', '10000', '告警心跳间隔（毫秒）');