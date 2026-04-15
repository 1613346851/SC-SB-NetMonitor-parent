SET NAMES utf8mb4;

USE `network_monitor`;

-- ============================================================
-- 1. 清空现有数据
-- ============================================================

DELETE FROM `sys_vulnerability_rule`;
DELETE FROM `sys_scan_interface`;
DELETE FROM `sys_vulnerability_monitor`;
DELETE FROM `sys_monitor_rule`;
DELETE FROM `sys_payload_library`;
DELETE FROM `sys_scan_target`;

-- ============================================================
-- 2. 重新插入攻击规则（与靶场接口对应）
-- ============================================================

-- 2.1 SQL注入检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('SQL注入-UNION查询', 'SQL_INJECTION', '(?i)\\bUNION\\b.*\\bSELECT\\b', '检测UNION SELECT联合查询注入', 'HIGH', 1, 10),
('SQL注入-OR恒真', 'SQL_INJECTION', '(?i)\\bOR\\b\\s+[\'"]?1[\'"]?\\s*=\\s*[\'"]?1[\'"]?', '检测OR 1=1恒真注入', 'HIGH', 1, 10),
('SQL注入-AND恒真', 'SQL_INJECTION', '(?i)\\bAND\\b\\s+[\'"]?\\d+[\'"]?\\s*=\\s*[\'"]?\\d+[\'"]?', '检测AND布尔盲注', 'MEDIUM', 1, 15),
('SQL注入-DROP语句', 'SQL_INJECTION', '(?i)\\bDROP\\b.*\\bTABLE\\b', '检测DROP TABLE恶意注入', 'CRITICAL', 1, 5),
('SQL注入-SLEEP延时', 'SQL_INJECTION', '(?i)\\bSLEEP\\b\\s*\\(', '检测SLEEP时间盲注', 'MEDIUM', 1, 20),
('SQL注入-BENCHMARK延时', 'SQL_INJECTION', '(?i)\\bBENCHMARK\\b\\s*\\(', '检测BENCHMARK时间盲注', 'MEDIUM', 1, 20),
('SQL注入-注释符号', 'SQL_INJECTION', '(--|#|/\\*)', '检测SQL注释符号', 'LOW', 1, 50),
('SQL注入-堆叠查询', 'SQL_INJECTION', ';\\s*(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER)\\b', '检测堆叠查询攻击', 'HIGH', 1, 15),
('SQL注入-单引号闭合', 'SQL_INJECTION', '\'', '检测单引号注入尝试', 'LOW', 1, 100);

-- 2.2 XSS攻击检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('XSS-script标签', 'XSS', '(?i)<\\s*script[^>]*>', '检测script标签注入', 'HIGH', 1, 10),
('XSS-javascript协议', 'XSS', '(?i)javascript\\s*:', '检测javascript协议注入', 'HIGH', 1, 10),
('XSS-onerror事件', 'XSS', '(?i)\\bonerror\\b\\s*=', '检测onerror事件处理器', 'MEDIUM', 1, 20),
('XSS-onload事件', 'XSS', '(?i)\\bonload\\b\\s*=', '检测onload事件处理器', 'MEDIUM', 1, 20),
('XSS-onclick事件', 'XSS', '(?i)\\bonclick\\b\\s*=', '检测onclick事件处理器', 'MEDIUM', 1, 20),
('XSS-alert函数', 'XSS', '(?i)\\balert\\b\\s*\\(', '检测alert弹窗函数', 'MEDIUM', 1, 30),
('XSS-document对象', 'XSS', '(?i)document\\.(cookie|location|write)', '检测document对象访问', 'HIGH', 1, 15),
('XSS-img标签注入', 'XSS', '(?i)<\\s*img[^>]+onerror', '检测img标签onerror注入', 'MEDIUM', 1, 25),
('XSS-SVG标签注入', 'XSS', '(?i)<\\s*svg[^>]*onload', '检测SVG标签onload注入', 'MEDIUM', 1, 25),
('XSS-eval函数', 'XSS', '(?i)\\beval\\b\\s*\\(', '检测eval函数调用', 'HIGH', 1, 20),
('XSS-iframe标签', 'XSS', '(?i)<\\s*iframe', '检测iframe标签注入', 'MEDIUM', 1, 30);

