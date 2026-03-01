USE vuln_target;

-- 存储型XSS核心表：评论表（存储用户提交的内容，含恶意脚本）
CREATE TABLE IF NOT EXISTS sys_comment (
                                           id VARCHAR(64) PRIMARY KEY COMMENT '评论ID',
                                           content TEXT NOT NULL COMMENT '评论内容（可能含恶意XSS脚本）',
                                           username VARCHAR(50) NOT NULL COMMENT '提交用户',
                                           create_time BIGINT NOT NULL COMMENT '创建时间戳'
) COMMENT '存储型XSS测试评论表';

-- 初始化测试数据
INSERT INTO sys_comment (id, content, username, create_time)
VALUES (REPLACE(UUID(), '-', ''), '系统公告：欢迎使用评论功能', 'admin', UNIX_TIMESTAMP() * 1000);