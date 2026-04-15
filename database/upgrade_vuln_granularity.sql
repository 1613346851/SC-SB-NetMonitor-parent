-- ============================================================
-- 漏洞颗粒度升级脚本
-- 将漏洞记录从接口级别改为规则级别
-- 每个漏洞对应一个具体的检测规则
-- ============================================================

-- 1. 清空现有的漏洞记录和关联关系
DELETE FROM `sys_vulnerability_rule`;
DELETE FROM `sys_vulnerability_monitor`;

-- 2. 重置自增ID
ALTER TABLE `sys_vulnerability_monitor` AUTO_INCREMENT = 1;
ALTER TABLE `sys_vulnerability_rule` AUTO_INCREMENT = 1;

-- ============================================================
-- 3. 插入细粒度漏洞记录（每个规则对应一个漏洞）
-- ============================================================

-- 3.1 SQL注入漏洞（9个漏洞，对应规则ID 1-9）
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `rule_ids`, `rule_count`, `defense_status`, `description`, `fix_suggestion`) VALUES
('SQL注入-UNION查询攻击', 'SQL_INJECTION', 'HIGH', '/target/sql/query', 0, '1', 1, 2, 
 '用户查询接口存在SQL注入漏洞，攻击者可使用UNION SELECT语句获取数据库敏感信息',
 '使用参数化查询或预编译语句，对用户输入进行严格验证'),
('SQL注入-OR恒真攻击', 'SQL_INJECTION', 'HIGH', '/target/sql/query', 0, '2', 1, 2,
 '用户查询接口存在SQL注入漏洞，攻击者可使用OR 1=1恒真条件绕过认证',
 '使用参数化查询或预编译语句，对用户输入进行严格验证'),
('SQL注入-AND布尔盲注', 'SQL_INJECTION', 'MEDIUM', '/target/sql/query', 0, '3', 1, 2,
 '用户查询接口存在SQL注入漏洞，攻击者可使用AND布尔条件进行盲注攻击',
 '使用参数化查询或预编译语句，对用户输入进行严格验证'),
('SQL注入-DROP语句攻击', 'SQL_INJECTION', 'CRITICAL', '/target/sql/query', 0, '4', 1, 2,
 '用户查询接口存在SQL注入漏洞，攻击者可执行DROP TABLE等危险操作',
 '使用参数化查询或预编译语句，对用户输入进行严格验证'),
('SQL注入-SLEEP延时盲注', 'SQL_INJECTION', 'MEDIUM', '/target/sql/query', 0, '5', 1, 2,
 '用户查询接口存在SQL注入漏洞，攻击者可使用SLEEP函数进行时间盲注',
 '使用参数化查询或预编译语句，对用户输入进行严格验证'),
('SQL注入-BENCHMARK延时攻击', 'SQL_INJECTION', 'MEDIUM', '/target/sql/query', 0, '6', 1, 2,
 '用户查询接口存在SQL注入漏洞，攻击者可使用BENCHMARK函数进行时间盲注',
 '使用参数化查询或预编译语句，对用户输入进行严格验证'),
('SQL注入-注释符号绕过', 'SQL_INJECTION', 'LOW', '/target/sql/query', 0, '7', 1, 2,
 '用户查询接口存在SQL注入漏洞，攻击者可使用注释符号绕过SQL语句限制',
 '使用参数化查询或预编译语句，对用户输入进行严格验证'),
('SQL注入-堆叠查询攻击', 'SQL_INJECTION', 'HIGH', '/target/sql/query', 0, '8', 1, 2,
 '用户查询接口存在SQL注入漏洞，攻击者可执行多条SQL语句（堆叠查询）',
 '使用参数化查询或预编译语句，对用户输入进行严格验证'),
('SQL注入-单引号闭合攻击', 'SQL_INJECTION', 'LOW', '/target/sql/query', 0, '9', 1, 2,
 '用户查询接口存在SQL注入漏洞，攻击者可使用单引号闭合SQL语句',
 '使用参数化查询或预编译语句，对用户输入进行严格验证');

