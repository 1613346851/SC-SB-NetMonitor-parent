-- ============================================================
-- 数据库迁移脚本：为 sys_traffic_monitor 表添加 traffic_id 字段
-- 用于存储网关服务生成的原始流量 ID
-- ============================================================

USE `network_monitor`;

-- 添加 traffic_id 字段
ALTER TABLE `sys_traffic_monitor` 
ADD COLUMN `traffic_id` VARCHAR(64) DEFAULT NULL COMMENT '原始流量 ID（网关生成）' AFTER `id`;

-- 为 traffic_id 添加唯一索引
ALTER TABLE `sys_traffic_monitor` 
ADD UNIQUE KEY `idx_traffic_id` (`traffic_id`);

-- 验证修改
SHOW CREATE TABLE `sys_traffic_monitor`;
