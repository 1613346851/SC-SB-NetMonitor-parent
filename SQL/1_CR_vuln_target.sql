-- 创建靶场数据库
CREATE DATABASE IF NOT EXISTS vuln_target DEFAULT CHARACTER SET utf8mb4;
USE vuln_target;

-- 创建用户表（含敏感数据）
CREATE TABLE IF NOT EXISTS sys_user
    (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL
    );

-- 初始化测试数据
INSERT INTO sys_user (username, password, phone) VALUES
                                                     ('admin', 'admin@123', '13800138000'),
                                                     ('test1', 'test1@456', '13900139111'),
                                                     ('test2', 'test2@456', '13900139222'),
                                                     ('test3', 'test3@456', '13900139333'),
                                                     ('test4', 'test4@456', '13900139444');