-- 3.2 XSS存储型漏洞（11个漏洞，对应规则ID 10-20）
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `rule_ids`, `rule_count`, `defense_status`, `description`, `fix_suggestion`) VALUES
('XSS存储型-script标签注入', 'XSS', 'HIGH', '/target/xss/submit-comment', 0, '10', 1, 2,
 '评论提交接口存在存储型XSS漏洞，攻击者可注入script标签执行恶意脚本',
 '对用户输入进行HTML实体转义，设置CSP策略'),
('XSS存储型-javascript协议注入', 'XSS', 'HIGH', '/target/xss/submit-comment', 0, '11', 1, 2,
 '评论提交接口存在存储型XSS漏洞，攻击者可使用javascript:协议执行代码',
 '对用户输入进行HTML实体转义，设置CSP策略'),
('XSS存储型-onerror事件注入', 'XSS', 'MEDIUM', '/target/xss/submit-comment', 0, '12', 1, 2,
 '评论提交接口存在存储型XSS漏洞，攻击者可使用onerror事件执行脚本',
 '对用户输入进行HTML实体转义，设置CSP策略'),
('XSS存储型-onload事件注入', 'XSS', 'MEDIUM', '/target/xss/submit-comment', 0, '13', 1, 2,
 '评论提交接口存在存储型XSS漏洞，攻击者可使用onload事件执行脚本',
 '对用户输入进行HTML实体转义，设置CSP策略'),
('XSS存储型-onclick事件注入', 'XSS', 'MEDIUM', '/target/xss/submit-comment', 0, '14', 1, 2,
 '评论提交接口存在存储型XSS漏洞，攻击者可使用onclick事件执行脚本',
 '对用户输入进行HTML实体转义，设置CSP策略'),
('XSS存储型-alert函数注入', 'XSS', 'MEDIUM', '/target/xss/submit-comment', 0, '15', 1, 2,
 '评论提交接口存在存储型XSS漏洞，攻击者可注入alert函数测试漏洞',
 '对用户输入进行HTML实体转义，设置CSP策略'),
('XSS存储型-document对象注入', 'XSS', 'HIGH', '/target/xss/submit-comment', 0, '16', 1, 2,
 '评论提交接口存在存储型XSS漏洞，攻击者可访问document对象窃取信息',
 '对用户输入进行HTML实体转义，设置CSP策略'),
('XSS存储型-img标签注入', 'XSS', 'MEDIUM', '/target/xss/submit-comment', 0, '17', 1, 2,
 '评论提交接口存在存储型XSS漏洞，攻击者可注入img标签执行脚本',
 '对用户输入进行HTML实体转义，设置CSP策略'),
('XSS存储型-SVG标签注入', 'XSS', 'MEDIUM', '/target/xss/submit-comment', 0, '18', 1, 2,
 '评论提交接口存在存储型XSS漏洞，攻击者可注入SVG标签执行脚本',
 '对用户输入进行HTML实体转义，设置CSP策略'),
('XSS存储型-eval函数注入', 'XSS', 'HIGH', '/target/xss/submit-comment', 0, '19', 1, 2,
 '评论提交接口存在存储型XSS漏洞，攻击者可使用eval函数执行任意代码',
 '对用户输入进行HTML实体转义，设置CSP策略'),
('XSS存储型-iframe标签注入', 'XSS', 'MEDIUM', '/target/xss/submit-comment', 0, '20', 1, 2,
 '评论提交接口存在存储型XSS漏洞，攻击者可注入iframe标签嵌入恶意页面',
 '对用户输入进行HTML实体转义，设置CSP策略');

-- 3.3 XSS反射型漏洞（5个漏洞，对应规则ID 10,11,12,15,16）
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `rule_ids`, `rule_count`, `defense_status`, `description`, `fix_suggestion`) VALUES
('XSS反射型-script标签注入', 'XSS', 'HIGH', '/target/xss/search', 0, '10', 1, 2,
 '搜索接口存在反射型XSS漏洞，攻击者可注入script标签执行恶意脚本',
 '对用户输入进行HTML实体转义，设置Content-Type响应头'),
