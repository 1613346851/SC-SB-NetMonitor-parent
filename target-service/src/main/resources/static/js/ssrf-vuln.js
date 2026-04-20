/**
 * SSRF服务端请求伪造漏洞测试平台JavaScript逻辑
 */

const CONFIG = {
    ENDPOINTS: {
        VULN: '/target/ssrf/request',
        SAFE: '/target/ssrf/safe-request',
        LIST: '/target/ssrf/list-allowed',
        LOGS: '/target/ssrf/logs',
        RESET_DB: '/target/db/reset/ssrf'
    }
};

document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
});

function initializeApp() {
    bindEventListeners();
    VulnCommon.updateStatus('ready', '就绪');
    updateLogCount();
    console.log('SSRF漏洞测试平台初始化完成');
}

function bindEventListeners() {
    document.getElementById('vulnRequestBtn')?.addEventListener('click', () => {
        const url = document.getElementById('urlInput')?.value.trim();
        if (url) makeRequest(url);
    });

    document.getElementById('safeRequestBtn')?.addEventListener('click', () => {
        const url = document.getElementById('urlInput')?.value.trim();
        if (url) safeRequest(url);
    });

    document.getElementById('urlInput')?.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            const url = document.getElementById('urlInput')?.value.trim();
            if (url) makeRequest(url);
        }
    });
}

