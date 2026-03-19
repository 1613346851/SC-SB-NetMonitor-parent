/**
 * 路径遍历漏洞测试平台JavaScript逻辑
 */

const CONFIG = {
    ENDPOINTS: {
        VULN: '/target/path/read',
        SAFE: '/target/path/safe-read',
        LIST: '/target/path/list-files'
    }
};

document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
});

function initializeApp() {
    bindEventListeners();
    VulnCommon.updateStatus('ready', '就绪');
    console.log('路径遍历漏洞测试平台初始化完成');
}

function bindEventListeners() {
    document.getElementById('vulnReadBtn')?.addEventListener('click', () => {
        const filename = document.getElementById('filenameInput')?.value.trim();
        if (filename) readFile(filename);
    });

    document.getElementById('safeReadBtn')?.addEventListener('click', () => {
        const filename = document.getElementById('filenameInput')?.value.trim();
        if (filename) safeReadFile(filename);
    });

    document.getElementById('filenameInput')?.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            const filename = document.getElementById('filenameInput')?.value.trim();
            if (filename) readFile(filename);
        }
    });
}

async function readFile(filename) {
    if (!filename) {
        VulnCommon.showNotification('请输入文件名', 'warning');
        return;
    }

    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '读取中...');

    try {
        const response = await fetch(`${CONFIG.ENDPOINTS.VULN}?filename=${encodeURIComponent(filename)}`);
        const data = await response.json();
        handleResponse(data, filename, 'vuln');
    } catch (error) {
        handleError(filename, error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

async function safeReadFile(filename) {
    if (!filename) {
        VulnCommon.showNotification('请输入文件名', 'warning');
        return;
    }

    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '安全读取中...');

    try {
        const response = await fetch(`${CONFIG.ENDPOINTS.SAFE}?filename=${encodeURIComponent(filename)}`);
        const data = await response.json();
        handleResponse(data, filename, 'safe');
    } catch (error) {
        handleError(filename, error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

async function listAllowedFiles() {
    VulnCommon.showLoading(true);
    try {
        const response = await fetch(CONFIG.ENDPOINTS.LIST);
        const data = await response.json();
        
        if (data.code === 200 && data.data?.files) {
            const listContainer = document.getElementById('allowedFilesList');
            if (listContainer) {
                let html = '<div class="list-group list-group-flush">';
                data.data.files.forEach(file => {
                    html += `
                        <div class="list-group-item d-flex justify-content-between align-items-center py-2">
                            <div>
                                <i class="fas fa-file me-2 text-primary"></i>
                                <span class="fw-medium">${VulnCommon.escapeHtml(file.name)}</span>
                                <br><small class="text-muted">${VulnCommon.escapeHtml(file.description)}</small>
                            </div>
                            <button class="btn btn-sm btn-outline-primary" onclick="readFile('${VulnCommon.escapeHtml(file.name)}')">
                                读取
                            </button>
                        </div>`;
                });
                html += '</div>';
                listContainer.innerHTML = html;
            }
            VulnCommon.showNotification('文件列表获取成功', 'success');
        }
    } catch (error) {
        VulnCommon.showNotification('获取文件列表失败: ' + error.message, 'danger');
    } finally {
        VulnCommon.showLoading(false);
    }
}

function handleResponse(data, filename, type) {
    const isSuccess = data.code === 200;
    const typeLabel = type === 'vuln' ? '漏洞接口' : '安全接口';
    const typeBadge = type === 'vuln' 
        ? '<span class="badge bg-danger">漏洞接口</span>'
        : '<span class="badge bg-success">安全接口</span>';

    let content = `
        <div class="mb-3">
            <span class="text-muted">文件名：</span>
            <code class="bg-light px-2 py-1 rounded">${VulnCommon.escapeHtml(filename)}</code>
        </div>`;

    if (data.data?.file_path) {
        content += `
            <div class="mb-3">
                <span class="text-muted">实际路径：</span>
                <code class="bg-light px-2 py-1 rounded">${VulnCommon.escapeHtml(data.data.file_path)}</code>
            </div>`;
    }

    if (data.data?.blocked_reason) {
        content += `
            <div class="alert alert-warning mb-3">
                <i class="fas fa-shield-alt me-2"></i>
                <strong>拦截原因：</strong>${VulnCommon.escapeHtml(data.data.blocked_reason)}
            </div>`;
    }

    if (data.data?.content) {
        content += `
            <div class="mb-3">
                <span class="text-muted">文件内容：</span>
                <pre class="bg-dark text-light p-3 rounded mt-2" style="max-height: 300px; overflow-y: auto;">${VulnCommon.escapeHtml(data.data.content)}</pre>
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
        title: `文件读取${isSuccess ? '成功' : '失败'}`,
        badge: typeBadge,
        content: content,
        type: type
    });

    VulnCommon.appendResult('outputContainer', html);
}

function handleError(filename, error) {
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
                    文件名: ${VulnCommon.escapeHtml(filename)}<br>
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
                <i class="fas fa-folder-open fa-4x mb-4 text-primary"></i>
                <h4 class="fw-bold text-dark">欢迎使用路径遍历漏洞测试平台</h4>
                <p class="mb-0 lead">请输入文件名或点击测试用例开始文件读取测试</p>
            </div>`;
    }
}