('XSS反射型-javascript协议注入', 'XSS', 'HIGH', '/target/xss/search', 0, '11', 1, 2,
 '搜索接口存在反射型XSS漏洞，攻击者可使用javascript:协议执行代码',
 '对用户输入进行HTML实体转义，设置Content-Type响应头'),
('XSS反射型-onerror事件注入', 'XSS', 'MEDIUM', '/target/xss/search', 0, '12', 1, 2,
 '搜索接口存在反射型XSS漏洞，攻击者可使用onerror事件执行脚本',
 '对用户输入进行HTML实体转义，设置Content-Type响应头'),
('XSS反射型-alert函数注入', 'XSS', 'MEDIUM', '/target/xss/search', 0, '15', 1, 2,
 '搜索接口存在反射型XSS漏洞，攻击者可注入alert函数测试漏洞',
 '对用户输入进行HTML实体转义，设置Content-Type响应头'),
('XSS反射型-document对象注入', 'XSS', 'HIGH', '/target/xss/search', 0, '16', 1, 2,
 '搜索接口存在反射型XSS漏洞，攻击者可访问document对象窃取信息',
 '对用户输入进行HTML实体转义，设置Content-Type响应头');

-- 3.4 XSS DOM型漏洞（5个漏洞，对应规则ID 10,11,12,13,18）
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `rule_ids`, `rule_count`, `defense_status`, `description`, `fix_suggestion`) VALUES
('XSS DOM型-script标签注入', 'XSS', 'HIGH', '/target/xss/profile', 0, '10', 1, 2,
 '用户资料接口存在DOM型XSS漏洞，攻击者可注入script标签执行恶意脚本',
 '前端使用安全的DOM操作方法，避免innerHTML直接插入'),
('XSS DOM型-javascript协议注入', 'XSS', 'HIGH', '/target/xss/profile', 0, '11', 1, 2,
 '用户资料接口存在DOM型XSS漏洞，攻击者可使用javascript:协议执行代码',
 '前端使用安全的DOM操作方法，避免innerHTML直接插入'),
('XSS DOM型-onerror事件注入', 'XSS', 'MEDIUM', '/target/xss/profile', 0, '12', 1, 2,
 '用户资料接口存在DOM型XSS漏洞，攻击者可使用onerror事件执行脚本',
 '前端使用安全的DOM操作方法，避免innerHTML直接插入'),
('XSS DOM型-onload事件注入', 'XSS', 'MEDIUM', '/target/xss/profile', 0, '13', 1, 2,
 '用户资料接口存在DOM型XSS漏洞，攻击者可使用onload事件执行脚本',
 '前端使用安全的DOM操作方法，避免innerHTML直接插入'),
('XSS DOM型-SVG标签注入', 'XSS', 'MEDIUM', '/target/xss/profile', 0, '18', 1, 2,
 '用户资料接口存在DOM型XSS漏洞，攻击者可注入SVG标签执行脚本',
 '前端使用安全的DOM操作方法，避免innerHTML直接插入');

-- 3.5 命令注入漏洞（9个漏洞，对应规则ID 21-29）
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `rule_ids`, `rule_count`, `defense_status`, `description`, `fix_suggestion`) VALUES
('命令注入-管道符攻击', 'COMMAND_INJECTION', 'HIGH', '/target/cmd/execute', 0, '21', 1, 2,
 '命令执行接口存在命令注入漏洞，攻击者可使用管道符|执行任意命令',
 '对接口建立命令白名单，禁止将用户输入直接传入系统命令'),
('命令注入-分号攻击', 'COMMAND_INJECTION', 'HIGH', '/target/cmd/execute', 0, '22', 1, 2,
 '命令执行接口存在命令注入漏洞，攻击者可使用分号;执行多条命令',
 '对接口建立命令白名单，禁止将用户输入直接传入系统命令'),
('命令注入-反引号攻击', 'COMMAND_INJECTION', 'HIGH', '/target/cmd/execute', 0, '23', 1, 2,
 '命令执行接口存在命令注入漏洞，攻击者可使用反引号`执行命令替换',
 '对接口建立命令白名单，禁止将用户输入直接传入系统命令'),
