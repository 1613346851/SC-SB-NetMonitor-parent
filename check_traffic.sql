-- 检查流量数据
USE network_monitor;

-- 查询流量记录
SELECT id, source_ip, request_uri, http_method, request_time, create_time 
FROM traffic_monitor 
ORDER BY id DESC 
LIMIT 10;

-- 统计总数
SELECT COUNT(*) as total FROM traffic_monitor;
