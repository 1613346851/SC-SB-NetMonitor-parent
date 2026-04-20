/**
 * XXE XML外部实体注入漏洞测试平台JavaScript逻辑
 */

const CONFIG = {
    ENDPOINTS: {
        VULN: '/target/xxe/parse',
        SAFE: '/target/xxe/safe-parse',
        TEST_CASES: '/target/xxe/test-cases',
        LOGS: '/target/xxe/logs',
        RESET_DB: '/target/db/reset/xxe'
    }
};

let testCases = {};

document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
    loadTestCases();
});

function initializeApp() {
    bindEventListeners();
    VulnCommon.updateStatus('ready', '就绪');
    updateLogCount();
    console.log('XXE漏洞测试平台初始化完成');
}

function bindEventListeners() {
    document.getElementById('vulnParseBtn')?.addEventListener('click', () => {
        const xml = document.getElementById('xmlInput')?.value.trim();
        if (xml) parseXml(xml, 'vuln');
    });

    document.getElementById('safeParseBtn')?.addEventListener('click', () => {
        const xml = document.getElementById('xmlInput')?.value.trim();
        if (xml) parseXml(xml, 'safe');
    });
}

async function loadTestCases() {
    try {
        const response = await fetch(CONFIG.ENDPOINTS.TEST_CASES);
        const data = await response.json();
        if (data.code === 200 && data.data?.test_cases) {
            testCases = data.data.test_cases;
        }
    } catch (error) {
        console.error('加载测试用例失败:', error);
    }
}

function loadTestCase(caseName) {
    if (testCases[caseName]) {
        document.getElementById('xmlInput').value = testCases[caseName];
        VulnCommon.showNotification(`已加载测试用例: ${caseName}`, 'info');
    } else {
        VulnCommon.showNotification('测试用例未加载，请刷新页面', 'warning');
    }
}

async function parseXml(xml, type) {
    if (!xml) {
        VulnCommon.showNotification('请输入XML内容', 'warning');
        return;
    }

    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '解析中...');

    const endpoint = type === 'vuln' ? CONFIG.ENDPOINTS.VULN : CONFIG.ENDPOINTS.SAFE;

    try {
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/xml'
            },
            body: xml
        });
        const data = await response.json();
        handleResponse(data, type);
    } catch (error) {
        handleError(error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

function handleResponse(data, type) {
    const isSuccess = data.code === 200;
    const typeBadge = type === 'vuln' 
        ? '<span class="badge bg-danger">漏洞接口</span>'
        : '<span class="badge bg-success">安全接口</span>';

    let content = '';

    if (data.data?.parsed_data) {
        const parsed = data.data.parsed_data;
        content += `
            <div class="mb-3">
                <span class="text-muted">根元素：</span>
                <code class="bg-light px-2 py-1 rounded">${VulnCommon.escapeHtml(parsed.root_element || 'N/A')}</code>
            </div>`;

        if (parsed.elements) {
            content += `
                <div class="mb-3">
                    <span class="text-muted">解析内容：</span>
                    <table class="table table-sm table-bordered mt-2">
                        <thead class="table-light"><tr><th>元素名</th><th>内容</th></tr></thead>
                        <tbody>`;
            for (const [key, value] of Object.entries(parsed.elements)) {
                const displayValue = value.length > 500 ? value.substring(0, 500) + '...(已截断)' : value;
                content += `<tr><td>${VulnCommon.escapeHtml(key)}</td><td><pre class="mb-0" style="white-space: pre-wrap;">${VulnCommon.escapeHtml(displayValue)}</pre></td></tr>`;
            }
            content += `</tbody></table></div>`;
        }
    }

    if (data.data?.has_external_entity) {
        content += `
            <div class="alert alert-warning mb-3">
                <i class="fas fa-exclamation-triangle me-2"></i>
                <strong>检测到外部实体：</strong>${data.data.has_external_entity ? '是' : '否'}
            </div>`;
    }

    if (data.data?.blocked_reason) {
        content += `
            <div class="alert alert-warning mb-3">
                <i class="fas fa-shield-alt me-2"></i>
                <strong>拦截原因：</strong>${VulnCommon.escapeHtml(data.data.blocked_reason)}
            </div>`;
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
        title: `XML解析${isSuccess ? '成功' : '失败'}`,
        badge: typeBadge,
        content: content,
        type: type
    });

    VulnCommon.appendResult('outputContainer', html);
}

function handleError(error) {
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
                <i class="fas fa-code fa-4x mb-4 text-primary"></i>
                <h4 class="fw-bold text-dark">欢迎使用XXE漏洞测试平台</h4>
                <p class="mb-0 lead">请输入XML内容或点击测试用例开始解析测试</p>
            </div>`;
    }
}

async function getXxeLogs() {
    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '获取解析日志...');

    try {
        const response = await fetch(CONFIG.ENDPOINTS.LOGS);
        const data = await response.json();

        if (data.code === 200) {
            const logs = data.data.logs || [];
            let content = '';

            if (logs.length === 0) {
                content = '<div class="alert alert-info mb-0"><i class="fas fa-info-circle me-2"></i>暂无解析日志</div>';
            } else {
                content = `
                    <div class="table-responsive">
                        <table class="table table-sm table-hover">
                            <thead class="table-light">
                                <tr>
                                    <th>ID</th>
                                    <th>包含外部实体</th>
                                    <th>解析结果</th>
                                    <th>创建时间</th>
                                </tr>
                            </thead>
                            <tbody>`;
                
                logs.forEach(log => {
                    const hasExternal = log.hasExternalEntity ? 
                        '<span class="badge bg-danger">是</span>' : 
                        '<span class="badge bg-success">否</span>';
                    const resultPreview = log.parseResult ? 
                        VulnCommon.escapeHtml(log.parseResult.substring(0, 50)) + '...' : '-';
                    
                    content += `
                        <tr>
                            <td>${log.id}</td>
                            <td>${hasExternal}</td>
                            <td><small>${resultPreview}</small></td>
                            <td><small>${log.createTime || '-'}</small></td>
                        </tr>`;
                });

                content += '</tbody></table></div>';
            }

            const html = `
                <div class="result-card border-info">
                    <div class="result-header d-flex justify-content-between align-items-center">
                        <h6 class="mb-0">
                            <i class="fas fa-database text-info me-2"></i>
                            XXE解析日志
                        </h6>
                        <small class="text-muted">共 ${logs.length} 条记录</small>
                    </div>
                    <div class="result-body">${content}</div>
                </div>`;

            VulnCommon.appendResult('outputContainer', html);
        }
    } catch (error) {
        handleError(error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

async function resetDatabase() {
    if (!confirm('确定要重置XXE日志表吗？这将删除所有解析日志！')) {
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
        handleError(error);
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