('命令注入-$()执行攻击', 'COMMAND_INJECTION', 'HIGH', '/target/cmd/execute', 0, '24', 1, 2,
 '命令执行接口存在命令注入漏洞，攻击者可使用$()执行命令替换',
 '对接口建立命令白名单，禁止将用户输入直接传入系统命令'),
('命令注入-Windows cmd攻击', 'COMMAND_INJECTION', 'HIGH', '/target/cmd/execute', 0, '25', 1, 2,
 '命令执行接口存在命令注入漏洞，攻击者可调用Windows cmd执行命令',
 '对接口建立命令白名单，禁止将用户输入直接传入系统命令'),
('命令注入-ping命令攻击', 'COMMAND_INJECTION', 'MEDIUM', '/target/cmd/execute', 0, '26', 1, 2,
 '命令执行接口存在命令注入漏洞，攻击者可执行ping命令探测网络',
 '对接口建立命令白名单，禁止将用户输入直接传入系统命令'),
('命令注入-whoami命令攻击', 'COMMAND_INJECTION', 'HIGH', '/target/cmd/execute', 0, '27', 1, 2,
 '命令执行接口存在命令注入漏洞，攻击者可执行whoami获取当前用户',
 '对接口建立命令白名单，禁止将用户输入直接传入系统命令'),
('命令注入-tasklist命令攻击', 'COMMAND_INJECTION', 'MEDIUM', '/target/cmd/execute', 0, '28', 1, 2,
 '命令执行接口存在命令注入漏洞，攻击者可执行tasklist获取进程列表',
 '对接口建立命令白名单，禁止将用户输入直接传入系统命令'),
('命令注入-cat命令攻击', 'COMMAND_INJECTION', 'HIGH', '/target/cmd/execute', 0, '29', 1, 2,
 '命令执行接口存在命令注入漏洞，攻击者可执行cat读取敏感文件',
 '对接口建立命令白名单，禁止将用户输入直接传入系统命令');

-- 3.6 路径遍历漏洞（6个漏洞，对应规则ID 30-35）
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `rule_ids`, `rule_count`, `defense_status`, `description`, `fix_suggestion`) VALUES
('路径遍历-父目录引用攻击', 'PATH_TRAVERSAL', 'HIGH', '/target/path/read', 0, '30', 1, 2,
 '文件读取接口存在路径遍历漏洞，攻击者可使用../读取任意文件',
 '限制文件访问目录，规范化路径处理，使用白名单验证'),
('路径遍历-Linux敏感文件攻击', 'PATH_TRAVERSAL', 'CRITICAL', '/target/path/read', 0, '31', 1, 2,
 '文件读取接口存在路径遍历漏洞，攻击者可读取/etc/passwd等敏感文件',
 '限制文件访问目录，规范化路径处理，使用白名单验证'),
('路径遍历-Windows敏感路径攻击', 'PATH_TRAVERSAL', 'HIGH', '/target/path/read', 0, '32', 1, 2,
 '文件读取接口存在路径遍历漏洞，攻击者可读取Windows系统文件',
 '限制文件访问目录，规范化路径处理，使用白名单验证'),
('路径遍历-URL编码绕过攻击', 'PATH_TRAVERSAL', 'HIGH', '/target/path/read', 0, '33', 1, 2,
 '文件读取接口存在路径遍历漏洞，攻击者可使用URL编码绕过过滤',
 '限制文件访问目录，规范化路径处理，使用白名单验证'),
('路径遍历-双重编码绕过攻击', 'PATH_TRAVERSAL', 'HIGH', '/target/path/read', 0, '34', 1, 2,
 '文件读取接口存在路径遍历漏洞，攻击者可使用双重URL编码绕过',
 '限制文件访问目录，规范化路径处理，使用白名单验证'),
('路径遍历-配置文件访问攻击', 'PATH_TRAVERSAL', 'HIGH', '/target/path/read', 0, '35', 1, 2,
 '文件读取接口存在路径遍历漏洞，攻击者可读取application.yml等配置文件',
 '限制文件访问目录，规范化路径处理，使用白名单验证');