-- 2.3 命令注入检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('命令注入-管道符', 'COMMAND_INJECTION', '\\|\\s*(cat|ls|pwd|whoami|wget|curl|nc|bash|sh|cmd|type|dir|find|ping|tasklist|id)\\b', '检测管道符命令注入', 'HIGH', 1, 10),
('命令注入-分号', 'COMMAND_INJECTION', ';\\s*(cat|ls|pwd|whoami|wget|curl|nc|bash|sh|cmd|type|dir|find|ping|tasklist|id)\\b', '检测分号命令注入', 'HIGH', 1, 10),
('命令注入-反引号', 'COMMAND_INJECTION', '`[^`]+`', '检测反引号命令执行', 'HIGH', 1, 5),
('命令注入-$()执行', 'COMMAND_INJECTION', '\\$\\([^)]+\\)', '检测$()命令执行', 'HIGH', 1, 5),
('命令注入-Windows cmd', 'COMMAND_INJECTION', '(?i)(cmd\\s*/c|cmd\\.exe|powershell\\s*-)', '检测Windows命令执行', 'HIGH', 1, 15),
('命令注入-ping命令', 'COMMAND_INJECTION', '(?i)ping\\s+\\d+\\.\\d+\\.\\d+\\.\\d+', '检测ping命令注入', 'MEDIUM', 1, 25),
('命令注入-whoami', 'COMMAND_INJECTION', '(?i)\\bwhoami\\b', '检测whoami命令', 'HIGH', 1, 20),
('命令注入-tasklist', 'COMMAND_INJECTION', '(?i)\\btasklist\\b', '检测tasklist命令', 'MEDIUM', 1, 30),
('命令注入-cat命令', 'COMMAND_INJECTION', '(?i)\\bcat\\s+[/\\w\\-\\.]+', '检测cat文件读取', 'HIGH', 1, 20);

-- 2.4 路径遍历检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('路径遍历-父目录引用', 'PATH_TRAVERSAL', '\\.\\./|\\.\\.\\\\', '检测../父目录引用', 'HIGH', 1, 10),
('路径遍历-Linux敏感文件', 'PATH_TRAVERSAL', '(?i)/etc/(passwd|shadow|hosts)', '检测Linux敏感文件路径', 'CRITICAL', 1, 5),
('路径遍历-Windows敏感路径', 'PATH_TRAVERSAL', '(?i)(c:|d:|e:)\\\\(windows|users|program)', '检测Windows系统路径', 'HIGH', 1, 15),
('路径遍历-URL编码绕过', 'PATH_TRAVERSAL', '%2e%2e%2f|%2e%2e/', '检测URL编码绕过', 'HIGH', 1, 15),
('路径遍历-双重编码', 'PATH_TRAVERSAL', '%252e%252e', '检测双重URL编码', 'HIGH', 1, 12),
('路径遍历-配置文件', 'PATH_TRAVERSAL', '(?i)(application|config|database)\\.(yml|yaml|properties|xml)', '检测配置文件访问', 'HIGH', 1, 10);

-- 2.5 文件包含检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('文件包含-PHP函数', 'FILE_INCLUSION', '(?i)\\b(include|require|include_once|require_once)\\b\\s*\\(', '检测PHP文件包含函数', 'HIGH', 1, 10),
('文件包含-data协议', 'FILE_INCLUSION', '(?i)data://', '检测data协议', 'HIGH', 1, 10),
('文件包含-php协议', 'FILE_INCLUSION', '(?i)php://', '检测php://协议', 'HIGH', 1, 10),
('文件包含-file协议', 'FILE_INCLUSION', '(?i)file://', '检测file://协议', 'HIGH', 1, 10),
('文件包含-classpath', 'FILE_INCLUSION', '(?i)classpath:', '检测classpath协议', 'HIGH', 1, 10);

-- 2.6 SSRF检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('SSRF-内网IP', 'SSRF', '(?i)(url|uri|target|host|domain|site|link|src|source)\\s*[=:]\\s*["\']?https?://(127\\.0\\.0\\.1|localhost|192\\.168\\.|10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.)', '检测内网IP请求', 'HIGH', 1, 10),
('SSRF-file协议', 'SSRF', '(?i)(url|uri|target|host|domain)\\s*[=:]\\s*["\']?file://', '检测file协议SSRF', 'HIGH', 1, 8),
('SSRF-dict协议', 'SSRF', '(?i)(url|uri|target|host)\\s*[=:]\\s*["\']?dict://', '检测dict协议SSRF', 'HIGH', 1, 8),
('SSRF-gopher协议', 'SSRF', '(?i)(url|uri|target|host)\\s*[=:]\\s*["\']?gopher://', '检测gopher协议SSRF', 'HIGH', 1, 8),
('SSRF-云元数据', 'SSRF', '(?i)(url|uri|target|host)\\s*[=:]\\s*["\']?https?://(169\\.254\\.169\\.254|metadata\\.azure|metadata\\.google)', '检测云元数据SSRF', 'HIGH', 1, 5),
('SSRF-本地回环', 'SSRF', '(?i)url\\s*=\\s*["\']?https?://(127\\.0\\.0\\.1|localhost)', '检测本地回环SSRF', 'HIGH', 1, 10);

