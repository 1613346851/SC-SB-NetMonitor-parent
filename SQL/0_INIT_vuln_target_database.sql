-- ============================================================
-- 靶场服务数据库完整初始化脚本
-- 功能：创建数据库、表结构、初始化测试数据
-- 执行顺序：此脚本应第一个执行，包含所有表创建和数据初始化
-- 数据库：MySQL 5.7+ / MySQL 8.0+
-- 字符集：utf8mb4
-- ============================================================

-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS vuln_target 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_unicode_ci;

USE vuln_target;

-- ============================================================
-- 2. 创建用户表（用于SQL注入测试）
-- ============================================================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(50) NOT NULL COMMENT '密码（明文存储，仅用于漏洞演示）',
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表（SQL注入测试用）';

-- 初始化用户测试数据
INSERT INTO sys_user (username, password, phone, create_time) VALUES
('admin', 'admin@123', '13800138000', NOW()),
('test1', 'test1@456', '13900139111', NOW()),
('test2', 'test2@456', '13900139222', NOW()),
('test3', 'test3@456', '13900139333', NOW()),
('test4', 'test4@456', '13900139444', NOW()),
('superadmin', 'super@admin@888', '13700137000', NOW()),
('guest', 'guest@123', '13600136000', NOW());

-- ============================================================
-- 3. 创建评论表（用于存储型XSS测试）
-- ============================================================
DROP TABLE IF EXISTS sys_comment;
CREATE TABLE sys_comment (
    id VARCHAR(64) PRIMARY KEY COMMENT '评论ID',
    content TEXT NOT NULL COMMENT '评论内容（可能包含恶意XSS脚本）',
    username VARCHAR(50) NOT NULL COMMENT '提交用户',
    create_time BIGINT NOT NULL COMMENT '创建时间戳（毫秒）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统评论表（XSS测试用）';

-- 创建索引优化查询性能
CREATE INDEX idx_comment_username ON sys_comment(username);
CREATE INDEX idx_comment_create_time ON sys_comment(create_time);

-- 初始化评论测试数据
INSERT INTO sys_comment (id, content, username, create_time) VALUES
(REPLACE(UUID(), '-', ''), '系统公告：欢迎使用评论功能，请文明发言！', 'admin', UNIX_TIMESTAMP() * 1000),
(REPLACE(UUID(), '-', ''), '这是一个正常的测试评论，用于验证功能。', 'test_user_001', (UNIX_TIMESTAMP() - 3600) * 1000),
(REPLACE(UUID(), '-', ''), 'Another normal comment for testing purposes.', 'test_user_002', (UNIX_TIMESTAMP() - 7200) * 1000),
(REPLACE(UUID(), '-', ''), '第三条评论内容，用于验证分页和排序功能。', 'test_user_003', (UNIX_TIMESTAMP() - 10800) * 1000);

-- ============================================================
-- 4. 创建用户配置表（用于CSRF测试）
-- ============================================================
DROP TABLE IF EXISTS sys_user_config;
CREATE TABLE sys_user_config (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    user_id INT NOT NULL COMMENT '用户ID',
    nickname VARCHAR(100) DEFAULT '' COMMENT '用户昵称',
    email VARCHAR(100) DEFAULT '' COMMENT '邮箱',
    avatar VARCHAR(255) DEFAULT '' COMMENT '头像URL',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户配置表（CSRF测试用）';

-- 初始化用户配置数据
INSERT INTO sys_user_config (user_id, nickname, email, avatar) VALUES
(1, '超级管理员', 'admin@example.com', '/avatar/admin.png'),
(2, '测试用户1', 'test1@example.com', '/avatar/test1.png'),
(3, '测试用户2', 'test2@example.com', '/avatar/test2.png'),
(4, '测试用户3', 'test3@example.com', '/avatar/test3.png'),
(5, '测试用户4', 'test4@example.com', '/avatar/test4.png');

-- ============================================================
-- 5. 创建测试文件记录表（用于路径遍历/文件包含测试）
-- ============================================================
DROP TABLE IF EXISTS sys_test_file;
CREATE TABLE sys_test_file (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '文件ID',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_path VARCHAR(500) NOT NULL COMMENT '文件相对路径',
    file_type VARCHAR(50) DEFAULT 'txt' COMMENT '文件类型',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小（字节）',
    description VARCHAR(500) DEFAULT '' COMMENT '文件描述',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测试文件记录表（路径遍历测试用）';

-- 初始化测试文件记录
INSERT INTO sys_test_file (file_name, file_path, file_type, file_size, description) VALUES
('test.txt', 'test.txt', 'txt', 1024, '基础测试文本文件'),
('config.properties', 'config/test.properties', 'properties', 2048, '配置文件示例'),
('readme.md', 'README.md', 'md', 4096, '项目说明文档'),
('application.yml', 'application.yml', 'yml', 3072, '应用配置文件');

-- ============================================================
-- 6. 创建序列化对象表（用于反序列化测试）
-- ============================================================
DROP TABLE IF EXISTS sys_serialized_object;
CREATE TABLE sys_serialized_object (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '对象ID',
    object_name VARCHAR(100) NOT NULL COMMENT '对象名称',
    object_type VARCHAR(100) NOT NULL COMMENT '对象类型（类名）',
    serialized_data LONGBLOB COMMENT '序列化数据',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='序列化对象表（反序列化测试用）';

-- ============================================================
-- 7. 创建SSRF请求日志表（用于SSRF测试记录）
-- ============================================================
DROP TABLE IF EXISTS sys_ssrf_log;
CREATE TABLE sys_ssrf_log (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    request_url VARCHAR(1000) NOT NULL COMMENT '请求URL',
    request_method VARCHAR(10) DEFAULT 'GET' COMMENT '请求方法',
    response_code INT DEFAULT 0 COMMENT '响应状态码',
    response_body TEXT COMMENT '响应内容',
    request_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '请求时间',
    source_ip VARCHAR(50) DEFAULT '' COMMENT '来源IP'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SSRF请求日志表';

-- ============================================================
-- 8. 创建XXE解析日志表（用于XXE测试记录）
-- ============================================================
DROP TABLE IF EXISTS sys_xxe_log;
CREATE TABLE sys_xxe_log (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    xml_content TEXT NOT NULL COMMENT 'XML内容',
    parse_result TEXT COMMENT '解析结果',
    has_external_entity TINYINT(1) DEFAULT 0 COMMENT '是否包含外部实体',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='XXE解析日志表';

-- ============================================================
-- 9. 验证表结构
-- ============================================================
SELECT '========== 数据库初始化完成 ==========' AS message;
SELECT 'sys_user' AS table_name, COUNT(*) AS row_count FROM sys_user
UNION ALL
SELECT 'sys_comment' AS table_name, COUNT(*) AS row_count FROM sys_comment
UNION ALL
SELECT 'sys_user_config' AS table_name, COUNT(*) AS row_count FROM sys_user_config
UNION ALL
SELECT 'sys_test_file' AS table_name, COUNT(*) AS row_count FROM sys_test_file;

-- ============================================================
-- 使用说明
-- ============================================================
-- 1. 此脚本用于靶场服务数据库的完整初始化
-- 2. 执行前请确保MySQL服务已启动
-- 3. 建议在测试环境中执行，生产环境请谨慎使用
-- 4. 所有表均包含测试数据，可直接用于漏洞演示
-- 
-- 安全提醒：
-- - 此数据库仅用于授权的安全测试环境
-- - 表中存储的密码为明文，仅用于SQL注入漏洞演示
-- - 请勿在生产环境中使用此数据库结构
-- ============================================================