-- 3.7 文件包含漏洞（5个漏洞，对应规则ID 36-40）
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `rule_ids`, `rule_count`, `defense_status`, `description`, `fix_suggestion`) VALUES
('文件包含-PHP函数攻击', 'FILE_INCLUSION', 'HIGH', '/target/file/include', 0, '36', 1, 2,
 '文件加载接口存在文件包含漏洞，攻击者可使用PHP包含函数加载任意文件',
 '限制接口仅加载预定义资源，并校验文件类型与资源根目录'),
('文件包含-data协议攻击', 'FILE_INCLUSION', 'HIGH', '/target/file/include', 0, '37', 1, 2,
 '文件加载接口存在文件包含漏洞，攻击者可使用data协议注入代码',
 '限制接口仅加载预定义资源，并校验文件类型与资源根目录'),
('文件包含-php协议攻击', 'FILE_INCLUSION', 'HIGH', '/target/file/include', 0, '38', 1, 2,
 '文件加载接口存在文件包含漏洞，攻击者可使用php://协议读取文件',
 '限制接口仅加载预定义资源，并校验文件类型与资源根目录'),
('文件包含-file协议攻击', 'FILE_INCLUSION', 'HIGH', '/target/file/include', 0, '39', 1, 2,
 '文件加载接口存在文件包含漏洞，攻击者可使用file://协议读取本地文件',
 '限制接口仅加载预定义资源，并校验文件类型与资源根目录'),
('文件包含-classpath协议攻击', 'FILE_INCLUSION', 'HIGH', '/target/file/include', 0, '40', 1, 2,
 '文件加载接口存在文件包含漏洞，攻击者可使用classpath:协议加载资源',
 '限制接口仅加载预定义资源，并校验文件类型与资源根目录');

-- 3.8 SSRF漏洞（6个漏洞，对应规则ID 41-46）
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `rule_ids`, `rule_count`, `defense_status`, `description`, `fix_suggestion`) VALUES
('SSRF-内网IP访问攻击', 'SSRF', 'HIGH', '/target/ssrf/request', 0, '41', 1, 2,
 'URL请求接口存在SSRF漏洞，攻击者可访问内网IP探测内部服务',
 '限制协议类型，禁用重定向，使用白名单验证目标地址'),
('SSRF-file协议攻击', 'SSRF', 'HIGH', '/target/ssrf/request', 0, '42', 1, 2,
 'URL请求接口存在SSRF漏洞，攻击者可使用file://协议读取本地文件',
 '限制协议类型，禁用重定向，使用白名单验证目标地址'),
('SSRF-dict协议攻击', 'SSRF', 'HIGH', '/target/ssrf/request', 0, '43', 1, 2,
 'URL请求接口存在SSRF漏洞，攻击者可使用dict://协议探测端口',
 '限制协议类型，禁用重定向，使用白名单验证目标地址'),
('SSRF-gopher协议攻击', 'SSRF', 'HIGH', '/target/ssrf/request', 0, '44', 1, 2,
 'URL请求接口存在SSRF漏洞，攻击者可使用gopher://协议发送任意请求',
 '限制协议类型，禁用重定向，使用白名单验证目标地址'),
('SSRF-云元数据攻击', 'SSRF', 'HIGH', '/target/ssrf/request', 0, '45', 1, 2,
 'URL请求接口存在SSRF漏洞，攻击者可访问云元数据服务获取敏感信息',
 '限制协议类型，禁用重定向，使用白名单验证目标地址'),
('SSRF-本地回环攻击', 'SSRF', 'HIGH', '/target/ssrf/request', 0, '46', 1, 2,
 'URL请求接口存在SSRF漏洞，攻击者可访问localhost绕过防火墙',
 '限制协议类型，禁用重定向，使用白名单验证目标地址');

