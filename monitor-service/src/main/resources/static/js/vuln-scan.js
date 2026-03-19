let scanPageState = {
    pollingTimer: null,
    currentResults: []
};

document.addEventListener('DOMContentLoaded', function() {
    bindScanModeUI();
    bindDetailModal();
    loadScanState();
});

function bindScanModeUI() {
    document.querySelectorAll('input[name="scanType"]').forEach(radio => {
        radio.addEventListener('change', () => {
            document.querySelectorAll('.scan-mode-option').forEach(item => item.classList.remove('active'));
            radio.closest('.scan-mode-option')?.classList.add('active');
        });
    });
}

function bindDetailModal() {
    const modal = document.getElementById('scanResultDetailModal');
    modal?.addEventListener('click', function(event) {
        if (event.target === this) {
            closeScanResultDetail();
        }
    });
}

async function loadScanState() {
    try {
        const [progress, result] = await Promise.all([
            http.get('/vuln/scan/progress'),
            http.get('/vuln/scan/result')
        ]);
        renderProgress(progress || {});
        renderScanResult(result || {});
        togglePolling((progress || {}).status);
    } catch (error) {
        console.error('加载扫描状态失败:', error);
        message.error('加载扫描状态失败：' + (error.message || '未知错误'));
    }
}

async function startScan() {
    const scanType = document.querySelector('input[name="scanType"]:checked')?.value || 'QUICK';
    try {
        const result = await http.post('/vuln/scan/start', { scanType });
        message.success(result.message || '扫描任务已启动');
        renderProgress(result);
        renderScanResult(result);
        togglePolling(result.status);
    } catch (error) {
        console.error('启动扫描失败:', error);
        message.error('启动扫描失败：' + (error.message || '未知错误'));
    }
}

async function controlScan(action) {
    try {
        const result = await http.post('/vuln/scan/control', { action });
        message.success(result.message || '操作成功');
        renderProgress(result);
        if (action === 'TERMINATE') {
            const latest = await http.get('/vuln/scan/result');
            renderScanResult(latest || result);
            togglePolling((latest || result).status);
            return;
        }
        togglePolling(result.status);
    } catch (error) {
        console.error('控制扫描失败:', error);
        message.error('控制扫描失败：' + (error.message || '未知错误'));
    }
}

async function manualSync() {
    try {
        const result = await http.post('/vuln/scan/sync');
        message.success(result.message || '同步完成');
        renderScanResult(result || {});
        renderProgress(result || {});
    } catch (error) {
        console.error('同步扫描结果失败:', error);
        message.error('同步扫描结果失败：' + (error.message || '未知错误'));
    }
}

function renderProgress(progress) {
    const status = progress.status || 'IDLE';
    const percent = Number(progress.progressPercent || 0);
    const completed = Number(progress.completedInterfaces || 0);
    const total = Number(progress.totalInterfaces || 0);
    const discovered = Number(progress.discoveredCount || 0);

    setText('scanStatusText', getStatusLabel(status));
    setText('scanLastMessage', progress.message || progress.lastMessage || '等待启动');
    const targetInput = document.getElementById('scanTarget');
    if (targetInput && progress.target) {
        targetInput.value = progress.target.replace('http://', '').replace('https://', '');
    }
    if (progress.scanType) {
        const radio = document.querySelector(`input[name="scanType"][value="${progress.scanType}"]`);
        if (radio) {
            radio.checked = true;
            document.querySelectorAll('.scan-mode-option').forEach(item => item.classList.remove('active'));
            radio.closest('.scan-mode-option')?.classList.add('active');
        }
    }


    setText('progressPercentValue', `${percent}%`);
    setText('progressCountValue', `${completed} / ${total}`);
    setText('discoveredCountValue', discovered);
    setText('scanStartTimeValue', progress.startTime || '--');
    setText('scanEndTimeValue', progress.endTime || '--');
    setText('scanDurationValue', formatDuration(progress.durationSeconds || 0));
    setText('scanCurrentStep', progress.currentStep || '等待启动');
    setText('scanProgressText', `${percent}%`);
    setText('scanSummaryText', progress.summary || progress.message || '等待启动');
    setText('syncedCountValue', scanPageState.currentResults.filter(item => item.synced).length);

    const statusEl = document.getElementById('scanStatusText');
    if (statusEl) {
        statusEl.className = `scan-status-card__value ${getStatusClass(status)}`;
    }

    const progressBar = document.getElementById('scanProgressBar');
    if (progressBar) {
        progressBar.style.width = `${percent}%`;
    }

    renderWarnings(progress.warnings || []);
    renderInterfaceTags(progress.interfaces || []);
    updateButtonStates(status, scanPageState.currentResults);
}