async function makeRequest(url) {
    if (!url) {
        VulnCommon.showNotification('请输入目标URL', 'warning');
        return;
    }

    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '请求中...');

    try {
        const response = await fetch(`${CONFIG.ENDPOINTS.VULN}?url=${encodeURIComponent(url)}`);
        const data = await response.json();
        handleResponse(data, url, 'vuln');
    } catch (error) {
        handleError(url, error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

async function safeRequest(url) {
    if (!url) {
        VulnCommon.showNotification('请输入目标URL', 'warning');
        return;
    }

    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '安全请求中...');

    try {
        const response = await fetch(`${CONFIG.ENDPOINTS.SAFE}?url=${encodeURIComponent(url)}`);
        const data = await response.json();
        handleResponse(data, url, 'safe');
    } catch (error) {
        handleError(url, error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

function handleResponse(data, url, type) {
    const isSuccess = data.code === 200;
    const typeBadge = type === 'vuln' 
        ? '<span class="badge bg-danger">漏洞接口</span>'
        : '<span class="badge bg-success">安全接口</span>';

    let content = `
        <div class="mb-3">
            <span class="text-muted">目标URL：</span>
            <code class="bg-light px-2 py-1 rounded">${VulnCommon.escapeHtml(url)}</code>
        </div>`;

    if (data.data?.status_code) {
        content += `
            <div class="mb-3">
                <span class="text-muted">响应状态码：</span>
                <span class="badge ${data.data.status_code < 400 ? 'bg-success' : 'bg-danger'}">${data.data.status_code}</span>
            </div>`;
    }

    if (data.data?.resolved_ip) {
        content += `
            <div class="mb-3">
                <span class="text-muted">解析IP：</span>
                <code class="bg-light px-2 py-1 rounded">${VulnCommon.escapeHtml(data.data.resolved_ip)}</code>
            </div>`;
    }

    if (data.data?.blocked_reason) {
        content += `
            <div class="alert alert-warning mb-3">
                <i class="fas fa-shield-alt me-2"></i>
                <strong>拦截原因：</strong>${VulnCommon.escapeHtml(data.data.blocked_reason)}
            </div>`;
    }

    if (data.data?.allowed_domains) {
        content += `
            <div class="alert alert-info mb-3">
                <i class="fas fa-list me-2"></i>
                <strong>允许的域名：</strong>${VulnCommon.escapeHtml(data.data.allowed_domains.join(', '))}
            </div>`;
    }

    if (data.data?.response_body) {
        let formattedBody = data.data.response_body;
        try {
            const jsonObj = JSON.parse(data.data.response_body);
            formattedBody = JSON.stringify(jsonObj, null, 2);
            if (jsonObj.data?.files) {
                const filesList = jsonObj.data.files.map(f => 
                    `<tr><td><code>${VulnCommon.escapeHtml(f.name)}</code></td><td>${VulnCommon.escapeHtml(f.description || '')}</td><td><span class="badge bg-secondary">${VulnCommon.escapeHtml(f.type || 'txt')}</span></td></tr>`
                ).join('');
                content += `
                    <div class="mb-3">
                        <span class="text-muted">响应内容：</span>
                        <pre class="bg-dark text-light p-3 rounded mt-2" style="max-height: 300px; overflow-y: auto;">${VulnCommon.escapeHtml(formattedBody)}</pre>
                    </div>
                    <div class="mb-3">
                        <span class="text-muted">文件列表：</span>
                        <table class="table table-striped table-hover table-sm mt-2">
                            <thead class="table-dark"><tr><th>文件名</th><th>描述</th><th>类型</th></tr></thead>
                            <tbody>${filesList}</tbody>
                        </table>
                    </div>`;
            } else {
                content += `
                    <div class="mb-3">
                        <span class="text-muted">响应内容：</span>
                        <pre class="bg-dark text-light p-3 rounded mt-2" style="max-height: 300px; overflow-y: auto;">${VulnCommon.escapeHtml(formattedBody)}</pre>
                    </div>`;
            }
        } catch (e) {
            content += `
                <div class="mb-3">
                    <span class="text-muted">响应内容：</span>
                    <pre class="bg-dark text-light p-3 rounded mt-2" style="max-height: 300px; overflow-y: auto;">${VulnCommon.escapeHtml(formattedBody)}</pre>
                </div>`;
        }
    }

    if (data.data?.warning) {
        content += `
            <div class="alert alert-danger mb-0">
                <i class="fas fa-exclamation-triangle me-2"></i>
                ${VulnCommon.escapeHtml(data.data.warning)}
            </div>`;
    }

    if (data.data?.security_note) {
        content += `
            <div class="alert alert-success mb-0">
                <i class="fas fa-shield-alt me-2"></i>
                ${VulnCommon.escapeHtml(data.data.security_note)}
            </div>`;
    }

    const html = VulnCommon.createResultCard({
        isSuccess: isSuccess,
        title: `请求${isSuccess ? '成功' : '失败'}`,
        badge: typeBadge,
        content: content,
        type: type
    });

    VulnCommon.appendResult('outputContainer', html);
}

function handleError(url, error) {
    const html = `
        <div class="result-card border-danger">
            <div class="result-header d-flex justify-content-between align-items-center">
                <h6 class="mb-0">
                    <i class="fas fa-times-circle text-danger me-2"></i>
                    请求失败
                </h6>
                <small class="text-muted">${VulnCommon.formatTime()}</small>
            </div>
            <div class="result-body">
                <div class="alert alert-danger mb-0">
                    <i class="fas fa-exclamation-triangle me-2"></i>
                    URL: ${VulnCommon.escapeHtml(url)}<br>
                    错误信息: ${VulnCommon.escapeHtml(error.message)}
                </div>
            </div>
        </div>`;

    VulnCommon.appendResult('outputContainer', html);
}

function clearOutput() {
    const outputContainer = document.getElementById('outputContainer');
    if (outputContainer) {
        outputContainer.innerHTML = `
            <div class="welcome-message text-center text-muted d-flex flex-column justify-content-center align-items-center h-100">
                <i class="fas fa-network-wired fa-4x mb-4 text-primary"></i>
                <h4 class="fw-bold text-dark">欢迎使用SSRF漏洞测试平台</h4>
                <p class="mb-0 lead">请输入目标URL或点击测试用例开始请求测试</p>
            </div>`;
    }
}

async function getSsrfLogs() {
    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '获取请求日志...');

    try {
        const response = await fetch(CONFIG.ENDPOINTS.LOGS);
        const data = await response.json();

        if (data.code === 200) {
            const logs = data.data.logs || [];
            let content = '';

            if (logs.length === 0) {
                content = '<div class="alert alert-info mb-0"><i class="fas fa-info-circle me-2"></i>暂无请求日志</div>';
            } else {
                content = `
                    <div class="table-responsive">
                        <table class="table table-sm table-hover">
                            <thead class="table-light">
                                <tr>
                                    <th>ID</th>
                                    <th>请求URL</th>
                                    <th>方法</th>
                                    <th>状态码</th>
                                    <th>来源IP</th>
                                    <th>请求时间</th>
                                </tr>
                            </thead>
                            <tbody>`;
                
                logs.forEach(log => {
                    content += `
                        <tr>
                            <td>${log.id}</td>
                            <td><code style="font-size: 0.75rem;">${VulnCommon.escapeHtml(log.requestUrl || '')}</code></td>
                            <td><span class="badge bg-primary">${log.requestMethod || 'GET'}</span></td>
                            <td><span class="badge ${log.responseCode >= 200 && log.responseCode < 400 ? 'bg-success' : 'bg-danger'}">${log.responseCode || 0}</span></td>
                            <td><small>${log.sourceIp || '-'}</small></td>
                            <td><small>${log.requestTime || '-'}</small></td>
                        </tr>`;
                });

                content += '</tbody></table></div>';
            }

            const html = `
                <div class="result-card border-info">
                    <div class="result-header d-flex justify-content-between align-items-center">
                        <h6 class="mb-0">
                            <i class="fas fa-database text-info me-2"></i>
                            SSRF请求日志
                        </h6>
                        <small class="text-muted">共 ${logs.length} 条记录</small>
                    </div>
                    <div class="result-body">${content}</div>
                </div>`;

            VulnCommon.appendResult('outputContainer', html);
        }
    } catch (error) {
        handleError('', error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

async function resetDatabase() {
    if (!confirm('确定要重置SSRF日志表吗？这将删除所有请求日志！')) {
        return;
    }

    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '重置数据库...');

    try {
        const response = await fetch(CONFIG.ENDPOINTS.RESET_DB, {
            method: 'DELETE'
        });
        const data = await response.json();

        if (data.code === 200) {
            const html = `
                <div class="result-card border-success">
                    <div class="result-header">
                        <h6 class="mb-0">
                            <i class="fas fa-check-circle text-success me-2"></i>
                            数据表重置成功
                        </h6>
                    </div>
                    <div class="result-body">
                        <div class="alert alert-success mb-0">
                            <i class="fas fa-info-circle me-2"></i>
                            已删除 <strong>${data.data.deleted_count}</strong> 条记录
                        </div>
                    </div>
                </div>`;

            VulnCommon.appendResult('outputContainer', html);
            VulnCommon.showNotification('数据表已重置', 'success');
            updateLogCount();
        }
    } catch (error) {
        handleError('', error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

async function updateLogCount() {
    try {
        const response = await fetch(CONFIG.ENDPOINTS.LOGS);
        const data = await response.json();

        if (data.code === 200) {
            const count = (data.data.logs || []).length;
            const countElement = document.getElementById('logCount');
            if (countElement) {
                countElement.textContent = count;
            }
        }
    } catch (error) {
        console.error('获取日志数量失败:', error);
    }
}
