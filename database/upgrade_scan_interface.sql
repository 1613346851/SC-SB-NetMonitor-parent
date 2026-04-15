-- ============================================================
-- 扫描接口表扩展 - 新增接口特征字段
-- 用于智能推断漏洞类型
-- ============================================================

USE `network_monitor`;

-- 1. 新增业务功能类型字段
ALTER TABLE `sys_scan_interface` ADD COLUMN `business_type` VARCHAR(50) DEFAULT NULL COMMENT '业务功能类型(USER_INPUT,DATA_QUERY,DATA_SUBMIT,FILE_OPERATION,FILE_UPLOAD,URL_FETCH,COMMAND_EXEC,AUTH_RELATED,XML_PROCESS,CONFIG_ACCESS,API_PROXY)' AFTER `defense_rule_note`;

-- 2. 新增输入参数描述字段
ALTER TABLE `sys_scan_interface` ADD COLUMN `input_params` TEXT DEFAULT NULL COMMENT '输入参数描述(JSON数组)' AFTER `business_type`;

-- 3. 新增输出类型字段
ALTER TABLE `sys_scan_interface` ADD COLUMN `output_type` VARCHAR(50) DEFAULT 'JSON' COMMENT '输出类型(JSON,HTML,XML,FILE,BINARY)' AFTER `input_params`;

-- 4. 新增是否需要认证字段
ALTER TABLE `sys_scan_interface` ADD COLUMN `auth_required` TINYINT DEFAULT 0 COMMENT '是否需要认证(0-否,1-是)' AFTER `output_type`;

-- 5. 新增请求内容类型字段
ALTER TABLE `sys_scan_interface` ADD COLUMN `content_type` VARCHAR(100) DEFAULT 'application/json' COMMENT '请求内容类型' AFTER `auth_required`;

-- 6. 新增是否发起外部请求字段
ALTER TABLE `sys_scan_interface` ADD COLUMN `external_request` TINYINT DEFAULT 0 COMMENT '是否发起外部请求(0-否,1-是)' AFTER `content_type`;

-- 7. 新增是否涉及文件操作字段
ALTER TABLE `sys_scan_interface` ADD COLUMN `file_operation` TINYINT DEFAULT 0 COMMENT '是否涉及文件操作(0-否,1-是)' AFTER `external_request`;

-- 8. 新增是否涉及数据库操作字段
ALTER TABLE `sys_scan_interface` ADD COLUMN `db_operation` TINYINT DEFAULT 0 COMMENT '是否涉及数据库操作(0-否,1-是)' AFTER `file_operation`;

-- 9. 新增推断的漏洞类型字段
ALTER TABLE `sys_scan_interface` ADD COLUMN `inferred_vuln_types` VARCHAR(500) DEFAULT NULL COMMENT '推断的漏洞类型(JSON数组)' AFTER `db_operation`;

