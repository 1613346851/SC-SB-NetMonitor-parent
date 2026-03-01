-- 为sys_user表添加create_time字段
USE vuln_target;

-- 添加create_time字段（时间戳类型，默认当前时间）
ALTER TABLE sys_user 
ADD COLUMN create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 更新现有数据的create_time为当前时间
UPDATE sys_user 
SET create_time = CURRENT_TIMESTAMP 
WHERE create_time IS NULL;

-- 验证表结构
DESCRIBE sys_user;