-- 2.7 XXE检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('XXE-DOCTYPE ENTITY', 'XXE', '(?i)<!DOCTYPE[^>]*\\bENTITY\\b', '检测DOCTYPE ENTITY声明', 'HIGH', 1, 5),
('XXE-SYSTEM关键字', 'XXE', '(?i)\\bSYSTEM\\s*["\']', '检测SYSTEM关键字', 'HIGH', 1, 5),
('XXE-PUBLIC关键字', 'XXE', '(?i)\\bPUBLIC\\s*["\']', '检测PUBLIC关键字', 'HIGH', 1, 5),
('XXE-file协议', 'XXE', '(?i)SYSTEM\\s*["\']?file://', '检测file协议外部实体', 'HIGH', 1, 8),
('XXE-http协议', 'XXE', '(?i)SYSTEM\\s*["\']?https?://', '检测http协议外部实体', 'HIGH', 1, 8),
('XXE-参数实体', 'XXE', '(?i)<!ENTITY\\s+%', '检测参数实体声明', 'HIGH', 1, 10);

-- 2.8 反序列化检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('反序列化-Java头', 'DESERIALIZATION', '\\xac\\xed\\x00\\x05', '检测Java序列化对象头', 'HIGH', 1, 5),
('反序列化-PHP序列化', 'DESERIALIZATION', '(?i)(O:\\d+:|a:\\d+:|s:\\d+:)', '检测PHP序列化字符串', 'HIGH', 1, 10),
('反序列化-Python pickle', 'DESERIALIZATION', '(?i)(c__builtin__|c__main__|cos\\n|csubprocess)', '检测Python pickle序列化', 'HIGH', 1, 10),
('反序列化-Base64 Java', 'DESERIALIZATION', '(?i)rO0AB', '检测Base64编码Java序列化', 'HIGH', 1, 8);

-- 2.9 CSRF检测规则
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('CSRF-表单自动提交', 'CSRF', '(?i)<\\s*form[^>]+action\\s*=\\s*["\']?https?://[^"\'>\\s]+', '检测自动提交表单', 'MEDIUM', 1, 20),
('CSRF-隐藏字段', 'CSRF', '(?i)<\\s*input[^>]+type\\s*=\\s*["\']?hidden', '检测隐藏表单字段', 'LOW', 1, 30),
('CSRF-跨域请求', 'CSRF', '(?i)<\\s*img[^>]+src\\s*=\\s*["\']?https?://[^"\'>\\s]+', '检测跨域图片请求', 'LOW', 1, 40);

-- 2.10 DDoS检测规则（基于频率）
INSERT INTO `sys_monitor_rule` (`rule_name`, `attack_type`, `rule_content`, `description`, `risk_level`, `enabled`, `priority`) VALUES
('DDoS-高频请求', 'DDOS', 'FREQUENCY_THRESHOLD', '基于请求频率的DDoS检测', 'HIGH', 1, 1);

-- ============================================================
-- 3. 重新插入漏洞信息（与靶场接口一一对应）
-- ============================================================

