/**
 * 文件包含漏洞测试平台JavaScript逻辑
 */

const CONFIG = {
    ENDPOINTS: {
        VULN: '/target/file/include',
        SAFE: '/target/file/safe-include',
        LIST: '/target/file/list-allowed'
    }
};

document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
});

function initializeApp() {
    bindEventListeners();
    VulnCommon.updateStatus('ready', '就绪');
    console.log('文件包含漏洞测试平台初始化完成');
}

function bindEventListeners() {
    document.getElementById('vulnIncludeBtn')?.addEventListener('click', () => {
        const path = document.getElementById('filePathInput')?.value.trim();
        if (path) includeFile(path);
    });

    document.getElementById('safeIncludeBtn')?.addEventListener('click', () => {
        const path = document.getElementById('filePathInput')?.value.trim();
        if (path) safeIncludeFile(path);
    });

    document.getElementById('filePathInput')?.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            const path = document.getElementById('filePathInput')?.value.trim();
            if (path) includeFile(path);
        }
    });
}

async function includeFile(filePath) {
    if (!filePath) {
        VulnCommon.showNotification('请输入文件路径', 'warning');
        return;
    }

    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '加载中...');

    try {
        const response = await fetch(`${CONFIG.ENDPOINTS.VULN}?path=${encodeURIComponent(filePath)}`);
        const data = await response.json();
        handleResponse(data, filePath, 'vuln');
    } catch (error) {
        handleError(filePath, error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

async function safeIncludeFile(filePath) {
    if (!filePath) {
        VulnCommon.showNotification('请输入文件路径', 'warning');
        return;
    }

    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '安全加载中...');

    try {
        const response = await fetch(`${CONFIG.ENDPOINTS.SAFE}?path=${encodeURIComponent(filePath)}`);
        const data = await response.json();
        handleResponse(data, filePath, 'safe');
    } catch (error) {
        handleError(filePath, error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

function handleResponse(data, filePath, type) {
    const isSuccess = data.code === 200;
    const typeBadge = type === 'vuln' 
        ? '<span class="badge bg-danger">漏洞接口</span>'
        : '<span class="badge bg-success">安全接口</span>';

    let content = `
        <div class="mb-3">
            <span class="text-muted">文件路径：</span>
            <code class="bg-light px-2 py-1 rounded">${VulnCommon.escapeHtml(filePath)}</code>
        </div>`;

    if (data.data?.blocked_reason) {
        content += `
            <div class="alert alert-warning mb-3">
                <i class="fas fa-shield-alt me-2"></i>
                <strong>拦截原因：</strong>${VulnCommon.escapeHtml(data.data.blocked_reason)}
            </div>`;
    }

    if (data.data?.allowed_extensions) {
        content += `
            <div class="alert alert-info mb-3">
                <i class="fas fa-list me-2"></i>
                <strong>允许的扩展名：</strong>${VulnCommon.escapeHtml(data.data.allowed_extensions.join(', '))}
            </div>`;
    }

    if (data.data?.content) {
        content += `
            <div class="mb-3">
                <span class="text-muted">文件内容：</span>
                <pre class="bg-dark text-light p-3 rounded mt-2" style="max-height: 300px; overflow-y: auto;">${VulnCommon.escapeHtml(data.data.content)}</pre>
            </div>`;
    }

    if (data.data?.parsed_content?.properties) {
        content += `
            <div class="mb-3">
                <span class="text-muted">解析后的属性：</span>
                <table class="table table-sm table-bordered mt-2">
                    <thead class="table-light"><tr><th>键</th><th>值</th></tr></thead>
                    <tbody>`;
        for (const [key, value] of Object.entries(data.data.parsed_content.properties)) {
            content += `<tr><td>${VulnCommon.escapeHtml(key)}</td><td>${VulnCommon.escapeHtml(value)}</td></tr>`;
        }
        content += `</tbody></table></div>`;
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
        title: `文件加载${isSuccess ? '成功' : '失败'}`,
        badge: typeBadge,
        content: content,
        type: type
    });

    VulnCommon.appendResult('outputContainer', html);
}

function handleError(filePath, error) {
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
                    文件路径: ${VulnCommon.escapeHtml(filePath)}<br>
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
                <i class="fas fa-file-import fa-4x mb-4 text-primary"></i>
                <h4 class="fw-bold text-dark">欢迎使用文件包含漏洞测试平台</h4>
                <p class="mb-0 lead">请输入文件路径或点击测试用例开始文件加载测试</p>
            </div>`;
    }
}
