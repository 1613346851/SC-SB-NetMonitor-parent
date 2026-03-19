/**
 * Java反序列化漏洞测试平台JavaScript逻辑
 */

const CONFIG = {
    ENDPOINTS: {
        VULN: '/target/deserial/parse',
        SAFE: '/target/deserial/safe-parse',
        GENERATE: '/target/deserial/generate-test-data',
        ALLOWED: '/target/deserial/allowed-classes'
    }
};

let generatedData = null;

document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
});

function initializeApp() {
    bindEventListeners();
    VulnCommon.updateStatus('ready', '就绪');
    console.log('Java反序列化漏洞测试平台初始化完成');
}

function bindEventListeners() {
    document.getElementById('vulnParseBtn')?.addEventListener('click', () => {
        const data = document.getElementById('serializedInput')?.value.trim();
        if (data) parseSerialized(data, 'vuln');
    });

    document.getElementById('safeParseBtn')?.addEventListener('click', () => {
        const data = document.getElementById('serializedInput')?.value.trim();
        if (data) parseSerialized(data, 'safe');
    });
}

async function generateTestData() {
    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '生成数据中...');

    try {
        const response = await fetch(CONFIG.ENDPOINTS.GENERATE);
        const data = await response.json();
        
        if (data.code === 200 && data.data?.test_data) {
            generatedData = data.data.test_data.serialized_base64;
            document.getElementById('serializedInput').value = generatedData;
            
            showResult(data, '生成测试数据');
            VulnCommon.showNotification('测试数据已生成并填入输入框', 'success');
        }
    } catch (error) {
        handleError(error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

async function testWithGeneratedData(type) {
    if (!generatedData) {
        VulnCommon.showNotification('请先生成测试数据', 'warning');
        return;
    }
    
    document.getElementById('serializedInput').value = generatedData;
    await parseSerialized(generatedData, type);
}

async function parseSerialized(data, type) {
    if (!data) {
        VulnCommon.showNotification('请输入序列化数据', 'warning');
        return;
    }

    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '反序列化中...');

    const endpoint = type === 'vuln' ? CONFIG.ENDPOINTS.VULN : CONFIG.ENDPOINTS.SAFE;

    try {
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain'
            },
            body: data
        });
        const result = await response.json();
        showResult(result, type === 'vuln' ? '漏洞反序列化' : '安全反序列化');
    } catch (error) {
        handleError(error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

async function getAllowedClasses() {
    VulnCommon.showLoading(true);
    try {
        const response = await fetch(CONFIG.ENDPOINTS.ALLOWED);
        const data = await response.json();
        
        if (data.code === 200) {
            let content = '<ul class="list-group">';
            data.data.allowed_classes.forEach(className => {
                content += `<li class="list-group-item"><code>${VulnCommon.escapeHtml(className)}</code></li>`;
            });
            content += '</ul>';

            const html = `
                <div class="result-card border-info">
                    <div class="result-header d-flex justify-content-between align-items-center">
                        <h6 class="mb-0">
                            <i class="fas fa-list text-info me-2"></i>
                            允许的类白名单
                        </h6>
                        <small class="text-muted">${VulnCommon.formatTime()}</small>
                    </div>
                    <div class="result-body">${content}</div>
                </div>`;

            VulnCommon.appendResult('outputContainer', html);
        }
    } catch (error) {
        handleError(error);
    } finally {
        VulnCommon.showLoading(false);
    }
}

function showResult(data, operation) {
    const isSuccess = data.code === 200;
    const isVuln = operation.includes('漏洞');

    let content = '';

    if (data.data?.original_object) {
        content += `
            <div class="mb-3">
                <span class="text-muted">原始对象：</span>
                <code class="bg-light px-2 py-1 rounded">${VulnCommon.escapeHtml(data.data.original_object)}</code>
            </div>`;
    }

    if (data.data?.serialized_base64) {
        content += `
            <div class="mb-3">
                <span class="text-muted">序列化数据：</span>
                <pre class="bg-light p-2 rounded mt-2" style="word-break: break-all; font-size: 0.75rem;">${VulnCommon.escapeHtml(data.data.serialized_base64.substring(0, 200))}...</pre>
            </div>`;
    }

    if (data.data?.deserialized_object) {
        const obj = data.data.deserialized_object;
        content += `
            <div class="mb-3">
                <span class="text-muted">反序列化结果：</span>
                <table class="table table-sm table-bordered mt-2">
                    <thead class="table-light"><tr><th>属性</th><th>值</th></tr></thead>
                    <tbody>`;
        
        for (const [key, value] of Object.entries(obj)) {
            content += `<tr><td>${VulnCommon.escapeHtml(key)}</td><td>${VulnCommon.escapeHtml(String(value))}</td></tr>`;
        }
        
        content += `</tbody></table></div>`;
    }

    if (data.data?.blocked_reason) {
        content += `
            <div class="alert alert-warning mb-3">
                <i class="fas fa-shield-alt me-2"></i>
                <strong>拦截原因：</strong>${VulnCommon.escapeHtml(data.data.blocked_reason)}
            </div>`;
    }

    if (data.data?.allowed_classes) {
        content += `
            <div class="alert alert-info mb-3">
                <i class="fas fa-list me-2"></i>
                <strong>允许的类：</strong><br>
                <small>${data.data.allowed_classes.map(c => VulnCommon.escapeHtml(c)).join('<br>')}</small>
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
        title: `${operation}${isSuccess ? '成功' : '失败'}`,
        badge: isVuln ? '<span class="badge bg-danger">漏洞接口</span>' : '<span class="badge bg-success">安全接口</span>',
        content: content,
        type: isVuln ? 'vuln' : 'safe'
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
                <i class="fas fa-exchange-alt fa-4x mb-4 text-primary"></i>
                <h4 class="fw-bold text-dark">欢迎使用Java反序列化漏洞测试平台</h4>
                <p class="mb-0 lead">请输入序列化数据或点击测试用例开始反序列化测试</p>
            </div>`;
    }
}