INSERT INTO `sys_vulnerability_monitor` (`id`, `vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `description`, `fix_suggestion`) VALUES
(1, 'SQL注入漏洞-用户查询接口', 'SQL_INJECTION', 'HIGH', '/target/sql/query', 0, 
 '用户查询接口存在SQL注入漏洞，id参数直接拼接到SQL语句中，可执行任意SQL命令',
 '使用参数化查询或预编译语句，对用户输入进行严格验证'),

(2, 'XSS漏洞-存储型评论提交', 'XSS', 'HIGH', '/target/xss/submit-comment', 0, 
 '评论提交接口存在存储型XSS漏洞，评论内容未过滤直接存入数据库',
 '对用户输入进行HTML实体转义，设置CSP策略'),

(3, 'XSS漏洞-反射型搜索', 'XSS', 'MEDIUM', '/target/xss/search', 0, 
 '搜索接口存在反射型XSS漏洞，keyword参数未转义直接返回',
 '对用户输入进行HTML实体转义，设置Content-Type响应头'),

(4, 'XSS漏洞-DOM型用户资料', 'XSS', 'MEDIUM', '/target/xss/profile', 0, 
 '用户资料接口存在DOM型XSS漏洞，username参数未转义直接渲染',
 '前端使用安全的DOM操作方法，避免innerHTML直接插入'),

(5, '命令注入漏洞-命令执行接口', 'COMMAND_INJECTION', 'CRITICAL', '/target/cmd/execute', 0, 
 '命令执行接口存在任意命令注入漏洞，cmd参数直接传递给系统执行',
 '避免直接调用系统命令，使用白名单验证，使用安全API'),

(6, '路径遍历漏洞-文件读取', 'PATH_TRAVERSAL', 'HIGH', '/target/path/read', 0, 
 '文件读取接口存在路径遍历漏洞，filename参数可包含../读取任意文件',
 '限制文件访问目录，规范化路径处理，使用白名单验证'),

(7, '文件包含漏洞-动态加载', 'FILE_INCLUSION', 'HIGH', '/target/file/include', 0, 
 '文件加载接口存在文件包含漏洞，path参数可加载任意文件',
 '禁用远程文件包含，使用白名单验证文件路径'),

(8, 'SSRF漏洞-URL请求接口', 'SSRF', 'HIGH', '/target/ssrf/request', 0, 
 'URL请求接口存在SSRF漏洞，url参数可请求内网服务',
 '限制协议类型，禁用重定向，使用白名单验证目标地址'),

(9, 'XXE漏洞-XML解析接口', 'XXE', 'HIGH', '/target/xxe/parse', 0, 
 'XML解析接口存在XXE漏洞，未禁用外部实体解析',
 '禁用DTD和外部实体解析，使用安全的XML解析配置'),

(10, 'Java反序列化漏洞-对象解析', 'DESERIALIZATION', 'CRITICAL', '/target/deserial/parse', 0, 
 '反序列化接口存在漏洞，未对反序列化类进行白名单校验',
 '使用类白名单校验，避免反序列化不可信数据'),

(11, 'CSRF漏洞-昵称修改', 'CSRF', 'MEDIUM', '/target/csrf/update-name', 0, 
 '昵称修改接口存在CSRF漏洞，无Token校验可被跨站伪造请求',
 '添加CSRF Token校验，验证Referer头，使用SameSite Cookie'),

(12, 'DDoS攻击目标-CPU密集型', 'DDOS', 'HIGH', '/target/ddos/compute-heavy', 0, 
 'CPU密集型计算接口易受DDoS攻击，高频请求可耗尽服务器资源',
 '添加请求频率限制，使用CDN防护，配置资源监控告警'),

(13, 'DDoS攻击目标-I/O延迟型', 'DDOS', 'MEDIUM', '/target/ddos/io-delay', 0, 
 'I/O延迟接口易受慢速攻击，可长期占用连接资源',
 '设置连接超时时间，限制并发连接数，使用连接池管理'),

(14, 'DDoS攻击目标-Ping洪水', 'DDOS', 'MEDIUM', '/target/ddos/ping', 0, 
 '简单Ping接口易受高频洪水攻击，可冲击网络栈',
 '添加请求频率限制，使用负载均衡，配置防火墙规则');

-- ============================================================
-- 4. 插入漏洞-规则关联关系
-- ============================================================

-- SQL注入漏洞关联规则
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`) VALUES
(1, 1, 'SQL注入-UNION查询', 'SQL_INJECTION', 'HIGH'),
(1, 2, 'SQL注入-OR恒真', 'SQL_INJECTION', 'HIGH'),
(1, 3, 'SQL注入-AND恒真', 'SQL_INJECTION', 'MEDIUM'),
(1, 4, 'SQL注入-DROP语句', 'SQL_INJECTION', 'CRITICAL'),
(1, 5, 'SQL注入-SLEEP延时', 'SQL_INJECTION', 'MEDIUM'),
(1, 6, 'SQL注入-BENCHMARK延时', 'SQL_INJECTION', 'MEDIUM'),
(1, 7, 'SQL注入-注释符号', 'SQL_INJECTION', 'LOW'),
(1, 8, 'SQL注入-堆叠查询', 'SQL_INJECTION', 'HIGH'),
(1, 9, 'SQL注入-单引号闭合', 'SQL_INJECTION', 'LOW');

