-- ============================================================
-- 网关配置项初始化脚本
-- Gateway Configuration Initialization Script
-- 
-- 版本: v1.0
-- 创建日期: 2026-03-24
-- 说明: 为网关服务添加统一管理的配置项
-- 
-- 执行前提:
-- 1. 已创建 sys_config 表
-- 2. 监测服务已正常运行
-- 
-- 配置项分类:
-- 1. 网关防御开关配置 (3项)
-- 2. 限流配置 (2项)
-- 3. 黑名单配置 (1项)
-- 4. 恶意请求检测配置 (2项)
-- 5. 缓存配置 (3项)
-- 6. 攻击状态配置 (2项)
-- 7. 请求限制配置 (2项)
-- ============================================================

USE `network_monitor`;

-- ============================================================
-- 1. 网关防御开关配置
-- ============================================================
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('gateway.defense.blacklist.enabled', 'true', '网关-黑名单防御开关'),
('gateway.defense.rate-limit.enabled', 'true', '网关-限流防御开关'),
('gateway.defense.malicious-request.enabled', 'true', '网关-恶意请求拦截开关');

-- ============================================================
-- 2. 限流配置
-- ============================================================
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('gateway.defense.rate-limit.default-threshold', '10', '网关-默认限流阈值(次/秒)'),
('gateway.defense.rate-limit.window-size', '1000', '网关-限流时间窗口(毫秒)');

-- ============================================================
-- 3. 黑名单配置
-- ============================================================
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('gateway.defense.blacklist.default-expire-seconds', '600', '网关-黑名单默认过期时间(秒)');

-- ============================================================
-- 4. 恶意请求检测配置
-- ============================================================
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('gateway.defense.malicious.user-agents', 'sqlmap,nessus,nmap,burp suite,zaproxy,nikto,w3af,arachni,skipfish,wvs,dirb,gobuster,ffuf,hydra,medusa', '网关-恶意User-Agent列表(逗号分隔)'),
('gateway.defense.malicious.uri-patterns', '/admin,/manager,/console,/wp-admin,/phpmyadmin,/mysql,/dbadmin,/webdav,/.git/config,/.env,/config/database.yml,/backup,/dump,/export,/download', '网关-恶意URI模式列表(逗号分隔)');

-- ============================================================
-- 5. 缓存配置
-- ============================================================
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('gateway.cache.traffic-expire-ms', '3600000', '网关-流量缓存过期时间(毫秒)'),
('gateway.cache.blacklist-expire-ms', '600000', '网关-黑名单缓存过期时间(毫秒)'),
('gateway.cache.cleanup-interval-ms', '60000', '网关-缓存清理间隔(毫秒)');

-- ============================================================
-- 6. 攻击状态配置
-- ============================================================
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('gateway.attack-state.cooldown-duration-ms', '300000', '网关-冷却持续时间(毫秒)'),
('gateway.attack-state.state-expire-ms', '600000', '网关-攻击状态过期时间(毫秒)');

-- ============================================================
-- 7. 请求限制配置
-- ============================================================
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
('gateway.request.max-body-size', '102400', '网关-最大请求体大小(字节)'),
('gateway.request.abnormal-response-threshold-ms', '3000', '网关-异常响应时间阈值(毫秒)');

-- ============================================================
-- 验证配置项
-- ============================================================
SELECT '========================================' AS '';
SELECT '网关配置项初始化完成！' AS '提示';
SELECT '========================================' AS '';
SELECT CONCAT('新增配置项数量：', COUNT(*)) AS config_count
FROM `sys_config` 
WHERE `config_key` LIKE 'gateway.%';

SELECT '配置项清单：' AS '';
SELECT `config_key`, `config_value`, `description` 
FROM `sys_config` 
WHERE `config_key` LIKE 'gateway.%'
ORDER BY `config_key`;
