/**
 * CSRF跨站请求伪造漏洞测试平台JavaScript逻辑
 */

const CONFIG = {
    ENDPOINTS: {
        VULN_UPDATE: '/target/csrf/update-name',
        SAFE_UPDATE: '/target/csrf/safe-update-name',
        GET_TOKEN: '/target/csrf/get-token',
        USER_INFO: '/target/csrf/user-info',
        ATTACK_PAGE: '/target/csrf/attack-page'
    },
    DEFAULT_USER_ID: 1
};

let currentToken = null;
let currentUser = null;

document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
    getUserInfo();
});

function initializeApp() {
    bindEventListeners();
    VulnCommon.updateStatus('ready', '就绪');
    console.log('CSRF漏洞测试平台初始化完成');
}

function bindEventListeners() {
    document.getElementById('vulnUpdateBtn')?.addEventListener('click', () => {
        const nickname = document.getElementById('nicknameInput')?.value.trim();
        if (nickname) updateNicknameVulnerable(nickname);
    });

    document.getElementById('safeUpdateBtn')?.addEventListener('click', () => {
        const nickname = document.getElementById('nicknameInput')?.value.trim();
        if (nickname) updateNicknameSafe(nickname);
    });
}

async function getUserInfo() {
    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '获取用户信息...');

    try {
        const response = await fetch(`${CONFIG.ENDPOINTS.USER_INFO}?userId=${CONFIG.DEFAULT_USER_ID}`);
        const data = await response.json();
        
        if (data.code === 200 && data.data?.user) {
            currentUser = data.data.user;
            displayUserInfo(currentUser);
            VulnCommon.showNotification('用户信息获取成功', 'success');
        }
    } catch (error) {
        handleError(error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

function displayUserInfo(user) {
    const panel = document.getElementById('userInfoPanel');
    if (panel) {
        panel.innerHTML = `
            <div class="user-info-item">
                <span class="text-muted">用户ID：</span>
                <strong>${VulnCommon.escapeHtml(user.userId || user.user_id || 'N/A')}</strong>
            </div>
            <div class="user-info-item">
                <span class="text-muted">用户名：</span>
                <strong>${VulnCommon.escapeHtml(user.username || 'N/A')}</strong>
            </div>
            <div class="user-info-item">
                <span class="text-muted">昵称：</span>
                <strong class="text-primary">${VulnCommon.escapeHtml(user.nickname || 'N/A')}</strong>
            </div>
            <div class="user-info-item">
                <span class="text-muted">邮箱：</span>
                <strong>${VulnCommon.escapeHtml(user.email || 'N/A')}</strong>
            </div>`;
    }
    
    document.getElementById('nicknameInput').value = user.nickname || '';
}

async function getCsrfToken() {
    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '获取Token...');

    try {
        const response = await fetch(`${CONFIG.ENDPOINTS.GET_TOKEN}?userId=${CONFIG.DEFAULT_USER_ID}`);
        const data = await response.json();
        
        if (data.code === 200 && data.data?.csrf_token) {
            currentToken = data.data.csrf_token;
            displayToken(currentToken);
            VulnCommon.showNotification('Token获取成功', 'success');
        }
    } catch (error) {
        handleError(error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

function displayToken(token) {
    const panel = document.getElementById('tokenPanel');
    if (panel) {
        panel.innerHTML = `
            <div class="token-display">
                <code class="d-block p-2 bg-light rounded" style="word-break: break-all; font-size: 0.75rem;">
                    ${VulnCommon.escapeHtml(token)}
                </code>
            </div>`;
    }
}

async function updateNicknameVulnerable(nickname) {
    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '修改中...');

    try {
        const formData = new FormData();
        formData.append('userId', CONFIG.DEFAULT_USER_ID);
        formData.append('nickname', nickname);

        const response = await fetch(CONFIG.ENDPOINTS.VULN_UPDATE, {
            method: 'POST',
            body: formData
        });
        const data = await response.json();
        showResult(data, '漏洞修改');
        
        if (data.code === 200) {
            getUserInfo();
        }
    } catch (error) {
        handleError(error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

async function updateNicknameSafe(nickname) {
    if (!currentToken) {
        VulnCommon.showNotification('请先获取CSRF Token', 'warning');
        return;
    }

    VulnCommon.showLoading(true);
    VulnCommon.updateStatus('executing', '安全修改中...');

    try {
        const formData = new FormData();
        formData.append('userId', CONFIG.DEFAULT_USER_ID);
        formData.append('nickname', nickname);
        formData.append('token', currentToken);

        const response = await fetch(CONFIG.ENDPOINTS.SAFE_UPDATE, {
            method: 'POST',
            body: formData
        });
        const data = await response.json();
        showResult(data, '安全修改');
        
        if (data.code === 200) {
            currentToken = null;
            document.getElementById('tokenPanel').innerHTML = '<small class="text-muted">Token已使用，请重新获取</small>';
            getUserInfo();
        }
    } catch (error) {
        handleError(error);
    } finally {
        VulnCommon.showLoading(false);
        VulnCommon.updateStatus('ready', '就绪');
    }
}

function openAttackPage() {
    const attackUrl = CONFIG.ENDPOINTS.ATTACK_PAGE;
    window.open(attackUrl, '_blank', 'width=600,height=400');
    VulnCommon.showNotification('已打开攻击演示页面', 'info');
}

function showResult(data, operation) {
    const isSuccess = data.code === 200;
    const isVuln = operation.includes('漏洞');

    let content = '';

    if (data.data?.user_id) {
        content += `
            <div class="mb-3">
                <span class="text-muted">用户ID：</span>
                <code class="bg-light px-2 py-1 rounded">${VulnCommon.escapeHtml(data.data.user_id)}</code>
            </div>`;
    }

    if (data.data?.old_nickname) {
        content += `
            <div class="mb-3">
                <span class="text-muted">原昵称：</span>
                <code class="bg-light px-2 py-1 rounded">${VulnCommon.escapeHtml(data.data.old_nickname)}</code>
            </div>`;
    }

    if (data.data?.new_nickname) {
        content += `
            <div class="mb-3">
                <span class="text-muted">新昵称：</span>
                <code class="bg-light px-2 py-1 rounded">${VulnCommon.escapeHtml(data.data.new_nickname)}</code>
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
                <i class="fas fa-user-shield fa-4x mb-4 text-primary"></i>
                <h4 class="fw-bold text-dark">欢迎使用CSRF漏洞测试平台</h4>
                <p class="mb-0 lead">请先获取用户信息，然后测试昵称修改功能</p>
            </div>`;
    }
}