-- XSS存储型漏洞关联规则
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`) VALUES
(2, 10, 'XSS-script标签', 'XSS', 'HIGH'),
(2, 11, 'XSS-javascript协议', 'XSS', 'HIGH'),
(2, 12, 'XSS-onerror事件', 'XSS', 'MEDIUM'),
(2, 13, 'XSS-onload事件', 'XSS', 'MEDIUM'),
(2, 14, 'XSS-onclick事件', 'XSS', 'MEDIUM'),
(2, 15, 'XSS-alert函数', 'XSS', 'MEDIUM'),
(2, 16, 'XSS-document对象', 'XSS', 'HIGH'),
(2, 17, 'XSS-img标签注入', 'XSS', 'MEDIUM'),
(2, 18, 'XSS-SVG标签注入', 'XSS', 'MEDIUM'),
(2, 19, 'XSS-eval函数', 'XSS', 'HIGH'),
(2, 20, 'XSS-iframe标签', 'XSS', 'MEDIUM');

-- XSS反射型漏洞关联规则
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`) VALUES
(3, 10, 'XSS-script标签', 'XSS', 'HIGH'),
(3, 11, 'XSS-javascript协议', 'XSS', 'HIGH'),
(3, 12, 'XSS-onerror事件', 'XSS', 'MEDIUM'),
(3, 15, 'XSS-alert函数', 'XSS', 'MEDIUM'),
(3, 16, 'XSS-document对象', 'XSS', 'HIGH');

-- XSS DOM型漏洞关联规则
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`) VALUES
(4, 10, 'XSS-script标签', 'XSS', 'HIGH'),
(4, 11, 'XSS-javascript协议', 'XSS', 'HIGH'),
(4, 12, 'XSS-onerror事件', 'XSS', 'MEDIUM'),
(4, 13, 'XSS-onload事件', 'XSS', 'MEDIUM'),
(4, 18, 'XSS-SVG标签注入', 'XSS', 'MEDIUM');

-- 命令注入漏洞关联规则
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`) VALUES
(5, 21, '命令注入-管道符', 'COMMAND_INJECTION', 'HIGH'),
(5, 22, '命令注入-分号', 'COMMAND_INJECTION', 'HIGH'),
(5, 23, '命令注入-反引号', 'COMMAND_INJECTION', 'HIGH'),
(5, 24, '命令注入-$()执行', 'COMMAND_INJECTION', 'HIGH'),
(5, 25, '命令注入-Windows cmd', 'COMMAND_INJECTION', 'HIGH'),
(5, 26, '命令注入-ping命令', 'COMMAND_INJECTION', 'MEDIUM'),
(5, 27, '命令注入-whoami', 'COMMAND_INJECTION', 'HIGH'),
(5, 28, '命令注入-tasklist', 'COMMAND_INJECTION', 'MEDIUM'),
(5, 29, '命令注入-cat命令', 'COMMAND_INJECTION', 'HIGH');

-- 路径遍历漏洞关联规则
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`) VALUES
(6, 30, '路径遍历-父目录引用', 'PATH_TRAVERSAL', 'HIGH'),
(6, 31, '路径遍历-Linux敏感文件', 'PATH_TRAVERSAL', 'CRITICAL'),
(6, 32, '路径遍历-Windows敏感路径', 'PATH_TRAVERSAL', 'HIGH'),
(6, 33, '路径遍历-URL编码绕过', 'PATH_TRAVERSAL', 'HIGH'),
(6, 34, '路径遍历-双重编码', 'PATH_TRAVERSAL', 'HIGH'),
(6, 35, '路径遍历-配置文件', 'PATH_TRAVERSAL', 'HIGH');

-- 文件包含漏洞关联规则
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`) VALUES
(7, 36, '文件包含-PHP函数', 'FILE_INCLUSION', 'HIGH'),
(7, 37, '文件包含-data协议', 'FILE_INCLUSION', 'HIGH'),
(7, 38, '文件包含-php协议', 'FILE_INCLUSION', 'HIGH'),
(7, 39, '文件包含-file协议', 'FILE_INCLUSION', 'HIGH'),
(7, 40, '文件包含-classpath', 'FILE_INCLUSION', 'HIGH');

-- SSRF漏洞关联规则
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`) VALUES
(8, 41, 'SSRF-内网IP', 'SSRF', 'HIGH'),
(8, 42, 'SSRF-file协议', 'SSRF', 'HIGH'),
(8, 43, 'SSRF-dict协议', 'SSRF', 'HIGH'),
(8, 44, 'SSRF-gopher协议', 'SSRF', 'HIGH'),
(8, 45, 'SSRF-云元数据', 'SSRF', 'HIGH'),
(8, 46, 'SSRF-本地回环', 'SSRF', 'HIGH');

-- XXE漏洞关联规则
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`) VALUES
(9, 47, 'XXE-DOCTYPE ENTITY', 'XXE', 'HIGH'),
(9, 48, 'XXE-SYSTEM关键字', 'XXE', 'HIGH'),
(9, 49, 'XXE-PUBLIC关键字', 'XXE', 'HIGH'),
(9, 50, 'XXE-file协议', 'XXE', 'HIGH'),
(9, 51, 'XXE-http协议', 'XXE', 'HIGH'),
(9, 52, 'XXE-参数实体', 'XXE', 'HIGH');