-- 3.9 XXE漏洞（6个漏洞，对应规则ID 47-52）
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `rule_ids`, `rule_count`, `defense_status`, `description`, `fix_suggestion`) VALUES
('XXE-DOCTYPE ENTITY攻击', 'XXE', 'HIGH', '/target/xxe/parse', 0, '47', 1, 2,
 'XML解析接口存在XXE漏洞，攻击者可声明外部实体读取文件',
 '禁用DTD和外部实体解析，使用安全的XML解析配置'),
('XXE-SYSTEM关键字攻击', 'XXE', 'HIGH', '/target/xxe/parse', 0, '48', 1, 2,
 'XML解析接口存在XXE漏洞，攻击者可使用SYSTEM关键字加载外部资源',
 '禁用DTD和外部实体解析，使用安全的XML解析配置'),
('XXE-PUBLIC关键字攻击', 'XXE', 'HIGH', '/target/xxe/parse', 0, '49', 1, 2,
 'XML解析接口存在XXE漏洞，攻击者可使用PUBLIC关键字加载外部资源',
 '禁用DTD和外部实体解析，使用安全的XML解析配置'),
('XXE-file协议攻击', 'XXE', 'HIGH', '/target/xxe/parse', 0, '50', 1, 2,
 'XML解析接口存在XXE漏洞，攻击者可使用file://协议读取本地文件',
 '禁用DTD和外部实体解析，使用安全的XML解析配置'),
('XXE-http协议攻击', 'XXE', 'HIGH', '/target/xxe/parse', 0, '51', 1, 2,
 'XML解析接口存在XXE漏洞，攻击者可使用http://协议发起SSRF攻击',
 '禁用DTD和外部实体解析，使用安全的XML解析配置'),
('XXE-参数实体攻击', 'XXE', 'HIGH', '/target/xxe/parse', 0, '52', 1, 2,
 'XML解析接口存在XXE漏洞，攻击者可使用参数实体绕过过滤',
 '禁用DTD和外部实体解析，使用安全的XML解析配置');

-- 3.10 反序列化漏洞（4个漏洞，对应规则ID 53-56）
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `rule_ids`, `rule_count`, `defense_status`, `description`, `fix_suggestion`) VALUES
('反序列化-Java序列化对象攻击', 'DESERIALIZATION', 'HIGH', '/target/deserial/parse', 0, '53', 1, 2,
 '反序列化接口存在漏洞，攻击者可注入恶意Java序列化对象执行代码',
 '使用类白名单校验，避免反序列化不可信数据'),
('反序列化-PHP序列化攻击', 'DESERIALIZATION', 'HIGH', '/target/deserial/parse', 0, '54', 1, 2,
 '反序列化接口存在漏洞，攻击者可注入PHP序列化字符串执行代码',
 '使用类白名单校验，避免反序列化不可信数据'),
('反序列化-Python pickle攻击', 'DESERIALIZATION', 'HIGH', '/target/deserial/parse', 0, '55', 1, 2,
 '反序列化接口存在漏洞，攻击者可注入Python pickle对象执行代码',
 '使用类白名单校验，避免反序列化不可信数据'),
('反序列化-Base64编码Java攻击', 'DESERIALIZATION', 'HIGH', '/target/deserial/parse', 0, '56', 1, 2,
 '反序列化接口存在漏洞，攻击者可注入Base64编码的Java序列化对象',
 '使用类白名单校验，避免反序列化不可信数据');

-- 3.11 CSRF漏洞（3个漏洞，对应规则ID 57-59）
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `rule_ids`, `rule_count`, `defense_status`, `description`, `fix_suggestion`) VALUES
('CSRF-表单自动提交攻击', 'CSRF', 'MEDIUM', '/target/csrf/update-name', 0, '57', 1, 2,
 '昵称修改接口存在CSRF漏洞，攻击者可构造自动提交表单发起攻击',
 '为接口启用CSRF Token校验，并校验请求来源'),
('CSRF-隐藏字段攻击', 'CSRF', 'LOW', '/target/csrf/update-name', 0, '58', 1, 2,
 '昵称修改接口存在CSRF漏洞，攻击者可使用隐藏字段构造攻击页面',
 '为接口启用CSRF Token校验，并校验请求来源'),