-- 10. 新增扫描状态字段
ALTER TABLE `sys_scan_interface` ADD COLUMN `scan_status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '扫描状态(PENDING,SCANNING,COMPLETED,FAILED)' AFTER `inferred_vuln_types`;

-- 11. 新增最后扫描时间字段
ALTER TABLE `sys_scan_interface` ADD COLUMN `last_scan_time` DATETIME DEFAULT NULL COMMENT '最后扫描时间' AFTER `scan_status`;

-- ============================================================
-- 更新现有接口配置，补充特征字段
-- ============================================================

-- SQL注入测试接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'DATA_QUERY',
    `input_params` = '[{"name":"id","type":"string","source":"query","required":true}]',
    `output_type` = 'JSON',
    `auth_required` = 0,
    `content_type` = 'application/json',
    `external_request` = 0,
    `file_operation` = 0,
    `db_operation` = 1,
    `inferred_vuln_types` = '["SQL_INJECTION"]'
WHERE `interface_path` = '/target/sql/query';

-- XSS存储型测试接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'DATA_SUBMIT',
    `input_params` = '[{"name":"content","type":"string","source":"body","required":true}]',
    `output_type` = 'JSON',
    `auth_required` = 0,
    `content_type` = 'application/x-www-form-urlencoded',
    `external_request` = 0,
    `file_operation` = 0,
    `db_operation` = 1,
    `inferred_vuln_types` = '["XSS"]'
WHERE `interface_path` = '/target/xss/submit-comment';

-- XSS反射型测试接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'USER_INPUT',
    `input_params` = '[{"name":"keyword","type":"string","source":"query","required":true}]',
    `output_type` = 'HTML',
    `auth_required` = 0,
    `content_type` = 'application/json',
    `external_request` = 0,
    `file_operation` = 0,
    `db_operation` = 0,
    `inferred_vuln_types` = '["XSS"]'
WHERE `interface_path` = '/target/xss/search';

-- XSS DOM型测试接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'USER_INPUT',
    `input_params` = '[{"name":"username","type":"string","source":"query","required":true}]',
    `output_type` = 'HTML',
    `auth_required` = 0,
    `content_type` = 'application/json',
    `external_request` = 0,
    `file_operation` = 0,
    `db_operation` = 0,
    `inferred_vuln_types` = '["XSS"]'
WHERE `interface_path` = '/target/xss/profile';

-- 命令注入测试接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'COMMAND_EXEC',
    `input_params` = '[{"name":"cmd","type":"string","source":"query","required":true}]',
    `output_type` = 'JSON',
    `auth_required` = 0,
    `content_type` = 'application/json',
    `external_request` = 0,
    `file_operation` = 0,
    `db_operation` = 0,
    `inferred_vuln_types` = '["COMMAND_INJECTION"]'
WHERE `interface_path` = '/target/cmd/execute';

-- 路径遍历测试接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'FILE_OPERATION',
    `input_params` = '[{"name":"filename","type":"string","source":"query","required":true}]',
    `output_type` = 'FILE',
    `auth_required` = 0,
    `content_type` = 'application/json',
    `external_request` = 0,
    `file_operation` = 1,
    `db_operation` = 0,
    `inferred_vuln_types` = '["PATH_TRAVERSAL","FILE_INCLUSION"]'
WHERE `interface_path` = '/target/path/read';

-- 文件包含测试接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'FILE_OPERATION',
    `input_params` = '[{"name":"path","type":"string","source":"query","required":true}]',
    `output_type` = 'JSON',
    `auth_required` = 0,
    `content_type` = 'application/json',
    `external_request` = 0,
    `file_operation` = 1,
    `db_operation` = 0,
    `inferred_vuln_types` = '["FILE_INCLUSION","PATH_TRAVERSAL"]'
WHERE `interface_path` = '/target/file/include';

-- SSRF测试接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'URL_FETCH',
    `input_params` = '[{"name":"url","type":"string","source":"query","required":true}]',
    `output_type` = 'JSON',
    `auth_required` = 0,
    `content_type` = 'application/json',
    `external_request` = 1,
    `file_operation` = 0,
    `db_operation` = 0,
    `inferred_vuln_types` = '["SSRF"]'
WHERE `interface_path` = '/target/ssrf/request';

-- XXE测试接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'XML_PROCESS',
    `input_params` = '[{"name":"xmlBody","type":"xml","source":"body","required":true}]',
    `output_type` = 'JSON',
    `auth_required` = 0,
    `content_type` = 'application/xml',
    `external_request` = 0,
    `file_operation` = 0,
    `db_operation` = 0,
    `inferred_vuln_types` = '["XXE"]'
WHERE `interface_path` = '/target/xxe/parse';

-- 反序列化测试接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'USER_INPUT',
    `input_params` = '[{"name":"serializedData","type":"string","source":"body","required":true}]',
    `output_type` = 'JSON',
    `auth_required` = 0,
    `content_type` = 'application/json',
    `external_request` = 0,
    `file_operation` = 0,
    `db_operation` = 0,
    `inferred_vuln_types` = '["DESERIALIZATION"]'
WHERE `interface_path` = '/target/deserial/parse';

-- CSRF测试接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'DATA_SUBMIT',
    `input_params` = '[{"name":"userId","type":"string","source":"body","required":true},{"name":"nickname","type":"string","source":"body","required":true}]',
    `output_type` = 'JSON',
    `auth_required` = 0,
    `content_type` = 'application/x-www-form-urlencoded',
    `external_request` = 0,
    `file_operation` = 0,
    `db_operation` = 1,
    `inferred_vuln_types` = '["CSRF","XSS"]'
WHERE `interface_path` = '/target/csrf/update-name';

-- DDoS CPU密集型接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'USER_INPUT',
    `input_params` = '[]',
    `output_type` = 'JSON',
    `auth_required` = 0,
    `content_type` = 'application/json',
    `external_request` = 0,
    `file_operation` = 0,
    `db_operation` = 0,
    `inferred_vuln_types` = '["DDOS"]'
WHERE `interface_path` = '/target/ddos/compute-heavy';

-- DDoS I/O延迟接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'USER_INPUT',
    `input_params` = '[{"name":"delay","type":"int","source":"query","required":false}]',
    `output_type` = 'JSON',
    `auth_required` = 0,
    `content_type` = 'application/json',
    `external_request` = 0,
    `file_operation` = 0,
    `db_operation` = 0,
    `inferred_vuln_types` = '["DDOS"]'
WHERE `interface_path` = '/target/ddos/io-delay';

-- DDoS Ping接口
UPDATE `sys_scan_interface` SET 
    `business_type` = 'USER_INPUT',
    `input_params` = '[]',
    `output_type` = 'JSON',
    `auth_required` = 0,
    `content_type` = 'application/json',
    `external_request` = 0,
    `file_operation` = 0,
    `db_operation` = 0,
    `inferred_vuln_types` = '["DDOS"]'
WHERE `interface_path` = '/target/ddos/ping';