function renderScanResult(result) {
    const results = Array.isArray(result.results) ? result.results : [];
    const historyList = Array.isArray(result.history) ? result.history : [];
    scanPageState.currentResults = results;

    renderResultTable(results);
    renderHistoryTable(historyList);
    setText('syncedCountValue', results.filter(item => item.synced).length);
    updateButtonStates(result.status || 'IDLE', results);
}

function renderResultTable(results) {
    const tbody = document.getElementById('scanResultTableBody');
    if (!tbody) return;

    if (!results.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center">暂无结果</td></tr>';
        return;
    }

    tbody.innerHTML = results.map((item, index) => `
        <tr>
            <td>${escapeHtml(item.vulnName || '-')}</td>
            <td>${tableRenderer.renderAttackType(item.vulnType || 'UNKNOWN')}</td>
            <td>${tableRenderer.renderRiskLevel(item.vulnLevel || 'LOW')}</td>
            <td><code>${escapeHtml(item.vulnPath || '-')}</code></td>
            <td><span class="scan-payload-chip" title="${escapeHtml(item.payload || '')}">${escapeHtml(truncateText(item.payload || '-', 32))}</span></td>
            <td>${item.synced ? '<span class="tag success">已同步</span>' : '<span class="tag warning">待同步</span>'}</td>
            <td>${escapeHtml(item.detectedAt || '-')}</td>
            <td><button class="btn btn-primary btn-sm" onclick="viewScanResultDetail(${index})">详情</button></td>
        </tr>
    `).join('');
}

function renderHistoryTable(historyList) {
    const tbody = document.getElementById('scanHistoryTableBody');
    if (!tbody) return;

    if (!historyList.length) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center">暂无历史记录</td></tr>';
        return;
    }

    tbody.innerHTML = historyList.map(item => `
        <tr>
            <td><code>${escapeHtml(item.taskId || '-')}</code></td>
            <td>${escapeHtml(item.scanType || '-')}</td>
            <td><span class="tag ${getStatusTagClass(item.status)}">${getStatusLabel(item.status)}</span></td>
            <td>${item.discoveredCount || 0}</td>
            <td>${item.completedInterfaces || 0} / ${item.totalInterfaces || 0}</td>
            <td>${escapeHtml(item.startTime || '-')}</td>
            <td>${escapeHtml(item.endTime || '-')}</td>
            <td title="${escapeHtml(item.summary || '')}">${escapeHtml(truncateText(item.summary || '-', 36))}</td>
        </tr>
    `).join('');
}

function renderWarnings(warnings) {
    const container = document.getElementById('scanWarnings');
    if (!container) return;

    if (!warnings.length) {
        container.innerHTML = '';
        return;
    }

    container.innerHTML = warnings.map(item => `<div class="scan-warning-item">${escapeHtml(item)}</div>`).join('');
}

function renderInterfaceTags(interfaces) {
    const container = document.getElementById('interfaceTagList');
    if (!container) return;

    if (!interfaces.length) {
        container.innerHTML = '<span class="tag info">暂无扫描计划</span>';
        return;
    }

    container.innerHTML = interfaces.map(item => `
        <span class="interface-tag" title="${escapeHtml(item.title || '')}">
            <strong>${escapeHtml(item.method || 'GET')}</strong>
            <span>${escapeHtml(item.path || '')}</span>
        </span>
    `).join('');
}

function updateButtonStates(status, results) {
    const hasResults = Array.isArray(results) && results.length > 0;
    const isRunning = status === 'RUNNING';
    const isPaused = status === 'PAUSED';

    setDisabled('startScanBtn', isRunning || isPaused);
    setDisabled('pauseScanBtn', !isRunning);
    setDisabled('resumeScanBtn', !isPaused);
    setDisabled('terminateScanBtn', !(isRunning || isPaused));
    setDisabled('syncScanBtn', !hasResults);
}

function togglePolling(status) {
    const active = status === 'RUNNING' || status === 'PAUSED';
    if (scanPageState.pollingTimer) {
        clearInterval(scanPageState.pollingTimer);
        scanPageState.pollingTimer = null;
    }

    if (active) {
        scanPageState.pollingTimer = setInterval(async () => {
            try {
                const [progress, result] = await Promise.all([
                    http.get('/vuln/scan/progress'),
                    http.get('/vuln/scan/result')
                ]);
                renderProgress(progress || {});
                renderScanResult(result || {});
                if (!['RUNNING', 'PAUSED'].includes((progress || {}).status)) {
                    togglePolling((progress || {}).status);
                }
            } catch (error) {
                console.error('轮询扫描进度失败:', error);
                togglePolling('IDLE');
            }
        }, 1500);
    }
}