-- 反序列化漏洞关联规则
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`) VALUES
(10, 53, '反序列化-Java头', 'DESERIALIZATION', 'HIGH'),
(10, 54, '反序列化-PHP序列化', 'DESERIALIZATION', 'HIGH'),
(10, 55, '反序列化-Python pickle', 'DESERIALIZATION', 'HIGH'),
(10, 56, '反序列化-Base64 Java', 'DESERIALIZATION', 'HIGH');

-- CSRF漏洞关联规则
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`) VALUES
(11, 57, 'CSRF-表单自动提交', 'CSRF', 'MEDIUM'),
(11, 58, 'CSRF-隐藏字段', 'CSRF', 'LOW'),
(11, 59, 'CSRF-跨域请求', 'CSRF', 'LOW');

-- DDoS漏洞关联规则
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`) VALUES
(12, 60, 'DDoS-高频请求', 'DDOS', 'HIGH'),
(13, 60, 'DDoS-高频请求', 'DDOS', 'HIGH'),
(14, 60, 'DDoS-高频请求', 'DDOS', 'HIGH');

-- ============================================================
-- 5. 更新漏洞表的规则统计字段
-- ============================================================

UPDATE `sys_vulnerability_monitor` v
SET `rule_count` = (SELECT COUNT(*) FROM `sys_vulnerability_rule` WHERE `vulnerability_id` = v.`id`),
    `rule_ids` = (SELECT GROUP_CONCAT(`rule_id` ORDER BY `rule_id`) FROM `sys_vulnerability_rule` WHERE `vulnerability_id` = v.`id`),
    `defense_status` = CASE 
        WHEN (SELECT COUNT(*) FROM `sys_vulnerability_rule` WHERE `vulnerability_id` = v.`id`) > 0 
        THEN 2 
        ELSE 0 
    END;

-- ============================================================
-- 6. 插入扫描目标
-- ============================================================

INSERT INTO `sys_scan_target` (`id`, `target_name`, `target_url`, `target_type`, `description`, `enabled`) VALUES
(1, '靶场服务', 'http://127.0.0.1:9001', 'TEST', '漏洞测试靶场服务，包含SQL注入、XSS、命令注入等多种漏洞测试接口', 1);

-- ============================================================
-- 7. 插入扫描接口配置
-- ============================================================

INSERT INTO `sys_scan_interface` (`target_id`, `interface_name`, `interface_path`, `http_method`, `vuln_type`, `risk_level`, `params_config`, `payload_config`, `match_rules`, `enabled`, `priority`, `defense_rule_status`, `defense_rule_count`, `defense_rule_note`) VALUES
(1, 'SQL注入测试接口', '/target/sql/query', 'GET', 'SQL_INJECTION', 'HIGH', 
 '{"id": {"type": "string", "required": true, "testValues": ["1", "1 OR 1=1", "1; SELECT 1", "1 UNION SELECT 1,2,3,4,5"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["OR 1=1", "statement_results", "executed_sql", "SQL注入漏洞"], "responsePatterns": ["SQL注入漏洞", "多语句执行成功"]}',
 1, 10, 2, 9, '已关联9条SQL注入检测规则'),

(1, 'XSS存储型测试接口', '/target/xss/submit-comment', 'POST', 'XSS', 'HIGH',
 '{"content": {"type": "string", "required": true, "testValues": ["test", "<script>alert(1)</script>"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["存储型XSS漏洞", "评论提交成功"], "responsePatterns": ["存储型XSS漏洞", "评论提交成功"]}',
 1, 15, 2, 11, '已关联11条XSS检测规则'),

(1, 'XSS反射型测试接口', '/target/xss/search', 'GET', 'XSS', 'MEDIUM',
 '{"keyword": {"type": "string", "required": true, "testValues": ["test", "<script>alert(1)</script>"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["反射型XSS漏洞", "搜索成功"], "responsePatterns": ["反射型XSS漏洞", "搜索成功"]}',
 1, 20, 2, 5, '已关联5条XSS检测规则'),

(1, 'XSS DOM型测试接口', '/target/xss/profile', 'GET', 'XSS', 'MEDIUM',
 '{"username": {"type": "string", "required": true, "testValues": ["test", "<svg/onload=alert(1)>"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["DOM型XSS漏洞", "获取资料成功"], "responsePatterns": ["DOM型XSS漏洞", "获取资料成功"]}',
 1, 25, 2, 5, '已关联5条XSS检测规则'),

(1, '命令注入测试接口', '/target/cmd/execute', 'GET', 'COMMAND_INJECTION', 'CRITICAL',
 '{"cmd": {"type": "string", "required": true, "testValues": ["ping 127.0.0.1 -n 2", "whoami", "tasklist"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["Pinging", "ping statistics", "\\\\", "root", "命令执行结果"], "responsePatterns": ["命令执行结果", "纯命令注入漏洞触发成功"]}',
 1, 5, 2, 9, '已关联9条命令注入检测规则'),

(1, '路径遍历测试接口', '/target/path/read', 'GET', 'PATH_TRAVERSAL', 'HIGH',
 '{"filename": {"type": "string", "required": true, "testValues": ["test.txt", "../../application.yml", "../../../pom.xml"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["server:", "<project", "root:", "路径遍历漏洞"], "responsePatterns": ["路径遍历漏洞", "文件读取成功"]}',
 1, 30, 2, 6, '已关联6条路径遍历检测规则'),

(1, '文件包含测试接口', '/target/file/include', 'GET', 'FILE_INCLUSION', 'HIGH',
 '{"path": {"type": "string", "required": true, "testValues": ["test.txt", "config/test.properties", "classpath:application.yml"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["文件包含漏洞", "文件加载成功", "parsed_content"], "responsePatterns": ["文件包含漏洞", "文件加载成功"]}',
 1, 35, 2, 5, '已关联5条文件包含检测规则'),

(1, 'SSRF测试接口', '/target/ssrf/request', 'GET', 'SSRF', 'HIGH',
 '{"url": {"type": "string", "required": true, "testValues": ["http://127.0.0.1:9001/target/ddos/status", "http://localhost:9001/target/sql/query?id=1"]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["DDoS被攻击目标状态", "SSRF漏洞", "请求成功"], "responsePatterns": ["SSRF漏洞", "请求成功（漏洞接口）"]}',
 1, 40, 2, 6, '已关联6条SSRF检测规则'),

(1, 'XXE测试接口', '/target/xxe/parse', 'POST', 'XXE', 'HIGH',
 '{"xmlBody": {"type": "xml", "required": true}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["has_external_entity", "XXE漏洞", "XML解析成功"], "responsePatterns": ["XXE漏洞", "has_external_entity"]}',
 1, 45, 2, 6, '已关联6条XXE检测规则'),

(1, '反序列化测试接口', '/target/deserial/parse', 'POST', 'DESERIALIZATION', 'CRITICAL',
 '{"serializedData": {"type": "string", "required": true}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["反序列化漏洞", "deserialized_object", "反序列化成功"], "responsePatterns": ["反序列化漏洞", "反序列化成功"]}',
 1, 50, 2, 4, '已关联4条反序列化检测规则'),

(1, 'CSRF测试接口', '/target/csrf/update-name', 'POST', 'CSRF', 'MEDIUM',
 '{"userId": {"type": "string", "required": true}, "nickname": {"type": "string", "required": true}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["CSRF漏洞", "昵称修改成功"], "responsePatterns": ["CSRF漏洞", "昵称修改成功（漏洞接口）"]}',
 1, 55, 2, 3, '已关联3条CSRF检测规则'),

(1, 'DDoS CPU密集型接口', '/target/ddos/compute-heavy', 'GET', 'DDOS', 'HIGH',
 '{}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["CPU密集型计算完成", "fibonacci"], "responsePatterns": ["CPU密集型计算完成"]}',
 1, 60, 2, 1, '已关联1条DDoS检测规则'),

(1, 'DDoS I/O延迟接口', '/target/ddos/io-delay', 'GET', 'DDOS', 'MEDIUM',
 '{"delay": {"type": "int", "required": false, "testValues": [1000, 5000]}}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["I/O操作模拟完成", "simulated_delay_ms"], "responsePatterns": ["I/O操作模拟完成"]}',
 1, 65, 2, 1, '已关联1条DDoS检测规则'),

(1, 'DDoS Ping接口', '/target/ddos/ping', 'GET', 'DDOS', 'MEDIUM',
 '{}',
 '{"payloadLevel": "BASIC", "customPayloads": []}',
 '{"keywords": ["pong", "total_requests"], "responsePatterns": ["pong"]}',
 1, 70, 2, 1, '已关联1条DDoS检测规则');

-- ============================================================
-- 8. 插入Payload库
-- ============================================================

INSERT INTO `sys_payload_library` (`vuln_type`, `payload_level`, `payload_content`, `match_keywords`, `description`, `risk_level`, `enabled`) VALUES
-- SQL注入Payload
('SQL_INJECTION', 'BASIC', '1 OR 1=1', 'OR 1=1', '布尔型恒真注入探测', 'HIGH', 1),
('SQL_INJECTION', 'BASIC', '1 AND 1=1', 'AND', '布尔型恒真注入探测', 'MEDIUM', 1),
('SQL_INJECTION', 'ADVANCED', '1; SELECT 1', 'statement_results', '堆叠语句执行探测', 'HIGH', 1),
('SQL_INJECTION', 'ADVANCED', '1 UNION SELECT 1,2,3,4,5', 'UNION', '联合查询注入探测', 'HIGH', 1),
('SQL_INJECTION', 'ADVANCED', '1\' AND \'1\'=\'1', 'AND', '字符串型注入探测', 'MEDIUM', 1),
('SQL_INJECTION', 'ADVANCED', '1 AND SLEEP(3)', 'SLEEP', '时间盲注探测', 'MEDIUM', 1),

-- XSS Payload
('XSS', 'BASIC', '<script>alert(1)</script>', 'script', 'Script标签探测', 'HIGH', 1),
('XSS', 'BASIC', '<svg/onload=alert(1)>', 'svg', 'SVG事件回显探测', 'MEDIUM', 1),
('XSS', 'ADVANCED', '<img src=x onerror=alert(1)>', 'img', 'IMG onerror回显探测', 'MEDIUM', 1),
('XSS', 'ADVANCED', '\'""><script>alert(1)</script>', 'script', '属性注入探测', 'HIGH', 1),
('XSS', 'ADVANCED', '<body onload=alert(1)>', 'body', 'Body事件探测', 'MEDIUM', 1),
('XSS', 'ADVANCED', '<iframe src="javascript:alert(1)">', 'iframe', 'Iframe注入探测', 'HIGH', 1),

-- 命令注入Payload
('COMMAND_INJECTION', 'BASIC', 'ping 127.0.0.1 -n 2', 'Pinging,ping statistics', 'Ping命令探测', 'HIGH', 1),
('COMMAND_INJECTION', 'BASIC', 'whoami', '\\\\,root', '用户身份探测', 'HIGH', 1),
('COMMAND_INJECTION', 'ADVANCED', 'dir', 'bytes,total', '目录列表探测', 'MEDIUM', 1),
('COMMAND_INJECTION', 'ADVANCED', 'tasklist', 'PID', '进程列表探测', 'MEDIUM', 1),

-- 路径遍历Payload
('PATH_TRAVERSAL', 'BASIC', '../../application.yml', 'server:', '读取服务配置文件', 'HIGH', 1),
('PATH_TRAVERSAL', 'ADVANCED', '../../../pom.xml', '<project', '读取项目构建文件', 'HIGH', 1),
('PATH_TRAVERSAL', 'ADVANCED', '....//....//etc/passwd', 'root:', 'Linux密码文件读取', 'CRITICAL', 1),

-- 文件包含Payload
('FILE_INCLUSION', 'BASIC', 'test.txt', 'content', '基础文件包含测试', 'LOW', 1),
('FILE_INCLUSION', 'ADVANCED', 'classpath:application.yml', 'server:', 'Classpath文件包含', 'HIGH', 1),

-- SSRF Payload
('SSRF', 'BASIC', 'http://127.0.0.1:9001/target/ddos/status', 'DDoS', '本地回环SSRF探测', 'HIGH', 1),
('SSRF', 'ADVANCED', 'http://localhost:9001/target/sql/query?id=1', 'SQL', '本地服务SSRF探测', 'HIGH', 1),

-- XXE Payload
('XXE', 'BASIC', '<?xml version="1.0"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><foo>&xxe;</foo>', 'root:', 'XXE文件读取探测', 'HIGH', 1),

-- 反序列化Payload
('DESERIALIZATION', 'BASIC', 'rO0ABXNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAADAAN3cIAAAAQAAAAAB4', 'HashMap', 'Java序列化对象探测', 'HIGH', 1);

-- ============================================================
-- 9. 完成提示
-- ============================================================

SELECT '漏洞和规则数据初始化完成！' AS message;
SELECT COUNT(*) AS rule_count FROM sys_monitor_rule;
SELECT COUNT(*) AS vuln_count FROM sys_vulnerability_monitor;
SELECT COUNT(*) AS relation_count FROM sys_vulnerability_rule;
SELECT COUNT(*) AS interface_count FROM sys_scan_interface;
