/*
 * XSS漏洞测试平台数据库初始化脚本
 * 创建用于存储型XSS测试的评论表
 * 注意：此脚本应在已有的MySQL数据库中执行
 */

-- 创建评论表（用于存储型XSS测试）
CREATE TABLE IF NOT EXISTS `sys_comment` (
    `id` VARCHAR(32) NOT NULL COMMENT '评论唯一标识',
    `content` TEXT NOT NULL COMMENT '评论内容（可能包含恶意XSS脚本）',
    `username` VARCHAR(50) NOT NULL COMMENT '评论用户',
    `create_time` BIGINT NOT NULL COMMENT '创建时间戳',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统评论表（XSS测试用）';

-- 插入一些初始测试数据
INSERT INTO `sys_comment` (`id`, `content`, `username`, `create_time`) VALUES
('test001', '这是一个正常的测试评论', 'test_user_001', UNIX_TIMESTAMP() * 1000),
('test002', 'Another normal comment for testing', 'test_user_002', (UNIX_TIMESTAMP() - 3600) * 1000),
('test003', '第三条评论内容，用于验证分页功能', 'test_user_003', (UNIX_TIMESTAMP() - 7200) * 1000);

-- 验证表结构
DESCRIBE `sys_comment`;

-- 查询初始数据确认
SELECT 
    id,
    content,
    username,
    FROM_UNIXTIME(create_time/1000) as formatted_time
FROM `sys_comment` 
ORDER BY create_time DESC;

-- 显示表状态
SHOW TABLE STATUS LIKE 'sys_comment';

/*
 * 使用说明：
 * 1. 此表用于存储型XSS漏洞测试
 * 2. content字段故意不进行HTML转义，以模拟真实漏洞环境
 * 3. 所有通过/target/xss/submit-comment接口提交的内容都会存储在此表中
 * 4. 查询评论时(/target/xss/list-comments)会直接返回未转义的内容，触发XSS
 *
 * 安全提醒：
 * - 此表仅用于授权的安全测试环境
 * - 生产环境中必须对用户输入进行严格的HTML转义处理
 * - 建议定期清理测试数据，避免积累过多恶意内容
 */