('CSRF-跨域请求攻击', 'CSRF', 'LOW', '/target/csrf/update-name', 0, '59', 1, 2,
 '昵称修改接口存在CSRF漏洞，攻击者可构造跨域请求发起攻击',
 '为接口启用CSRF Token校验，并校验请求来源');

-- 3.12 DDoS漏洞（3个漏洞，对应规则ID 60）
INSERT INTO `sys_vulnerability_monitor` (`vuln_name`, `vuln_type`, `vuln_level`, `vuln_path`, `verify_status`, `rule_ids`, `rule_count`, `defense_status`, `description`, `fix_suggestion`) VALUES
('DDoS-CPU密集型攻击', 'DDOS', 'HIGH', '/target/ddos/compute-heavy', 0, '60', 1, 2,
 'CPU密集型计算接口易受DDoS攻击，高频请求可耗尽服务器资源',
 '添加请求频率限制，使用CDN防护，配置资源监控告警'),
('DDoS-I/O延迟型攻击', 'DDOS', 'MEDIUM', '/target/ddos/io-delay', 0, '60', 1, 2,
 'I/O延迟接口易受慢速攻击，可长期占用连接资源',
 '设置连接超时时间，限制并发连接数，使用连接池管理'),
('DDoS-Ping洪水攻击', 'DDOS', 'MEDIUM', '/target/ddos/ping', 0, '60', 1, 2,
 '简单Ping接口易受高频洪水攻击，可冲击网络栈',
 '添加请求频率限制，使用负载均衡，配置防火墙规则');

-- ============================================================
-- 4. 插入漏洞-规则关联关系（一对一）
-- ============================================================
INSERT INTO `sys_vulnerability_rule` (`vulnerability_id`, `rule_id`, `rule_name`, `attack_type`, `risk_level`)
SELECT v.id, v.rule_ids, 
       (SELECT r.rule_name FROM sys_monitor_rule r WHERE r.id = CAST(v.rule_ids AS UNSIGNED)),
       v.vuln_type,
       v.vuln_level
FROM sys_vulnerability_monitor v
WHERE v.rule_ids IS NOT NULL;

-- ============================================================
-- 5. 验证数据
-- ============================================================
SELECT '漏洞记录总数' AS '统计项', COUNT(*) AS '数量' FROM sys_vulnerability_monitor
UNION ALL
SELECT '漏洞-规则关联总数', COUNT(*) FROM sys_vulnerability_rule
UNION ALL
SELECT 'SQL注入漏洞数', COUNT(*) FROM sys_vulnerability_monitor WHERE vuln_type = 'SQL_INJECTION'
UNION ALL
SELECT 'XSS漏洞数', COUNT(*) FROM sys_vulnerability_monitor WHERE vuln_type = 'XSS'
UNION ALL
SELECT '命令注入漏洞数', COUNT(*) FROM sys_vulnerability_monitor WHERE vuln_type = 'COMMAND_INJECTION'
UNION ALL
SELECT '路径遍历漏洞数', COUNT(*) FROM sys_vulnerability_monitor WHERE vuln_type = 'PATH_TRAVERSAL'
UNION ALL
SELECT '文件包含漏洞数', COUNT(*) FROM sys_vulnerability_monitor WHERE vuln_type = 'FILE_INCLUSION'
UNION ALL
SELECT 'SSRF漏洞数', COUNT(*) FROM sys_vulnerability_monitor WHERE vuln_type = 'SSRF'
UNION ALL
SELECT 'XXE漏洞数', COUNT(*) FROM sys_vulnerability_monitor WHERE vuln_type = 'XXE'
UNION ALL
SELECT '反序列化漏洞数', COUNT(*) FROM sys_vulnerability_monitor WHERE vuln_type = 'DESERIALIZATION'
UNION ALL
SELECT 'CSRF漏洞数', COUNT(*) FROM sys_vulnerability_monitor WHERE vuln_type = 'CSRF'
UNION ALL
SELECT 'DDoS漏洞数', COUNT(*) FROM sys_vulnerability_monitor WHERE vuln_type = 'DDOS';