function viewScanResultDetail(index) {
    const result = scanPageState.currentResults[index];
    if (!result) {
        message.error('扫描结果不存在');
        return;
    }

    const content = document.getElementById('scanResultDetailContent');
    if (!content) return;

    content.innerHTML = `
        <div class="scan-detail-grid">
            <div>
                <p><strong>漏洞名称：</strong>${escapeHtml(result.vulnName || '-')}</p>
                <p><strong>漏洞类型：</strong>${escapeHtml(result.vulnType || '-')}</p>
                <p><strong>风险等级：</strong>${tableRenderer.renderRiskLevel(result.vulnLevel || 'LOW')}</p>
                <p><strong>请求方法：</strong>${escapeHtml(result.requestMethod || '-')}</p>
                <p><strong>漏洞路径：</strong><code>${escapeHtml(result.vulnPath || '-')}</code></p>
                <p><strong>匹配关键字：</strong>${escapeHtml(result.matchedKeyword || '-')}</p>
            </div>
            <div>
                <p><strong>发现时间：</strong>${escapeHtml(result.detectedAt || '-')}</p>
                <p><strong>来源：</strong>${escapeHtml(result.source || '-')}</p>
                <p><strong>同步状态：</strong>${result.synced ? '<span class="tag success">已同步</span>' : '<span class="tag warning">待同步</span>'}</p>
                <p><strong>漏洞ID：</strong>${escapeHtml(result.vulnerabilityId ? String(result.vulnerabilityId) : '-')}</p>
                <p><strong>AI研判预留：</strong>${escapeHtml(result.aiVerdict || '当前未启用 AI 自动研判')}</p>
            </div>
        </div>
        <div class="scan-detail-section">
            <p><strong>Payload：</strong></p>
            <pre class="scan-code-block">${escapeHtml(result.payload || '-')}</pre>
        </div>
        <div class="scan-detail-section">
            <p><strong>漏洞描述：</strong></p>
            <p>${escapeHtml(result.description || '-')}</p>
        </div>
        <div class="scan-detail-section">
            <p><strong>修复建议：</strong></p>
            <p>${escapeHtml(result.fixSuggestion || '-')}</p>
        </div>
        <div class="scan-detail-section">
            <p><strong>响应摘要：</strong></p>
            <pre class="scan-code-block">${escapeHtml(result.responseSnippet || '-')}</pre>
        </div>
    `;

    document.getElementById('scanResultDetailModal').style.display = 'flex';
}

function closeScanResultDetail() {
    const modal = document.getElementById('scanResultDetailModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

function setText(id, value) {
    const el = document.getElementById(id);
    if (el) {
        el.textContent = value;
    }
}

function setDisabled(id, disabled) {
    const el = document.getElementById(id);
    if (el) {
        el.disabled = !!disabled;
    }
}

function getStatusLabel(status) {
    const statusMap = {
        IDLE: '空闲',
        RUNNING: '运行中',
        PAUSED: '已暂停',
        COMPLETED: '已完成',
        TERMINATED: '已终止',
        FAILED: '失败'
    };
    return statusMap[status] || '未知';
}

function getStatusClass(status) {
    const classMap = {
        IDLE: 'status-idle',
        RUNNING: 'status-running',
        PAUSED: 'status-paused',
        COMPLETED: 'status-completed',
        TERMINATED: 'status-terminated',
        FAILED: 'status-failed'
    };
    return classMap[status] || 'status-idle';
}

function getStatusTagClass(status) {
    const classMap = {
        RUNNING: 'info',
        PAUSED: 'warning',
        COMPLETED: 'success',
        TERMINATED: 'danger',
        FAILED: 'danger',
        IDLE: 'info'
    };
    return classMap[status] || 'info';
}

function formatDuration(seconds) {
    const totalSeconds = Number(seconds || 0);
    if (totalSeconds < 60) {
        return `${totalSeconds}s`;
    }
    const minutes = Math.floor(totalSeconds / 60);
    const remain = totalSeconds % 60;
    return `${minutes}m ${remain}s`;
}

function truncateText(text, length) {
    if (!text || text.length <= length) {
        return text;
    }
    return `${text.slice(0, length)}...`;
}

function escapeHtml(text) {
    if (text === null || text === undefined) {
        return '';
    }
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
